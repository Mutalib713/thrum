package com.mosman.thrum

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns a user-picked audio file into plain numbers. Task 3.
 *
 * **Streams, never accumulates.** Three minutes of stereo CD-quality audio is
 * about 30 MB of samples, and Mutalib's own ringtones are not the longest files
 * a user will pick. Decoded audio is handed to [onMono] a chunk at a time and
 * dropped; nothing here holds more than one buffer. Task 4's envelope follower
 * is the thing that consumes the stream, and what it keeps is thousands of times
 * smaller than the audio.
 *
 * Everything decidable — the downmix, the frame arithmetic — lives in [Pcm]
 * where it can be tested on the PC. This file is the part that genuinely needs
 * a phone, so it stays as thin as the platform allows.
 */
object AudioDecoder {

    /**
     * Decode [uri] to mono 16-bit samples.
     *
     * @param onMono receives each decoded chunk: an array, and how many samples
     *   of it are valid. The array is **reused between calls** — copy anything
     *   that needs to outlive the callback. Reusing it is the difference between
     *   one allocation and several thousand on a long track.
     */
    fun decode(
        ctx: Context,
        uri: Uri,
        onFormat: (sampleRate: Int, channels: Int) -> Unit = { _, _ -> },
        onMono: (samples: ShortArray, count: Int) -> Unit = { _, _ -> },
    ): Decoded {
        val startedAt = SystemClock.elapsedRealtime()
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(ctx, uri, null)
            } catch (e: Exception) {
                // Covers a deleted file, a revoked permission, and a file that
                // is not media at all. The user cannot act on the difference.
                return Decoded.Failed("Couldn't open that file. It may have been moved or deleted, or it isn't audio this phone can read.")
            }

            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return Decoded.Failed("There's no audio in that file.")

            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/unknown"
            extractor.selectTrack(track)

