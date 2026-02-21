package com.shk238.diary

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke // 追加
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.room.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- カラー定義を修正 ---
val DarkBg = Color(0xFF0F0F0F)
val SurfaceBg = Color(0xFF1A1A1A)
// 黄緑色(ADFF2F)から、より緑らしい色(32CD32: LimeGreen)に変更
val MainGreen = Color(0xFF32CD32)

// --- 1. Entity ---
@Entity(tableName = "diary_table")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val rating: Int,
    val sleepHours: Float,
    val tasks: String,
    val notes: String
)

// --- 2. DAO ---
@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_table ORDER BY date ASC")
    fun getAllLogs(): Flow<List<LogEntry>>
    @Insert suspend fun insert(entry: LogEntry)
    @Update suspend fun update(entry: LogEntry)
    @Delete suspend fun delete(entry: LogEntry)
}

// --- 3. Database ---
@Database(entities = [LogEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "diary_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- 4. UI Components ---

@Composable
fun MainApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = db.diaryDao()
    val historyList by dao.getAllLogs().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("input") }

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            NavigationBar(containerColor = DarkBg, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = currentScreen == "input",
                    onClick = { currentScreen = "input" },
                    label = { Text("記録", fontSize = 10.sp, color = Color.White) },
                    icon = { Text("✍️") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MainGreen, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = currentScreen == "history",
                    onClick = { currentScreen = "history" },
                    label = { Text("履歴", fontSize = 10.sp, color = Color.White) },
                    icon = { Text("📈") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MainGreen, indicatorColor = Color.Transparent)
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (currentScreen == "input") {
                LifeLogScreen(onSave = { scope.launch { dao.insert(it); currentScreen = "history" } })
            } else {
                HistoryScreen(historyList, dao)
            }
        }
    }
}

@Composable
fun LifeLogScreen(onSave: (LogEntry) -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var rating by remember { mutableFloatStateOf(3f) }
    var sleepHours by remember { mutableFloatStateOf(7.0f) }
    var note by remember { mutableStateOf("") }
    var hasExercise by remember { mutableStateOf(false) }
    var hasSelfDev by remember { mutableStateOf(false) }
    var hasLunch by remember { mutableStateOf(false) }

    val datePickerDialog = DatePickerDialog(context, { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)

    val sliderColors = SliderDefaults.colors(
        thumbColor = MainGreen,
        activeTrackColor = MainGreen,
        inactiveTrackColor = Color(0xFF333333),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent
    )

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        TextButton(onClick = { datePickerDialog.show() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Default.DateRange, null, tint = MainGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), color = Color.White, fontSize = 18.sp)
        }

        Column {
            Text("Rating: ${rating.toInt()}", color = Color.Gray, fontSize = 14.sp)
            Slider(value = rating, onValueChange = { rating = it }, valueRange = 1f..5f, steps = 3, colors = sliderColors)
        }

        Column {
            Text("Sleep: ${String.format("%.1f", sleepHours)}h", color = Color.Gray, fontSize = 14.sp)
            Slider(value = sleepHours, onValueChange = { sleepHours = it }, valueRange = 0f..12f, steps = 23, colors = sliderColors)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = hasExercise, onClick = { hasExercise = !hasExercise }, label = { Text("運動") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MainGreen, selectedLabelColor = Color.Black))
            FilterChip(selected = hasSelfDev, onClick = { hasSelfDev = !hasSelfDev }, label = { Text("自己啓発") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MainGreen, selectedLabelColor = Color.Black))
            FilterChip(selected = hasLunch, onClick = { hasLunch = !hasLunch }, label = { Text("昼食") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MainGreen, selectedLabelColor = Color.Black))
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it },
            placeholder = { Text("Memo...", color = Color.DarkGray) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.White),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFF333333), focusedBorderColor = MainGreen)
        )

        Button(
            onClick = {
                val tasks = listOfNotNull(if(hasExercise)"運動" else null, if(hasSelfDev)"自己啓発" else null, if(hasLunch)"昼食" else null).joinToString(", ")
                onSave(LogEntry(date = selectedDate.toString(), rating = rating.toInt(), sleepHours = sleepHours, tasks = tasks, notes = note))
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MainGreen, contentColor = Color.Black),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text("SAVE", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HistoryScreen(history: List<LogEntry>, dao: DiaryDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(7)) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var editingEntry by remember { mutableStateOf<LogEntry?>(null) }

    val filtered = remember(history, startDate, endDate) {
        history.filter { val d = LocalDate.parse(it.date); (d.isAfter(startDate) || d.isEqual(startDate)) && (d.isBefore(endDate) || d.isEqual(endDate)) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { DatePickerDialog(context, { _, y, m, d -> startDate = LocalDate.of(y, m + 1, d) }, startDate.year, startDate.monthValue - 1, startDate.dayOfMonth).show() }) { Text(startDate.toString(), color = MainGreen, fontSize = 12.sp) }
            Text("-", color = Color.Gray)
            TextButton(onClick = { DatePickerDialog(context, { _, y, m, d -> endDate = LocalDate.of(y, m + 1, d) }, endDate.year, endDate.monthValue - 1, endDate.dayOfMonth).show() }) { Text(endDate.toString(), color = MainGreen, fontSize = 12.sp) }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (filtered.isEmpty()) Text("No Data", Modifier.align(Alignment.Center), color = Color.DarkGray)
            else TrendGraph(filtered)
        }

        Column(modifier = Modifier.weight(1.5f).padding(16.dp)) {
            TaskSummaryRow(filtered)
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered.reversed()) { entry ->
                    Surface(onClick = { editingEntry = entry }, color = Color.Transparent) {
                        CompactHistoryRow(entry)
                    }
                }
            }
        }
    }

    if (editingEntry != null) {
        EditDialog(entry = editingEntry!!, onDismiss = { editingEntry = null },
            onSave = { scope.launch { dao.update(it); editingEntry = null } },
            onDelete = { scope.launch { dao.delete(it); editingEntry = null } }
        )
    }
}

