package com.mosman.thrum

/**
 * The decisions behind Set as ringtone, kept apart from Android so they can
 * be tested on the PC. Task 31 (Mutalib, 2026-10-07): "Use for calls" and
 * "Set as ringtone" are one action, so what the phone rings with and what
 * Thrum vibrates are the same song.
 *
 * Addresses are compared as strings here; [Ringtone] turns them into the
 * phone's own `Uri`s.
 */
object RingtoneRules {

    /** How long the ringtone Thrum saves is: the window a call's vibration repeats. */
    const val CLIP_MS = ScoreBuilder.RINGTONE_SECONDS * 1000L

    /**
     * The highest sample rate a clip is written at. AAC-LC, the kind of `.m4a`
     * every ringtone player reads, stops at 48 kHz, and Android's own encoder
     * refuses more. A 96 kHz FLAC is brought down to this first ([clipRate]).
     */
    const val MAX_CLIP_RATE = 48_000

    /** The fade at the end of a cut clip, so the jump back to its start doesn't click. */
    const val FADE_OUT_MS = 150L

    /**
     * The most Thrum will skip ahead to catch up with a ringtone that started
     * before it heard about the call. Measured so far: 0.3–0.7 s. A later
     * start still skips only this far: a delay that long is more likely a
     * wrong clock than a truly late call, so 3 s is the most Thrum bets on.
     */
    const val MAX_CATCH_UP_MS = 3_000L

    /**
     * How long a Set as ringtone waits for the user to come back from
     * Android's "Modify system settings" page. Long enough to read the page
     * and find the switch; short enough that a ringtone is never set some
     * later day the permission happens to be on.
     */
    const val PENDING_FOR_MS = 10 * 60_000L

    /** Stored as the old ringtone when the user's ringtone was "None". */
    const val NO_RINGTONE = "none"

    /**
     * The same address, written two ways. Android may keep the phone's user in
     * it (`content://0@media/...`) and hand it back without, and may add a
     * query (`?title=...`) to a stored ringtone. Both are taken out first.
     */
    fun sameAddress(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        return bare(a) == bare(b)
    }

    private fun bare(address: String): String =
        address.substringBefore('?').replace(Regex("^content://\\d+@"), "content://")

    /**
     * Whether the phone's ringtone right now is the song Thrum vibrates to.
     * Only then does Thrum play in Ring mode: otherwise the user would hear
     * one song and feel another, which Mutalib ruled out.
     *
     * @param current the phone's ringtone setting now.
     * @param ours the ringtone Thrum last set.
     * @param oursFor the song that ringtone was made from.
     * @param armed the song calls vibrate to.
     */
    fun ringtoneIsTheSong(current: String?, ours: String?, oursFor: String?, armed: String?): Boolean =
        armed != null && oursFor == armed && sameAddress(current, ours)

    /**
     * Whether to note [current] as the user's own ringtone before Thrum
     * replaces it. Only once, and never one of Thrum's: "Go back" has to
     * reach the ringtone the user chose, not the one Thrum set last week.
     *
     * @param saved the old ringtone already noted, if any.
     * @param currentIsThrums [current] is a file Thrum made, or the song calls
     *   already vibrate to (an earlier build set songs as they were).
     */
    fun shouldRemember(saved: String?, currentIsThrums: Boolean): Boolean =
        saved == null && !currentIsThrums

    /**
     * Whether a track of this length needs cutting to [CLIP_MS]; unknown (0)
     * means read it and see. Anything past 45.0 s is cut: the vibration
     * repeats every 45.0 s, so a 45.4 s ringtone would slip 0.4 s further
     * behind it on every repeat.
     */
    fun needsCut(durationMs: Long): Boolean = durationMs <= 0 || durationMs > CLIP_MS

    /**
     * The rate a clip is written at: the song's own, or, above
     * [MAX_CLIP_RATE], the standard rate it divides into evenly (88.2 and
     * 176.4 kHz → 44.1 kHz; 96 and 192 kHz → 48 kHz), so bringing it down is
     * a clean step.
     */
    fun clipRate(sourceRate: Int): Int = when {
        sourceRate <= MAX_CLIP_RATE -> sourceRate
        sourceRate % 44_100 == 0 -> 44_100
        else -> MAX_CLIP_RATE
    }

    /**
     * The file name for a song's ringtone in Ringtones/Thrum, e.g.
     * "AIZO (Thrum 3f2a1b).m4a". The six letters come from the song's
     * address, so two songs that share a name ("Intro", "Track 01") get two
     * files, and the same song always finds the file it made before. The
     * name was once the song's name alone, and a second "Intro" quietly got
     * the first one's sound.
     */
    fun clipFileName(songName: String, sourceUri: String, extension: String): String =
        "${safeName(songName)} (Thrum ${tagOf(sourceUri)}).$extension"

