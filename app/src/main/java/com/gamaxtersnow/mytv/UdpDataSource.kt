package com.gamaxtersnow.mytv

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * ExoPlayer DataSource for UDP multicast streaming (RTP/UDP IPTV)
 *
 * Optimized for high-bitrate 4K HEVC streams:
 * - Large packet queue (8192) to handle burst traffic
 * - Zero-copy payload delivery where possible
 * - ByteArray pool to reduce GC pressure
 */
class UdpDataSource : BaseDataSource(/* isNetwork = */ true) {

    companion object {
        private const val TAG = "UdpDataSource"
        private const val SOCKET_TIMEOUT_MS = 8000
        private const val RTP_HEADER_SIZE = 12
        private const val RTP_PAYLOAD_MPEG_TS = 33

        // ByteArray pool to reduce GC pressure for high packet rates
        private const val POOL_SIZE = 256
        private val bufferPool = LinkedBlockingQueue<ByteArray>()

        init {
            // Pre-allocate pool buffers
            repeat(POOL_SIZE) {
                bufferPool.offer(ByteArray(2048))
            }
        }
    }

    private var socket: MulticastSocket? = null
    private var uri: Uri? = null
    private var opened = false

    // Packet data holder - avoids DatagramPacket object overhead
    private data class Packet(val data: ByteArray, val length: Int, val rtpHeaderSize: Int = 0, val seqNumber: Int = -1)

    // Large bounded queue: 65536 packets * ~2KB = ~128MB max
    // Prevents OOM while giving TsExtractor enough data for 4K track detection
    private val packetQueue = LinkedBlockingQueue<Packet>(65536)

    // Current packet being consumed
    private var currentPacket: Packet? = null
    private var currentPacketOffset = 0

    // RTP detection state
    private var isRtpStream: Boolean? = null

    // Receiver thread
    private var receiverThread: Thread? = null
    private var receiverRunning = false

    // ---- RTP sequence reordering (lightweight) ----
    // Small window to handle network jitter without adding significant latency.
    // HEVC reference frames span ~100-200 RTP packets; a 64-packet window
    // handles typical WiFi jitter while keeping memory low.
    private val sortedPackets = java.util.TreeMap<Int, Packet>()
    private var nextExpectedSeq: Int? = null
    private var lastSeqReceived: Int? = null
    private val SEQ_BUFFER_SIZE = 128

    // Stats for diagnostics
    private var packetsReceived = 0L
    private var packetsDropped = 0L
    private var packetsOutOfOrder = 0L
    private var packetsLostDetected = 0L
    private var lastStatsTime = 0L

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri

        val url = uri.toString()
        Log.i(TAG, "Opening UDP/RTP stream: $url")

        val (address, port) = parseUrl(url)
            ?: throw IOException("Invalid UDP/RTP URL: $url")

