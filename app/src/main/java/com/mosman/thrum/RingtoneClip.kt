package com.mosman.thrum

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * The ringtone Thrum saves: a song's first 45 seconds as an `.m4a` file, the
 * same window a call's vibration repeats, so the two start over together
 * (Task 31, Mutalib's pick on 2026-10-07).
 *
 * Made on the phone with the converters every Android phone carries:
 * `MediaCodec` reads the song's sound and writes AAC, `MediaMuxer` puts it in
 * an MP4 audio file. No FFmpeg, nothing downloaded (Sacred Rule 3).
 *
 * Two steps rather than one stream: the opening is read into memory first
 * (45 s of stereo at 48 kHz is about 8.6 MB of samples), faded, then written.
 * Reading and writing at once would need a hand-built queue between two
 * codecs, which is where this kind of code goes wrong.
 */
object RingtoneClip {

    /**
     * Write the opening of [source] into [out]. False when the file has no
     * sound this phone can read or write; the caller then says so instead of
     * setting a ringtone that doesn't exist.
     */
    fun make(ctx: Context, source: Uri, out: File): Boolean {
        val opening = runCatching { readOpening(ctx, source) }.getOrNull() ?: return false
        if (opening.frames == 0) return false
        fade(opening)
        return runCatching { write(opening, out) }.getOrDefault(false).also { written ->
            if (!written) out.delete()
        }
    }

    private class Opening(
        val samples: ShortArray,
        val frames: Int,
        val sampleRate: Int,
        val channels: Int,
        /** True when the song went on past the clip, so the end needs a fade. */
        val cut: Boolean,
    )

    /** The first [RingtoneRules.CLIP_MS] of the first audio track, as 16-bit samples. */
    private fun readOpening(ctx: Context, source: Uri): Opening? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(ctx, source, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(track)
            var rate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, 0)
            var channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 0)
            var encoding = format.intOr(KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            val collected = Collector()

            // WAV arrives already as samples; there is nothing to decode.
            if (mime == MIME_RAW) {
                if (rate <= 0 || channels <= 0 || !Pcm.isReadable(encoding)) return null
                val bytes = java.nio.ByteBuffer.allocate(RAW_CHUNK_BYTES)
                var scratch = ShortArray(RAW_CHUNK_BYTES)
                while (!collected.full(rate, channels)) {
                    bytes.clear()
                    val size = extractor.readSampleData(bytes, 0)
                    if (size < 0) break
                    val need = Pcm.samplesIn(size, encoding)
                    if (scratch.size < need) scratch = ShortArray(need)
                    collected.add(scratch, Pcm.readSamples(bytes, 0, size, encoding, scratch), rate, channels)
                    extractor.advance()
                }
                return collected.toOpening(rate, channels)
            }

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var scratch = ShortArray(INITIAL_BUFFER)
                var inputDone = false
                var idle = 0
                while (true) {
                    var moved = false
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (index >= 0) {
                            moved = true
                            val buffer = codec.getInputBuffer(index)
                            val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            moved = true
                            val outFormat = codec.outputFormat
                            rate = outFormat.intOr(MediaFormat.KEY_SAMPLE_RATE, rate)
                            channels = outFormat.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                            encoding = outFormat.intOr(KEY_PCM_ENCODING, encoding)
                        }
                        index >= 0 -> {
                            moved = true
                            val buffer = codec.getOutputBuffer(index)
                            if (buffer != null && info.size > 0 && rate > 0 && channels > 0) {
                                val need = Pcm.samplesIn(info.size, encoding)
                                if (need == Pcm.UNREADABLE_ENCODING) return null
                                if (scratch.size < need) scratch = ShortArray(need)
                                val count = Pcm.readSamples(buffer, info.offset, info.size, encoding, scratch)
                                collected.add(scratch, count, rate, channels)
                            }
                            codec.releaseOutputBuffer(index, false)
                            val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            if (ended || collected.full(rate, channels)) break
                        }
                    }
                    // A decoder that neither takes input nor gives output is
                    // stuck; give up rather than hang (AudioDecoder's guard).
                    idle = if (moved) 0 else idle + 1
                    if (idle > MAX_IDLE_ROUNDS) return null
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return collected.toOpening(rate, channels)
        } finally {
            extractor.release()
        }
    }

    /**
     * Gathers samples up to the clip's length. Anything wider than stereo is
     * mixed down to one channel, because the encoder is only asked for one or
     * two.
     */
    private class Collector {
        var samples = ShortArray(0)
        var used = 0
        var cut = false
        private var mono = ShortArray(0)

        fun full(rate: Int, channels: Int): Boolean =
            rate > 0 && channels > 0 && used >= RingtoneRules.clipFrames(rate) * outChannels(channels)

        fun add(from: ShortArray, count: Int, rate: Int, channels: Int) {
            if (count <= 0) return
            val outCh = outChannels(channels)
            val (source, n) = if (channels > 2) {
                if (mono.size < count / channels) mono = ShortArray(count / channels)
                mono to Pcm.downmixToMono(from, count, channels, mono)
            } else {
                from to count
            }
            val limit = RingtoneRules.clipFrames(rate) * outCh
            val take = minOf(n, limit - used)
            if (take < n) cut = true
            if (take <= 0) return
            if (samples.size < used + take) samples = samples.copyOf(maxOf(used + take, minOf(limit, samples.size * 2 + take)))
            System.arraycopy(source, 0, samples, used, take)
            used += take
        }

        fun toOpening(rate: Int, channels: Int): Opening? {
            if (rate <= 0 || channels <= 0) return null
            val outCh = outChannels(channels)
            return Opening(samples, used / outCh, rate, outCh, cut)
        }
    }

    /** The last [RingtoneRules.FADE_OUT_MS] down to silence, so the loop back to the start doesn't click. */
    private fun fade(opening: Opening) {
        if (!opening.cut) return
        val ch = opening.channels
        val fadeFrames = (RingtoneRules.FADE_OUT_MS * opening.sampleRate / 1000).toInt()
        for (frame in maxOf(0, opening.frames - fadeFrames) until opening.frames) {
            val gain = RingtoneRules.fadeGain(frame, opening.frames, opening.sampleRate, cut = true)
            for (c in 0 until ch) {
                val i = frame * ch + c
                opening.samples[i] = (opening.samples[i] * gain).roundToInt().toShort()
            }
        }
    }

    /** AAC in an MP4 audio file, which Android's ringtone player and media library both read. */
    private fun write(opening: Opening, out: File): Boolean {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, opening.sampleRate, opening.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_PER_CHANNEL * opening.channels)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_CHUNK_BYTES)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val info = MediaCodec.BufferInfo()
            val total = opening.frames * opening.channels
            var written = 0
            var inputDone = false
            var track = -1
            var idle = 0
            while (true) {
                var moved = false
                if (!inputDone) {
                    val index = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        moved = true
                        val buffer = encoder.getInputBuffer(index) ?: return false
                        buffer.clear()
                        // Whole frames only, so left and right never swap.
                        val room = (buffer.remaining() / 2 / opening.channels) * opening.channels
                        val n = minOf(room, total - written)
                        val timeUs = (written / opening.channels).toLong() * 1_000_000L / opening.sampleRate
                        if (n <= 0) {
                            encoder.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(opening.samples, written, n)
                            encoder.queueInputBuffer(index, 0, n * 2, timeUs, 0)
                            written += n
                        }
                    }
                }
                val index = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        moved = true
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        started = true
                    }
                    index >= 0 -> {
                        moved = true
                        val buffer = encoder.getOutputBuffer(index)
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (buffer != null && info.size > 0 && !config && started) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
                idle = if (moved) 0 else idle + 1
                if (idle > MAX_IDLE_ROUNDS) return false
            }
            return started
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            runCatching { if (started) muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun outChannels(channels: Int) = if (channels > 2) 1 else channels

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    private const val MIME_RAW = "audio/raw"

    /** Present since API 24 but public only later; named here, as in [AudioDecoder]. */
    private const val KEY_PCM_ENCODING = "pcm-encoding"

    private const val TIMEOUT_US = 10_000L
    private const val INITIAL_BUFFER = 8192
    private const val RAW_CHUNK_BYTES = 64 * 1024
    private const val INPUT_CHUNK_BYTES = 16 * 1024

    /** About three seconds of a codec doing nothing in either direction, at a 10 ms wait per round. */
    private const val MAX_IDLE_ROUNDS = 300

    /** 96 kbps a channel: 192 kbps stereo AAC, far past what a phone speaker can show the difference of. */
    private const val BIT_RATE_PER_CHANNEL = 96_000
}
