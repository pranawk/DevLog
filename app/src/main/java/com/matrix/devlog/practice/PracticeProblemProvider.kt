package com.matrix.devlog.practice

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Random

object PracticeProblemProvider {

    private var cachedCfProblems: List<PracticeProblem>? = null
    private var cachedLcProblems: List<PracticeProblem>? = null
    
    private val allTopics = listOf("Math", "DP", "Greedy", "Graphs", "Strings", "Implementation", "Data Structures", "Number Theory", "DFS/BFS", "Sorting")

    fun getProblems(context: Context, platform: String): List<PracticeProblem> {
        return when (platform) {
            "Codeforces" -> getCfProblems(context)
            "LeetCode" -> getLcProblems(context)
            else -> emptyList()
        }
    }

    private fun getCfProblems(context: Context): List<PracticeProblem> {
        if (cachedCfProblems != null) return cachedCfProblems!!
        
        val problems = mutableListOf<PracticeProblem>()
        try {
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
                
                val ratingValue = extractCfRating(fileName)
                
                for (i in 1 until rows.size) {
                    val cols = rows[i].select("td")
                    if (cols.size >= 4) {
                        val nameElement = cols[1].select("a").first()
                        val name = nameElement?.text() ?: ""
                        val link = nameElement?.attr("href") ?: ""
                        val id = extractCfId(link)
                        
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
                            displayId = id,
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
            Log.e("PracticeProblemProvider", "Error parsing CF problems", e)
        }
        
        cachedCfProblems = problems.sortedBy { it.rating }
        return cachedCfProblems!!
    }

    private fun getLcProblems(context: Context): List<PracticeProblem> {
        if (cachedLcProblems != null) return cachedLcProblems!!
        
        val problems = mutableListOf<PracticeProblem>()
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open("problems/leetcode/leetcode_450.md")
            val content = inputStream.bufferedReader().use { it.readText() }
            
            val lines = content.lines()
            var currentDifficulty = "Easy"
            
            for (line in lines) {
                val trimmed = line.trim()
                
                if (trimmed.startsWith("##")) {
                    val headerText = trimmed.lowercase()
                    if (headerText.contains("easy")) currentDifficulty = "Easy"
                    else if (headerText.contains("medium")) currentDifficulty = "Medium"
                    else if (headerText.contains("hard")) currentDifficulty = "Hard"
                    continue
                }
                
                if (trimmed.startsWith("|")) {
                    val parts = trimmed.split("|").map { it.trim() }.filter { it.isNotBlank() }
                    
                    if (parts.size < 4) continue
                    if (parts[0].contains("#")) continue
                    if (parts[0].contains("---")) continue
                    if (parts[1].contains("Problem Title")) continue
                    
                    val problemNum = parts[0]
                    val name = parts[1]
                    val tagsStr = parts[2]
                    val linkPart = parts[3]
                    
                    val linkRegex = """\[.*\]\((.*)\)""".toRegex()
                    val linkMatch = linkRegex.find(linkPart)
                    val link = linkMatch?.groupValues?.get(1) ?: ""
                    val slug = extractLcSlug(link)
                    
                    if (name.isNotEmpty() && slug.isNotEmpty()) {
                        val tags = tagsStr.split("&", ",").map { it.trim() }.filter { it.isNotEmpty() }
                        
                        problems.add(PracticeProblem(
                            id = slug,
                            displayId = problemNum,
                            name = name,
                            link = link,
                            platform = "LeetCode",
                            difficulty = currentDifficulty,
                            rating = when(currentDifficulty) {
                                "Easy" -> 1
                                "Medium" -> 2
                                "Hard" -> 3
                                else -> 0
                            },
                            topics = tags
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PracticeProblemProvider", "Error parsing LC problems", e)
        }
        
        cachedLcProblems = problems
        return cachedLcProblems!!
    }

    private fun loadRealTags(context: Context): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        try {
            val inputStream = context.assets.open("problems/codeforces_all.json")
            val jsonStr = inputStream.bufferedReader().use { it.readText() }
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
        } catch (e: Exception) {}
        return map
    }

    private fun extractCfId(link: String): String {
        val parts = link.trimEnd('/').split("/")
        if (parts.size >= 2) {
            val index = parts.last()
            val contestId = parts[parts.size - 2]
            return "$contestId$index"
        }
        return link
    }

    private fun extractLcSlug(link: String): String {
        return link.trimEnd('/').split("/").last()
    }

    private fun extractCfRating(fileName: String): Int {
        return when {
            fileName.contains("lt_1300") -> 0
            fileName.contains("gt_2200") -> 2200
            else -> {
                try {
                    fileName.split("_").first().filter { it.isDigit() }.toInt()
                } catch (e: Exception) {
                    10000
                }
            }
        }
    }
}
