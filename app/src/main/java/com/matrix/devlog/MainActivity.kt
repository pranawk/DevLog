package com.matrix.devlog

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrix.devlog.data.PlatformAccount
import com.matrix.devlog.practice.PracticeScreen
import com.matrix.devlog.ui.ContributionViewModel
import com.matrix.devlog.ui.StatsScreen
import com.matrix.devlog.ui.theme.MyApplicationTheme
import com.matrix.devlog.widget.WidgetDrawingHelper
import com.matrix.devlog.worker.DataRefreshWorker

class MainActivity : ComponentActivity() {
    private val viewModel: ContributionViewModel by viewModels {
        ContributionViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enqueue periodic data refresh
        DataRefreshWorker.enqueuePeriodicWork(this)

        setContent {
            MyApplicationTheme {
                MainContainer(viewModel = viewModel)
            }
        }
    }
}

enum class ScreenTab {
    HOME, PRACTICE, STATS
}

@Composable
fun MainContainer(viewModel: ContributionViewModel) {
    var currentTab by remember { mutableStateOf(ScreenTab.HOME) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                FloatingBottomNavigation(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0) // Disable default insets handling for content to avoid double padding
    ) { innerPadding ->
        // We handle top for the status bar if needed.
        Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            when (currentTab) {
                ScreenTab.HOME -> DashboardScreen(viewModel = viewModel)
                ScreenTab.PRACTICE -> PracticeScreen()
                ScreenTab.STATS -> StatsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FloatingBottomNavigation(
    currentTab: ScreenTab,
    onTabSelected: (ScreenTab) -> Unit
) {
    // Floating Design
    Surface(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .height(68.dp),
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItem(
                icon = Icons.Default.Dashboard,
                label = "Home",
                isSelected = currentTab == ScreenTab.HOME,
                onClick = { onTabSelected(ScreenTab.HOME) }
            )
            NavigationItem(
                icon = Icons.Default.School,
                label = "Practice",
                isSelected = currentTab == ScreenTab.PRACTICE,
                onClick = { onTabSelected(ScreenTab.PRACTICE) }
            )
            NavigationItem(
                icon = Icons.Default.PieChart,
                label = "Stats",
                isSelected = currentTab == ScreenTab.STATS,
                onClick = { onTabSelected(ScreenTab.STATS) }
            )
        }
    }
}

@Composable
fun RowScope.NavigationItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

data class PlatformInfo(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val iconResId: Int,
    val usernamePlaceholder: String
)

val platforms = listOf(
    PlatformInfo("github", "GitHub", Color(0xFF24292E), R.drawable.logo_github, "e.g., torvalds"),
    PlatformInfo("leetcode", "LeetCode", Color(0xFFFFA116), R.drawable.logo_leetcode, "e.g., username"),
    PlatformInfo("codeforces", "Codeforces", Color(0xFF3182CE), R.drawable.logo_codeforces, "e.g., tourist"),
    PlatformInfo("geeksforgeeks", "GeeksForGeeks", Color(0xFF2F8D46), R.drawable.logo_geeksforgeeks, "e.g., gfg_user"),
    PlatformInfo("codechef", "CodeChef", Color(0xFF5B4636), R.drawable.logo_codechef, "e.g., chef_pro"),
    PlatformInfo("atcoder", "AtCoder", Color(0xFF1F1F1F), R.drawable.logo_atcoder, "e.g., chokudai"),
    PlatformInfo("topcoder", "Topcoder", Color(0xFF666666), R.drawable.logo_topcoder, "e.g., tc_coder")
)

data class ColorThemeOption(
    val key: String,
    val label: String,
    val colorHexes: List<String>
)

val colorThemeOptions = listOf(
    ColorThemeOption("GREEN", "Emerald Green", listOf("#1E1F22", "#0E4429", "#006D32", "#26A641", "#39D353")),
    ColorThemeOption("BLUE", "Ocean Blue", listOf("#1E1F22", "#0A3055", "#004F9F", "#0077E6", "#3399FF")),
    ColorThemeOption("RED", "Sunset Red", listOf("#1E1F22", "#4C1010", "#801A1A", "#C02626", "#EF4444")),
    ColorThemeOption("ORANGE", "Neon Orange", listOf("#1E1F22", "#4F250A", "#8E3E0F", "#D96B27", "#F97316")),
    ColorThemeOption("PURPLE", "Royal Purple", listOf("#1E1F22", "#30104C", "#5B1A8F", "#8B26D9", "#A855F7"))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: ContributionViewModel) {
    val context = LocalContext.current
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val loadingStates by viewModel.loadingStates.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var expandedPlatformId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "DevLog",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Contribution heat map widgets",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "How to use:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1. Tap on any platform below to configure.\n" +
                                   "2. Enter your username and choose your color strip.\n" +
                                   "3. Save to immediately update and cache details.\n" +
                                   "4. Add DevLog widgets to your home screen!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            items(platforms) { platform ->
                val account = accounts.find { it.id == platform.id }
                val isLoading = loadingStates[platform.id] == true

                PlatformCard(
                    platform = platform,
                    account = account,
                    expanded = expandedPlatformId == platform.id,
                    isLoading = isLoading,
                    onExpandClick = {
                        expandedPlatformId = if (expandedPlatformId == platform.id) null else platform.id
                    },
                    onSave = { username, theme ->
                        viewModel.savePlatform(platform.id, username, theme)
                    },
                    onRefresh = {
                        viewModel.refreshPlatform(platform.id)
                    }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun PlatformCard(
    platform: PlatformInfo,
    account: PlatformAccount?,
    expanded: Boolean,
    isLoading: Boolean,
    onExpandClick: () -> Unit,
    onSave: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var username by remember(account) { mutableStateOf(account?.username ?: "") }
    var selectedTheme by remember(account) { mutableStateOf(account?.colorTheme ?: "GREEN") }
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = if (expanded) BorderStroke(1.dp, platform.primaryColor.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("platform_card_${platform.id}")
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onExpandClick
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(platform.primaryColor.copy(alpha = 0.15f))
                ) {
                    Icon(
                        painter = painterResource(id = platform.iconResId),
                        contentDescription = platform.name,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platform.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    
                    if (account != null && account.username.isNotBlank()) {
                        Text(
                            text = "@${account.username}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Tap to configure",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (account != null && account.username.isNotBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${account.streak}d Streak",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (account.streak > 0) Color(0xFFFFA116) else Color(0xFF24292E)
                        )
                        val solvedDisplay = if (account.totalProblems > 0) {
                            "${account.totalSolved}/${account.totalProblems}"
                        } else {
                            "${account.totalContributions} total"
                        }
                        Text(
                            text = solvedDisplay,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onExpandClick) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable(enabled = false) {}
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                    Text(
                        text = "Enter Username",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = { Text(platform.usernamePlaceholder) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input_${platform.id}"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = platform.primaryColor,
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Select Widget Color Strip",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorThemeOptions.forEach { theme ->
                            val isSelected = selectedTheme == theme.key
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) platform.primaryColor.copy(alpha = 0.4f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedTheme = theme.key }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = theme.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    theme.colorHexes.forEach { hex ->
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(android.graphics.Color.parseColor(hex)))
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedTheme = theme.key },
                                    colors = RadioButtonDefaults.colors(selectedColor = platform.primaryColor),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Real-time Widget Preview",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val previewBitmap = remember(username, selectedTheme, account, isDark) {
                        val tempAccount = PlatformAccount(
                            id = platform.id,
                            username = username.ifBlank { "preview" },
                            colorTheme = selectedTheme,
                            cachedDataJson = account?.cachedDataJson ?: "{}",
                            totalContributions = account?.totalContributions ?: 0,
                            totalSolved = account?.totalSolved ?: 0,
                            totalProblems = account?.totalProblems ?: 0,
                            streak = account?.streak ?: 0
                        )
                        WidgetDrawingHelper.drawWidgetBitmap(context, tempAccount, isDark).asImageBitmap()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(28.dp))
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = previewBitmap,
                            contentDescription = "Widget Live Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (account != null) {
                            OutlinedButton(
                                onClick = onRefresh,
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Refresh", fontSize = 13.sp)
                                }
                            }
                        }

                        Button(
                            onClick = { onSave(username, selectedTheme) },
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading && username.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA116)),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save & Update", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (account != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val appWidgetManager = AppWidgetManager.getInstance(context)
                                val className = when (platform.id) {
                                    "github" -> "com.matrix.devlog.widget.GithubWidgetProvider"
                                    "leetcode" -> "com.matrix.devlog.widget.LeetcodeWidgetProvider"
                                    "codeforces" -> "com.matrix.devlog.widget.CodeforcesWidgetProvider"
                                    "geeksforgeeks" -> "com.matrix.devlog.widget.GfgWidgetProvider"
                                    "codechef" -> "com.matrix.devlog.widget.CodechefWidgetProvider"
                                    "atcoder" -> "com.matrix.devlog.widget.AtcoderWidgetProvider"
                                    "topcoder" -> "com.matrix.devlog.widget.TopcoderWidgetProvider"
                                    else -> null
                                }
                                if (className != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    try {
                                        val myProvider = ComponentName(context, className)
                                        if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                            appWidgetManager.requestPinAppWidget(myProvider, null, null)
                                        } else {
                                            Toast.makeText(context, "Pinning not supported on this launcher", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Long-press on home screen to add widget manually", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Widget to Home", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
