package com.matrix.devlog.practice

data class PracticeProblem(
    val id: String,
    val name: String,
    val link: String,
    val platform: String, // "Codeforces" or "LeetCode"
    val difficulty: String, // "Easy", "Medium", etc. or "1300-1399"
    val rating: Int = 0,
    val topics: List<String> = emptyList(),
    val isSolved: Boolean = false
)
