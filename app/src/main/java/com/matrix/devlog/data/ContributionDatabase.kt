package com.matrix.devlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlatformAccount::class], version = 2, exportSchema = false)
abstract class ContributionDatabase : RoomDatabase() {
    abstract fun contributionDao(): ContributionDao

    companion object {
        @Volatile
        private var INSTANCE: ContributionDatabase? = null

        fun getDatabase(context: Context): ContributionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContributionDatabase::class.java,
                    "contribution_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
