package com.lizongying.mytv

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
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * ExoPlayer DataSource for UDP multicast streaming (RTP/UDP IPTV)
 *
 * Receives UDP multicast packets, handles RTP header stripping if present,
 * and feeds MPEG-TS payload to ExoPlayer's buffer management.
 *
 * Key advantage over ijkplayer: uses ExoPlayer's advanced DefaultLoadControl
 * for jitter smoothing, similar to udpxy->HTTP approach.
 */
class UdpDataSource : BaseDataSource(/* isNetwork = */ true) {

    companion object {
        private const val TAG = "UdpDataSource"
        private const val SOCKET_TIMEOUT_MS = 8000
        private const val PACKET_QUEUE_SIZE = 256
        private const val RTP_HEADER_SIZE = 12
        private const val RTP_VERSION_MASK = 0xC0.toByte()
        private const val RTP_VERSION_2 = 0x80.toByte()
        private const val RTP_PAYLOAD_MPEG_TS = 33

        // Sequence number reordering window
        private const val SEQ_BUFFER_SIZE = 128
        private const val SEQ_MAX_WAIT_MS = 50L
        private const val SEQ_ACCEPTABLE_GAP = 8
    }

    private var socket: MulticastSocket? = null
    private var uri: Uri? = null
    private var opened = false

    // Raw packet queue from receiver thread
    private val rawPacketQueue = ArrayBlockingQueue<DatagramPacket>(PACKET_QUEUE_SIZE)

    // Sorted packet delivery using TreeMap for sequence-number ordering
    private val sortedPackets = java.util.TreeMap<Int, DatagramPacket>()
    private var nextExpectedSeq: Int? = null

    // Current packet being consumed
    private var currentPacket: DatagramPacket? = null
    private var currentPacketOffset = 0

    // RTP detection state
    private var isRtpStream: Boolean? = null

    // Receiver thread
    private var receiverThread: Thread? = null
    private var receiverRunning = false

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri

        val url = uri.toString()
        Log.i(TAG, "Opening UDP/RTP stream: $url")

        // Parse address and port from URL
        // Supported formats: udp://@239.0.0.1:1234, rtp://239.0.0.1:1234
        val (address, port) = parseUrl(url)
            ?: throw IOException("Invalid UDP/RTP URL: $url")

