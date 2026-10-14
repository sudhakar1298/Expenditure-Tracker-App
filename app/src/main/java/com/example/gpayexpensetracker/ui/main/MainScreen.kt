package com.example.gpayexpensetracker.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.gpayexpensetracker.data.BackendSyncUtility
import com.example.gpayexpensetracker.data.DefaultDataRepository
import com.example.gpayexpensetracker.data.ExpenseCategories
import com.example.gpayexpensetracker.data.TransactionDatabase
import com.example.gpayexpensetracker.data.TransactionEntity
import com.example.gpayexpensetracker.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { TransactionDatabase.getDatabase(context) }
    val repository = remember { DefaultDataRepository(database.transactionDao) }
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(repository, context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        viewModel.checkNotificationPermission()
        onDispose {}
    }

    VaultGlassApp(viewModel = viewModel, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultGlassApp(
    viewModel: MainScreenViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val tabs = remember { listOf(0, 1, 3, 4) }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val activeTab = tabs[pagerState.currentPage]
    val coroutineScope = rememberCoroutineScope()
    var showAddBottomSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val bgMain = if (isDark) BotanicalDarkBackground else BotanicalLightBackground

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgMain)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BotanicalSageContainer.copy(alpha = if (isDark) 0.18f else 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.12f),
                    radius = size.width * 0.7f
                ),
                center = Offset(size.width * 0.5f, size.height * 0.12f),
                radius = size.width * 0.7f
            )
        }

        Scaffold(
            topBar = {
                VaultGlassTopBar(
                    activeTabName = when (activeTab) {
                        0 -> "Home"
                        1 -> "Transactions"
                        3 -> "Insights"
                        else -> "Profile"
                    },
                    onSettingsClick = { showSettingsDialog = true }
                )
            },
            bottomBar = {
                VaultGlassBottomBar(
                    activeTab = activeTab,
                    onTabSelected = { tabIdx ->
                        if (tabIdx == 2) {
                            showAddBottomSheet = true
                        } else {
                            val page = tabs.indexOf(tabIdx)
                            if (page >= 0) {
                                coroutineScope.launch { pagerState.animateScrollToPage(page) }
                            }
                        }
                    }
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                key = { page -> tabs[page] }
            ) { page ->
                when (tabs[page]) {
                    0 -> VaultGlassHomeScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettingsDialog = true },
                        onSeeAll = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(tabs.indexOf(1))
                            }
                        }
                    )
                    1 -> VaultGlassActivityScreen(viewModel = viewModel)
                    3 -> VaultGlassInsightsScreen(viewModel = viewModel)
                    else -> VaultGlassProfileScreen(viewModel = viewModel)
                }
            }
        }

        if (showAddBottomSheet) {
            AddExpenseBottomSheet(
                categories = categories,
                onDismiss = { showAddBottomSheet = false },
                onSave = { amount, merchant, category, reason, onResult ->
                    viewModel.addManualTransaction(amount, merchant, category, reason) { error ->
                        onResult(error)
                        if (error == null) showAddBottomSheet = false
                    }
                }
            )
        }

        val budget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
        val sheetsUrl by viewModel.googleSheetsUrl.collectAsStateWithLifecycle()
        val backendApiUrl by viewModel.backendApiUrl.collectAsStateWithLifecycle()
        val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
        var visibleSyncStatus by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(syncStatus) {
            if (syncStatus == "Sync not checked") {
                visibleSyncStatus = null
            } else {
                visibleSyncStatus = syncStatus
                delay(3500)
                if (visibleSyncStatus == syncStatus) visibleSyncStatus = null
            }
        }
        visibleSyncStatus?.let { message ->
            Text(
                message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { visibleSyncStatus = null }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
            )
        }

        if (showSettingsDialog) {
            VaultGlassSettingsDialog(
                currentBudget = budget,
                currentSheetsUrl = sheetsUrl,
                currentBackendApiUrl = backendApiUrl,
                onDismiss = { showSettingsDialog = false },
                onSave = { newBudget, newUrl, newBackendUrl ->
                    viewModel.setMonthlyBudget(newBudget)
                    viewModel.setGoogleSheetsUrl(newUrl)
                    viewModel.setBackendApiUrl(newBackendUrl)
                    showSettingsDialog = false
                }
            )
        }
    }
}