@Composable
fun TrendGraph(data: List<LogEntry>) {
    AndroidView(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textColor = android.graphics.Color.GRAY
                    gridColor = android.graphics.Color.parseColor("#222222")
                    granularity = 1f
                }

                axisLeft.apply {
                    textColor = android.graphics.Color.parseColor("#32CD32") // グラフの色も修正
                    axisMinimum = 0f
                    axisMaximum = 12f
                    setDrawGridLines(false)
                    setLabelCount(5, true)
                }

                axisRight.apply {
                    textColor = android.graphics.Color.WHITE
                    axisMinimum = 1f
                    axisMaximum = 5f
                    setDrawGridLines(true)
                    gridColor = android.graphics.Color.parseColor("#222222")
                    setLabelCount(5, true)
                }
            }
        },
        update = { chart ->
            val sD = LineDataSet(data.mapIndexed { i, e -> Entry(i.toFloat(), e.sleepHours) }, "Sleep").apply { axisDependency = YAxis.AxisDependency.LEFT; color = android.graphics.Color.parseColor("#32CD32"); setDrawCircles(false); lineWidth = 2f }
            val rD = LineDataSet(data.mapIndexed { i, e -> Entry(i.toFloat(), e.rating.toFloat()) }, "Rating").apply { axisDependency = YAxis.AxisDependency.RIGHT; color = android.graphics.Color.WHITE; setDrawCircles(false); lineWidth = 1.5f }

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float): String {
                    val i = v.toInt()
                    return if (i in data.indices) data[i].date.substring(5) else ""
                }
            }

            chart.data = LineData(sD, rD)
            chart.invalidate()
        }
    )
}

@Composable
fun TaskSummaryRow(history: List<LogEntry>) {
    val total = history.size.toFloat()
    if (total == 0f) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("運動", "自己啓発", "昼食").forEach { label ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 10.sp, color = Color.Gray)
                Text("${(history.count { it.tasks.contains(label) } / total * 100).toInt()}%", color = MainGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CompactHistoryRow(entry: LogEntry) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(entry.date.substring(5), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(45.dp))

        // --- ここを修正: 黄色から緑色(MainGreen)に変更 ---
        Text("★${entry.rating}", color = MainGreen, fontSize = 12.sp, modifier = Modifier.width(40.dp))

        Text("${entry.sleepHours}h", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(45.dp))
        Text(entry.tasks, color = Color.DarkGray, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun EditDialog(entry: LogEntry, onDismiss: () -> Unit, onSave: (LogEntry) -> Unit, onDelete: (LogEntry) -> Unit) {
    val context = LocalContext.current
    var editDate by remember { mutableStateOf(LocalDate.parse(entry.date)) }
    var r by remember { mutableFloatStateOf(entry.rating.toFloat()) }
    var s by remember { mutableFloatStateOf(entry.sleepHours) }
    var n by remember { mutableStateOf(entry.notes) }
    var e by remember { mutableStateOf(entry.tasks.contains("運動")) }
    var sd by remember { mutableStateOf(entry.tasks.contains("自己啓発")) }
    var l by remember { mutableStateOf(entry.tasks.contains("昼食")) }

    val datePickerDialog = DatePickerDialog(context, { _, y, m, d -> editDate = LocalDate.of(y, m + 1, d) }, editDate.year, editDate.monthValue - 1, editDate.dayOfMonth)

    val sliderColors = SliderDefaults.colors(thumbColor = MainGreen, activeTrackColor = MainGreen, inactiveTrackColor = Color(0xFF333333), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceBg,
        title = { Text("記録を編集", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MainGreen), border = BorderStroke(1.dp, Color(0xFF333333))) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(editDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rating:", color = Color.Gray, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("${r.toInt()}", color = MainGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Slider(value = r, onValueChange = { r = it }, valueRange = 1f..5f, steps = 3, colors = sliderColors)
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sleep:", color = Color.Gray, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("${String.format("%.1f", s)}h", color = MainGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Slider(value = s, onValueChange = { s = it }, valueRange = 0f..12f, steps = 23, colors = sliderColors)
                }
                Text("Activities", color = Color.Gray, fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = e, onClick = { e = !e }, label = { Text("運動") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MainGreen, selectedLabelColor = Color.Black))
                    FilterChip(selected = sd, onClick = { sd = !sd }, label = { Text("自己啓発") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MainGreen, selectedLabelColor = Color.Black))
                    FilterChip(selected = l, onClick = { l = !l }, label = { Text("昼食") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MainGreen, selectedLabelColor = Color.Black))
                }
                OutlinedTextField(value = n, onValueChange = { n = it }, placeholder = { Text("メモを入力...", color = Color.DarkGray) }, textStyle = TextStyle(color = Color.White), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFF333333), focusedBorderColor = MainGreen))
                TextButton(onClick = { onDelete(entry) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("このデータを削除する", color = Color.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = { Button(onClick = { val t = listOfNotNull(if(e)"運動" else null, if(sd)"自己啓発" else null, if(l)"昼食" else null).joinToString(", "); onSave(entry.copy(date = editDate.toString(), rating = r.toInt(), sleepHours = s, tasks = t, notes = n)) }, colors = ButtonDefaults.buttonColors(containerColor = MainGreen)) { Text("更新", color = Color.Black, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = Color.White) } }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { MainApp() } }
    }
}