    /**
     * "Song.mp3" → "Song", but "Mr. Brown" stays "Mr. Brown". Characters a
     * file name can't hold become spaces, so the name Android stores is the
     * name Thrum looks for next time.
     */
    fun safeName(name: String): String =
        name.replace(Regex("\\.[A-Za-z0-9]{2,4}$"), "")
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_NAME_CHARS)
            .trim()
            .ifEmpty { "Thrum" }

    /** Six hex letters from the song's address, written the same way however Android spells it. */
    private fun tagOf(sourceUri: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bare(sourceUri).toByteArray())
            .take(3)
            .joinToString("") { "%02x".format(it) }

    /** Long enough for any real title; short enough that the whole name fits a file system's 255 bytes. */
    private const val MAX_NAME_CHARS = 80

    /** Frames in the clip at this sample rate. */
    fun clipFrames(sampleRate: Int): Int = (CLIP_MS * sampleRate / 1000).toInt()

    /**
     * The gain for a frame near the end of a clip that was cut: 1 until the
     * last [FADE_OUT_MS], then down in a straight line to 0 at the last frame.
     * A clip that ended on its own (the song was shorter) gets no fade.
     */
    fun fadeGain(frame: Int, totalFrames: Int, sampleRate: Int, cut: Boolean): Float {
        if (!cut || totalFrames <= 0) return 1f
        val fadeFrames = (FADE_OUT_MS * sampleRate / 1000).toInt().coerceIn(1, totalFrames)
        val fromEnd = totalFrames - 1 - frame
        return if (fromEnd >= fadeFrames) 1f else (fromEnd.toFloat() / fadeFrames).coerceIn(0f, 1f)
    }

    /**
     * How far into the vibration a ringing call should start. The ringtone
     * began before Thrum heard about the call, so starting the vibration from
     * zero would leave it behind the sound for the whole ring. Within one
     * loop of the score, and never more than [MAX_CATCH_UP_MS].
     */
    fun catchUpMs(lateMs: Long, scoreMs: Long): Long {
        if (scoreMs <= 0) return 0
        return lateMs.coerceIn(0, MAX_CATCH_UP_MS) % scoreMs
    }

    /**
     * The number at the end of a media-library address ("…/audio/media/412"
     * → 412), or null. Used to keep any file a phone sound still points at:
     * the same file can be written `external` or `external_primary`, so the
     * number is compared, not the whole address. Two libraries (the phone's
     * built-in sounds and the user's files) can share a number, which only
     * ever makes Thrum keep a file it could have deleted, never the reverse.
     */
    fun mediaId(address: String?): Long? =
        address?.let { bare(it).substringAfterLast('/').toLongOrNull() }

    /**
     * A Set as ringtone waiting on Android's settings page, as one line in
     * the app's storage. Kept there, not only in memory, because Android may
     * close Thrum while the user is on that page (low-memory phones do), and
     * the ask has to survive to be finished on the way back.
     */
    fun encodePending(ask: RingtoneAsk, savedAtMs: Long): String = listOf(
        PENDING_VERSION,
        savedAtMs.toString(),
        ask.track.sourceUri,
        ask.track.name,
        ask.track.durationMs.toString(),
        ask.track.kind.name,
        ask.armed.toString(),
        ask.previousSong.orEmpty(),
    ).joinToString(FIELD) { it.replace(FIELD, " ") }

    /** The ask written by [encodePending], or null when it's unreadable or older than [PENDING_FOR_MS]. */
    fun decodePending(text: String, nowMs: Long): RingtoneAsk? = runCatching {
        val f = text.split(FIELD)
        if (f.size != 8 || f[0] != PENDING_VERSION) return null
        val savedAt = f[1].toLong()
        if (nowMs < savedAt || nowMs - savedAt > PENDING_FOR_MS) return null
        val kind = TrackKind.entries.firstOrNull { it.name == f[5] } ?: return null
        RingtoneAsk(
            track = Track(f[2], f[3], durationMs = f[4].toLong(), kind = kind),
            armed = f[6].toBooleanStrict(),
            previousSong = f[7].ifEmpty { null },
        )
    }.getOrNull()

    private const val PENDING_VERSION = "1"

    /** ASCII's unit separator: nothing in a song name or an address uses it. */
    private const val FIELD = "\u001F"
}

/**
 * A Set as ringtone in progress: the song, whether calls already vibrate to
 * it, and the song they vibrated to before, captured when the ask began.
 */
data class RingtoneAsk(val track: Track, val armed: Boolean, val previousSong: String?)
