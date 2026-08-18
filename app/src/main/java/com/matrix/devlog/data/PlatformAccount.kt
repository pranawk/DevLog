package com.matrix.devlog.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "platform_accounts")
data class PlatformAccount(
    @PrimaryKey val id: String, // "github", "leetcode", "codeforces", "geeksforgeeks", "codechef", "atcoder", "topcoder"
    val username: String,
    val colorTheme: String, // "GREEN", "BLUE", "RED", "ORANGE", "PURPLE"
    val cachedDataJson: String, // Map of "YYYY-MM-DD" to Int (count)
    val solvedProblemsJson: String = "[]", // List of solved problem IDs/links
    val totalContributions: Int = 0,
    val totalSolved: Int = 0,
    val totalProblems: Int = 0,
    val streak: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
