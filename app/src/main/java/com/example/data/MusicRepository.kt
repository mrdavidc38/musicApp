package com.example.data

import kotlinx.coroutines.flow.Flow

class MusicRepository(private val musicDao: MusicDao) {

    val allFavorites: Flow<List<FavoriteTrackEntity>> = musicDao.getAllFavorites()

    val allScannedTracks: Flow<List<ScannedTrackEntity>> = musicDao.getAllScannedTracks()

    suspend fun saveScannedTracks(tracks: List<ScannedTrackEntity>) {
        musicDao.insertScannedTracks(tracks)
    }

    suspend fun clearScannedTracks() {
        musicDao.clearScannedTracks()
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
