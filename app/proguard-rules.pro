# Thrum's shrinker rules. Task 8.
#
# The rule that matters is the enum one, and it is here because this exact bug
# silently wiped every saved routine in pixel-routines: R8 renames enum
# constants, the name written to storage yesterday no longer matches the name
# the code produces today, and a decoder that returns null on anything it cannot
# parse turns that into "nothing was ever saved" without a word.
#
# It fails in the worst possible way — only in release builds, only after an
# upgrade, and only for users who had data worth losing.

# --- Anything persisted by name ------------------------------------------

# Event.Kind is written into SharedPreferences as `kind.name` (see Event.encode)
# and matched back by string (Event.decode). Its constants must keep the names
# they were saved under, forever.
-keepclassmembers enum com.mosman.thrum.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# --- Kept deliberately small ---------------------------------------------
#
# Score and Event serialise themselves by hand into a pipe-delimited string
# rather than through a reflection-based JSON library, precisely so the shrinker
# has nothing to break. That decision is why this file is nine rules long
# instead of ninety. PROFILE.md §8.

# The notification listener is constructed by the system from the manifest name,
# so nothing in our code references it and the shrinker cannot see it is used.
-keep class com.mosman.thrum.NotifService { *; }

# Line numbers in crash reports, without shipping the whole source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
