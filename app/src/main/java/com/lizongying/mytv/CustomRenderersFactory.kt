package com.lizongying.mytv

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Custom RenderersFactory that injects CustomMediaCodecVideoRenderer
 * to force HEVC format handling for 4K playback on Hisilicon decoders.
 */
@OptIn(UnstableApi::class)
class CustomRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    companion object {
        private const val TAG = "CustomRenderersFactory"
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        // Let the superclass add its default video renderers
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )

        // Replace any MediaCodecVideoRenderer with our custom version
        val iterator = out.iterator()
        var replaced = false
        while (iterator.hasNext()) {
            val renderer = iterator.next()
            if (renderer is androidx.media3.exoplayer.video.MediaCodecVideoRenderer &&
                renderer !is CustomMediaCodecVideoRenderer
            ) {
                iterator.remove()
                Log.d(TAG, "Replaced default MediaCodecVideoRenderer with custom version")
                replaced = true
            }
        }

        // Add our custom video renderer
        out.add(
            CustomMediaCodecVideoRenderer(
                context,
                mediaCodecSelector,
                allowedVideoJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
            )
        )

        if (replaced) {
            Log.i(TAG, "CustomMediaCodecVideoRenderer injected successfully")
        }
    }
}
