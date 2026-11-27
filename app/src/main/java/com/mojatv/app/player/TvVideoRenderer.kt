package com.mojatv.app.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.NalUnitUtil
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * Wybór dekodera obrazu na Fire TV.
 *
 * Fire TV Stick 4K (i inne Fire TV z układem MediaTek) ma znany błąd sprzętowego dekodera
 * przy obrazie H.264 z przeplotem (1080i/576i — tak nadaje większość polskich kanałów TV):
 * obraz staje albo idzie w zwolnionym tempie, a dźwięk leci dalej.
 * Dlatego:
 *  - kanały z przeplotem → dekoder programowy (procesor),
 *  - pozostałe (filmy, kanały 1080p/720p) → dekoder sprzętowy, nawet gdy urządzenie
 *    „twierdzi”, że strumień przekracza jego możliwości (programowy byłby za wolny).
 */
@UnstableApi
object VideoQuirks {
    /** true = przeplot H.264 przez dekoder programowy (Fire TV). */
    @Volatile var softwareForInterlaced = true

    /** Czy obraz H.264 jest z przeplotem (frame_mbs_only_flag = 0 w SPS); null = nie wiadomo. */
    fun isInterlacedH264(format: Format?): Boolean? {
        if (format == null || format.sampleMimeType != MimeTypes.VIDEO_H264) return null
        for (data in format.initializationData) {
            var i = 0
            while (i + 3 < data.size) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte()) {
                    val header = i + 3
                    if ((data[header].toInt() and 0x1F) == 7) {
                        return try {
                            !NalUnitUtil.parseSpsNalUnit(data, header, data.size).frameMbsOnlyFlag
                        } catch (e: Exception) {
                            null
                        }
                    }
                    i = header
                } else {
                    i++
                }
            }
        }
        return null
    }
}

@UnstableApi
class TvVideoRenderer(
    context: Context,
    selector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    handler: Handler?,
    listener: VideoRendererEventListener?,
    allowedJoiningTimeMs: Long,
) : MediaCodecVideoRenderer(
    context,
    selector,
    allowedJoiningTimeMs,
    enableDecoderFallback,
    handler,
    listener,
    MAX_DROPPED_FRAMES_TO_NOTIFY,
) {
    override fun getDecoderInfos(
        mediaCodecSelector: MediaCodecSelector,
        format: Format,
        requiresSecureDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val infos = super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder)
        val hw = infos.filter { !it.softwareOnly }
        val sw = infos.filter { it.softwareOnly }
        val interlaced = VideoQuirks.isInterlacedH264(format) == true
        return if (interlaced && VideoQuirks.softwareForInterlaced && sw.isNotEmpty()) sw + hw else hw + sw
    }

    override fun canReuseCodec(codecInfo: MediaCodecInfo, oldFormat: Format, newFormat: Format): DecoderReuseEvaluation {
        // zmiana kanału z przeplotem ↔ bez przeplotu: inny dekoder, nie używamy starego
        if (VideoQuirks.isInterlacedH264(oldFormat) != VideoQuirks.isInterlacedH264(newFormat)) {
            return DecoderReuseEvaluation(
                codecInfo.name, oldFormat, newFormat,
                DecoderReuseEvaluation.REUSE_RESULT_NO,
                DecoderReuseEvaluation.DISCARD_REASON_APP_OVERRIDE,
            )
        }
        return super.canReuseCodec(codecInfo, oldFormat, newFormat)
    }

    companion object {
        private const val MAX_DROPPED_FRAMES_TO_NOTIFY = 50
    }
}

/** Fabryka odtwarzacza na Fire TV — z powyższym wyborem dekodera. */
@UnstableApi
class TvRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        out.add(
            TvVideoRenderer(
                context, mediaCodecSelector, enableDecoderFallback,
                eventHandler, eventListener, allowedVideoJoiningTimeMs,
            )
        )
    }
}
