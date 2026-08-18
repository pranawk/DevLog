package com.matrix.devlog.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrix.devlog.data.PlatformAccount
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: ContributionViewModel) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    
    val platformOptions = remember(accounts) {
        val list = mutableListOf("all" to "Cumulative")
        accounts.filter { JSONObject(it.topicStatsJson).length() > 0 }.forEach {
            val name = when(it.id) {
                "github" -> "GitHub"
                "leetcode" -> "LeetCode"
                "codeforces" -> "Codeforces"
                "atcoder" -> "AtCoder"
                else -> it.id.replaceFirstChar { c -> c.uppercase() }
            }
            list.add(it.id to name)
        }
        list
    }

    val pagerState = rememberPagerState(pageCount = { platformOptions.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Topic Statistics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (platformOptions.size > 1) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    platformOptions.forEachIndexed { index, (_, name) ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { 
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(name, fontSize = 13.sp) }
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val selectedPlatformId = platformOptions[pageIndex].first
                
                val filteredStats = remember(accounts, selectedPlatformId) {
                    val aggregate = mutableMapOf<String, Int>()
                    accounts.filter { selectedPlatformId == "all" || it.id == selectedPlatformId }.forEach { account ->
                        val json = JSONObject(account.topicStatsJson)
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val rawKey = keys.next()
                            val count = json.optInt(rawKey, 0)
                            
                            // Normalization logic
                            val normalizedKey = when (rawKey.lowercase().trim()) {
                                "greedy" -> "Greedy"
                                "dp", "dynamic programming" -> "Dynamic Programming"
                                "math", "mathematics" -> "Math"
                                "graphs", "graph" -> "Graphs"
                                "dsu", "disjoint set union" -> "DSU"
                                "bitmask", "bitmasks" -> "Bitmasks"
                                "strings", "string" -> "Strings"
                                "data structures" -> "Data Structures"
                                "binary search" -> "Binary Search"
                                "trees", "tree" -> "Trees"
                                "dfs and similar", "dfs" -> "DFS"
                                "bfs" -> "BFS"
                                "sort", "sorting", "sortings" -> "Sorting"
                                "two pointers" -> "Two Pointers"
                                "brute force" -> "Brute Force"
                                "implementation" -> "Implementation"
                                "number theory" -> "Number Theory"
                                "constructive algorithms" -> "Constructive"
                                else -> rawKey.replaceFirstChar { it.uppercase() }
                            }
                            
                            aggregate[normalizedKey] = (aggregate[normalizedKey] ?: 0) + count
                        }
                    }
                    aggregate.toList().sortedByDescending { it.second }
                }

                if (filteredStats.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (accounts.isEmpty()) "No accounts configured." else "No topic data available for this platform.\nTry refreshing your account.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {




                                    val chartData = filteredStats.take(15)
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(190.dp)
                                            .aspectRatio(1f)
                                            .padding(12.dp), // Padding inside the square Box ensures no clipping
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PieChart(
                                            data = chartData.map { it.second.toFloat() },
                                            colors = chartColors.take(chartData.size),
                                            modifier = Modifier.fillMaxSize(),
                                            thickness = 45.dp
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(20.dp))
                                    
                                    // Legend - Strictly 3 lines (3 items per line = 9 items)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        maxItemsInEachRow = 3 
                                    ) {
                                        filteredStats.take(9).forEachIndexed { index, (name, _) ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(chartColors[index % chartColors.size])
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(text = name, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Detailed Breakdown",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        item {
                            // Height adjusted to 225dp for 3.5 topics peek
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(225.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                ) {
                                    filteredStats.forEach { (name, count) ->
                                        TopicRow(name = name, count = count)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopicRow(name: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = count.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun PieChart(
    data: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    thickness: Dp = 50.dp
) {
    val totalSum = data.sum()
    if (totalSum == 0f) return

    val floatValue = mutableListOf<Float>()
    data.forEachIndexed { index, value ->
        floatValue.add(index, 360 * value / totalSum)
    }

    var lastValue = 0f
    val gapDegrees = 0.5f // Small gap between slices to act as a border

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            floatValue.forEachIndexed { index, value ->
                // Shave off a tiny bit from the sweep angle to create a visual gap/border
                val sweepAngle = if (value > gapDegrees) value - gapDegrees else value
                
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = lastValue,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = thickness.toPx(), cap = StrokeCap.Butt)
                )
                lastValue += value
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        maxItemsInEachRow = maxItemsInEachRow,
        content = { content() }
    )
}

val chartColors = listOf(
    Color(0xFFE57373),
    Color(0xFFF06292),
    Color(0xFFBA68C8),
    Color(0xFF9575CD),
    Color(0xFF7986CB),
    Color(0xFF64B5F6),
    Color(0xFF4FC3F7),
    Color(0xFF4DD0E1),
    Color(0xFF4DB6AC),
    Color(0xFF81C784),
    Color(0xFFAED581),
    Color(0xFFFFD54F),
    Color(0xFFFFB74D),
    Color(0xFFFF8A65),
    Color(0xFFA1887F),
    Color(0xFF90A4AE)
)
