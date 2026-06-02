package com.example.gpayexpensetracker.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpayexpensetracker.data.DefaultDataRepository
import com.example.gpayexpensetracker.data.TransactionDatabase
import com.example.gpayexpensetracker.data.TransactionEntity
import java.text.SimpleDateFormat
import java.util.*

// --- Custom HSL Theme Colors ---
val DarkBackground = Color(0xFF12141C)
val SurfaceColor = Color(0xFF1B1E2B)
val TealAccent = Color(0xFF00F2FE)
val IndigoAccent = Color(0xFF4FACFE)
val AccentGradient = Brush.horizontalGradient(listOf(IndigoAccent, TealAccent))
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)
val DangerColor = Color(0xFFEF4444)

@Composable
fun MainScreen(
    onItemClick: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { TransactionDatabase.getDatabase(context) }
    val repository = remember { DefaultDataRepository(database.transactionDao) }
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(repository, context) }

    // Check permission on resume/launch
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        viewModel.checkNotificationPermission()
        onDispose {}
    }

    DashboardScreen(viewModel = viewModel, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainScreenViewModel,
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val budget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
    val isPermissionEnabled by viewModel.isNotificationServiceEnabled.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var selectedTransactionForEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var selectedTransactionForConfirm by remember { mutableStateOf<TransactionEntity?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val context = LocalContext.current

    // Separate pending vs finalized
    val pendingTransactions = transactions.filter { it.isPending }
    val finalizedTransactions = transactions.filter { !it.isPending }

    // Calculate expenditure stats (only count finalized transactions)
    val calendar = Calendar.getInstance()
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentYear = calendar.get(Calendar.YEAR)

    val monthlyTransactions = finalizedTransactions.filter {
        val transCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        transCal.get(Calendar.MONTH) == currentMonth && transCal.get(Calendar.YEAR) == currentYear
    }
    val totalSpentThisMonth = monthlyTransactions.sumOf { it.amount }

    // Filter transactions by category tab
    val filteredTransactions = if (selectedCategoryFilter == "All") {
        finalizedTransactions
    } else {
        finalizedTransactions.filter { it.category == selectedCategoryFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "GPay Expense Tracker",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = { showBudgetDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Budget Settings", tint = TealAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = TealAccent,
                contentColor = DarkBackground
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = DarkBackground,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. Permission Warning Banner if missing
            if (!isPermissionEnabled) {
                item {
                    PermissionWarningCard(
                        onGrantPermissionClick = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // 2. Budget Card
            item {
                BudgetSummaryCard(
                    totalSpent = totalSpentThisMonth,
                    budget = budget,
                    onEditBudgetClick = { showBudgetDialog = true }
                )
            }

            // 3. Weekly Chart
            if (finalizedTransactions.isNotEmpty()) {
                item {
                    WeeklySpendingChartCard(transactions = finalizedTransactions)
                }
            }

            // 3.5. Pending Confirmation list
            if (pendingTransactions.isNotEmpty()) {
                item {
                    Text(
                        text = "Pending Confirmation (${pendingTransactions.size})",
                        color = Color(0xFFFF9F43),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(pendingTransactions, key = { it.id }) { pending ->
                    PendingTransactionCard(
                        transaction = pending,
                        onClick = { selectedTransactionForConfirm = pending },
                        onDeleteClick = { viewModel.deleteTransaction(pending) }
                    )
                }
            }

            // 4. Quick Category Filters
            item {
                CategoryTabs(
                    selectedCategory = selectedCategoryFilter,
                    onCategorySelected = { selectedCategoryFilter = it }
                )
            }

            // 5. Recent Transactions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transactions (${filteredTransactions.size})",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }

            // 6. Transactions List
            if (filteredTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No transactions found",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(filteredTransactions, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        onRowClick = { selectedTransactionForEdit = transaction },
                        onDeleteClick = { viewModel.deleteTransaction(transaction) }
                    )
                }
            }
        }
    }

    // --- Dialogs ---
    if (showAddDialog) {
        AddTransactionDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, merchant, category ->
                viewModel.addManualTransaction(amount, merchant, category)
                showAddDialog = false
            }
        )
    }

    val googleSheetsUrl by viewModel.googleSheetsUrl.collectAsStateWithLifecycle()

    if (showBudgetDialog) {
        SettingsDialog(
            currentBudget = budget,
            currentSheetsUrl = googleSheetsUrl,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { newBudget, newUrl ->
                viewModel.setMonthlyBudget(newBudget)
                viewModel.setGoogleSheetsUrl(newUrl)
                showBudgetDialog = false
            }
        )
    }

    if (selectedTransactionForEdit != null) {
        EditCategoryDialog(
            transaction = selectedTransactionForEdit!!,
            onDismiss = { selectedTransactionForEdit = null },
            onConfirm = { category ->
                viewModel.updateTransactionCategory(selectedTransactionForEdit!!, category)
                selectedTransactionForEdit = null
            }
        )
    }

    if (selectedTransactionForConfirm != null) {
        ConfirmPendingTransactionDialog(
            transaction = selectedTransactionForConfirm!!,
            onDismiss = { selectedTransactionForConfirm = null },
            onConfirm = { merchant, category ->
                viewModel.finalizePendingTransaction(selectedTransactionForConfirm!!, merchant, category)
                selectedTransactionForConfirm = null
            }
        )
    }
}

// --- Composable Sub-Components ---

@Composable
fun PermissionWarningCard(onGrantPermissionClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DangerColor.copy(alpha = 0.15f)),
        border = CardStrokeHelper.border(DangerColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Permission Required",
                tint = DangerColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Notification Access Required",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "Allow this app to read GPay notifications to track your transactions automatically.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onGrantPermissionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Grant Permission", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BudgetSummaryCard(
    totalSpent: Double,
    budget: Double,
    onEditBudgetClick: () -> Unit
) {
    val progress = if (budget > 0) (totalSpent / budget).toFloat() else 0f
    val remaining = budget - totalSpent

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Progress Indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(85.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 1.0f },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White.copy(alpha = 0.1f),
                    strokeWidth = 8.dp
                )
                CircularProgressIndicator(
                    progress = { progress.coerceAtMost(1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = if (progress >= 1.0f) DangerColor else IndigoAccent,
                    strokeWidth = 8.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "spent",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Expenditure details
            Column(modifier = Modifier.weight(1f)) {
                Text("This Month's Spending", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₹%,.2f".format(totalSpent),
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Remaining", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = "₹%,.0f".format(remaining),
                            color = if (remaining >= 0) TealAccent else DangerColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Limit", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = "₹%,.0f".format(budget),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklySpendingChartCard(transactions: List<TransactionEntity>) {
    // Generate dates for the last 7 days (index 0 is 6 days ago, index 6 is today)
    val calendar = Calendar.getInstance()
    val weeklyData = ArrayList<Pair<String, Double>>() // DayLabel to Amount
    val sdf = SimpleDateFormat("EEE", Locale.getDefault())

    // Initialize days
    val sums = DoubleArray(7) { 0.0 }
    val labels = Array(7) { "" }

    for (i in 6 downTo 0) {
        val checkCal = Calendar.getInstance()
        checkCal.add(Calendar.DAY_OF_YEAR, -i)
        val dayStart = checkCal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayEnd = checkCal.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val daySum = transactions
            .filter { it.timestamp in dayStart..dayEnd }
            .sumOf { it.amount }

        sums[6 - i] = daySum
        labels[6 - i] = sdf.format(checkCal.time)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Weekly Spending Trend",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            SpendingChart(sums = sums, labels = labels)
        }
    }
}

@Composable
fun SpendingChart(sums: DoubleArray, labels: Array<String>) {
    val maxVal = sums.maxOrNull() ?: 1.0
    val maxScale = if (maxVal <= 0) 1.0 else maxVal

    // Entry animation progress
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(sums) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val spacing = width / 6f // 7 items = 6 intervals
                
                val points = sums.mapIndexed { index, amount ->
                    val x = index * spacing
                    // Scale height, keep some margin at the top (15%) and bottom (10%)
                    val y = height - (amount / maxScale * (height * 0.75f)).toFloat() - (height * 0.05f)
                    Offset(x, y)
                }

                // Curved Line Path
                val linePath = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points[0].x, height - (height - points[0].y) * animationProgress.value)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val animatedY = height - (height - curr.y) * animationProgress.value
                            val animatedPrevY = height - (height - prev.y) * animationProgress.value
                            
                            val controlX1 = prev.x + (curr.x - prev.x) / 2f
                            val controlY1 = animatedPrevY
                            val controlX2 = prev.x + (curr.x - prev.x) / 2f
                            val controlY2 = animatedY

                            cubicTo(controlX1, controlY1, controlX2, controlY2, curr.x, animatedY)
                        }
                    }
                }

                // Fill Path under the line
                val fillPath = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points[0].x, height)
                        lineTo(points[0].x, height - (height - points[0].y) * animationProgress.value)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val animatedY = height - (height - curr.y) * animationProgress.value
                            val animatedPrevY = height - (height - prev.y) * animationProgress.value
                            
                            val controlX1 = prev.x + (curr.x - prev.x) / 2f
                            val controlY1 = animatedPrevY
                            val controlX2 = prev.x + (curr.x - prev.x) / 2f
                            val controlY2 = animatedY

                            cubicTo(controlX1, controlY1, controlX2, controlY2, curr.x, animatedY)
                        }
                        lineTo(points.last().x, height)
                        close()
                    }
                }

                // Draw Gradient Fill
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            IndigoAccent.copy(alpha = 0.35f),
                            TealAccent.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )

                // Draw Stroke Line
                drawPath(
                    path = linePath,
                    brush = AccentGradient,
                    style = Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )

                // Draw Interactive Circles
                points.forEachIndexed { index, offset ->
                    val animatedY = height - (height - offset.y) * animationProgress.value
                    if (sums[index] > 0) {
                        drawCircle(
                            color = TealAccent,
                            radius = 5.dp.toPx(),
                            center = Offset(offset.x, animatedY)
                        )
                        drawCircle(
                            color = SurfaceColor,
                            radius = 2.5.dp.toPx(),
                            center = Offset(offset.x, animatedY)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CategoryTabs(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = listOf("All", "Food", "Shopping", "Travel", "Bills", "Entertainment", "Others")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // To avoid nesting scrollable lists, we do a row wrapper, but since Compose doesn't permit nesting,
                // let's just make it a scrollable Row inside this item block!
            }
            
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) TealAccent else SurfaceColor)
                            .clickable { onCategorySelected(category) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) DarkBackground else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    onRowClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = sdf.format(Date(transaction.timestamp))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getCategoryColor(transaction.category).copy(alpha = 0.15f)),
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

            // Merchant and details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = transaction.sourceApp,
                            color = TealAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Amount & Delete Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "-₹%,.2f".format(transaction.amount),
                    color = DangerColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Helpers for visual indicators

fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        "Food" -> Icons.Default.Home // Close representation for restaurants
        "Shopping" -> Icons.Default.ShoppingCart
        "Travel" -> Icons.Default.Place // Maps / travel symbol
        "Bills" -> Icons.Default.Info
        "Entertainment" -> Icons.Default.Star
        else -> Icons.Default.List
    }
}

fun getCategoryColor(category: String): Color {
    return when (category) {
        "Food" -> Color(0xFFFF9F43) // Warm Orange
        "Shopping" -> Color(0xFF00D2D3) // Bright Teal
        "Travel" -> Color(0xFF54A0FF) // Blue
        "Bills" -> Color(0xFFFF6B6B) // Light Red
        "Entertainment" -> Color(0xFF1DD1A1) // Emerald Green
        else -> Color(0xFF8395A7) // Grey
    }
}

// --- Dialog Implementations ---

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    val categories = listOf("Food", "Shopping", "Travel", "Bills", "Entertainment", "Others")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Manual Expense", color = TextPrimary, fontWeight = FontWeight.Bold) },
        containerColor = SurfaceColor,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TealAccent,
                        focusedBorderColor = TealAccent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / To") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TealAccent,
                        focusedBorderColor = TealAccent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Category", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                
                // Horizontal category chips selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = cat == category
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) TealAccent else DarkBackground)
                                    .clickable { category = cat }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) DarkBackground else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val doubleAmt = amount.toDoubleOrNull()
                    if (doubleAmt != null && merchant.isNotBlank()) {
                        onConfirm(doubleAmt, merchant, category)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = DarkBackground)
            ) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TealAccent)
            }
        }
    )
}