            // What the container claims, for comparison with what we actually
            // decode. A gap between the two is the signal that a file was
            // truncated or that a decoder gave up early — invisible otherwise.
            val containerMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION) / 1000
            } else {
                0
            }

            // Refuse a long file before decoding it, not after. The file states
            // its own length here, and the check below only fires once we have
            // decoded past the limit — half an hour of audio, ground through at
            // the user's expense, to reach a conclusion available in the header.
            // Mutalib's two 1h49m recordings each took most of a minute to
            // refuse. The in-loop check stays as a backstop for the files that
            // declare no duration at all.
            if (containerMs > MAX_DECODE_MS) {
                return Decoded.Failed(
                    "That file is ${clock(containerMs)} long. Thrum reads up to " +
                        "${MAX_DECODE_MS / 60_000} minutes — pick a ringtone rather than an album.",
                    mime,
                )
            }

            // WAV arrives already decoded. Handing raw PCM to a decoder is not
            // guaranteed to work — several devices ship no "audio/raw" decoder
            // at all — and there is nothing for one to do anyway.
            // Announced lazily, immediately before the first chunk, so callers
            // always receive the values the decoder settled on rather than the
            // ones the container advertised — a decoder may resample or downmix.
            var announced = false
            val announce: (Int, Int) -> Unit = { rate, channels ->
                if (!announced) {
                    announced = true
                    onFormat(rate, channels)
                }
            }

            val run = if (mime == MIME_RAW) {
                readRaw(extractor, format, announce, onMono)
            } else {
                runCodec(extractor, format, mime, announce, onMono)
            }

            return when (run) {
                is Run.Failed -> Decoded.Failed(run.message, mime)
                is Run.Ok -> Decoded.Ok(
                    name = displayName(ctx, uri),
                    mime = mime,
                    sampleRate = run.sampleRate,
                    channels = run.channels,
                    frames = run.frames,
                    decodedMs = Pcm.framesToMs(run.frames, run.sampleRate),
                    containerMs = containerMs,
                    peak = run.peak,
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                )
            }
        } catch (e: Exception) {
            // Task 3's proof says a file that fails must produce a message, not
            // a crash. Decoders on real devices throw things the documentation
            // does not mention, so the net is deliberately wide.
            val detail = e.message?.let { ": $it" } ?: ""
            return Decoded.Failed("That file couldn't be decoded (${e.javaClass.simpleName}$detail).")
        } finally {
            extractor.release()
        }
    }

    /** MP3, M4A, OGG and anything else the phone has a decoder for. */
    private fun runCodec(
        extractor: MediaExtractor,
        format: MediaFormat,
        mime: String,
        announce: (Int, Int) -> Unit,
        onMono: (ShortArray, Int) -> Unit,
    ): Run {
        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            return Run.Failed("This phone has no decoder for $mime files.")
        }

        // Read from the *input* format only as a starting point. The values that
        // matter come from the output format below: a decoder is entitled to
        // resample or downmix, and trusting the input would misreport both the
        // duration and the downmix width.
        var sampleRate = format.optInt(MediaFormat.KEY_SAMPLE_RATE, 0)
        var channels = format.optInt(MediaFormat.KEY_CHANNEL_COUNT, 0)
        var encoding = format.optInt(KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

        var interleaved = ShortArray(INITIAL_BUFFER)
        var mono = ShortArray(INITIAL_BUFFER)
        var frames = 0L
        var peak = 0
        var inputDone = false
        var idleRounds = 0

        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()

            while (true) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
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

                when (val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.optInt(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = out.optInt(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        encoding = out.optInt(KEY_PCM_ENCODING, encoding)
                        idleRounds = 0
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // A decoder that has been fed everything and still emits
                        // nothing is stuck. Without this the loop would spin
                        // until the user force-stopped the app.
                        if (inputDone && ++idleRounds > MAX_IDLE_ROUNDS) {
                            return Run.Failed("The decoder stopped responding partway through that file.")
                        }
                    }

                    else -> {
                        if (index >= 0) {
                            idleRounds = 0
                            if (info.size > 0 && channels > 0) {
                                val buffer = codec.getOutputBuffer(index)
                                if (buffer != null) {
                                    val samples = samplesIn(info.size, encoding)
                                    if (interleaved.size < samples) interleaved = ShortArray(samples)
                                    val count = readSamples(
                                        buffer, info.offset, info.size, encoding, interleaved,
                                    )
                                    val monoFrames = Pcm.monoFrames(count, channels)
                                    if (mono.size < monoFrames) mono = ShortArray(monoFrames)
                                    Pcm.downmixToMono(interleaved, count, channels, mono)
                                    frames += monoFrames
                                    peak = maxOf(peak, Pcm.peak(mono, monoFrames))
                                    announce(sampleRate, channels)
                                    onMono(mono, monoFrames)
                                }
                            }
                            codec.releaseOutputBuffer(index, false)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            return finish(sampleRate, channels, frames, peak)
                        }
                    }
                }

                if (sampleRate > 0 && Pcm.framesToMs(frames, sampleRate) > MAX_DECODE_MS) {
                    return Run.Failed(
                    "That file doesn't state its length, and has already decoded past " +
                        "${MAX_DECODE_MS / 60_000} minutes. Pick a ringtone rather than an album.",
                )
                }
            }
        } catch (e: MediaCodec.CodecException) {
            return Run.Failed("This phone's $mime decoder failed on that file: ${e.diagnosticInfo}")
        } catch (e: IllegalStateException) {
            // A decoder that errors asynchronously leaves the codec unusable, so
            // the throw surfaces from the next ordinary call rather than from
            // anything that looks wrong. Naming the format matters: Task 3 lost
            // an hour to a bare "IllegalStateException" that turned out to be a
            // Dolby Atmos track this phone's own music player also could not play.
            return Run.Failed(
                "This phone's $mime decoder stopped partway through that file. " +
                    "Some tracks need a decoder this phone doesn't have — Dolby Atmos " +
                    "inside an .m4a is the common one.",
            )
        } catch (e: Exception) {
            val detail = e.message?.let { ": $it" } ?: ""
            return Run.Failed("That file couldn't be decoded (${e.javaClass.simpleName}$detail).")
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    /** WAV: the samples are already PCM, so the extractor is the whole pipeline. */
    private fun readRaw(
        extractor: MediaExtractor,
        format: MediaFormat,
        announce: (Int, Int) -> Unit,
        onMono: (ShortArray, Int) -> Unit,
    ): Run {
        val sampleRate = format.optInt(MediaFormat.KEY_SAMPLE_RATE, 0)
        val channels = format.optInt(MediaFormat.KEY_CHANNEL_COUNT, 0)
        val encoding = format.optInt(KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        if (sampleRate <= 0 || channels <= 0) {
            return Run.Failed("That file doesn't say what sample rate or how many channels it has.")
        }

        val bytes = ByteBuffer.allocate(RAW_CHUNK_BYTES)
        var interleaved = ShortArray(INITIAL_BUFFER)
        var mono = ShortArray(INITIAL_BUFFER)
        var frames = 0L
        var peak = 0

        while (true) {
            bytes.clear()
            val size = extractor.readSampleData(bytes, 0)
            if (size < 0) return finish(sampleRate, channels, frames, peak)

            val samples = samplesIn(size, encoding)
            if (interleaved.size < samples) interleaved = ShortArray(samples)
            val count = readSamples(bytes, 0, size, encoding, interleaved)
            val monoFrames = Pcm.monoFrames(count, channels)
            if (mono.size < monoFrames) mono = ShortArray(monoFrames)
            Pcm.downmixToMono(interleaved, count, channels, mono)
            frames += monoFrames
            peak = maxOf(peak, Pcm.peak(mono, monoFrames))
            announce(sampleRate, channels)
            onMono(mono, monoFrames)
            extractor.advance()

            if (Pcm.framesToMs(frames, sampleRate) > MAX_DECODE_MS) {
                return Run.Failed(
                    "That file doesn't state its length, and has already decoded past " +
                        "${MAX_DECODE_MS / 60_000} minutes. Pick a ringtone rather than an album.",
                )
            }
        }
    }

    private fun finish(sampleRate: Int, channels: Int, frames: Long, peak: Int): Run {
        if (sampleRate <= 0 || channels <= 0) {
            return Run.Failed("That file decoded, but didn't say its sample rate or channel count.")
        }
        if (frames == 0L) {
            return Run.Failed("That file contains no audio — it decoded to nothing at all.")
        }
        return Run.Ok(sampleRate, channels, frames, peak)
    }

    /**
     * Copy one decoder buffer into [out] as 16-bit samples.
     *
     * Decoders are allowed to hand back 32-bit floats instead of shorts, and
     * several do for OGG. Treating those bytes as shorts produces noise that
     * still "decodes" — a plausible-looking result that is entirely wrong — so
     * the encoding is read from the format rather than assumed.
     */
    private fun readSamples(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        encoding: Int,
        out: ShortArray,
    ): Int {
        buffer.position(offset)
        buffer.limit(offset + size)
        buffer.order(ByteOrder.nativeOrder())

        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buffer.asFloatBuffer()
                val count = minOf(floats.remaining(), out.size)
                for (i in 0 until count) {
                    val v = floats.get(i).coerceIn(-1f, 1f)
                    out[i] = (v * Pcm.MAX_SAMPLE).toInt().toShort()
                }
                count
            }

            else -> {
                val shorts = buffer.asShortBuffer()
                val count = minOf(shorts.remaining(), out.size)
                shorts.get(out, 0, count)
                count
            }
        }
    }

    private fun samplesIn(byteCount: Int, encoding: Int): Int =
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) byteCount / 4 else byteCount / 2

    /** The file's own name, for the results readout. Falls back rather than failing. */
    fun displayName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0)
                }
        }
        return uri.lastPathSegment ?: "the file you picked"
    }

    private fun MediaFormat.optInt(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    /** `1:49:42`, so a refusal states the length it is refusing. */
    private fun clock(ms: Long): String {
        val total = ms / 1000
        return if (total >= 3600) {
            "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
        } else {
            "%d:%02d".format(total / 60, total % 60)
        }
    }

    private const val MIME_RAW = "audio/raw"

    /**
     * Present in `MediaFormat` since API 24 but only made public later, and this
     * app targets 31+. Named here rather than referenced so the constant cannot
     * disappear on a platform that hides it again.
     */
    private const val KEY_PCM_ENCODING = "pcm-encoding"

    private const val TIMEOUT_US = 10_000L
    private const val INITIAL_BUFFER = 8192
    private const val RAW_CHUNK_BYTES = 64 * 1024

    /** Enough rounds of nothing, after the last input, to call a decoder dead. */
    private const val MAX_IDLE_ROUNDS = 50

    /** A ringtone, not an album. Guards against a file that decodes forever. */
    private const val MAX_DECODE_MS = 30 * 60 * 1000L

    private sealed interface Run {
        data class Ok(val sampleRate: Int, val channels: Int, val frames: Long, val peak: Int) : Run
        data class Failed(val message: String) : Run
    }
}

