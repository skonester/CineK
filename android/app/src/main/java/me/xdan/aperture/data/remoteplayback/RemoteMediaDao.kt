package me.xdan.aperture.data.remoteplayback

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteMediaDao {
    @Query("SELECT * FROM remote_media WHERE contentId = :id")
    suspend fun get(id: String): RemoteMediaEntity?

    @Query("SELECT * FROM remote_media ORDER BY lastUpdated DESC, title ASC")
    fun observeAll(): Flow<List<RemoteMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(media: RemoteMediaEntity)

    @Transaction
    suspend fun remember(media: RemoteMediaEntity) {
        val previous = get(media.contentId)
        put(if (previous == null) media else media.copy(lastProvider = previous.lastProvider,
            lastQuality = previous.lastQuality, positionMs = previous.positionMs, durationMs = previous.durationMs,
            isCompleted = previous.isCompleted, lastUpdated = previous.lastUpdated))
    }

    @Query("UPDATE remote_media SET lastProvider = :provider, lastQuality = :quality WHERE contentId = :id")
    suspend fun selectProvider(id: String, provider: String, quality: Int?)

    @Transaction
    suspend fun saveProgress(id: String, snapshot: StreamPlaybackSnapshot, now: Long) {
        get(id)?.let { put(it.withSnapshot(snapshot, now)) }
    }

    @Query("DELETE FROM remote_media WHERE contentId = :id")
    suspend fun delete(id: String)
}
