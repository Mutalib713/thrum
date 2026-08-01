package com.mosman.thrum

/**
 * One thing that happened during a call, recorded so it can be read back off
 * the screen.
 *
 * This exists because there is no USB cable, so `adb logcat` is unavailable and
 * the app has to be its own measuring instrument. Task 2's results table is
 * filled in from this list, which makes it evidence rather than recollection.
 *
 * Pure Kotlin, same reasoning as [Score].
 */
data class Event(
    val at: Long,
    val kind: Kind,
    val ringer: String,
    val latencyMs: Long,
    val note: String = "",
) {
    enum class Kind {
        /** An incoming call was seen and a score was started. */
        FIRED,

        /** An incoming call was seen and deliberately ignored. [note] says why. */
        SKIPPED,

        /** The call ended, was answered, or the notification went away. Vibration stopped. */
        STOPPED,

        /** The safety cap stopped a loop that had run too long. Should never happen in normal use. */
        CAPPED,

        /** Android bound or unbound the listener. Evidence for R5. */
        LISTENER,

        /**
         * A file was read by [AudioDecoder]. Task 3's proof, recorded rather
         * than read off the screen — screenshots are unreliable on this machine,
         * and a result dictated aloud is recollection, not evidence.
         */
        DECODED,
    }

    fun encode(): String = listOf(
        FORMAT_VERSION.toString(),
        at.toString(),
        kind.name,
        esc(ringer),
        latencyMs.toString(),
        esc(note),
    ).joinToString(SEP.toString())

    companion object {
        private const val FORMAT_VERSION = 1
        private const val SEP = '|'
        const val MAX_KEPT = 40

        fun decode(line: String): Event? {
            val p = line.split(SEP)
            if (p.size != 6) return null
            if (p[0].toIntOrNull() != FORMAT_VERSION) return null
            val at = p[1].toLongOrNull() ?: return null
            // Names are matched explicitly rather than with valueOf() so that a
            // renamed or removed constant degrades to "unreadable line" instead
            // of throwing. R8 renaming enum constants silently destroyed all
            // saved data in pixel-routines; see CLAUDE.md.
            val kind = Kind.entries.firstOrNull { it.name == p[2] } ?: return null
            val latency = p[4].toLongOrNull() ?: return null
            return Event(at, kind, unesc(p[3]), latency, unesc(p[5]))
        }

        fun encodeAll(events: List<Event>): String =
            events.takeLast(MAX_KEPT).joinToString("\n") { it.encode() }

        fun decodeAll(text: String): List<Event> =
            if (text.isEmpty()) emptyList()
            else text.lineSequence().mapNotNull { decode(it) }.toList()

        private fun esc(s: String) =
            s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")

        private fun unesc(s: String): String {
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        '\\' -> out.append('\\')
                        'p' -> out.append('|')
                        'n' -> out.append('\n')
                        else -> out.append(s[i + 1])
                    }
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
