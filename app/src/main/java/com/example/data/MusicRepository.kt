package com.example.data

import kotlinx.coroutines.flow.Flow

class MusicRepository(private val musicDao: MusicDao) {

    val allFavorites: Flow<List<FavoriteTrackEntity>> = musicDao.getAllFavorites()

    val allScannedTracks: Flow<List<ScannedTrackEntity>> = musicDao.getAllScannedTracks()

    val allRemovedTracks: Flow<List<RemovedTrackEntity>> = musicDao.getAllRemovedTracks()

    suspend fun saveScannedTracks(tracks: List<ScannedTrackEntity>) {
        musicDao.insertScannedTracks(tracks)
    }

    suspend fun clearScannedTracks() {
        musicDao.clearScannedTracks()
    }

    suspend fun deleteScannedTracksByPaths(paths: List<String>) {
        musicDao.deleteScannedTracksByPaths(paths)
    }

    suspend fun deleteScannedTrackByPath(path: String) {
        musicDao.deleteScannedTrackByPath(path)
    }

    suspend fun markTracksAsRemoved(urls: List<String>) {
        musicDao.insertRemovedTracks(urls.map { RemovedTrackEntity(it) })
        musicDao.deleteScannedTracksByPaths(urls)
    }

    suspend fun restoreRemovedTracks(urls: List<String>) {
        musicDao.deleteRemovedTracks(urls)
    }

    suspend fun clearRemovedTracks() {
        musicDao.clearRemovedTracks()
    }

    fun getRecentHistory(limit: Int = 30): Flow<List<TrackHistoryEntity>> =
        musicDao.getRecentHistory(limit)

    suspend fun addFavorite(track: FavoriteTrackEntity) {
        musicDao.insertFavorite(track)
    }

    suspend fun removeFavoriteByUrl(url: String) {
        musicDao.deleteFavoriteByUrl(url)
    }

    suspend fun isTrackFavorite(url: String): Boolean {
        return musicDao.isTrackFavorite(url) > 0
    }

    suspend fun addHistoryRecord(record: TrackHistoryEntity) {
        musicDao.insertHistory(record)
    }

    suspend fun clearHistory() {
        musicDao.clearHistory()
    }
}