        try {
            socket = MulticastSocket(port).apply {
                soTimeout = SOCKET_TIMEOUT_MS
                // 16MB socket receive buffer for high bitrate 4K streams
                receiveBufferSize = 16 * 1024 * 1024
            }

            val inetAddress = InetAddress.getByName(address)
            val group = InetSocketAddress(inetAddress, port)
            socket?.joinGroup(group, null)
            Log.d(TAG, "Joined multicast group: $address:$port, socketBuffer=${socket?.receiveBufferSize}")

            // Reset stats and ordering state
            packetsReceived = 0
            packetsDropped = 0
            packetsOutOfOrder = 0
            packetsLostDetected = 0
            lastStatsTime = System.currentTimeMillis()
            nextExpectedSeq = null
            lastSeqReceived = null
            sortedPackets.clear()

            // Start receiver thread with high priority to minimize kernel buffer overflow
            receiverRunning = true
            receiverThread = Thread({ receiveLoop() }, "UdpReceiver").apply {
                // High priority for receiver thread to keep socket buffer drained
                priority = Thread.MAX_PRIORITY
                start()
            }

            opened = true
            transferStarted(dataSpec)

            return C.LENGTH_UNSET.toLong()
        } catch (e: Exception) {
            closeSocket()
            throw IOException("Failed to open UDP/RTP stream: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (!opened) return C.RESULT_END_OF_INPUT

        var totalRead = 0
        var currentOffset = offset
        var remaining = readLength

        while (remaining > 0) {
            // Check if we have data from current packet
            val pkt = currentPacket
            if (pkt != null) {
                val payloadOffset = pkt.rtpHeaderSize
                val payloadSize = pkt.length - pkt.rtpHeaderSize
                val available = payloadSize - currentPacketOffset

                if (available > 0) {
                    val toRead = minOf(remaining, available)
                    System.arraycopy(pkt.data, payloadOffset + currentPacketOffset, buffer, currentOffset, toRead)
                    currentPacketOffset += toRead
                    currentOffset += toRead
                    remaining -= toRead
                    totalRead += toRead
                    bytesTransferred(toRead)
                    continue
                } else {
                    // Current packet exhausted - return buffer to pool
                    returnBuffer(currentPacket!!.data)
                    currentPacket = null
                    currentPacketOffset = 0
                }
            }

            // Get next packet from queue - use 1s timeout to avoid premature END_OF_INPUT
            // which causes ExoPlayer Extractor to think the stream has ended
            currentPacket = try {
                packetQueue.poll(1000, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }

            if (currentPacket == null) {
                if (totalRead > 0) break
                if (!receiverRunning) return C.RESULT_END_OF_INPUT
                continue
            }

            currentPacketOffset = 0
        }

        return if (totalRead > 0) totalRead else C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        receiverRunning = false
        receiverThread?.interrupt()
        receiverThread?.join(500)
        receiverThread = null

        closeSocket()

        // Return all queued packets to pool
        while (true) {
            val pkt = packetQueue.poll() ?: break
            returnBuffer(pkt.data)
        }
        // Return sorted cache packets to pool
        for (pkt in sortedPackets.values) {
            returnBuffer(pkt.data)
        }
        sortedPackets.clear()
        currentPacket?.let { returnBuffer(it.data) }
        currentPacket = null
        currentPacketOffset = 0
        isRtpStream = null
        nextExpectedSeq = null
        lastSeqReceived = null

        if (opened) {
            opened = false
            transferEnded()
        }
    }

    // Reusable datagram to avoid per-packet allocation
    private var reuseDatagram: DatagramPacket? = null
    // Cached RTP header size after first packet
    private var cachedRtpHeaderSize: Int? = null

    private fun receiveLoop() {
        // Pre-allocate reusable datagram
        val dg = DatagramPacket(ByteArray(0), 0)
        reuseDatagram = dg

        while (receiverRunning) {
            try {
                // Use pooled buffer or allocate new one
                val buffer = acquireBuffer()
                // Reuse datagram with new buffer (avoid allocation)
                dg.setData(buffer, 0, buffer.size)

                socket?.receive(dg)

                val packetLength = dg.length
                if (packetLength <= 0) continue

                packetsReceived++

                // Detect RTP on first packet only
                if (isRtpStream == null) {
                    isRtpStream = detectRtp(buffer, 0, packetLength)
                    cachedRtpHeaderSize = if (isRtpStream == true) {
                        calculateRtpHeaderSize(buffer, 0, packetLength)
                    } else 0
                    Log.i(TAG, "Stream type: ${if (isRtpStream == true) "RTP" else "Raw UDP"}, " +
                            "pktSize=$packetLength, queueSize=${packetQueue.size}")
                }

                val rtpHeaderSize = cachedRtpHeaderSize ?: 0
                val seqNumber = if (isRtpStream == true) extractRtpSequenceNumber(buffer, 0) else -1

                // Create packet holder
                val packet = Packet(buffer, packetLength, rtpHeaderSize, seqNumber)

                // For RTP streams, use sequence-number ordering to handle jitter.
                // HEVC reference frames span many packets; out-of-order delivery
                // causes "Ref frame lost" and AV sync drift.
                if (isRtpStream == true && seqNumber >= 0) {
                    handleSequencedPacket(seqNumber, packet)
                } else {
                    if (!packetQueue.offer(packet)) {
                        returnBuffer(buffer)
                        packetsDropped++
                        logDropStats(packetLength)
                    }
                }
            } catch (e: Exception) {
                if (receiverRunning) {
                    Log.e(TAG, "Receive error: ${e.message}")
                }
            }
        }
    }

    /**
     * Handle a sequenced RTP packet with lightweight reordering.
     * Uses a 64-packet window to absorb network jitter without adding
     * perceptible latency.
     */
    private fun handleSequencedPacket(seqNumber: Int, packet: Packet) {
        lastSeqReceived = seqNumber

        if (nextExpectedSeq == null) {
            // First packet: establish baseline
            nextExpectedSeq = (seqNumber + 1) and 0xFFFF
            if (!packetQueue.offer(packet)) {
                returnBuffer(packet.data)
                packetsDropped++
            }
            return
        }

        val expected = nextExpectedSeq!!
        // Forward diff: how far ahead seqNumber is from expected
        val forwardDiff = (seqNumber - expected) and 0xFFFF
        // Backward diff: how far behind seqNumber is from expected
        val backwardDiff = (expected - seqNumber) and 0xFFFF

        when {
            forwardDiff == 0 -> {
                // In-order packet: output immediately and flush any cached successors
                nextExpectedSeq = (expected + 1) and 0xFFFF
                if (!packetQueue.offer(packet)) {
                    returnBuffer(packet.data)
                    packetsDropped++
                } else {
                    flushSortedPackets()
                }
            }
            forwardDiff in 1..SEQ_BUFFER_SIZE -> {
                // Future packet (within window): cache for reordering
                packetsOutOfOrder++
                val old = sortedPackets.put(seqNumber, packet)
                if (old != null) {
                    // Duplicate sequence number: keep newer, return older buffer
                    returnBuffer(old.data)
                }
                // If cache grows too large, force-output the oldest packet
                // to prevent memory bloat on sustained jitter
                if (sortedPackets.size > SEQ_BUFFER_SIZE) {
                    val oldestSeq = sortedPackets.firstKey()
                    val oldestPacket = sortedPackets.remove(oldestSeq)
                    if (oldestPacket != null) {
                        nextExpectedSeq = (oldestSeq + 1) and 0xFFFF
                        if (!packetQueue.offer(oldestPacket)) {
                            returnBuffer(oldestPacket.data)
                            packetsDropped++
                        } else {
                            flushSortedPackets()
                        }
                    }
                }
            }
            backwardDiff in 1..SEQ_BUFFER_SIZE -> {
                // Late packet (arrived after we already passed this sequence number).
                // The frame it belongs to is already broken, so drop it to avoid
                // feeding discontinuous data to the decoder.
                returnBuffer(packet.data)
                packetsDropped++
            }
            else -> {
                // Large gap in both directions: likely a sequence number wrap-around
                // or severe burst loss. Reset state to avoid stalling.
                packetsLostDetected += forwardDiff.toLong()
                Log.w(TAG, "Large seq gap: expected=$expected, got=$seqNumber, " +
                        "forwardDiff=$forwardDiff, backwardDiff=$backwardDiff. " +
                        "Resetting reorder state.")
                // Flush cached packets in order before reset
                while (sortedPackets.isNotEmpty()) {
                    val oldestSeq = sortedPackets.firstKey()
                    val oldestPacket = sortedPackets.remove(oldestSeq)
                    if (oldestPacket != null) {
                        if (!packetQueue.offer(oldestPacket)) {
                            returnBuffer(oldestPacket.data)
                            packetsDropped++
                        }
                    }
                }
                nextExpectedSeq = (seqNumber + 1) and 0xFFFF
                if (!packetQueue.offer(packet)) {
                    returnBuffer(packet.data)
                    packetsDropped++
                }
            }
        }
    }

    /**
     * Flush any cached packets that are now in-order.
     */
    private fun flushSortedPackets() {
        while (true) {
            val expected = nextExpectedSeq ?: break
            val pkt = sortedPackets.remove(expected)
            if (pkt != null) {
                nextExpectedSeq = (expected + 1) and 0xFFFF
                if (!packetQueue.offer(pkt)) {
                    returnBuffer(pkt.data)
                    packetsDropped++
                }
            } else {
                break
            }
        }
    }

    /**
     * Extract 16-bit RTP sequence number (bytes 2-3 of header).
     */
    private fun extractRtpSequenceNumber(data: ByteArray, offset: Int): Int {
        if (data.size < offset + 4) return -1
        return ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun logDropStats(@Suppress("UNUSED_PARAMETER") packetLength: Int) {
        val now = System.currentTimeMillis()
        if (now - lastStatsTime >= 5000) {
            val dropRate = if (packetsReceived > 0) {
                (packetsDropped * 100 / packetsReceived).toInt()
            } else 0
            val reorderRate = if (packetsReceived > 0) {
                (packetsOutOfOrder * 100 / packetsReceived).toInt()
            } else 0
            Log.w(TAG, "Stats: received=$packetsReceived, dropped=$packetsDropped ($dropRate%), " +
                    "outOfOrder=$packetsOutOfOrder ($reorderRate%), lostDetected=$packetsLostDetected, " +
                    "queueSize=${packetQueue.size}, sortedSize=${sortedPackets.size}")
            lastStatsTime = now
        }
    }

    private fun acquireBuffer(): ByteArray {
        return bufferPool.poll() ?: ByteArray(2048)
    }

    private fun returnBuffer(buffer: ByteArray) {
        if (buffer.size == 2048) {
            bufferPool.offer(buffer)
        }
    }

    private fun detectRtp(data: ByteArray, offset: Int, length: Int): Boolean {
        if (length < RTP_HEADER_SIZE) return false
        if ((data[offset].toInt() and 0xC0) != 0x80) return false
        val payloadType = data[offset + 1].toInt() and 0x7F
        return payloadType == RTP_PAYLOAD_MPEG_TS
    }

    private fun calculateRtpHeaderSize(data: ByteArray, offset: Int, length: Int): Int {
        if (length < 12) return 0
        val cc = data[offset].toInt() and 0x0F
        val hasExtension = (data[offset].toInt() shr 4) and 1
        var headerSize = 12 + cc * 4
        if (hasExtension == 1 && length >= headerSize + 4) {
            val extensionLength = ((data[offset + headerSize + 2].toInt() and 0xFF) shl 8) or
                    (data[offset + headerSize + 3].toInt() and 0xFF)
            headerSize += 4 + extensionLength * 4
        }
        return headerSize
    }

    private fun parseUrl(url: String): Pair<String, Int>? {
        return when {
            url.startsWith("rtp://", ignoreCase = true) -> {
                parseAddressPort(url.removePrefix("rtp://"))
            }
            url.startsWith("udp://@", ignoreCase = true) -> {
                parseAddressPort(url.removePrefix("udp://@"))
            }
            url.startsWith("udp://", ignoreCase = true) -> {
                parseAddressPort(url.removePrefix("udp://"))
            }
            else -> null
        }
    }

    private fun parseAddressPort(remainder: String): Pair<String, Int>? {
        val parts = remainder.split(":")
        if (parts.size != 2) return null
        val address = parts[0]
        val port = parts[1].toIntOrNull() ?: return null
        return address to port
    }

    private fun closeSocket() {
        try {
            val groupAddress = uri?.host
            if (groupAddress != null) {
                val inetAddress = InetAddress.getByName(groupAddress)
                socket?.leaveGroup(inetAddress)
            }
        } catch (e: Exception) { /* ignore */ }
        try {
            socket?.close()
        } catch (e: Exception) { /* ignore */ }
        socket = null
    }
}
