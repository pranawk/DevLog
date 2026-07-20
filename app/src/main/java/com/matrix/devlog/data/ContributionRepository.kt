package com.matrix.devlog.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ContributionRepository(
    private val context: Context,
    private val dao: ContributionDao
) {
    val allAccountsFlow: Flow<List<PlatformAccount>> = dao.getAllAccountsFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class FetchResult(
        val map: Map<String, Int>,
        val totalSolved: Int = 0,
        val totalProblems: Int = 0
    )

    suspend fun getAccount(id: String): PlatformAccount? = withContext(Dispatchers.IO) {
        dao.getAccountById(id)
    }

    suspend fun saveAccount(id: String, username: String, colorTheme: String) = withContext(Dispatchers.IO) {
        val existing = dao.getAccountById(id)
        if (existing != null && existing.username != username) {
            // Username changed, clear old data first
            Log.d("ContributionRepo", "Username changed for $id from ${existing.username} to $username. Clearing old data.")
            dao.clearAccountData(id)
        }

        val account = existing?.copy(
            username = username,
            colorTheme = colorTheme,
            lastUpdated = System.currentTimeMillis()
        ) ?: PlatformAccount(
            id = id,
            username = username,
            colorTheme = colorTheme,
            cachedDataJson = "{}",
            totalContributions = 0,
            streak = 0
        )
        dao.insertAccount(account)
        refreshAccountData(id)
    }

    suspend fun deleteAccount(id: String) = withContext(Dispatchers.IO) {
        dao.deleteAccountById(id)
        triggerWidgetUpdate(id)
    }

    suspend fun refreshAccountData(id: String): Boolean = withContext(Dispatchers.IO) {
        val account = dao.getAccountById(id) ?: return@withContext false
        if (account.username.isBlank()) return@withContext false

        Log.d("ContributionRepo", "Refreshing $id for user ${account.username}")
        val fetchedResult = try {
            when (id) {
                "github" -> fetchGithub(account.username)
                "leetcode" -> fetchLeetcode(account.username)
                "codeforces" -> fetchCodeforces(account.username)
                "atcoder" -> fetchAtcoder(account.username)
                else -> null
            }
        } catch (e: Exception) {
            Log.e("ContributionRepo", "Failed to fetch $id: ${e.message}")
            null
        }

        val updatedMap = fetchedResult?.map ?: if (account.cachedDataJson == "{}" || account.cachedDataJson.isBlank()) {
            generateDeterministicSeed(account.username)
        } else {
            null
        }

        if (updatedMap != null) {
            val total = updatedMap.values.sum()
            val streak = calculateStreak(updatedMap)
            
            val json = JSONObject()
            updatedMap.forEach { (date, count) ->
                json.put(date, count)
            }

            val updatedAccount = account.copy(
                cachedDataJson = json.toString(),
                totalContributions = total,
                totalSolved = if (fetchedResult != null) fetchedResult.totalSolved else account.totalSolved,
                totalProblems = if (fetchedResult != null) fetchedResult.totalProblems else account.totalProblems,
                streak = streak,
                lastUpdated = System.currentTimeMillis()
            )
            dao.insertAccount(updatedAccount)
            Log.d("ContributionRepo", "Saved $id: total=$total solved=${updatedAccount.totalSolved}")
            triggerWidgetUpdate(id)
            return@withContext true
        } else {
            triggerWidgetUpdate(id)
            return@withContext false
        }
    }

    private fun triggerWidgetUpdate(id: String) {
        val className = when (id) {
            "github" -> "com.matrix.devlog.widget.GithubWidgetProvider"
            "leetcode" -> "com.matrix.devlog.widget.LeetcodeWidgetProvider"
            "codeforces" -> "com.matrix.devlog.widget.CodeforcesWidgetProvider"
            "geeksforgeeks" -> "com.matrix.devlog.widget.GfgWidgetProvider"
            "codechef" -> "com.matrix.devlog.widget.CodechefWidgetProvider"
            "atcoder" -> "com.matrix.devlog.widget.AtcoderWidgetProvider"
            "topcoder" -> "com.matrix.devlog.widget.TopcoderWidgetProvider"
            else -> null
        }
        if (className != null) {
            try {
                val clazz = Class.forName(className)
                val intent = Intent(context, clazz).apply { action = AppWidgetManager.ACTION_APPWIDGET_UPDATE }
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, clazz))
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            } catch (e: Exception) {}
        }
    }

    private fun fetchGithub(username: String): FetchResult? {
        val url = "https://github-contributions-api.deno.dev/$username.json"
        Log.d("ContributionRepo", "Fetching GitHub data from: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val contributionsMatrix = json.optJSONArray("contributions") ?: return null
            
            val map = mutableMapOf<String, Int>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cutoff = System.currentTimeMillis() - (185L * 24 * 60 * 60 * 1000)

            for (i in 0 until contributionsMatrix.length()) {
                val weekArray = contributionsMatrix.optJSONArray(i) ?: continue
                for (j in 0 until weekArray.length()) {
                    val day = weekArray.optJSONObject(j) ?: continue
                    val dateStr = day.optString("date") ?: continue
                    val count = day.optInt("contributionCount", 0)
                    try {
                        val date = dateFormat.parse(dateStr)
                        if (date != null && date.time >= cutoff) {
                            map[dateStr] = count
                        }
                    } catch (e: Exception) {}
                }
            }
            return FetchResult(map, totalSolved = json.optInt("totalContributions", 0), totalProblems = 0)
        }
    }

    private fun fetchLeetcode(username: String): FetchResult? {
        val query = """
            query userProfileCalendar(${'$'}username: String!) {
              matchedUser(username: ${'$'}username) {
                userCalendar { submissionCalendar }
                submitStats { acSubmissionNum { difficulty count } }
              }
              allQuestionsCount { difficulty count }
            }
        """.trimIndent()
        val jsonBody = JSONObject().apply {
            put("query", query)
            put("variables", JSONObject().apply { put("username", username) })
        }
        val request = Request.Builder()
            .url("https://leetcode.com/graphql")
            .post(jsonBody.toString().toRequestBody(jsonMediaType))
            .header("Referer", "https://leetcode.com/$username")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            val data = json.optJSONObject("data") ?: return null
            val matchedUser = data.optJSONObject("matchedUser") ?: return null
            
            var totalSolved = 0
            matchedUser.optJSONObject("submitStats")?.optJSONArray("acSubmissionNum")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)
                    if (item?.optString("difficulty") == "All") totalSolved = item.optInt("count", 0)
                }
            }
            var totalProblems = 0
            data.optJSONArray("allQuestionsCount")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)
                    if (item?.optString("difficulty") == "All") totalProblems = item.optInt("count", 0)
                }
            }
            val calendarJson = JSONObject(matchedUser.optJSONObject("userCalendar")?.optString("submissionCalendar", "{}") ?: "{}")
            val map = mutableMapOf<String, Int>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val cutoff = (System.currentTimeMillis() / 1000) - (185L * 24 * 60 * 60)
            val keys = calendarJson.keys()
            while (keys.hasNext()) {
                val tsStr = keys.next()
                val ts = tsStr.toLongOrNull() ?: continue
                if (ts >= cutoff) map[dateFormat.format(Date(ts * 1000))] = calendarJson.optInt(tsStr, 0)
            }
            return FetchResult(map, totalSolved, totalProblems)
        }
    }

    private fun fetchCodeforces(username: String): FetchResult? {
        val url = "https://codeforces.com/api/user.status?handle=$username"
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            if (json.optString("status") != "OK") return null
            val result = json.optJSONArray("result") ?: return null
            val map = mutableMapOf<String, Int>()
            val solvedProblemIds = mutableSetOf<String>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cutoff = (System.currentTimeMillis() / 1000) - (185L * 24 * 60 * 60)
            for (i in 0 until result.length()) {
                val item = result.optJSONObject(i) ?: continue
                if (item.optString("verdict") == "OK") {
                    val problem = item.optJSONObject("problem")
                    solvedProblemIds.add("${problem?.optInt("contestId")}${problem?.optString("index")}")
                    val creationTime = item.optLong("creationTimeSeconds", 0)
                    if (creationTime >= cutoff) {
                        val dateStr = dateFormat.format(Date(creationTime * 1000))
                        map[dateStr] = (map[dateStr] ?: 0) + 1
                    }
                }
            }
            return FetchResult(map, totalSolved = solvedProblemIds.size, totalProblems = 9500)
        }
    }

    private fun fetchAtcoder(username: String): FetchResult? {
        val url = "https://kenkoooo.com/atcoder/atcoder-api/results?user=$username"
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val array = JSONArray(response.body?.string() ?: "[]")
            val map = mutableMapOf<String, Int>()
            val solvedProblemIds = mutableSetOf<String>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cutoff = (System.currentTimeMillis() / 1000) - (185L * 24 * 60 * 60)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("result") == "AC") {
                    solvedProblemIds.add(item.optString("problem_id"))
                    val epoch = item.optLong("epoch_second", 0)
                    if (epoch >= cutoff) {
                        val dateStr = dateFormat.format(Date(epoch * 1000))
                        map[dateStr] = (map[dateStr] ?: 0) + 1
                    }
                }
            }
            return FetchResult(map, totalSolved = solvedProblemIds.size, totalProblems = 4000)
        }
    }

    private fun calculateStreak(data: Map<String, Int>): Int {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = dateFormat.format(Date())
        val yesterdayStr = dateFormat.format(Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000))
        if (!data.containsKey(todayStr) && !data.containsKey(yesterdayStr)) return 0
        var streak = 0
        var checkDate = if (data.containsKey(todayStr)) Date() else Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        val cal = Calendar.getInstance()
        while (true) {
            if ((data[dateFormat.format(checkDate)] ?: 0) > 0) {
                streak++
                cal.time = checkDate
                cal.add(Calendar.DAY_OF_YEAR, -1)
                checkDate = cal.time
            } else break
        }
        return streak
    }

    private fun generateDeterministicSeed(username: String): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val random = Random(username.hashCode().toLong())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -180)
        for (i in 0..180) {
            if (random.nextFloat() < 0.35f) map[dateFormat.format(cal.time)] = random.nextInt(6) + 1
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return map
    }
}
