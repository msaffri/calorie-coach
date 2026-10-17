package com.saffri.caloriecoach

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Matrix
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

val GoodGreen = Color(0xFF2E7D32)
val BadRed = Color(0xFFC62828)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = Storage(applicationContext)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF00695C), secondary = Color(0xFF00897B))) {
                Surface(Modifier.fillMaxSize()) { App(storage) }
            }
        }
    }
}

@Composable
fun App(storage: Storage) {
    var profile by remember { mutableStateOf(storage.loadProfile()) }
    val today = remember { LocalDate.now().toString() }
    var day by remember { mutableStateOf(storage.loadDay(today)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val p = profile
    if (p == null) {
        ProfileScreen(initial = null) { saved ->
            storage.saveProfile(saved)
            profile = saved
        }
    } else {
        val update: (DayLog) -> Unit = { d -> day = d; storage.saveDay(today, d) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("🏠" to "Today", "🏃" to "Activity", "🍽️" to "Food", "👤" to "Profile")
                        .forEachIndexed { i, (icon, label) ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Text(icon, fontSize = 20.sp) },
                                label = { Text(label) }
                            )
                        }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> TodayScreen(p, day)
                    1 -> ActivityScreen(p, day, update)
                    2 -> FoodScreen(storage, day, update)
                    else -> ProfileScreen(initial = p, extra = { ApiKeyCard(storage) }) { saved ->
                        storage.saveProfile(saved)
                        profile = saved
                        tab = 0
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

@Composable
fun Title(text: String) = Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold)

@Composable
fun NumField(label: String, value: String, error: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun StatRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

// ---------------------------------------------------------------- Profile

@Composable
fun ProfileScreen(initial: Profile?, extra: @Composable () -> Unit = {}, onSave: (Profile) -> Unit) {
    fun fmt(d: Double) = if (d == d.roundToInt().toDouble()) d.roundToInt().toString() else d.toString()
    var current by remember { mutableStateOf(initial?.let { fmt(it.currentWeightKg) } ?: "") }
    var target by remember { mutableStateOf(initial?.let { fmt(it.targetWeightKg) } ?: "") }
    var height by remember { mutableStateOf(initial?.let { fmt(it.heightCm) } ?: "") }
    var age by remember { mutableStateOf(initial?.age?.toString() ?: "") }
    var male by remember { mutableStateOf(initial?.isMale ?: true) }
    var submitted by remember { mutableStateOf(false) }

    val errors = CalorieCalc.validate(current.toDoubleOrNull(), target.toDoubleOrNull(), height.toDoubleOrNull(), age.toIntOrNull())
    fun err(k: String) = if (submitted || errors[k]?.contains("15 kg") == true) errors[k] else null

    ScreenColumn {
        Title(if (initial == null) "Set up your goal" else "Your profile")
        Text("Your daily calorie target is worked out from these details.", color = Color.Gray)
        NumField("Current weight (kg)", current, err("current")) { current = it }
        NumField("Expected weight in 3 months (kg, within ±15)", target, err("target")) { target = it }
        NumField("Current height (cm)", height, err("height")) { height = it }
        NumField("Age (years)", age, err("age")) { age = it.filter(Char::isDigit) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sex:")
            FilterChip(selected = male, onClick = { male = true }, label = { Text("Male") })
            FilterChip(selected = !male, onClick = { male = false }, label = { Text("Female") })
        }

        if (errors.isEmpty()) {
            val p = Profile(current.toDouble(), target.toDouble(), height.toDouble(), age.toInt(), male)
            PlanCard(p)
        }

        Button(
            onClick = {
                submitted = true
                if (errors.isEmpty()) onSave(Profile(current.toDouble(), target.toDouble(), height.toDouble(), age.toInt(), male))
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (initial == null) "Calculate my plan" else "Save changes") }
        extra()
    }
}

@Composable
fun PlanCard(p: Profile) {
    val change = p.targetWeightKg - p.currentWeightKg
    val adj = CalorieCalc.dailyAdjustment(p).roundToInt()
    val target = CalorieCalc.dailyTarget(p)
    val floored = CalorieCalc.maintenance(p) + CalorieCalc.dailyAdjustment(p) < CalorieCalc.floor(p)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your plan", fontWeight = FontWeight.Bold)
            val goal = when {
                change < 0 -> "Lose %.1f kg in 90 days (%.2f kg/week)".format(-change, -change / 90 * 7)
                change > 0 -> "Gain %.1f kg in 90 days (%.2f kg/week)".format(change, change / 90 * 7)
                else -> "Maintain your weight"
            }
            Text(goal)
            StatRow("Maintenance (BMR × 1.2)", "${CalorieCalc.maintenance(p).roundToInt()} kcal")
            StatRow("Daily adjustment", "${if (adj > 0) "+" else ""}$adj kcal")
            HorizontalDivider()
            StatRow("Expected net intake / day", "$target kcal", bold = true)
            if (floored) Text("Capped at a safe minimum — this goal is too aggressive to reach fully in 3 months.", color = BadRed, fontSize = 13.sp)
        }
    }
}

@Composable
fun ApiKeyCard(storage: Storage) {
    var key by remember { mutableStateOf(storage.apiKey) }
    var saved by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✨ AI photo recognition", fontWeight = FontWeight.Bold)
            Text(
                "Paste a Claude API key (from console.anthropic.com) to recognise food from photos. " +
                    "Each photo costs a fraction of a cent. The key stays on this phone.",
                fontSize = 14.sp, color = Color.Gray
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim(); saved = false },
                label = { Text("API key (sk-ant-…)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { storage.apiKey = key; saved = true }) { Text("Save key") }
                Spacer(Modifier.width(12.dp))
                if (saved) Text(if (key.isBlank()) "Key removed" else "Saved ✓", color = GoodGreen)
            }
        }
    }
}

// ---------------------------------------------------------------- Today

@Composable
fun TodayScreen(p: Profile, day: DayLog) {
    val target = CalorieCalc.dailyTarget(p)
    val tol = CalorieCalc.tolerance(target)
    val status = CalorieCalc.status(day.net, target)
    val color = if (status == NetStatus.GOOD) GoodGreen else BadRed
    val label = when (status) {
        NetStatus.LOW -> "Too low — ${target - tol - day.net} kcal under the healthy range"
        NetStatus.HIGH -> "Too high — ${day.net - target - tol} kcal over the healthy range"
        NetStatus.GOOD -> "On track — within the healthy range"
    }

    ScreenColumn {
        Title("Today")
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Color.Gray)

        Card(
            colors = CardDefaults.cardColors(containerColor = color),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NET CALORIES", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${day.net}", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Text("target $target kcal (±$tol)", color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("🍽️ Food eaten (${day.foods.size} meals)", "+${day.intake} kcal")
                StatRow("🏃 Burned by activity (${day.activities.size})", "−${day.burned} kcal")
                HorizontalDivider()
                StatRow("Net", "${day.net} kcal", bold = true)
                StatRow("Expected net", "$target kcal")
                val remaining = target - day.net
                Text(
                    if (remaining > 0) "You can still eat about $remaining kcal today."
                    else "You are ${-remaining} kcal over today's target.",
                    color = Color.Gray, fontSize = 14.sp
                )
            }
        }
        PlanCard(p)
    }
}