/** The outcome of a decode: numbers to show, or a sentence to show. Never a crash. */
sealed interface Decoded {

    data class Ok(
        val name: String,
        val mime: String,
        val sampleRate: Int,
        val channels: Int,
        val frames: Long,
        val decodedMs: Long,
        val containerMs: Long,
        val peak: Int,
        val elapsedMs: Long,
    ) : Decoded {

        /**
         * Whether what we decoded matches what the container promised.
         *
         * Task 3's proof is that our duration matches the music player's, and
         * the music player reads the container. A second of drift on a real
         * track means the decode stopped early, which would silently truncate
         * every score built from it.
         */
        val durationAgrees: Boolean
            get() = containerMs <= 0 || kotlin.math.abs(containerMs - decodedMs) <= DURATION_TOLERANCE_MS

        /** Peak as a percentage of full scale, which is easier to read than 0–32767. */
        val peakPercent: Int get() = (peak * 100) / Pcm.MAX_SAMPLE

        val isSilent: Boolean get() = peak == 0
    }

    /** [mime] is empty when the file failed before a format could be read at all. */
    data class Failed(val message: String, val mime: String = "") : Decoded

    companion object {
        /**
         * Encoders pad. MP3 in particular carries a frame or two of silence the
         * container never counts, so exact agreement is the wrong bar — 50 ms is
         * inaudible and far below anything a truncated decode would produce.
         */
        const val DURATION_TOLERANCE_MS = 50
    }
}
