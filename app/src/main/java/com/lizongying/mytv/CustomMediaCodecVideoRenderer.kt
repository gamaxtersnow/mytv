package com.lizongying.mytv

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Custom MediaCodecVideoRenderer that forces FORMAT_HANDLED for HEVC formats
 * that would otherwise be rejected as EXCEEDS_CAPABILITIES.
 *
 * Problem: ExoPlayer's default capability check is conservative for 4K HEVC
 * Main 10 Profile (e.g., hvc1.2.4.L180), returning NO_EXCEEDS_CAPABILITIES
 * even though the Hisilicon decoder (OMX.hisi.video.decoder.hevc) can decode it.
 *
 * Solution: When super.supportsFormat() returns EXCEEDS_CAPABILITIES for
 * video/hevc, we check if any HEVC decoder exists and if so, return HANDLED.
 */
@OptIn(UnstableApi::class)
class CustomMediaCodecVideoRenderer(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    allowedJoiningTimeMs: Long = DEFAULT_ALLOWED_JOINING_TIME_MS,
    enableDecoderFallback: Boolean = true,
    eventHandler: Handler? = null,
    eventListener: VideoRendererEventListener? = null,
    maxDroppedFramesToNotify: Int = MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
) : MediaCodecVideoRenderer(
    context,
    mediaCodecSelector,
    allowedJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify
) {

    companion object {
        private const val TAG = "CustomVideoRenderer"
        private const val DEFAULT_ALLOWED_JOINING_TIME_MS = 5000L
        private const val MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY = 50
    }

    override fun supportsFormat(
        mediaCodecSelector: MediaCodecSelector,
        format: Format
    ): Int {
        val superResult = super.supportsFormat(mediaCodecSelector, format)

        // Extract format support level (bits 0-2)
        val formatSupport = superResult and 7

        // If super already handles it, no need to override
        if (formatSupport == C.FORMAT_HANDLED) {
            return superResult
        }

        // Only override for HEVC/H.265 formats
        if (format.sampleMimeType != "video/hevc") {
            return superResult
        }

        Log.d(TAG, "HEVC format support check: ${format.codecs}, superResult=$formatSupport")

        // If super says EXCEEDS_CAPABILITIES or UNSUPPORTED_SUBTYPE for HEVC,
        // check if any HEVC decoder exists. If so, force FORMAT_HANDLED.
        if (formatSupport == C.FORMAT_EXCEEDS_CAPABILITIES ||
            formatSupport == C.FORMAT_UNSUPPORTED_SUBTYPE
        ) {
            try {
                val decoderInfos = MediaCodecUtil.getDecoderInfos(
                    "video/hevc",
                    format.cryptoType != C.CRYPTO_TYPE_NONE,
                    false
                )

                if (decoderInfos.isNotEmpty()) {
                    val firstDecoder = decoderInfos[0]
                    Log.i(TAG, "Forcing FORMAT_HANDLED for HEVC ${format.codecs}, " +
                            "decoder=${firstDecoder.name}, " +
                            "hardwareAccelerated=${firstDecoder.hardwareAccelerated}")

                    // Build a FORMAT_HANDLED result preserving adaptive/hardware flags from super
                    val adaptiveSupport = superResult and 24
                    val tunnelingSupport = superResult and 32
                    val hardwareAccelSupport = if (firstDecoder.hardwareAccelerated) 64 else 0
                    val decoderSupport = 128 // DECODER_SUPPORT_PRIMARY

                    return RendererCapabilities.create(
                        C.FORMAT_HANDLED,
                        adaptiveSupport,
                        tunnelingSupport,
                        hardwareAccelSupport,
                        decoderSupport
                    )
                }
            } catch (e: MediaCodecUtil.DecoderQueryException) {
                Log.e(TAG, "Failed to query HEVC decoders", e)
            }
        }

        return superResult
    }
}
