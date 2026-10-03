package com.mosman.thrum

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The haptic library, persisted with Room — Mutalib's pick over plain files,
 * because hundreds of haptics need lists and search, which is what a database
 * is for (PROFILE.md §7, decided 2026-10-03).
 *
 * This file is the only Room-aware code in the project. [Track], [Haptic] and
 * [HapticQueue] stay pure Kotlin with no Android imports, so every decision
 * about them is testable on the PC; the entities here are thin row shapes and
 * the mappers are the only place the two worlds meet.
 *
 * The score is stored **as its own encoded line** — `Score.encode()`, the same
 * pipe format the call settings already use — rather than as columns. A score
 * is a few thousand small integers; one text column keeps the proven format
 * and means Task 25's export files need no second serialisation design.
 *
 * **Why `exportSchema = false`:** the library is a cache over sources that
 * always exist elsewhere — the song files on the phone, the collection in the
 * APK. Every row is re-derivable by re-scanning and re-analysing, so the
 * migration policy for v1 is "wipe and rescan" rather than carried-forward
 * schema history. If a table ever starts holding something irreplaceable, that
 * is the moment to start exporting schemas — and it should be a deliberate
 * step, not a default.
 */

@Entity(tableName = "tracks")
data class TrackEntity(
    /** The track's identity — see [Track.sourceUri]. */
    @PrimaryKey val sourceUri: String,
    val name: String,
    val artist: String,
    val durationMs: Long,
    /** A [TrackKind] name. The R8 enum rule in `proguard-rules.pro` keeps these names stable. */
    val kind: String,
    val readable: Boolean,
    /** When the scan last saw the file, so a later scan can drop vanished ones. */
    val lastSeenAtMs: Long,
)

/** One row per track — a retune rewrites it, so a haptic and its tuning cannot disagree. */
@Entity(tableName = "haptics")
data class HapticEntity(
    @PrimaryKey val trackUri: String,
    /** The whole track's score as [Score.encode] — possibly far over the vibrator's step cap. */
    val scoreText: String,
    val punch: Int,
    val distance: Int,
    val bodyMs: Int,
    val madeAtMs: Long,
)

fun TrackEntity.toTrack(): Track = Track(
    sourceUri = sourceUri,
    name = name,
    artist = artist,
    durationMs = durationMs,
    kind = runCatching { TrackKind.valueOf(kind) }.getOrDefault(TrackKind.FILE),
    readable = readable,
)

fun Track.toEntity(lastSeenAtMs: Long): TrackEntity = TrackEntity(
    sourceUri = sourceUri,
    name = name,
    artist = artist,
    durationMs = durationMs,
    kind = kind.name,
    readable = readable,
    lastSeenAtMs = lastSeenAtMs,
)

/**
 * Returns null rather than throwing for a row whose score no longer decodes —
 * the same degrade-to-absent rule [Score.decode] sets, so one corrupt row can
 * never take down the My Haptics list.
 */
fun HapticEntity.toHaptic(): Haptic? = Score.decode(scoreText)?.let { score ->
    Haptic(
        trackUri = trackUri,
        score = score,
        punch = punch,
        distance = distance,
        bodyMs = bodyMs,
        madeAtMs = madeAtMs,
    )
}

fun Haptic.toEntity(): HapticEntity = HapticEntity(
    trackUri = trackUri,
    scoreText = score.encode(),
    punch = punch,
    distance = distance,
    bodyMs = bodyMs,
    madeAtMs = madeAtMs,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM tracks ORDER BY name COLLATE NOCASE")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM haptics ORDER BY madeAtMs DESC")
    fun observeHaptics(): Flow<List<HapticEntity>>

    @Query("SELECT * FROM haptics WHERE trackUri = :trackUri")
    suspend fun hapticFor(trackUri: String): HapticEntity?

    /** The made set, for seeding [HapticQueue] without loading any scores. */
    @Query("SELECT trackUri FROM haptics")
    suspend fun madeTrackUris(): List<String>

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Upsert
    suspend fun upsertHaptic(haptic: HapticEntity)

    @Query("DELETE FROM tracks WHERE sourceUri = :trackUri")
    suspend fun removeTrack(trackUri: String)

    /** Deleting a track takes its haptic with it — a haptic without a source is a lie on screen. */
    @Query("DELETE FROM haptics WHERE trackUri = :trackUri")
    suspend fun removeHaptic(trackUri: String)
}

@Database(
    entities = [TrackEntity::class, HapticEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LibraryDb : RoomDatabase() {
    abstract fun dao(): LibraryDao
}
