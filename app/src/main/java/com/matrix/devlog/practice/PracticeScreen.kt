package com.matrix.devlog.practice

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrix.devlog.data.ContributionDatabase
import com.matrix.devlog.data.ContributionRepository
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val db = remember { ContributionDatabase.getDatabase(context) }
    val repository = remember { ContributionRepository(context, db.contributionDao()) }
    val accounts by db.contributionDao().getAllAccountsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    
    var selectedPlatform by remember { mutableStateOf("Codeforces") }
    val platformOptions = listOf("Codeforces", "LeetCode", "AtCoder")

    LaunchedEffect(selectedPlatform) {
        repository.refreshAccountData(selectedPlatform.lowercase())
    }

    val problems = remember(selectedPlatform) { 
        PracticeProblemProvider.getProblems(context, selectedPlatform) 
    }
    val categories = remember(problems) { 
        problems.map { it.difficulty }.distinct() 
    }
    
    val pagerState = rememberPagerState(pageCount = { categories.size })
    
    // Reset pager when platform changes
    LaunchedEffect(selectedPlatform) {
        if (pagerState.pageCount > 0) {
            pagerState.scrollToPage(0)
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Practice",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                )
                
                // Platform Selector
                ScrollableTabRow(
                    selectedTabIndex = platformOptions.indexOf(selectedPlatform),
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {},
                    indicator = {}
                ) {
                    platformOptions.forEach { platform ->
                        val isSelected = selectedPlatform == platform
                        Tab(
                            selected = isSelected,
                            onClick = { selectedPlatform = platform },
                            text = {
                                Text(
                                    text = platform,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                if (categories.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        divider = {},
                        indicator = { tabPositions ->
                            if (pagerState.currentPage < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) {
                        categories.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { 
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                                    ) 
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (categories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selectedPlatform == "AtCoder") {
                        Text("Coming soon!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (selectedPlatform == "LeetCode") {
                        Text("Please save your LeetCode username first!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        CircularProgressIndicator()
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val category = categories[page]
                    val problemsInCategory = problems.filter { it.difficulty == category }
                    val account = accounts.find { it.id == selectedPlatform.lowercase() }
                    val solvedIds = remember(account) {
                        val set = mutableSetOf<String>()
                        try {
                            val arr = JSONArray(account?.solvedProblemsJson ?: "[]")
                            for (i in 0 until arr.length()) {
                                set.add(arr.getString(i))
                            }
                        } catch (e: Exception) {}
                        set
                    }

                    ProblemList(
                        problems = problemsInCategory,
                        solvedIds = solvedIds,
                        onProblemClick = { problem ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(problem.link))
                            context.startActivity(intent)
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(0.dp))
        }
    }
}

@Composable
fun ProblemList(
    problems: List<PracticeProblem>,
    solvedIds: Set<String>,
    onProblemClick: (PracticeProblem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(problems) { problem ->
            val isSolved = solvedIds.contains(problem.id)
            ProblemItem(
                problem = problem,
                isSolved = isSolved,
                onClick = { onProblemClick(problem) }
            )
        }
    }
}

@Composable
fun ProblemItem(
    problem: PracticeProblem,
    isSolved: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSolved) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isSolved) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (problem.platform == "Codeforces") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSolved) Color(0xFFC8E6C9) else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = problem.id,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = if (isSolved) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Text(
                        text = problem.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isSolved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val displayTags = problem.topics.take(3)
                    displayTags.forEach { topic ->
                        Surface(
                            shape = CircleShape,
                            color = if (isSolved) Color(0xFFC8E6C9).copy(alpha = 0.5f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSolved) Color(0xFF4CAF50).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text(
                                text = topic,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 0.dp)
                                    .wrapContentHeight(Alignment.CenterVertically),
                                color = if (isSolved) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            if (isSolved) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Solved",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
