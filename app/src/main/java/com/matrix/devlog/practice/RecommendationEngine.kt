package com.matrix.devlog.practice

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class RecommendationEngine(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val modelPath = "problems/recommendation_model.tflite"

    // Difficulty mapping (Alphabetical): Easy=0, Expert=1, Hard=2, Medium=3
    private val difficultyMap = mapOf(
        "Easy" to 0.0f,
        "Expert" to 1.0f,
        "Hard" to 2.0f,
        "Medium" to 3.0f
    )

    // Tag list for mapping (must be consistent with model training)
    // Alphabetical order of common tags + 'general'
    private val tagList = listOf(
        "binary search", "bitmasks", "brute force", "combinatorics", 
        "constructive algorithms", "data structures", "dfs and similar", 
        "dp", "general", "geometry", "graphs", "greedy", 
        "implementation", "math", "number theory", "probabilities", 
        "sortings", "strings", "trees", "two pointers"
    ).sorted()

    init {
        try {
            interpreter = Interpreter(loadModelFile())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun getTagIdx(tags: List<String>): Float {
        if (tags.isEmpty()) return tagList.indexOf("general").toFloat()
        val firstTag = tags[0].lowercase()
        val idx = tagList.indexOf(firstTag)
        return if (idx != -1) idx.toFloat() else tagList.indexOf("general").toFloat()
    }

    fun getRecommendationScore(platform: String, difficulty: String, tags: List<String>, userRating: Int): Float {
        val interp = interpreter ?: return 0f

        // Platform: Codeforces=0, LeetCode=1
        val platformIdx = if (platform.lowercase() == "codeforces") 0.0f else 1.0f

        // Difficulty
        val diffIdx = difficultyMap[difficulty] ?: 3.0f // Default to Medium

        // Tags
        val tagsIdx = getTagIdx(tags)

        // Rating normalization (max_rating in notebook was 2500, but we use 3500 for safety)
        val maxRating = 3500f
        val ratingNorm = userRating.toFloat() / maxRating

        // Input shape (1, 4)
        val input = arrayOf(floatArrayOf(platformIdx, diffIdx, tagsIdx, ratingNorm))
        val output = Array(1) { FloatArray(1) }

        try {
            interp.run(input, output)
            return output[0][0]
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return 0f
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