// ---------------------------------------------------------------- Activity

@Composable
fun ActivityScreen(p: Profile, day: DayLog, onUpdate: (DayLog) -> Unit) {
    var selected by remember { mutableStateOf(ACTIVITIES[1]) }
    var expanded by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf("") }
    val mins = minutes.toIntOrNull()
    val preview = if (mins != null && mins > 0) burnedKcal(selected.met, p.currentWeightKg, mins) else null

    ScreenColumn {
        Title("Activities")
        Text("Activity done", fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("${selected.name}  ▾", modifier = Modifier.fillMaxWidth())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ACTIVITIES.forEach { a ->
                    DropdownMenuItem(text = { Text(a.name) }, onClick = { selected = a; expanded = false })
                }
            }
        }
        NumField("Time spent (minutes)", minutes) { minutes = it.filter(Char::isDigit) }
        if (preview != null) Text("≈ $preview kcal burned", color = GoodGreen, fontWeight = FontWeight.SemiBold)
        Button(
            enabled = mins != null && mins in 1..600,
            onClick = {
                val e = ActivityEntry(System.currentTimeMillis(), selected.name, mins!!, preview!!)
                onUpdate(day.copy(activities = day.activities + e))
                minutes = ""
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Add activity") }

        HorizontalDivider()
        Text("Today: ${day.burned} kcal burned", fontWeight = FontWeight.Bold)
        if (day.activities.isEmpty()) Text("No activities yet.", color = Color.Gray)
        day.activities.forEach { a ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontWeight = FontWeight.SemiBold)
                        Text("${a.minutes} min · ${a.kcal} kcal", color = Color.Gray)
                    }
                    TextButton(onClick = { onUpdate(day.copy(activities = day.activities.filter { it.id != a.id })) }) {
                        Text("Remove", color = BadRed)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Food

@Composable
fun FoodScreen(storage: Storage, day: DayLog, onUpdate: (DayLog) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var note by remember { mutableStateOf("") }
    var manualKcal by remember { mutableStateOf("") }
    var ai by remember { mutableStateOf<VisionResult?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    fun runAi(bmp: Bitmap) {
        val key = storage.apiKey
        if (key.isBlank()) {
            aiError = "Add your Claude API key in the Profile tab to analyse photos. Using the note estimate for now."
            return
        }
        aiBusy = true; aiError = null
        scope.launch {
            try {
                ai = withContext(Dispatchers.IO) { FoodVision.analyze(key, bmp, note) }
            } catch (e: Exception) {
                aiError = e.message ?: "Photo analysis failed. Check your internet connection."
            } finally {
                aiBusy = false
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingFile
        if (ok && f != null) {
            val bmp = loadScaled(f, 1280)
            if (bmp != null) {
                photo = bmp; ai = null
                runAi(bmp)
            }
        }
    }

    val matches = remember(note) { FoodEstimator.estimate(note) }
    val noteEstimate = FoodEstimator.total(matches)
    val aiResult = ai
    val estimated = aiResult?.totalKcal ?: noteEstimate
    val finalKcal = manualKcal.toIntOrNull() ?: estimated

    ScreenColumn {
        Title("Food")
        val bmp = photo
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Meal photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
            )
        }
        OutlinedButton(
            onClick = {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
                pendingFile = f
                camera.launch(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (bmp == null) "📷  Take a photo of your food" else "📷  Retake photo") }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note: what did you eat?") },
            placeholder = { Text("e.g. 2 chapati, 1 cup dal, 150g chicken, karak tea") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        if (bmp != null) {
            Button(
                enabled = !aiBusy,
                onClick = { runAi(bmp) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (aiResult == null) "✨  Analyse photo with AI" else "✨  Re-analyse (uses note too)") }
        }
        if (aiBusy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Recognising food in your photo…")
            }
        }
        aiError?.let { Text(it, color = BadRed, fontSize = 14.sp) }

        if (aiResult != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("✨ From your photo", fontWeight = FontWeight.Bold)
                    if (aiResult.items.isEmpty()) Text("No food recognised in the photo.", color = BadRed)
                    aiResult.items.forEach { Text("• ${it.name} (${it.portion}) — ${it.kcal} kcal", fontSize = 14.sp) }
                    HorizontalDivider()
                    StatRow("AI estimate", "${aiResult.totalKcal} kcal", bold = true)
                }
            }
        } else if (matches.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Estimate from note", fontWeight = FontWeight.Bold)
                    matches.forEach { m ->
                        Text(FoodEstimator.describe(m), fontSize = 14.sp, color = if (m.food == null) BadRed else Color.Unspecified)
                    }
                    HorizontalDivider()
                    StatRow("Estimated total", "$noteEstimate kcal", bold = true)
                }
            }
        }
        NumField("Override calories (optional)", manualKcal) { manualKcal = it.filter(Char::isDigit) }

        Button(
            enabled = (note.isNotBlank() || bmp != null) && finalKcal > 0 && !aiBusy,
            onClick = {
                val id = System.currentTimeMillis()
                val path = bmp?.let { savePhoto(context.filesDir, id, it) }
                val breakdown = (aiResult?.items?.joinToString("\n") { "• ${it.name} (${it.portion}) — ${it.kcal} kcal" }
                    ?: matches.joinToString("\n") { FoodEstimator.describe(it) }) +
                    if (manualKcal.isNotEmpty()) "\n(Manually set to $finalKcal kcal)" else ""
                val title = note.trim().ifBlank { aiResult?.items?.joinToString(", ") { it.name } ?: "Meal" }
                onUpdate(day.copy(foods = day.foods + FoodEntry(id, title, path, finalKcal, breakdown)))
                pendingFile?.delete()
                photo = null; note = ""; manualKcal = ""; ai = null; aiError = null; pendingFile = null
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Add meal ($finalKcal kcal)") }

        HorizontalDivider()
        Text("Today: ${day.intake} kcal eaten", fontWeight = FontWeight.Bold)
        if (day.foods.isEmpty()) Text("No meals yet.", color = Color.Gray)
        day.foods.forEach { f -> MealCard(f) { onUpdate(day.copy(foods = day.foods.filter { it.id != f.id })) } }
    }
}

/** Decode a camera photo, downscaled so the longest side is about [maxSide] px, and upright. */
fun loadScaled(f: File, maxSide: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val raw = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    val rotation = when (ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (raw != null && rotation != 0f)
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
    else raw
} catch (e: Exception) {
    null
}

@Composable
fun MealCard(f: FoodEntry, onRemove: () -> Unit) {
    val thumb = remember(f.photoPath) { f.photoPath?.let { BitmapFactory.decodeFile(it) } }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE0F2F1)),
                    contentAlignment = Alignment.Center
                ) { Text("🍽️", fontSize = 26.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.note, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("${f.kcal} kcal", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onRemove) { Text("Remove", color = BadRed) }
        }
    }
}

fun savePhoto(dir: File, id: Long, bmp: Bitmap): String? = try {
    val f = File(dir, "meal_$id.jpg")
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    f.absolutePath
} catch (e: Exception) {
    null
}
