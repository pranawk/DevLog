package com.matrix.devlog.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContributionDao {
    @Query("SELECT * FROM platform_accounts")
    fun getAllAccountsFlow(): Flow<List<PlatformAccount>>

    @Query("SELECT * FROM platform_accounts WHERE id = :id")
    suspend fun getAccountById(id: String): PlatformAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: PlatformAccount)

    @Delete
    suspend fun deleteAccount(account: PlatformAccount)

    @Query("DELETE FROM platform_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("UPDATE platform_accounts SET cachedDataJson = '{}', totalContributions = 0, totalSolved = 0, streak = 0 WHERE id = :id")
    suspend fun clearAccountData(id: String)
}
