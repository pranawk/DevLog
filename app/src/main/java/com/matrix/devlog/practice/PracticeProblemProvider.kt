package com.matrix.devlog.practice

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Random

object PracticeProblemProvider {

    private var cachedProblems: List<PracticeProblem>? = null
    private val allTopics = listOf("Math", "DP", "Greedy", "Graphs", "Strings", "Implementation", "Data Structures", "Number Theory", "DFS/BFS", "Sorting")

    fun getProblems(context: Context, platform: String): List<PracticeProblem> {
        if (cachedProblems != null) return cachedProblems!!
        
        val problems = mutableListOf<PracticeProblem>()
        try {
            // Load real tags from cached JSON
            val cfTagsMap = loadRealTags(context)
            
            val assetManager = context.assets
            val files = assetManager.list("problems/codeforces") ?: emptyArray()
            
            for (fileName in files) {
                if (!fileName.endsWith(".html")) continue
                
                val inputStream = assetManager.open("problems/codeforces/$fileName")
                val html = inputStream.bufferedReader().use { it.readText() }
                val doc = Jsoup.parse(html)
                val rows = doc.select("table").last()?.select("tr") ?: continue
                
                val difficultyLabel = when (fileName) {
                    "lt_1300.html" -> "< 1300"
                    "gt_2200.html" -> "2200+"
                    else -> fileName.replace(".html", "").replace("_", "-")
                }
                
                val ratingValue = extractRating(fileName)
                
                for (i in 1 until rows.size) { // skip header
                    val cols = rows[i].select("td")
                    if (cols.size >= 4) {
                        val nameElement = cols[1].select("a").first()
                        val name = nameElement?.text() ?: ""
                        val link = nameElement?.attr("href") ?: ""
                        val id = extractId(link)
                        
                        // Use real tags if available, else deterministic mock ones
                        var topics = cfTagsMap[id]
                        if (topics == null || topics.isEmpty()) {
                            val random = Random(id.hashCode().toLong())
                            val numTopics = random.nextInt(2) + 2
                            val mockTopics = mutableSetOf<String>()
                            while (mockTopics.size < numTopics) {
                                mockTopics.add(allTopics[random.nextInt(allTopics.size)])
                            }
                            topics = mockTopics.toList()
                        }
                        
                        problems.add(PracticeProblem(
                            id = id,
                            name = name,
                            link = link,
                            platform = "Codeforces",
                            difficulty = difficultyLabel,
                            rating = ratingValue,
                            topics = topics
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PracticeProblemProvider", "Error parsing problems", e)
        }
        
        // Sorting by rating ensures < 1300 (rating 0) comes first
        cachedProblems = problems.sortedBy { it.rating }
        return cachedProblems!!
    }

    private fun loadRealTags(context: Context): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        try {
            val jsonStr = context.assets.open("problems/codeforces_all.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val result = root.optJSONObject("result")
            val problems = result?.optJSONArray("problems")
            if (problems != null) {
                for (i in 0 until problems.length()) {
                    val p = problems.getJSONObject(i)
                    val contestId = p.optInt("contestId")
                    val index = p.optString("index")
                    val id = "$contestId$index"
                    val tagsArray = p.optJSONArray("tags")
                    if (tagsArray != null) {
                        val tags = mutableListOf<String>()
                        for (j in 0 until tagsArray.length()) {
                            tags.add(tagsArray.getString(j))
                        }
                        map[id] = tags
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PracticeProblemProvider", "Error loading real tags", e)
        }
        return map
    }

    private fun extractId(link: String): String {
        // http://codeforces.com/problemset/problem/144/A -> 144A
        val parts = link.trimEnd('/').split("/")
        if (parts.size >= 2) {
            val index = parts.last()
            val contestId = parts[parts.size - 2]
            return "$contestId$index"
        }
        return link
    }

    private fun extractRating(fileName: String): Int {
        return when {
            fileName.contains("lt_1300") -> 0
            fileName.contains("gt_2200") -> 2200
            else -> {
                try {
                    fileName.split("_").first().filter { it.isDigit() }.toInt()
                } catch (e: Exception) {
                    10000 // default high
                }
            }
        }
    }
}