@Composable
fun VaultGlassTopBar(
    activeTabName: String,
    onSettingsClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted
    val primaryAccent = if (isDark) BotanicalOlivePrimary else BotanicalSagePrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(primaryAccent.copy(alpha = 0.2f))
                    .border(1.dp, primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "VaultGlass",
                    tint = primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "VaultGlass",
                color = primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = (-0.5).sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                activeTabName,
                color = mutedText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(primaryAccent.copy(alpha = 0.25f))
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "SU",
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun VaultGlassHomeScreen(
    viewModel: MainScreenViewModel,
    onOpenSettings: () -> Unit,
    onSeeAll: () -> Unit
) {
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val budget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
    val isPermissionEnabled by viewModel.isNotificationServiceEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val pendingTransactions = transactions.filter { it.isPending }
    val finalizedTransactions = transactions.filter { !it.isPending }

    val calendar = Calendar.getInstance()
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentYear = calendar.get(Calendar.YEAR)

    val monthlyTransactions = finalizedTransactions.filter {
        val transCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        transCal.get(Calendar.MONTH) == currentMonth && transCal.get(Calendar.YEAR) == currentYear
    }
    val totalSpent = monthlyTransactions.sumOf { it.amount }

    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted
    val amberAlert = if (isDark) BotanicalAmberAlert else BotanicalWarmOchreAlert

    var selectedTransactionForConfirm by remember { mutableStateOf<TransactionEntity?>(null) }
    var showClearPendingDialog by remember { mutableStateOf(false) }
    var isClearingPending by remember { mutableStateOf(false) }
    var clearPendingError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(BotanicalSageContainer.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SU", color = primaryText, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF52B788))
                                .align(Alignment.BottomEnd)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Welcome back 👋", color = mutedText, fontSize = 12.sp)
                        Text("Sudhakar", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .glassPanel(isDark = isDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = primaryText, modifier = Modifier.size(20.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(amberAlert)
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                        )
                    }
                }
            }
        }

        if (!isPermissionEnabled) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassAlertPanel(isDark = isDark)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Access Required", tint = amberAlert, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notification Listener Access Required", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Grant access so GPay & bank SMS transactions are tracked automatically.", color = mutedText, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = amberAlert, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            VaultTrackHeroCard(totalSpent = totalSpent, budget = budget, onEditBudget = onOpenSettings)
        }

        if (pendingTransactions.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(amberAlert))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${pendingTransactions.size} SMS Drafts to Categorize",
                                color = primaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 2
                            )
                        }
                        TextButton(
                            onClick = { showClearPendingDialog = true },
                            enabled = !isClearingPending,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                if (isClearingPending) "Clearing…" else "Clear all",
                                color = amberAlert,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(pendingTransactions, key = { it.id }) { pending ->
                            PendingDraftCard(
                                transaction = pending,
                                onConfirmClick = { selectedTransactionForConfirm = pending },
                                onDeleteClick = { viewModel.deleteTransaction(pending) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent Activity", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF52B788).copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Live", color = Color(0xFF52B788), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(
                    onClick = onSeeAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("See all >", color = mutedText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        items(finalizedTransactions.take(5), key = { it.id }) { transaction ->
            VaultGlassTransactionRow(transaction = transaction, onDeleteClick = { viewModel.deleteTransaction(transaction) })
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(isDark = isDark)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF52B788).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Pace", tint = Color(0xFF52B788), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spending Pace Alert", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Safe track: 14% below threshold", color = Color(0xFF52B788), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Trend", tint = mutedText)
                }
            }
        }
    }

    if (selectedTransactionForConfirm != null) {
        ConfirmPendingTransactionDialog(
            transaction = selectedTransactionForConfirm!!,
            categories = categories,
            onDismiss = { selectedTransactionForConfirm = null },
            onConfirm = { merchant, category, reason ->
                viewModel.finalizePendingTransaction(selectedTransactionForConfirm!!, merchant, category, reason)
                selectedTransactionForConfirm = null
            }
        )
    }

    if (showClearPendingDialog) {
        AlertDialog(
            onDismissRequest = { showClearPendingDialog = false },
            title = { Text("Clear all pending drafts?") },
            text = {
                Text("This will permanently remove all ${pendingTransactions.size} messages marked Needs Action. Confirmed transactions will not be removed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearPendingDialog = false
                    isClearingPending = true
                    viewModel.clearPendingTransactions { error ->
                        isClearingPending = false
                        clearPendingError = error
                    }
                }) {
                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPendingDialog = false }) { Text("Cancel") }
            }
        )
    }

    clearPendingError?.let { error ->
        AlertDialog(
            onDismissRequest = { clearPendingError = null },
            title = { Text("Could not clear drafts") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { clearPendingError = null }) { Text("OK") }
            }
        )
    }
}

