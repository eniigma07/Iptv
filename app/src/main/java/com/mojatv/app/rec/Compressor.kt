package com.mojatv.app.rec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Zmniejszanie nagrań: H.264 720p ok. 1,1 Mbit/s + AAC 128 kbit/s ≈ 600 MB na godzinę.
 * Kompresję robi sprzętowy koder telefonu (Media3 Transformer).
 */
@androidx.annotation.OptIn(UnstableApi::class)
object Compressor {
    private const val VIDEO_BITRATE = 1_100_000
    private const val AUDIO_BITRATE = 128_000
    private const val HEIGHT = 720

    /** Kompresuje jeden kawałek nagrania (.ts) do .mp4. Rzuca wyjątek, gdy się nie uda. */
    suspend fun compress(context: Context, input: File, output: File) {
        output.delete()
        val app = context.applicationContext
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                )
                val assetLoader = DefaultAssetLoaderFactory(
                    app,
                    DefaultDecoderFactory.Builder(app).setEnableDecoderFallback(true).build(),
                    Clock.DEFAULT,
                    DefaultMediaSourceFactory(app, extractors),
                    DataSourceBitmapLoader(app),
                )
                val encoders = DefaultEncoderFactory.Builder(app)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
                    .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(AUDIO_BITRATE).build())
                    .setEnableFallback(true)
                    .build()
                val transformer = Transformer.Builder(app)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoders)
                    .setAssetLoaderFactory(assetLoader)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(HEIGHT))))
                    .build()
                transformer.start(item, output.absolutePath)
                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { runCatching { transformer.cancel() } }
                }
            }
        }
    }

    /** Skleja kawałki .mp4 w jeden plik (bez ponownej kompresji). */
    fun mergeMp4(parts: List<File>, out: File) {
        out.delete()
        if (parts.size == 1) {
            if (!parts[0].renameTo(out)) parts[0].copyTo(out, overwrite = true)
            return
        }
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            // ścieżki (obraz, dźwięk) według pierwszego kawałka
            val tracks = HashMap<String, Int>()
            MediaExtractor().apply {
                setDataSource(parts[0].absolutePath)
                for (i in 0 until trackCount) {
                    val f = getTrackFormat(i)
                    val kind = f.getString(MediaFormat.KEY_MIME)?.substringBefore('/') ?: continue
                    if ((kind == "video" || kind == "audio") && kind !in tracks) tracks[kind] = muxer.addTrack(f)
                }
                release()
            }
            muxer.start()
            val buf = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var base = 0L
            for (p in parts) {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(p.absolutePath)
                    val map = HashMap<Int, Int>()
                    for (i in 0 until ex.trackCount) {
                        val kind = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.substringBefore('/') ?: continue
                        val t = tracks[kind] ?: continue
                        if (t in map.values) continue
                        ex.selectTrack(i)
                        map[i] = t
                    }
                    var first = Long.MIN_VALUE
                    var last = 0L
                    while (true) {
                        val ti = ex.sampleTrackIndex
                        if (ti < 0) break
                        buf.clear()
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) break
                        val time = ex.sampleTime
                        if (first == Long.MIN_VALUE) first = time
                        val rel = maxOf(0L, time - first)
                        last = maxOf(last, rel)
                        val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        info.set(0, size, base + rel, flags)
                        map[ti]?.let { muxer.writeSampleData(it, buf, info) }
                        ex.advance()
                    }
                    base += last + 40_000
                } finally {
                    ex.release()
                }
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
        }
    }

    /** Skleja kawałki .ts (format strumieniowy — wystarczy dopisać jeden za drugim). */
    fun concatTs(parts: List<File>, out: File) {
        out.delete()
        if (parts.size == 1) {
            if (!parts[0].renameTo(out)) parts[0].copyTo(out, overwrite = true)
            return
        }
        FileOutputStream(out).use { o -> parts.forEach { p -> p.inputStream().use { it.copyTo(o, 256 * 1024) } } }
    }
}

/** Kolejka kawałków do zmniejszenia — działa równolegle z nagrywaniem. */
class PartCompressor(private val context: Context, scope: CoroutineScope) {
    private val queue = Channel<File>(Channel.UNLIMITED)
    @Volatile private var disabled = false
    @Volatile private var anySuccess = false
    private val worker = scope.launch { for (f in queue) process(f) }

    fun enqueue(f: File) {
        queue.trySend(f)
    }

    /** Czeka, aż wszystkie kawałki zostaną zmniejszone. */
    suspend fun finish() {
        queue.close()
        worker.join()
    }

    private suspend fun process(ts: File) {
        if (disabled || !ts.exists()) return
        val dir = ts.parentFile ?: return
        val out = File(dir, ts.nameWithoutExtension + ".mp4")
        val tmp = File(dir, ts.nameWithoutExtension + ".tmp.mp4")
        var ok = false
        val attempts = if (anySuccess) 2 else 1
        for (i in 0 until attempts) {
            ok = try {
                Compressor.compress(context, ts, tmp)
                tmp.length() > 1024
            } catch (e: Exception) {
                false
            }
            if (ok) break
        }
        if (ok && tmp.renameTo(out)) {
            ts.delete()
            anySuccess = true
        } else {
            tmp.delete()
            // pierwszy kawałek się nie udał — ten telefon/kanał nie da się zmniejszyć, nagrywamy w oryginale
            if (!anySuccess) disabled = true
        }
    }
}
