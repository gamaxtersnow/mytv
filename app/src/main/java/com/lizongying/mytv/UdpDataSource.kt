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
    private data class Packet(val data: ByteArray, val length: Int, val rtpHeaderSize: Int = 0)

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

    // Stats for diagnostics
    private var packetsReceived = 0L
    private var packetsDropped = 0L
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

            // Reset stats
            packetsReceived = 0
            packetsDropped = 0
            lastStatsTime = System.currentTimeMillis()

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
        currentPacket?.let { returnBuffer(it.data) }
        currentPacket = null
        currentPacketOffset = 0
        isRtpStream = null

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

                // Create packet holder
                val packet = Packet(buffer, packetLength, rtpHeaderSize)

                // Offer to bounded queue (drop if full to prevent memory bloat)
                if (!packetQueue.offer(packet)) {
                    returnBuffer(buffer)
                    packetsDropped++
                    logDropStats(packetLength)
                }
            } catch (e: Exception) {
                if (receiverRunning) {
                    Log.e(TAG, "Receive error: ${e.message}")
                }
            }
        }
    }

    private fun logDropStats(packetLength: Int) {
        val now = System.currentTimeMillis()
        if (now - lastStatsTime >= 1000) {
            val dropRate = if (packetsReceived > 0) {
                (packetsDropped * 100 / packetsReceived).toInt()
            } else 0
            Log.w(TAG, "Queue overflow: dropped=$packetsDropped, received=$packetsReceived, " +
                    "dropRate=${dropRate}%, queueSize=${packetQueue.size}, lastPktSize=$packetLength")
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