@Composable
fun VaultTrackHeroCard(totalSpent: Double, budget: Double, onEditBudget: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val progress = if (budget > 0) (totalSpent / budget).toFloat() else 0f
    val remaining = budget - totalSpent

    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted
    val primaryAccent = if (isDark) BotanicalOlivePrimary else BotanicalSagePrimary
    val pistachioAccent = if (isDark) BotanicalPistachioAccent else BotanicalDeepOlivePrimary

    val cal = Calendar.getInstance()
    val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
    val daysLeft = (cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1)
        .coerceAtLeast(1)
    val dailySafeSpend = remaining.coerceAtLeast(0.0) / daysLeft

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 24.dp, isDark = isDark)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(pistachioAccent))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("MONTHLY VAULT TRACK", color = mutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryAccent.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("$monthName Cycle", color = pistachioAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidthPx = 9.dp.toPx()
                        drawArc(
                            color = mutedText.copy(alpha = 0.15f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(strokeWidthPx)
                        )
                        drawArc(
                            brush = Brush.sweepGradient(listOf(primaryAccent, pistachioAccent)),
                            startAngle = -90f,
                            sweepAngle = (1f - progress.coerceAtMost(1f)) * 360f,
                            useCenter = false,
                            style = Stroke(strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${((1f - progress.coerceAtMost(1f)) * 100).toInt()}%", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("LEFT", color = pistachioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Spent this month", color = mutedText, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("₹%,.0f".format(totalSpent), color = primaryText, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Text(" / ₹%,.0f".format(budget), color = mutedText, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(mutedText.copy(alpha = 0.08f))
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("⏱ ₹%,.0f left".format(remaining), color = Color(0xFF52B788), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("$daysLeft days left", color = mutedText, fontSize = 10.sp)
                            }
                            Text("Daily safe spend: ₹%,.0f/day".format(dailySafeSpend), color = mutedText, fontSize = 11.sp)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onEditBudget,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = if (isDark) BotanicalDarkBackground else Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Budget", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = onEditBudget,
                    colors = ButtonDefaults.buttonColors(containerColor = mutedText.copy(alpha = 0.15f), contentColor = primaryText),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adjust Limits", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun PendingDraftCard(
    transaction: TransactionEntity,
    onConfirmClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted

    Box(
        modifier = Modifier
            .width(280.dp)
            .glassPanel(cornerRadius = 18.dp, isDark = isDark)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE9B44C).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFFE9B44C), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(transaction.merchant, color = primaryText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Parsed from SMS", color = mutedText, fontSize = 10.sp)
                    }
                }
                Text("₹%,.0f".format(transaction.amount), color = primaryText, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }

            Text("SUGGESTED CATEGORY", color = mutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(getCategoryColor(transaction.category).copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    ExpenseCategories.normalize(transaction.category),
                    color = getCategoryColor(transaction.category),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ignore",
                    color = mutedText,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onDeleteClick() }
                )
                Button(
                    onClick = onConfirmClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52B788), contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("✓ Confirm", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VaultGlassTransactionRow(
    transaction: TransactionEntity,
    onDeleteClick: () -> Unit
) {
    var showDeleteConfirmation by remember(transaction.id) { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val sdf = SimpleDateFormat("EEE, hh:mm a", Locale.getDefault())
    val dateStr = sdf.format(Date(transaction.timestamp))

    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(cornerRadius = 16.dp, isDark = isDark)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(getCategoryColor(transaction.category).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(transaction.category),
                    contentDescription = transaction.category,
                    tint = getCategoryColor(transaction.category),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.merchant, color = primaryText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                transaction.reason?.takeUnless { it.isBlank() }?.let { reason ->
                    Text(reason, color = mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateStr, color = mutedText, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BotanicalSageContainer.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(transaction.sourceApp, color = BotanicalPistachioAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("-₹%,.0f".format(transaction.amount), color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(transaction.category, color = mutedText, fontSize = 10.sp)
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete activity",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete activity?") },
            text = { Text("This will remove ${transaction.merchant} for ₹%,.0f from your activity.".format(transaction.amount)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDeleteClick()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun VaultGlassActivityScreen(viewModel: MainScreenViewModel) {
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    var search by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted

    val searchFiltered = transactions.filter {
        it.merchant.contains(search, ignoreCase = true) ||
            it.category.contains(search, ignoreCase = true) ||
            it.reason.orEmpty().contains(search, ignoreCase = true) ||
            it.amount.toString().contains(search)
    }
    val filtered = searchFiltered.filter {
        when (selectedFilter) {
            "Pending" -> it.isPending
            "GPay" -> it.sourceApp.equals("GPay", ignoreCase = true)
            "SMS" -> it.sourceApp.equals("SMS", ignoreCase = true)
            else -> true
        }
    }
    val filterOptions = listOf(
        "All" to transactions.size,
        "Pending" to transactions.count { it.isPending },
        "GPay" to transactions.count { it.sourceApp.equals("GPay", ignoreCase = true) },
        "SMS" to transactions.count { it.sourceApp.equals("SMS", ignoreCase = true) }
    )

    // Dynamic Today Spent Calculation
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val todayStart = todayCal.timeInMillis

    val spentToday = transactions
        .filter { !it.isPending && it.timestamp >= todayStart }
        .sumOf { it.amount }

    val sdfHeaderDate = SimpleDateFormat("dd MMM", Locale.getDefault())
    val todayHeaderStr = "Today, ${sdfHeaderDate.format(Date())}"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Transactions", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("🟢 ${transactions.size} Logged total", color = mutedText, fontSize = 12.sp)
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search merchant, category, amount...", color = mutedText, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = mutedText) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = primaryText,
                    unfocusedTextColor = primaryText,
                    focusedBorderColor = BotanicalOlivePrimary,
                    unfocusedBorderColor = mutedText.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterOptions) { (filter, count) ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) BotanicalSageContainer else mutedText.copy(alpha = 0.15f))
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("$filter ($count)", color = if (isSelected) Color.White else mutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Dynamic Spent Today Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 16.dp, isDark = isDark)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = BotanicalOlivePrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("SPENT TODAY", color = mutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("₹%,.2f".format(spentToday), color = primaryText, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF52B788).copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Within Daily Limit", color = Color(0xFF52B788), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Dynamic Date Group Header
        item {
            Text("$todayHeaderStr · ${filtered.size} matching", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        items(filtered, key = { it.id }) { item ->
            VaultGlassTransactionRow(transaction = item, onDeleteClick = { viewModel.deleteTransaction(item) })
        }
    }
}

@Composable
fun VaultGlassInsightsScreen(viewModel: MainScreenViewModel) {
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted
    val primaryAccent = if (isDark) BotanicalOlivePrimary else BotanicalSagePrimary

    val now = Calendar.getInstance()
    val currentMonthTransactions = transactions.filter { transaction ->
        if (transaction.isPending) return@filter false
        val date = Calendar.getInstance().apply { timeInMillis = transaction.timestamp }
        date.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
            date.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    }
    val categoryTotals = currentMonthTransactions
        .groupBy { ExpenseCategories.normalize(it.category) }
        .mapValues { (_, entries) -> entries.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
    val totalSpent = categoryTotals.sumOf { it.second }
    val topCategory = categoryTotals.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        item {
            Column {
                Text("INTELLIGENCE", color = primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Financial Insights", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 24.dp, isDark = isDark)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Category Distribution", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Canvas(modifier = Modifier.size(160.dp)) {
                            val stroke = 22.dp.toPx()
                            val size = Size(size.width - stroke, size.height - stroke)
                            val offset = Offset(stroke / 2, stroke / 2)

                            if (totalSpent <= 0.0) {
                                drawArc(mutedText.copy(alpha = 0.18f), -90f, 360f, false, topLeft = offset, size = size, style = Stroke(stroke))
                            } else {
                                var startAngle = -90f
                                categoryTotals.forEach { (category, amount) ->
                                    val sweep = (amount / totalSpent * 360f).toFloat()
                                    drawArc(
                                        color = getCategoryColor(category),
                                        startAngle = startAngle,
                                        sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                                        useCenter = false,
                                        topLeft = offset,
                                        size = size,
                                        style = Stroke(stroke)
                                    )
                                    startAngle += sweep
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TOTAL SPENT", color = mutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("₹%,.0f".format(totalSpent), color = primaryText, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Text("${currentMonthTransactions.size} Transactions", color = mutedText, fontSize = 10.sp)
                        }
                    }

                    if (categoryTotals.isEmpty()) {
                        Text("No confirmed expenses this month yet.", color = mutedText, fontSize = 12.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categoryTotals.take(6).forEach { (category, amount) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(getCategoryColor(category))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(category, color = primaryText, fontSize = 11.sp)
                                    }
                                    Text(
                                        "%.0f%% · ₹%,.0f".format(amount / totalSpent * 100, amount),
                                        color = mutedText,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 20.dp, isDark = isDark)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("TOP SPENDING CATEGORY", color = primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(topCategory?.first ?: "No spending yet", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("₹%,.0f".format(topCategory?.second ?: 0.0), color = primaryAccent, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                    Text(
                        if (topCategory == null) "Confirm an expense to see your monthly insights."
                        else "Your largest confirmed spending category for this month.",
                        color = mutedText,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun VaultGlassProfileScreen(viewModel: MainScreenViewModel) {
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted
    val primaryAccent = if (isDark) BotanicalOlivePrimary else BotanicalSagePrimary
    var showAddCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var deletingCategory by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 24.dp, isDark = isDark)
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.size(72.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(primaryAccent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SU", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Sudhakar", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("sudhakar@example.com", color = mutedText, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryAccent.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("🛡️ VAULTGLASS PRO MEMBER", color = primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${transactions.size}", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("TRANSACTIONS", color = mutedText, fontSize = 9.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${transactions.count { it.isPending }}", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("PENDING", color = mutedText, fontSize = 9.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val categorizedPercent = if (transactions.isEmpty()) 0 else
                                transactions.count { ExpenseCategories.normalize(it.category) != ExpenseCategories.OTHER } * 100 / transactions.size
                            Text("$categorizedPercent%", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("CATEGORIZED", color = mutedText, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 20.dp, isDark = isDark)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PRECISION LEDGER", color = primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Expense summary", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Your dashboard currently contains ${transactions.size} transactions, including ${transactions.count { it.isPending }} that still need confirmation.", color = mutedText, fontSize = 11.sp)
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 20.dp, isDark = isDark)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CATEGORIES", color = primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Manage categories", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        FilledTonalButton(onClick = { showAddCategory = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                    Text("Rename categories or remove ones you no longer use.", color = mutedText, fontSize = 11.sp)

                    categories.forEachIndexed { index, category ->
                        if (index > 0) HorizontalDivider(color = mutedText.copy(alpha = 0.15f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                getCategoryIcon(category),
                                contentDescription = null,
                                tint = getCategoryColor(category),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(category, color = primaryText, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            if (category == ExpenseCategories.OTHER) {
                                Icon(Icons.Default.Lock, contentDescription = "Required category", tint = mutedText, modifier = Modifier.size(18.dp))
                            } else {
                                IconButton(onClick = { editingCategory = category }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename $category", tint = mutedText)
                                }
                                IconButton(onClick = { deletingCategory = category }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete $category", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddCategory) {
        CategoryNameDialog(
            title = "Add category",
            initialName = "",
            confirmLabel = "Add",
            onDismiss = { showAddCategory = false },
            onSave = viewModel::addCategory
        )
    }

    editingCategory?.let { category ->
        CategoryNameDialog(
            title = "Rename category",
            initialName = category,
            confirmLabel = "Save",
            onDismiss = { editingCategory = null },
            onSave = { newName -> viewModel.renameCategory(category, newName) }
        )
    }

    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Remove $category?") },
            text = { Text("Existing transactions in this category will be moved to Other.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(category)
                    deletingCategory = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CategoryNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> String?
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Category name") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                error = onSave(name)
                if (error == null) onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun VaultGlassBottomBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted
    val primaryAccent = if (isDark) BotanicalOlivePrimary else BotanicalSagePrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .height(64.dp)
                .glassPanel(cornerRadius = 32.dp, isDark = isDark)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onTabSelected(0) }) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = if (activeTab == 0) primaryAccent else mutedText)
                }

                IconButton(onClick = { onTabSelected(1) }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Activity", tint = if (activeTab == 1) primaryAccent else mutedText)
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(primaryAccent)
                        .clickable { onTabSelected(2) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = if (isDark) BotanicalDarkBackground else Color.White, modifier = Modifier.size(26.dp))
                }

                IconButton(onClick = { onTabSelected(3) }) {
                    Icon(Icons.Default.Star, contentDescription = "Insights", tint = if (activeTab == 3) primaryAccent else mutedText)
                }

                IconButton(onClick = { onTabSelected(4) }) {
                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = if (activeTab == 4) primaryAccent else mutedText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseBottomSheet(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (Double, String, String, String, (String?) -> Unit) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember(categories) { mutableStateOf(categories.firstOrNull() ?: ExpenseCategories.OTHER) }
    var reason by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted
    val primaryAccent = if (isDark) BotanicalOlivePrimary else BotanicalSagePrimary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) BotanicalDarkSurface else BotanicalLightSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🟢 New Entry", color = primaryText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = mutedText)
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = primaryText, unfocusedTextColor = primaryText, focusedBorderColor = primaryAccent),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant / Purpose (optional)") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = primaryText, unfocusedTextColor = primaryText, focusedBorderColor = primaryAccent),
                modifier = Modifier.fillMaxWidth()
            )

            ExpenseCategorySelector(
                selected = category,
                onSelected = { category = it },
                isDark = isDark,
                categories = categories
            )

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (saveError != null) {
                Text(saveError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Button(
                onClick = {
                    val doubleAmt = amount.toDoubleOrNull()
                    when {
                        doubleAmt == null || !doubleAmt.isFinite() || doubleAmt <= 0.0 ->
                            saveError = "Enter a valid amount greater than zero."
                        else -> {
                            saveError = null
                            isSaving = true
                            onSave(doubleAmt, merchant, category, reason.trim()) { error ->
                                saveError = error
                                if (error != null) isSaving = false
                            }
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = if (isDark) BotanicalDarkBackground else Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (isSaving) "Saving…" else "✓ Save Expense", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun ExpenseCategorySelector(
    selected: String,
    onSelected: (String) -> Unit,
    isDark: Boolean,
    categories: List<String>
) {
    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val mutedText = if (isDark) BotanicalDarkTextMuted else BotanicalLightTextMuted
    val normalized = ExpenseCategories.normalize(selected)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Category", color = mutedText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { option ->
                FilterChip(
                    selected = normalized == option,
                    onClick = { onSelected(option) },
                    label = { Text(option) },
                    leadingIcon = {
                        Icon(
                            getCategoryIcon(option),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = primaryText,
                        iconColor = mutedText,
                        selectedContainerColor = BotanicalSagePrimary,
                        selectedLabelColor = BotanicalLightTextPrimary,
                        selectedLeadingIconColor = BotanicalLightTextPrimary
                    )
                )
            }
        }
    }
}

@Composable
fun VaultGlassSettingsDialog(
    currentBudget: Double,
    currentSheetsUrl: String,
    currentBackendApiUrl: String,
    onDismiss: () -> Unit,
    onSave: (Double, String, String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var budget by remember { mutableStateOf(currentBudget.toString()) }
    var sheetsUrl by remember { mutableStateOf(currentSheetsUrl) }
    var backendApiUrl by remember { mutableStateOf(currentBackendApiUrl) }

    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val primaryAccent = if (isDark) BotanicalOlivePrimary else BotanicalSagePrimary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = primaryText, fontWeight = FontWeight.Bold) },
        containerColor = if (isDark) BotanicalDarkSurface else BotanicalLightSurface,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("Monthly Limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = primaryText, unfocusedTextColor = primaryText, focusedBorderColor = primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sheetsUrl,
                    onValueChange = { sheetsUrl = it },
                    label = { Text("Google Sheets Web App URL") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = primaryText, unfocusedTextColor = primaryText, focusedBorderColor = primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = backendApiUrl,
                    onValueChange = { backendApiUrl = it },
                    label = { Text("Backend API URL") },
                    placeholder = { Text(BackendSyncUtility.DEFAULT_BACKEND_BASE_URL) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = primaryText, unfocusedTextColor = primaryText, focusedBorderColor = primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val b = budget.toDoubleOrNull()
                    if (b != null) onSave(b, sheetsUrl, backendApiUrl)
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = if (isDark) BotanicalDarkBackground else Color.White)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = primaryAccent)
            }
        }
    )
}

@Composable
fun ConfirmPendingTransactionDialog(
    transaction: TransactionEntity,
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var merchant by remember { mutableStateOf(transaction.merchant) }
    var category by remember { mutableStateOf(ExpenseCategories.normalize(transaction.category)) }
    var reason by remember { mutableStateOf(transaction.reason ?: "") }

    val primaryText = if (isDark) BotanicalDarkTextPrimary else BotanicalLightTextPrimary
    val primaryAccent = if (isDark) BotanicalOlivePrimary else BotanicalSagePrimary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm SMS Transaction", color = primaryText, fontWeight = FontWeight.Bold) },
        containerColor = if (isDark) BotanicalDarkSurface else BotanicalLightSurface,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Amount: ₹%,.2f".format(transaction.amount), color = primaryAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant Name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = primaryText, unfocusedTextColor = primaryText, focusedBorderColor = primaryAccent),
                    modifier = Modifier.fillMaxWidth()
                )
                ExpenseCategorySelector(
                    selected = category,
                    onSelected = { category = it },
                    isDark = isDark,
                    categories = categories
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / purpose (optional)") },
                    placeholder = { Text("Lunch, medicine, commute…") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primaryText,
                        unfocusedTextColor = primaryText,
                        focusedBorderColor = primaryAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (merchant.isNotBlank()) onConfirm(merchant, category, reason) },
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = if (isDark) BotanicalDarkBackground else Color.White)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = primaryAccent)
            }
        }
    )
}

fun getCategoryIcon(category: String): ImageVector {
    return when (ExpenseCategories.normalize(category)) {
        ExpenseCategories.FOOD -> Icons.Default.Home
        ExpenseCategories.GROCERIES, ExpenseCategories.SHOPPING -> Icons.Default.ShoppingCart
        ExpenseCategories.TRANSPORT, ExpenseCategories.TRAVEL -> Icons.Default.Place
        ExpenseCategories.BILLS, ExpenseCategories.EDUCATION -> Icons.Default.Info
        ExpenseCategories.ENTERTAINMENT -> Icons.Default.Star
        ExpenseCategories.HEALTH -> Icons.Default.Favorite
        ExpenseCategories.RENT -> Icons.Default.Home
        else -> Icons.AutoMirrored.Filled.List
    }
}

fun getCategoryColor(category: String): Color {
    return when (ExpenseCategories.normalize(category)) {
        ExpenseCategories.FOOD -> Color(0xFFFF8A65)
        ExpenseCategories.GROCERIES -> Color(0xFF5BC5A7)
        ExpenseCategories.TRANSPORT -> Color(0xFF4C9AFF)
        ExpenseCategories.SHOPPING -> Color(0xFF8B6FC0)
        ExpenseCategories.BILLS -> Color(0xFFFFB74D)
        ExpenseCategories.ENTERTAINMENT -> Color(0xFFEC6F91)
        ExpenseCategories.HEALTH -> Color(0xFFE35D6A)
        ExpenseCategories.EDUCATION -> Color(0xFF3DA5A0)
        ExpenseCategories.RENT -> Color(0xFF2E8B73)
        ExpenseCategories.TRAVEL -> Color(0xFF3478C9)
        else -> Color(0xFF7D878A)
    }
}