        try {
            socket = MulticastSocket(port).apply {
                soTimeout = SOCKET_TIMEOUT_MS
                receiveBufferSize = 8 * 1024 * 1024 // 8MB socket buffer
            }

            val inetAddress = InetAddress.getByName(address)
            val group = InetSocketAddress(inetAddress, port)
            socket?.joinGroup(group, null)
            Log.d(TAG, "Joined multicast group: $address:$port")

            // Start receiver thread
            receiverRunning = true
            receiverThread = Thread({ receiveLoop() }, "UdpReceiver").apply { start() }

            opened = true
            transferStarted(dataSpec)

            // Return C.LENGTH_UNSET for unbounded live streams
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
            if (currentPacket != null) {
                val payload = getPayload(currentPacket!!)
                val available = payload.size - currentPacketOffset

                if (available > 0) {
                    val toRead = minOf(remaining, available)
                    System.arraycopy(payload, currentPacketOffset, buffer, currentOffset, toRead)
                    currentPacketOffset += toRead
                    currentOffset += toRead
                    remaining -= toRead
                    totalRead += toRead
                    bytesTransferred(toRead)
                    continue
                } else {
                    // Current packet exhausted
                    currentPacket = null
                    currentPacketOffset = 0
                }
            }

            // Get next packet from queue (with sequence number sorting for RTP)
            currentPacket = try {
                getNextPacket()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }

            if (currentPacket == null) {
                // Timeout - if we've read some data, return it; otherwise try again
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
        rawPacketQueue.clear()
        sortedPackets.clear()
        nextExpectedSeq = null
        sortWaitStartTime = 0
        currentPacket = null
        currentPacketOffset = 0
        isRtpStream = null

        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun receiveLoop() {
        while (receiverRunning) {
            try {
                // Allocate new buffer for each packet to avoid data races
                val buffer = ByteArray(65535)
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)

                // Copy received data to dedicated buffer
                val packetLength = packet.length
                val packetOffset = packet.offset
                val dedicatedBuffer = ByteArray(packetLength)
                System.arraycopy(packet.data, packetOffset, dedicatedBuffer, 0, packetLength)
                val dedicatedPacket = DatagramPacket(dedicatedBuffer, packetLength)

                // Detect RTP on first packet
                if (isRtpStream == null && packetLength > 0) {
                    isRtpStream = detectRtp(dedicatedBuffer, 0, packetLength)
                    Log.i(TAG, "Stream type detected: ${if (isRtpStream == true) "RTP" else "Raw UDP"}, pktSize=$packetLength, first ${minOf(packetLength, 32)} bytes: ${dedicatedBuffer.take(minOf(packetLength, 32)).joinToString(" ") { "%02X".format(it) }}")
                } else if (isRtpStream != null && rawPacketQueue.size % 100 == 0) {
                    // Periodic debug: check payload starts with TS sync byte 0x47
                    val headerSize = getRtpHeaderSize(dedicatedBuffer, 0, packetLength)
                    if (packetLength > headerSize) {
                        val syncByte = dedicatedBuffer[headerSize]
                        if (syncByte.toInt() != 0x47) {
                            Log.w(TAG, "TS sync byte mismatch! Expected 0x47, got 0x${"%02X".format(syncByte)}, headerSize=$headerSize, pktSize=$packetLength")
                        }
                    }
                }

                // Offer to raw queue (drop if full to avoid memory bloat)
                if (!rawPacketQueue.offer(dedicatedPacket)) {
                    Log.w(TAG, "Raw packet queue full, dropping packet (size=$packetLength)")
                }
            } catch (e: Exception) {
                if (receiverRunning) {
                    Log.e(TAG, "Receive error: ${e.message}")
                }
            }
        }
    }

    /**
     * Parse RTP sequence number from packet (big-endian, bytes 2-3)
     */
    private fun getRtpSequenceNumber(data: ByteArray, offset: Int, length: Int): Int? {
        if (length < 4) return null
        if ((data[offset].toInt() and 0xC0) != 0x80) return null // Not RTP v2
        return ((data[offset + 2].toInt() and 0xFF) shl 8) or
               (data[offset + 3].toInt() and 0xFF)
    }

    /**
     * Get next packet. For live streaming, direct delivery is preferred
     * over reordering to minimize latency.
     */
    private fun getNextPacket(): DatagramPacket? {
        return rawPacketQueue.poll(1000, TimeUnit.MILLISECONDS)
    }

    private var sortWaitStartTime: Long = 0

    /**
     * Sequence-number-aware packet delivery.
     * Buffers packets and delivers them in sequence number order.
     * Handles packet loss by skipping missing sequence numbers after timeout.
     */
    private fun getNextSortedPacket(): DatagramPacket? {
        // First, drain raw queue into sorted buffer
        while (true) {
            val raw = rawPacketQueue.poll() ?: break
            val seq = getRtpSequenceNumber(raw.data, 0, raw.length)
            if (seq != null) {
                sortedPackets[seq] = raw
            } else {
                // Non-RTP packet, deliver immediately
                return raw
            }
        }

        // If we have sorted packets, try to deliver in order
        if (sortedPackets.isNotEmpty()) {
            if (nextExpectedSeq == null) {
                // First packet: use lowest sequence number
                val firstSeq = sortedPackets.firstKey()
                nextExpectedSeq = firstSeq
                sortWaitStartTime = 0
                Log.d(TAG, "First RTP seq=$firstSeq, bufferSize=${sortedPackets.size}")
            }

            val expected = nextExpectedSeq!!

            // Check if expected packet is available
            if (sortedPackets.containsKey(expected)) {
                val packet = sortedPackets.remove(expected)
                nextExpectedSeq = (expected + 1) and 0xFFFF
                sortWaitStartTime = 0
                return packet
            }

            // Check gap size to expected packet
            val firstSeq = sortedPackets.firstKey()
            val gap = if (firstSeq >= expected) {
                firstSeq - expected
            } else {
                // Wrap around
                (0xFFFF - expected) + firstSeq + 1
            }

            // If gap is small, wait a bit for the missing packet
            // If gap is large or we've waited too long, deliver what we have
            val now = System.currentTimeMillis()
            if (sortWaitStartTime == 0L) {
                sortWaitStartTime = now
            }
            val waited = now - sortWaitStartTime

            if (gap <= SEQ_ACCEPTABLE_GAP && waited < SEQ_MAX_WAIT_MS) {
                // Small gap, wait a bit more
                return rawPacketQueue.poll(10, TimeUnit.MILLISECONDS)
            }

            // Gap too large or waited too long: deliver oldest available packet
            val packet = sortedPackets.remove(firstSeq)
            if (gap > 0 && gap < 100) {
                Log.w(TAG, "Skip gap=$gap, delivering seq=$firstSeq (expected=$expected), waited=${waited}ms")
            }
            nextExpectedSeq = (firstSeq + 1) and 0xFFFF
            sortWaitStartTime = 0
            return packet
        }

        // No packets buffered, wait for more
        return rawPacketQueue.poll(10, TimeUnit.MILLISECONDS)
    }

    private fun detectRtp(data: ByteArray, offset: Int, length: Int): Boolean {
        if (length < RTP_HEADER_SIZE) return false
        val firstByte = data[offset]
        val secondByte = data[offset + 1]

        // Check RTP version 2 (Kotlin Byte bitwise ops need Int conversion)
        if ((firstByte.toInt() and 0xC0) != 0x80) return false

        // Check payload type (33 = MPEG-TS)
        val payloadType = secondByte.toInt() and 0x7F
        Log.d(TAG, "RTP detection: version=${(firstByte.toInt() and 0xC0) shr 6}, PT=$payloadType")

        return payloadType == RTP_PAYLOAD_MPEG_TS
    }

    /**
     * Calculate actual RTP header size including CSRC and extension
     */
    private fun getRtpHeaderSize(data: ByteArray, offset: Int, length: Int): Int {
        if (length < 12) return 0
        val firstByte = data[offset]
        val cc = firstByte.toInt() and 0x0F          // CSRC count
        val hasExtension = (firstByte.toInt() shr 4) and 1  // Extension flag

        var headerSize = 12 + cc * 4

        // Handle RTP extension header
        if (hasExtension == 1 && length >= headerSize + 4) {
            val extensionLength = ((data[offset + headerSize + 2].toInt() and 0xFF) shl 8) or
                                  (data[offset + headerSize + 3].toInt() and 0xFF)
            headerSize += 4 + extensionLength * 4
        }

        return headerSize
    }

    private fun getPayload(packet: DatagramPacket): ByteArray {
        val data = packet.data
        val length = packet.length

        return if (isRtpStream == true) {
            val headerSize = getRtpHeaderSize(data, 0, length)
            if (headerSize > 0 && length > headerSize) {
                val payloadLength = length - headerSize
                val payload = ByteArray(payloadLength)
                System.arraycopy(data, headerSize, payload, 0, payloadLength)
                payload
            } else {
                data
            }
        } else {
            // Raw UDP data - packet already has dedicated buffer with offset=0
            data
        }
    }

    private fun parseUrl(url: String): Pair<String, Int>? {
        return when {
            url.startsWith("rtp://", ignoreCase = true) -> {
                val remainder = url.removePrefix("rtp://")
                parseAddressPort(remainder)
            }
            url.startsWith("udp://@", ignoreCase = true) -> {
                val remainder = url.removePrefix("udp://@")
                parseAddressPort(remainder)
            }
            url.startsWith("udp://", ignoreCase = true) -> {
                val remainder = url.removePrefix("udp://")
                parseAddressPort(remainder)
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
        } catch (e: Exception) {
            // Ignore leave group errors
        }
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        socket = null
    }
}