@Composable
fun SettingsDialog(
    currentBudget: Double,
    currentSheetsUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var budget by remember { mutableStateOf(currentBudget.toString()) }
    var sheetsUrl by remember { mutableStateOf(currentSheetsUrl) }

    val parsedBudget = budget.toDoubleOrNull()
    val isBudgetValid = parsedBudget != null && parsedBudget >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
        containerColor = SurfaceColor,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("Monthly Limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !isBudgetValid,
                    supportingText = {
                        if (!isBudgetValid) {
                            Text("Please enter a valid positive number", color = DangerColor)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TealAccent,
                        focusedBorderColor = TealAccent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = sheetsUrl,
                    onValueChange = { sheetsUrl = it },
                    label = { Text("Google Sheets Web App URL") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TealAccent,
                        focusedBorderColor = TealAccent
                    ),
                    placeholder = { Text("https://script.google.com/macros/s/...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isBudgetValid) {
                        onConfirm(parsedBudget!!, sheetsUrl)
                    }
                },
                enabled = isBudgetValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealAccent, 
                    contentColor = DarkBackground,
                    disabledContainerColor = TealAccent.copy(alpha = 0.5f),
                    disabledContentColor = DarkBackground.copy(alpha = 0.5f)
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TealAccent)
            }
        }
    )
}

