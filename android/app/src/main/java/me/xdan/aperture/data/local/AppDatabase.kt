package me.xdan.aperture.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import me.xdan.aperture.data.local.dao.MediaDao
import me.xdan.aperture.data.local.dao.PlaybackProgressDao
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.data.remoteplayback.RemoteMediaDao
import me.xdan.aperture.data.remoteplayback.RemoteMediaEntity

@Database(
    entities = [MediaEntity::class, PlaybackProgressEntity::class, RemoteMediaEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun remoteMediaDao(): RemoteMediaDao

    companion object {
        const val DATABASE_NAME = "cinek_db"
    }
}
