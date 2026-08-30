package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // --- Favorites ---
    @Query("SELECT * FROM favorite_tracks ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(track: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE trackUrl = :url")
    suspend fun deleteFavoriteByUrl(url: String)

    @Query("SELECT COUNT(*) FROM favorite_tracks WHERE trackUrl = :url")
    suspend fun isTrackFavorite(url: String): Int

    // --- Scanned Local Tracks ---
    @Query("SELECT * FROM scanned_tracks ORDER BY title ASC")
    fun getAllScannedTracks(): Flow<List<ScannedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScannedTracks(tracks: List<ScannedTrackEntity>)

    @Query("DELETE FROM scanned_tracks WHERE path IN (:paths)")
    suspend fun deleteScannedTracksByPaths(paths: List<String>)

    @Query("DELETE FROM scanned_tracks WHERE path = :path")
    suspend fun deleteScannedTrackByPath(path: String)

    @Query("DELETE FROM scanned_tracks")
    suspend fun clearScannedTracks()

    // --- Removed Tracks (User excluded) ---
    @Query("SELECT * FROM removed_tracks")
    fun getAllRemovedTracks(): Flow<List<RemovedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemovedTracks(tracks: List<RemovedTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemovedTrack(track: RemovedTrackEntity)

    @Query("DELETE FROM removed_tracks WHERE trackUrl IN (:urls)")
    suspend fun deleteRemovedTracks(urls: List<String>)

    @Query("DELETE FROM removed_tracks")
    suspend fun clearRemovedTracks()

    // --- History ---
    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<TrackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(record: TrackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
