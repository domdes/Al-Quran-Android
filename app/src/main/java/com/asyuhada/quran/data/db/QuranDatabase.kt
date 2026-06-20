package com.asyuhada.quran.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.asyuhada.quran.data.db.dao.QuranDao
import com.asyuhada.quran.data.db.entities.*

@Database(
    entities = [
        QuranSettingsEntity::class,
        QuranBookmarkEntity::class,
        TafsirEntity::class,
        TranslationEntity::class,
        DownloadProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {

    abstract fun quranDao(): QuranDao

    companion object {
        @Volatile
        private var INSTANCE: QuranDatabase? = null

        fun getDatabase(context: Context): QuranDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    "quran_local_db"
                )
                .fallbackToDestructiveMigration() // Useful for updates during development
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
