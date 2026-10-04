package com.mosman.thrum

/**
 * The Thrum pattern file — what "Export" saves and "import" opens. Task 25.
 *
 * A small text file holding the score **in the format the app already uses
 * to remember it** (`Score.encode`, the proven pipe line), plus the tuning
 * the haptic was made with. That is the whole file. **No audio, no URI, no
 * path — only the vibration** (Sacred Rule 3's export exception, drawn as
 * narrowly as it can be): the song stays where it is, and the file works on
 * another phone precisely because it carries nothing phone-specific.
 *
 * Layout, one haptic per block, blocks separated by a blank line:
 *
 * ```text
 * THRUM 1
 * <Score.encode()>
 * <punch> <distance> <bodyMs>
 * ```
 *
 * Score lines cannot contain a newline (the encoder escapes them), so the
 * line structure is safe. A bundle ("All songs") is several blocks in one
 * file; import reads however many are there.
 *
 * Pure Kotlin, like every format in this project: the round-trip test —
 * export, then import, gives back an identical score (PROFILE §12) — runs on
 * the PC in seconds.
 */
object ThrumFile {

    /**
     * The scheme of an imported haptic's track URI. An import carries no song
     * — that is the format's whole point — so its "track" is a placeholder
     * the player recognises: it plays feel-only, and switching to "hear and
     * feel" is refused with a sentence instead of a player error.
     */
    const val IMPORTED_SCHEME = "thrum-imported://"

    fun isImported(uri: String): Boolean = uri.startsWith(IMPORTED_SCHEME)

    /**
     * Deterministic per name, so re-importing the same file overwrites its
     * own row instead of piling up duplicates.
     */
    fun importedUriFor(name: String): String =
        IMPORTED_SCHEME + name.trim().lowercase().replace(' ', '-').ifEmpty { "imported" }

    /** One or more haptics as text. Blocks in, blocks out. */
    fun encode(haptics: List<Haptic>): String = haptics.joinToString("\n\n") { haptic ->
        listOf(
            MAGIC,
            haptic.score.encode(),
            "${haptic.punch} ${haptic.distance} ${haptic.bodyMs}",
        ).joinToString("\n")
    }

    /**
     * Every valid block in the text, or an empty list for anything
     * unparseable — a corrupt or foreign file degrades to nothing imported,
     * never to a crash or a half-understood row.
     */
    fun decode(text: String, nowMs: Long = System.currentTimeMillis()): List<Haptic> {
        val out = mutableListOf<Haptic>()
        var scoreLine: String? = null
        var tuningLine: String? = null

        fun flush() {
            val scoreText = scoreLine
            val tuning = tuningLine
            if (scoreText != null && tuning != null) {
                val parts = tuning.split(' ').mapNotNull { it.toIntOrNull() }
                if (parts.size == 3) {
                    val score = Score.decode(scoreText)
                    if (score != null && score.amplitudes.isNotEmpty()) {
                        out.add(
                            Haptic(
                                trackUri = importedUriFor(score.sourceName),
                                score = score,
                                punch = parts[0],
                                distance = parts[1],
                                bodyMs = parts[2],
                                madeAtMs = nowMs,
                            ),
                        )
                    }
                }
            }
            scoreLine = null
            tuningLine = null
        }

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            when {
                line == MAGIC -> flush()
                line.isEmpty() -> Unit
                scoreLine == null -> scoreLine = line
                tuningLine == null -> tuningLine = line
            }
        }
        flush()
        return out
    }

    /** A safe suggested filename: the song's name, tolerable to a file system. */
    fun fileNameFor(name: String): String =
        name.ifEmpty { "haptic" }.replace(Regex("[^A-Za-z0-9 _-]"), "").trim()
            .ifEmpty { "haptic" } + ".thrum"

    private const val MAGIC = "THRUM 1"
}