@Composable
fun EditCategoryDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val categories = listOf("Food", "Shopping", "Travel", "Bills", "Entertainment", "Others")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Category", color = TextPrimary, fontWeight = FontWeight.Bold) },
        containerColor = SurfaceColor,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Transaction to ${transaction.merchant} (₹${transaction.amount})",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                categories.forEach { cat ->
                    val isSelected = cat == transaction.category
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) TealAccent.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onConfirm(cat) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(getCategoryColor(cat))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = cat,
                            color = if (isSelected) TealAccent else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TealAccent)
            }
        }
    )
}

// Helpers to work around Android SDK differences

object CardStrokeHelper {
    @Composable
    fun border(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)
}

@Composable
fun PendingTransactionCard(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = CardStrokeHelper.border(Color(0xFFFF9F43).copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF9F43).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Pending Details",
                    tint = Color(0xFFFF9F43),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SMS Debit Detected",
                        color = Color(0xFFFF9F43),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Draft",
                            color = TextSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Prefilled: ${transaction.merchant}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to confirm reason & category",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "₹%,.2f".format(transaction.amount),
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmPendingTransactionDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var merchant by remember { mutableStateOf(transaction.merchant) }
    var category by remember { mutableStateOf(transaction.category) }
    val categories = listOf("Food", "Shopping", "Travel", "Bills", "Entertainment", "Others")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm SMS Transaction", color = TextPrimary, fontWeight = FontWeight.Bold) },
        containerColor = SurfaceColor,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Amount: ₹%,.2f".format(transaction.amount),
                    color = TealAccent,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
                Text(
                    text = "Please enter the reason (merchant name) and select a category to finalize this expense.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Reason / Merchant") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = TealAccent,
                        focusedBorderColor = TealAccent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("Category", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = cat == category
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) TealAccent else DarkBackground)
                                    .clickable { category = cat }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) DarkBackground else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (merchant.isNotBlank()) {
                        onConfirm(merchant, category)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = DarkBackground)
            ) {
                Text("Save Transaction", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TealAccent)
            }
        }
    )
}
