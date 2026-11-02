package com.gfh.electricalworkbench

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.sqrt

private val WorkspaceCard = Color(0xFF152A3D)
private val WorkspaceCard2 = Color(0xFF0F2235)
private val WorkspaceCyan = Color(0xFF22D3EE)
private val WorkspaceAmber = Color(0xFFF5B942)
private val WorkspaceText = Color(0xFFF1F5F9)
private val WorkspaceMuted = Color(0xFFB6C4D4)
private val WorkspaceGood = Color(0xFF5EEAD4)
private val WorkspaceDanger = Color(0xFFFF8A80)

private class MobileWorkspaceStore(context: Context, username: String) {
    private val safeUser = username.ifBlank { "default" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private val prefs = context.getSharedPreferences("electrical_workbench_$safeUser", Context.MODE_PRIVATE)

    fun records(name: String): List<JSONObject> {
        val text = prefs.getString("records_$name", "[]") ?: "[]"
        val arr = runCatching { JSONArray(text) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::add)
        }
    }

    fun append(name: String, value: JSONObject) {
        val arr = JSONArray()
        records(name).forEach { arr.put(it) }
        arr.put(value)
        prefs.edit().putString("records_$name", arr.toString()).apply()
    }

    fun putString(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }

    fun getString(key: String): String? = prefs.getString(key, null)
}

@Composable
fun SafetyWorkspaceScreen(username: String) {
    val context = LocalContext.current
    val store = remember(username) { MobileWorkspaceStore(context, username) }
    var tab by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    val names = listOf("Dashboard", "Thunderstorm", "PPE", "Approach", "Unsafe", "Gloves", "Maintenance")

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Electrical Safety", color = WorkspaceText, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(
                "Native Android safety workspace. Records stay on this device and support—not replace—your employer safety program, hazard assessment, energized-work procedure or equipment instructions.",
                color = WorkspaceMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = WorkspaceCard2) {
            names.forEachIndexed { index, name ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) })
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> SafetyDashboard(store, revision)
                1 -> StormSafety(store) { revision++ }
                2 -> PpeSafety(store) { revision++ }
                3 -> ApproachSafety()
                4 -> UnsafeConditionSafety(store) { revision++ }
                5 -> GloveSafety(store) { revision++ }
                else -> MaintenanceSafety(store) { revision++ }
            }
        }
    }
}

@Composable
private fun SafetyDashboard(store: MobileWorkspaceStore, revision: Int) {
    val today = LocalDate.now()
    val unsafe = remember(revision) { store.records("unsafeConditions") }
    val gloves = remember(revision) { store.records("rubberGloves") }
    val maintenance = remember(revision) { store.records("safetyMaintenance") }
    val assessments = remember(revision) { store.records("safetyAssessments") }
    val storms = remember(revision) { store.records("stormStandDowns") }
    val openHazards = unsafe.count { !it.optString("status", "OPEN").equals("RESOLVED", true) }
    val overdueGloves = gloves.count { parseDate(it.optString("nextTestDue"))?.isBefore(today) == true }
    val overdueMaintenance = maintenance.count { parseDate(it.optString("nextDue"))?.isBefore(today) == true }

    FeaturePage("Safety Dashboard", "Local status for your saved electrical-safety records.") {
        FeatureCard("Current status") {
            StatLine("Open unsafe-condition records", openHazards.toString(), openHazards == 0)
            StatLine("Overdue glove tests", overdueGloves.toString(), overdueGloves == 0)
            StatLine("Overdue equipment maintenance", overdueMaintenance.toString(), overdueMaintenance == 0)
        }
        FeatureCard("Saved records") {
            Text("PPE / arc-flash assessments: ${assessments.size}", color = WorkspaceText)
            Text("Thunderstorm / stand-down events: ${storms.size}", color = WorkspaceText)
            Text("Unsafe-condition records: ${unsafe.size}", color = WorkspaceText)
            Text("Rubber glove records: ${gloves.size}", color = WorkspaceText)
            Text("Safety-equipment records: ${maintenance.size}", color = WorkspaceText)
        }
        FeatureCard("Important") {
            Text(
                "The app does not authorize energized work, determine complete CSA Z462 PPE, or replace a supervisor/work authority. Use current employer/site procedures and verified hazard information.",
                color = WorkspaceMuted
            )
        }
    }
}

@Composable
private fun StormSafety(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    val context = LocalContext.current
    var site by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var secondsText by remember { mutableStateOf("30") }
    var distanceResult by remember { mutableStateOf("") }
    var clearAfter by remember { mutableStateOf(store.getString("storm_clear_after")?.toLongOrNull()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(clearAfter) {
        while (clearAfter != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remaining = ((clearAfter ?: 0L) - now).coerceAtLeast(0L)
    val remainingSeconds = remaining / 1000
    val history = remember(revision) { store.records("stormStandDowns").takeLast(12).reversed() }

    fun standDown(event: String) {
        val expiry = System.currentTimeMillis() + 30L * 60L * 1000L
        clearAfter = expiry
        store.putString("storm_clear_after", expiry.toString())
        store.append("stormStandDowns", JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("eventType", event)
            .put("site", site)
            .put("notes", notes))
        revision++
        onChanged()
    }

    FeaturePage("Thunderstorm / Lightning", "Field stand-down record and 30-minute public shelter timer for substation / utility work.") {
        FeatureCard("Stand-down control") {
            LabeledField("Site / substation", site) { site = it }
            LabeledField("Observation / clearance notes", notes) { notes = it }
            if (clearAfter != null) {
                val min = remainingSeconds / 60
                val sec = remainingSeconds % 60
                Text(
                    if (remaining > 0) "STAND DOWN — ${"%02d:%02d".format(min, sec)} remaining"
                    else "30 minutes elapsed. Do not automatically restart work; site procedure and work authority still control restart.",
                    color = if (remaining > 0) WorkspaceDanger else WorkspaceAmber,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
            } else {
                Text("No stand-down timer is active.", color = WorkspaceGood, fontWeight = FontWeight.Bold)
            }
            Button(onClick = { standDown("THUNDER_HEARD") }, modifier = Modifier.fillMaxWidth()) { Text("🔊 Thunder heard — START / RESET") }
            Button(onClick = { standDown("LIGHTNING_SEEN") }, modifier = Modifier.fillMaxWidth()) { Text("⚡ Lightning seen — START / RESET") }
            OutlinedButton(onClick = { standDown("SUPERVISOR_SITE_STAND_DOWN") }, modifier = Modifier.fillMaxWidth()) { Text("Supervisor / site stand-down") }
            OutlinedButton(onClick = {
                store.append("stormStandDowns", JSONObject().put("timestamp", System.currentTimeMillis()).put("eventType", "RESTART_AUTHORIZATION_RECORDED").put("site", site).put("notes", notes))
                clearAfter = null
                store.putString("storm_clear_after", null)
                revision++
                onChanged()
            }, modifier = Modifier.fillMaxWidth()) { Text("Record restart authorization / clearance") }
        }
        FeatureCard("Live Canadian lightning context") {
            Text("Open Environment and Climate Change Canada's live lightning map. The map is supplemental; thunder, lightning, detector alarms and site procedure still control stand-down decisions.", color = WorkspaceMuted)
            OutlinedButton(onClick = { openExternal(context, "https://weather.gc.ca/?layers=lightning") }, modifier = Modifier.fillMaxWidth()) { Text("Open official ECCC Lightning Map") }
        }
        FeatureCard("Flash-to-thunder estimate") {
            LabeledField("Seconds from flash to thunder", secondsText) { secondsText = it }
            Button(onClick = {
                distanceResult = runCatching {
                    val seconds = secondsText.toDouble()
                    require(seconds >= 0)
                    val km = seconds / 3.0
                    String.format(Locale.CANADA, "Approximate lightning distance: %.1f km. %s", km, if (seconds < 30) "UNDER 30 SECONDS — seek shelter under public 30–30 guidance." else "This estimate does not authorize work.")
                }.getOrElse { "Enter a valid non-negative time." }
            }) { Text("Estimate distance") }
            if (distanceResult.isNotBlank()) Text(distanceResult, color = WorkspaceText)
        }
        FeatureCard("Recent stand-down events") {
            if (history.isEmpty()) Text("No events saved yet.", color = WorkspaceMuted)
            history.forEach {
                Text("${it.optString("eventType")} · ${it.optString("site")} · ${it.optString("notes")}", color = WorkspaceText, fontSize = 12.sp)
                HorizontalDivider(color = Color(0x334E6A80))
            }
        }
    }
}

@Composable
private fun PpeSafety(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    var task by remember { mutableStateOf("Diagnostic testing / voltage verification") }
    var equipment by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf("600") }
    var incident by remember { mutableStateOf("8") }
    var garment by remember { mutableStateOf("12") }
    var face by remember { mutableStateOf("12") }
    var glove by remember { mutableStateOf("1000") }
    var source by remember { mutableStateOf("Arc-flash label / employer study") }
    var result by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    fun calculate(): String = runCatching {
        val v = voltage.toDouble(); val ie = incident.toDouble(); val g = garment.toDouble(); val f = face.toDouble(); val gv = glove.toDouble()
        require(v > 0 && ie > 0 && g > 0 && f > 0 && gv > 0)
        "Incident energy: %.2f cal/cm²\nGarment: %.2f → %s\nFace/head: %.2f → %s\nGlove maximum-use voltage: %.0f V vs %.0f V system → %s\n\nRating comparison only. Use the complete employer hazard assessment and current PPE requirements.".format(
            ie, g, if (g >= ie) "MEETS entered rating" else "INSUFFICIENT", f, if (f >= ie) "MEETS entered rating" else "INSUFFICIENT", gv, v, if (gv >= v) "not below system voltage" else "INSUFFICIENT"
        )
    }.getOrElse { "Check all numeric inputs." }

    FeaturePage("PPE / Arc Flash", "Compare entered PPE ratings against incident energy from an authorized label, study or employer procedure.") {
        FeatureCard("PPE rating check") {
            LabeledField("Task", task) { task = it }
            LabeledField("Equipment / location", equipment) { equipment = it }
            LabeledField("Nominal voltage (V)", voltage) { voltage = it }
            LabeledField("Incident energy (cal/cm²)", incident) { incident = it }
            LabeledField("Garment arc rating", garment) { garment = it }
            LabeledField("Face/head arc rating", face) { face = it }
            LabeledField("Glove maximum-use voltage", glove) { glove = it }
            LabeledField("Hazard-data source", source) { source = it }
            Button(onClick = { result = calculate() }, modifier = Modifier.fillMaxWidth()) { Text("Check entered ratings") }
            OutlinedButton(onClick = {
                val calc = calculate(); result = calc
                store.append("safetyAssessments", JSONObject().put("date", LocalDate.now().toString()).put("task", task).put("equipment", equipment).put("voltage", voltage).put("incidentEnergy", incident).put("garmentArcRating", garment).put("faceArcRating", face).put("gloveMaxUseVoltage", glove).put("source", source))
                status = "Assessment record saved locally."
                onChanged()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save assessment record") }
            if (status.isNotBlank()) Text(status, color = WorkspaceGood)
            if (result.isNotBlank()) Text(result, color = WorkspaceText, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ApproachSafety() {
    var voltage by remember { mutableStateOf("13800") }
    var overheadResult by remember { mutableStateOf("") }
    var limited by remember { mutableStateOf("1.0") }
    var restricted by remember { mutableStateOf("0.3") }
    var arc by remember { mutableStateOf("1.5") }
    var planned by remember { mutableStateOf("2.0") }
    var source by remember { mutableStateOf("Employer shock/arc-flash assessment") }
    var boundaryResult by remember { mutableStateOf("") }

    FeaturePage("Approach Distance", "Public Ontario overhead-conductor planning aid plus an entered-boundary checker.") {
        FeatureCard("Ontario construction — energized overhead conductors") {
            LabeledField("Nominal phase-to-phase voltage (V)", voltage) { voltage = it }
            Button(onClick = {
                overheadResult = runCatching {
                    val v = voltage.toDouble()
                    when {
                        v < 750 -> "The public construction table begins at 750 V. Use the applicable site/employer procedure for lower-voltage proximity work."
                        v <= 150000 -> "Minimum distance: 3.0 m"
                        v <= 250000 -> "Minimum distance: 4.5 m"
                        else -> "Minimum distance: 6.0 m"
                    } + "\nOntario construction reference: O. Reg. 213/91 s.188, subject to the regulation and applicable conditions."
                }.getOrElse { "Enter a valid voltage." }
            }) { Text("Calculate Ontario overhead minimum") }
            if (overheadResult.isNotBlank()) Text(overheadResult, color = WorkspaceText)
        }
        FeatureCard("Authorized shock / arc-flash boundary checker") {
            LabeledField("Limited approach boundary (m)", limited) { limited = it }
            LabeledField("Restricted approach boundary (m)", restricted) { restricted = it }
            LabeledField("Arc-flash boundary (m)", arc) { arc = it }
            LabeledField("Planned closest distance (m)", planned) { planned = it }
            LabeledField("Boundary source", source) { source = it }
            Button(onClick = {
                boundaryResult = runCatching {
                    val l = limited.toDouble(); val r = restricted.toDouble(); val a = arc.toDouble(); val p = planned.toDouble()
                    val shock = if (p < r) "INSIDE entered restricted boundary" else if (p < l) "INSIDE entered limited boundary" else "OUTSIDE entered limited/restricted boundaries"
                    val arcState = if (p < a) "INSIDE entered arc-flash boundary" else "OUTSIDE entered arc-flash boundary"
                    "Shock status: $shock\nArc-flash status: $arcState\nSource: $source\n\nThe app intentionally requires authorized boundary values instead of reproducing proprietary tables."
                }.getOrElse { "Check boundary inputs." }
            }) { Text("Check planned working distance") }
            if (boundaryResult.isNotBlank()) Text(boundaryResult, color = WorkspaceText)
        }
    }
}

@Composable
private fun UnsafeConditionSafety(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    var project by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Electrical hazard") }
    var severity by remember { mutableStateOf("High") }
    var description by remember { mutableStateOf("") }
    var immediate by remember { mutableStateOf("") }
    var recordStatus by remember { mutableStateOf("OPEN") }
    var photoUri by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> photoUri = uri?.toString().orEmpty() }
    val records = remember(revision) { store.records("unsafeConditions").takeLast(15).reversed() }

    FeaturePage("Unsafe Conditions", "Save electrical hazards, near misses and optional photos locally on the phone.") {
        FeatureCard("New unsafe-condition record") {
            LabeledField("Project / site", project) { project = it }
            LabeledField("Specific location", location) { location = it }
            LabeledField("Category", category) { category = it }
            LabeledField("Severity", severity) { severity = it }
            LabeledField("Description", description) { description = it }
            LabeledField("Immediate control / action", immediate) { immediate = it }
            LabeledField("Status", recordStatus) { recordStatus = it }
            OutlinedButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (photoUri.isBlank()) "Attach photo" else "Photo attached — change") }
            Button(onClick = {
                store.append("unsafeConditions", JSONObject().put("date", LocalDate.now().toString()).put("project", project).put("location", location).put("category", category).put("severity", severity).put("description", description).put("immediateAction", immediate).put("status", recordStatus).put("photoUri", photoUri))
                message = "Unsafe-condition record saved locally."
                revision++; onChanged()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save unsafe-condition record") }
            if (message.isNotBlank()) Text(message, color = WorkspaceGood)
        }
        FeatureCard("Saved records") {
            if (records.isEmpty()) Text("No unsafe-condition records saved yet.", color = WorkspaceMuted)
            records.forEach {
                Text("${it.optString("date")} · ${it.optString("severity")} · ${it.optString("status")}\n${it.optString("project")} — ${it.optString("description")}", color = WorkspaceText, fontSize = 12.sp)
                HorizontalDivider(color = Color(0x334E6A80))
            }
        }
    }
}

@Composable
private fun GloveSafety(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    var asset by remember { mutableStateOf("GLV-001") }
    var manufacturer by remember { mutableStateOf("") }
    var gloveClass by remember { mutableStateOf("") }
    var maxVoltage by remember { mutableStateOf("") }
    var lastTest by remember { mutableStateOf(LocalDate.now().toString()) }
    var nextDue by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val records = remember(revision) { store.records("rubberGloves").takeLast(15).reversed() }

    FeaturePage("Rubber Gloves", "Track glove identification, ratings and dielectric-test due dates. Use the employer program and applicable standard for required intervals.") {
        FeatureCard("Glove record") {
            LabeledField("Asset / serial", asset) { asset = it }
            LabeledField("Manufacturer", manufacturer) { manufacturer = it }
            LabeledField("Glove class", gloveClass) { gloveClass = it }
            LabeledField("Maximum-use voltage", maxVoltage) { maxVoltage = it }
            LabeledField("Last test date (YYYY-MM-DD)", lastTest) { lastTest = it }
            LabeledField("Next test due (YYYY-MM-DD)", nextDue) { nextDue = it }
            LabeledField("Notes", notes) { notes = it }
            Button(onClick = {
                store.append("rubberGloves", JSONObject().put("asset", asset).put("manufacturer", manufacturer).put("class", gloveClass).put("maxVoltage", maxVoltage).put("lastTest", lastTest).put("nextTestDue", nextDue).put("notes", notes))
                revision++; onChanged()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save glove record") }
        }
        FeatureCard("Glove status") {
            if (records.isEmpty()) Text("No glove records saved yet.", color = WorkspaceMuted)
            records.forEach {
                val due = parseDate(it.optString("nextTestDue"))
                val overdue = due?.isBefore(LocalDate.now()) == true
                Text("${it.optString("asset")} · Class ${it.optString("class")} · Due ${it.optString("nextTestDue", "not set")}${if (overdue) " · OVERDUE" else ""}", color = if (overdue) WorkspaceDanger else WorkspaceText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MaintenanceSafety(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    var asset by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("Electrical safety equipment") }
    var lastService by remember { mutableStateOf(LocalDate.now().toString()) }
    var nextDue by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val records = remember(revision) { store.records("safetyMaintenance").takeLast(15).reversed() }

    FeaturePage("Equipment Maintenance", "Track inspection/service due dates for electrical safety equipment.") {
        FeatureCard("Maintenance record") {
            LabeledField("Asset / equipment ID", asset) { asset = it }
            LabeledField("Equipment", equipment) { equipment = it }
            LabeledField("Last inspection / service", lastService) { lastService = it }
            LabeledField("Next due", nextDue) { nextDue = it }
            LabeledField("Notes", notes) { notes = it }
            Button(onClick = {
                store.append("safetyMaintenance", JSONObject().put("asset", asset).put("equipment", equipment).put("lastService", lastService).put("nextDue", nextDue).put("notes", notes))
                revision++; onChanged()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save maintenance record") }
        }
        FeatureCard("Maintenance status") {
            if (records.isEmpty()) Text("No equipment records saved yet.", color = WorkspaceMuted)
            records.forEach {
                val due = parseDate(it.optString("nextDue")); val overdue = due?.isBefore(LocalDate.now()) == true
                Text("${it.optString("asset")} · ${it.optString("equipment")} · Due ${it.optString("nextDue", "not set")}${if (overdue) " · OVERDUE" else ""}", color = if (overdue) WorkspaceDanger else WorkspaceText, fontSize = 12.sp)
            }
        }
    }
}

private enum class MoreArea { HUB, LEARN, FIELD, PROJECTS, CAREER }

@Composable
fun MoreWorkspaceScreen(username: String, onOpenWeb: (String) -> Unit) {
    var area by remember { mutableStateOf(MoreArea.HUB) }
    if (area == MoreArea.HUB) {
        FeaturePage("More Workspaces", "The Android app now exposes the same major electrician workspace families as desktop.") {
            HubCard("🎓 Learn", "Daily 25 quiz, CEC navigator, formulas, control trainer and province/code profile") { area = MoreArea.LEARN }
            HubCard("🛠 Field", "Troubleshooting, nameplates, commissioning, inspection, blueprint scaling, fault-current and grounding/bonding tools") { area = MoreArea.FIELD }
            HubCard("📁 Projects", "Project log, material takeoff, saved field records and cable planning") { area = MoreArea.PROJECTS }
            HubCard("📈 Career", "Apprenticeship hours, credential wallet, wage calculator and official job resources") { area = MoreArea.CAREER }
            HubCard("⚡ Code News", "ESA/OESC bulletins and CEC/OESC notices") { onOpenWeb("/news.html") }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            OutlinedButton(onClick = { area = MoreArea.HUB }, modifier = Modifier.padding(12.dp)) { Text("← All workspaces") }
            Box(Modifier.fillMaxSize()) {
                when (area) {
                    MoreArea.LEARN -> LearnWorkspace(username)
                    MoreArea.FIELD -> FieldWorkspace(username)
                    MoreArea.PROJECTS -> ProjectsWorkspace(username)
                    MoreArea.CAREER -> CareerWorkspace(username)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun LearnWorkspace(username: String) {
    val context = LocalContext.current
    val store = remember(username) { MobileWorkspaceStore(context, username) }
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("Daily 25", "CEC Navigator", "Formulas", "Controls", "Province")
    Column(Modifier.fillMaxSize()) {
        SectionHeader("Learn", "Red Seal / C of Q study tools and reference workflows.")
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = WorkspaceCard2) {
            names.forEachIndexed { i, n -> Tab(tab == i, { tab = i }, text = { Text(n) }) }
        }
        when (tab) {
            0 -> DailyQuiz(username, store)
            1 -> CecNavigator()
            2 -> FormulaLibrary()
            3 -> ControlTrainer()
            else -> ProvinceProfile()
        }
    }
}

@Composable
private fun DailyQuiz(username: String, store: MobileWorkspaceStore) {
    val questions = remember(username, LocalDate.now()) { MobileQuestionBank.daily25(LocalDate.now(), username) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var answered by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    FeaturePage("Red Seal / C of Q Daily 25", "Five questions from each A–E task category. The same user gets a stable set for the calendar day.") {
        if (index >= questions.size) {
            FeatureCard("Daily quiz complete") {
                Text("Final score: $score / ${questions.size}", color = WorkspaceCyan, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Review explanations and use the CEC Navigator for weak topics.", color = WorkspaceMuted)
                if (!saved) {
                    Button(onClick = {
                        store.append("quizHistory", JSONObject().put("date", LocalDate.now().toString()).put("score", score).put("total", questions.size))
                        saved = true
                    }) { Text(if (saved) "Saved" else "Save result") }
                } else Text("Result saved locally.", color = WorkspaceGood)
            }
        } else {
            val q = questions[index]
            FeatureCard("Question ${index + 1} of ${questions.size}") {
                Text(q.category, color = WorkspaceCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(q.question, color = WorkspaceText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                q.options.forEachIndexed { optionIndex, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == optionIndex, onClick = { if (!answered) selected = optionIndex })
                        Text("${('A'.code + optionIndex).toChar()}. $option", color = WorkspaceText, modifier = Modifier.weight(1f))
                    }
                }
                if (answered) {
                    val correct = selected == q.correctIndex
                    Text(if (correct) "Correct." else "Incorrect. Correct answer: ${('A'.code + q.correctIndex).toChar()}.", color = if (correct) WorkspaceGood else WorkspaceDanger, fontWeight = FontWeight.Bold)
                    Text(q.explanation, color = WorkspaceMuted)
                    Text("Reference: ${q.reference}", color = WorkspaceCyan, fontSize = 12.sp)
                }
                Button(onClick = {
                    if (!answered) {
                        if (selected >= 0) {
                            if (selected == q.correctIndex) score++
                            answered = true
                        }
                    } else {
                        index++; selected = -1; answered = false
                    }
                }, enabled = answered || selected >= 0, modifier = Modifier.fillMaxWidth()) {
                    Text(if (answered) if (index == questions.lastIndex) "Finish quiz" else "Next question" else "Check answer")
                }
                Text("Score: $score / ${if (answered) index + 1 else index}", color = WorkspaceMuted)
            }
        }
    }
}

private data class RuleEntry(val topic: String, val reference: String, val summary: String)

@Composable
private fun CecNavigator() {
    var query by remember { mutableStateOf("") }
    val entries = remember { listOf(
        RuleEntry("Voltage drop / conductor length", "Appendix D / Table D3 study workflow", "Voltage drop, maximum run length and conductor-selection checks."),
        RuleEntry("Residential service", "Rule 8-200 study workflow", "Dwelling demand including basic load, range, heating/A-C and special loads."),
        RuleEntry("Apartment service", "Rule 8-202 study workflow", "Multi-suite demand and common/house load calculation."),
        RuleEntry("School service", "Section 8 study workflow", "School occupancy demand by area and connected loads."),
        RuleEntry("Hospital service", "Section 8 study workflow", "Hospital occupancy demand including high-intensity areas."),
        RuleEntry("Grounding and bonding", "Section 10", "Service grounding, bonding jumpers, electrodes and equipment bonding."),
        RuleEntry("Raceway fill", "Section 12 / Table 9 series", "Raceway dimensions, conductor fill and wiring-method installation."),
        RuleEntry("Tap conductors", "Section 14", "Tap ampacity, length and downstream overcurrent-protection conditions."),
        RuleEntry("Class 1 / Class 2", "Section 16", "Low-voltage source, OCPD and conductor requirements."),
        RuleEntry("Patient care", "Section 24", "Patient-care environments, bonding, receptacles and isolated systems."),
        RuleEntry("Transformers", "Section 26", "Transformer currents, conductors, OCPD, grounding and capacitor equipment."),
        RuleEntry("Motors / feeders", "Section 28", "Motor conductors, overloads, branch OCPD, disconnects and multi-motor feeders."),
        RuleEntry("Fire alarm / fire pump", "Section 32", "Fire-alarm wiring/power and special fire-pump requirements."),
        RuleEntry("Welders", "Section 42", "Duty-cycle and group-demand treatment for welder conductors/feeders."),
        RuleEntry("Emergency systems", "Section 46", "Emergency power, transfer and emergency lighting supply requirements."),
        RuleEntry("Electric heating", "Section 62", "Space heating, trace heating and heating load demand treatment."),
        RuleEntry("Communications / fibre", "Section 60/86 study topics", "Separation, cable suitability and communications wiring considerations.")
    ) }
    val filtered = entries.filter { query.isBlank() || (it.topic + it.reference + it.summary).contains(query, true) }
    FeaturePage("CEC Rule Navigator", "Search original topic summaries and rule/table references without reproducing Code text.") {
        LabeledField("Search", query) { query = it }
        filtered.forEach { e -> FeatureCard(e.topic) { Text(e.reference, color = WorkspaceCyan, fontWeight = FontWeight.Bold); Text(e.summary, color = WorkspaceMuted) } }
    }
}

@Composable
private fun FormulaLibrary() {
    val text = """
OHM'S LAW
V = I × R     I = V / R     R = V / I

POWER
DC / 1φ resistive: P = V × I
1φ AC: P = V × I × PF
3φ AC: P = √3 × V(L-L) × I × PF
I(3φ) = P / (√3 × V × PF)

TRANSFORMERS
1φ: I = VA / V
3φ: I = VA / (√3 × V)
Turns/voltage ratio: Vp/Vs = Np/Ns = Is/Ip
Approx terminal fault current: I_fault ≈ I_FLA / Z(pu)

VOLTAGE DROP — K FACTOR
1φ: VD = 2 × K × I × L / CM
3φ: VD = √3 × K × I × L / CM
L = VD × CM / (factor × K × I)
K = VD × CM / (factor × I × L)

POWER FACTOR CORRECTION
kVAR = kW × [tan(cos⁻¹ PF_old) − tan(cos⁻¹ PF_new)]

CT RATIO
Primary = Secondary × (CT primary / CT secondary)

4–20 mA
Signal = 4 + (process fraction × 16)

HYDRAULICS
Force = Pressure × Area

PVC MOVEMENT
Movement = run × ΔT × expansion coefficient
""".trimIndent()
    FeaturePage("Formula Library", "Fast trade formulas and rearranged forms.") { FeatureCard("Electrical formulas") { Text(text, color = WorkspaceText, fontFamily = FontFamily.Monospace, fontSize = 12.sp) } }
}

@Composable
private fun ControlTrainer() {
    var scenario by remember { mutableStateOf("3-wire starter") }
    val scenarios = listOf("3-wire starter", "Reversing starter", "Wye-delta starter", "VFD 4–20 mA", "CT measurement", "Fire alarm Class A/B")
    val lesson = when (scenario) {
        "3-wire starter" -> "STOP(NC) and overload contacts are in series with the starter coil. START(NO) energizes the coil and an auxiliary contact seals around START. Opening STOP or the overload path de-energizes the coil and stops the motor."
        "Reversing starter" -> "Forward and reverse contactors swap two motor phases. Electrical and mechanical interlocking prevent both from closing together."
        "Wye-delta starter" -> "Start in WYE so each winding sees about 57.7% of line voltage, then transition to DELTA for normal running."
        "VFD 4–20 mA" -> "For 0–1800 rpm mapped to 4–20 mA: 1200 rpm = 4 + (1200/1800 × 16) ≈ 14.67 mA."
        "CT measurement" -> "For a 400:5 CT, ratio = 80:1. A measured 2.5 A secondary corresponds to 200 A primary. Never open-circuit a CT secondary while energized."
        else -> "Class A topology returns to the source/control equipment and can maintain operation beyond a single open depending on topology. Class B terminates in the field with supervision."
    }
    FeaturePage("Control Trainer", "Motor controls, VFDs, CTs and signalling scenarios.") {
        FeatureCard("Choose scenario") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scenarios.forEach { s -> OutlinedButton(onClick = { scenario = s }) { Text(s) } }
            }
            Text(scenario, color = WorkspaceCyan, fontWeight = FontWeight.Bold)
            Text(lesson, color = WorkspaceText)
        }
    }
}

@Composable
private fun ProvinceProfile() {
    FeaturePage("Province / Code Profile", "CEC-based tools remain common while provincial adoption, amendments and inspection guidance are layered on top.") {
        FeatureCard("Ontario") {
            Text("Ontario profile: CEC/OESC-focused workspace with ESA news integration. Verify the currently adopted Ontario amendments, bulletins and inspection requirements before relying on a final installation decision.", color = WorkspaceText)
        }
        FeatureCard("Other provinces / territories") {
            Text("Core CEC tools can be reused, but province-specific amendments and inspection bulletins should be treated as a separate verified dataset.", color = WorkspaceMuted)
        }
    }
}

@Composable
private fun FieldWorkspace(username: String) {
    val context = LocalContext.current
    val store = remember(username) { MobileWorkspaceStore(context, username) }
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("Troubleshoot", "Nameplate", "Commission", "Inspect", "Blueprint", "Fault", "Ground/Bond")
    Column(Modifier.fillMaxSize()) {
        SectionHeader("Field", "Native field tools and offline records.")
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = WorkspaceCard2) { names.forEachIndexed { i,n -> Tab(tab == i, { tab = i }, text = { Text(n) }) } }
        when(tab) {
            0 -> TroubleshootingTool()
            1 -> NameplateTool(store)
            2 -> CommissioningTool(store, username)
            3 -> InspectionTool(store)
            4 -> BlueprintTool()
            5 -> FaultCurrentTool()
            else -> GroundBondTool()
        }
    }
}

@Composable
private fun TroubleshootingTool() {
    val symptoms = listOf("Motor will not start", "Breaker trips immediately", "Contactor chatters", "GFCI nuisance trips", "VFD fault / won't run", "Transformer secondary low", "4–20 mA reading wrong", "Fire alarm open trouble")
    var symptom by remember { mutableStateOf(symptoms.first()) }
    val steps = when(symptom) {
        "Motor will not start" -> "1. Establish safe work condition / required energized-work procedure.\n2. Confirm source voltage and disconnect/fuses/breaker.\n3. Check overload and stop/E-stop/interlock chain.\n4. Verify control voltage.\n5. Check starter coil command and auxiliary contacts.\n6. Check motor leads/windings and mechanical load.\n7. For VFD-fed motors, check drive enable, reference and faults."
        "Breaker trips immediately" -> "1. Do not repeatedly reclose into a fault.\n2. Isolate the load and inspect for phase-phase/phase-ground faults.\n3. Verify breaker type/rating and fault duty.\n4. Check conductor insulation and equipment faults.\n5. Distinguish inrush from a true fault using the applicable protection rules."
        "Contactor chatters" -> "Check control voltage under load, loose control wiring, failing coil, dirty armature/shading ring, unstable PLC/VFD output, seal-in contact and stop/overload chain."
        "GFCI nuisance trips" -> "Check actual leakage: moisture, damaged cords, downstream neutral-ground connections, shared/miswired neutrals, filters/VFDs and aggregate leakage. Do not bypass GFCI protection."
        "VFD fault / won't run" -> "Record the exact fault code. Check line voltage, DC bus, motor cable/motor insulation, acceleration/deceleration, overload/current limit, enable/interlocks, local/remote mode and speed reference."
        "Transformer secondary low" -> "Check primary voltage and tap position, phase/connection, secondary unloaded and loaded, loading/voltage drop, transformer rating/frequency and terminations."
        "4–20 mA reading wrong" -> "Confirm loop power, polarity, series wiring, input configuration, common/reference, scaling endpoints and sourcing/sinking. Measure loop current in series."
        else -> "Identify the zone/circuit and trouble indication. Check listed equipment instructions, field wiring for opens/shorts/grounds, EOL device/location, modules and power supplies."
    }
    FeaturePage("Troubleshooting Assistant", "Organizes a diagnostic process; does not replace safe isolation, testing procedures or equipment instructions.") {
        FeatureCard("Symptom") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { symptoms.forEach { s -> OutlinedButton(onClick = { symptom = s }) { Text(s) } } }
            Text(symptom, color = WorkspaceCyan, fontWeight = FontWeight.Bold)
            Text(steps, color = WorkspaceText)
        }
    }
}

@Composable
private fun NameplateTool(store: MobileWorkspaceStore) {
    var equipment by remember { mutableStateOf("Motor") }; var maker by remember { mutableStateOf("") }; var model by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf("575") }; var phases by remember { mutableStateOf("3") }; var rating by remember { mutableStateOf("60 hp") }; var current by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var photo by remember { mutableStateOf("") }; var status by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> photo = uri?.toString().orEmpty() }
    FeaturePage("Equipment Nameplate Capture", "Attach a nameplate image and save the equipment values locally.") {
        FeatureCard("Nameplate / equipment record") {
            LabeledField("Equipment type", equipment) { equipment = it }; LabeledField("Manufacturer", maker) { maker = it }; LabeledField("Model", model) { model = it }; LabeledField("Voltage", voltage) { voltage = it }; LabeledField("Phases", phases) { phases = it }; LabeledField("Rating", rating) { rating = it }; LabeledField("FLA / RLA / current", current) { current = it }; LabeledField("Notes", notes) { notes = it }
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if(photo.isBlank()) "Choose nameplate photo" else "Photo attached — change") }
            Button(onClick = { store.append("nameplates", JSONObject().put("date", LocalDate.now().toString()).put("equipment", equipment).put("manufacturer", maker).put("model", model).put("voltage", voltage).put("phases", phases).put("rating", rating).put("current", current).put("photo", photo).put("notes", notes)); status = "Saved locally." }, modifier = Modifier.fillMaxWidth()) { Text("Save equipment record") }
            if(status.isNotBlank()) Text(status, color = WorkspaceGood)
        }
    }
}

@Composable
private fun CommissioningTool(store: MobileWorkspaceStore, username: String) {
    var project by remember { mutableStateOf("") }; var equipment by remember { mutableStateOf("") }; var voltage by remember { mutableStateOf("") }; var current by remember { mutableStateOf("") }; var insulation by remember { mutableStateOf("") }; var continuity by remember { mutableStateOf("PASS") }; var rotation by remember { mutableStateOf("ABC") }; var notes by remember { mutableStateOf("") }; var status by remember { mutableStateOf("") }
    FeaturePage("Commissioning & Test Sheet", "Record test results offline with the project workspace.") {
        FeatureCard("Electrical test record") {
            LabeledField("Project", project){project=it}; LabeledField("Equipment / circuit", equipment){equipment=it}; LabeledField("Measured voltage", voltage){this@CommissioningTool.run { }}
            LabeledField("Measured voltage", voltage){ voltage=it }; LabeledField("Measured current", current){ current=it }; LabeledField("Insulation resistance", insulation){insulation=it}; LabeledField("Continuity / bonding", continuity){continuity=it}; LabeledField("Phase rotation", rotation){rotation=it}; LabeledField("Notes", notes){notes=it}
            Button(onClick = { store.append("commissioning", JSONObject().put("date", LocalDate.now().toString()).put("project",project).put("equipment",equipment).put("voltage",voltage).put("current",current).put("insulationResistance",insulation).put("continuity",continuity).put("phaseRotation",rotation).put("tester",username).put("notes",notes)); status="Commissioning record saved." }, modifier=Modifier.fillMaxWidth()){ Text("Save test sheet") }
            if(status.isNotBlank()) Text(status, color=WorkspaceGood)
        }
    }
}

@Composable
private fun InspectionTool(store: MobileWorkspaceStore) {
    var project by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }; var deficiencies by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf(false) }; var bonding by remember { mutableStateOf(false) }; var torque by remember { mutableStateOf(false) }; var covers by remember { mutableStateOf(false) }; var clearance by remember { mutableStateOf(false) }; var testing by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("") }
    FeaturePage("Inspection / Deficiency Checklist", "General field checklist. The inspection authority, Code and project specifications remain authoritative.") {
        FeatureCard("Field inspection") {
            LabeledField("Project", project){project=it}; LabeledField("Location", location){location=it}
            CheckLine("Equipment/circuits labelled", labels){labels=it}; CheckLine("Bonding/grounding visually checked", bonding){bonding=it}; CheckLine("Terminations/torque documentation checked", torque){torque=it}; CheckLine("Covers/barriers installed", covers){covers=it}; CheckLine("Working space/access checked", clearance){clearance=it}; CheckLine("Required testing completed", testing){testing=it}
            LabeledField("Deficiencies / notes", deficiencies){deficiencies=it}
            Button(onClick = { store.append("inspections", JSONObject().put("date",LocalDate.now().toString()).put("project",project).put("location",location).put("labels",labels).put("bonding",bonding).put("torque",torque).put("covers",covers).put("clearance",clearance).put("testing",testing).put("deficiencies",deficiencies)); status="Inspection saved." }, modifier=Modifier.fillMaxWidth()){Text("Save inspection")}
            if(status.isNotBlank()) Text(status, color=WorkspaceGood)
        }
    }
}

@Composable
private fun BlueprintTool() {
    var drawing by remember { mutableStateOf("125") }; var scaleDrawing by remember { mutableStateOf("10") }; var scaleActual by remember { mutableStateOf("1000") }; var allowance by remember { mutableStateOf("10") }; var result by remember { mutableStateOf("") }
    FeaturePage("Drawing / Blueprint Helper", "Scale measurements and estimate actual cable/raceway lengths.") {
        FeatureCard("Scale calculator") {
            LabeledField("Measured drawing length (mm)", drawing){drawing=it}; LabeledField("Scale drawing units", scaleDrawing){scaleDrawing=it}; LabeledField("Scale actual units", scaleActual){scaleActual=it}; LabeledField("Routing/waste allowance (%)", allowance){allowance=it}
            Button(onClick = { result=runCatching { val actual=drawing.toDouble()*scaleActual.toDouble()/scaleDrawing.toDouble(); val with=actual*(1+allowance.toDouble()/100); "Scaled actual length: %.2f mm\nWith %.1f%% allowance: %.2f mm\n= %.2f m".format(actual,allowance.toDouble(),with,with/1000) }.getOrElse{"Check inputs."} }){Text("Calculate actual length")}
            if(result.isNotBlank()) Text(result,color=WorkspaceText,fontFamily=FontFamily.Monospace)
        }
    }
}

@Composable
private fun FaultCurrentTool() {
    var kva by remember { mutableStateOf("75") }; var voltage by remember { mutableStateOf("208") }; var phases by remember { mutableStateOf("3") }; var impedance by remember { mutableStateOf("3") }; var upstream by remember { mutableStateOf("0") }; var result by remember { mutableStateOf("") }
    FeaturePage("Transformer Fault-Current Estimate", "Approximate terminal symmetrical current for study/planning; not a protection-coordination or arc-flash study.") {
        FeatureCard("Available fault-current worksheet") {
            LabeledField("Transformer kVA",kva){kva=it};LabeledField("Secondary voltage",voltage){voltage=it};LabeledField("Phases (1 or 3)",phases){phases=it};LabeledField("Transformer impedance (%)",impedance){impedance=it};LabeledField("Upstream available fault current A (0=ignore)",upstream){upstream=it}
            Button(onClick={result=runCatching{val k=kva.toDouble();val v=voltage.toDouble();val z=impedance.toDouble()/100;require(k>0&&v>0&&z>0);val fla=if(phases.trim()=="3")k*1000/(sqrt(3.0)*v) else k*1000/v;val t=fla/z;val u=upstream.toDouble();val combined=if(u>0)1.0/(1.0/t+1.0/u) else t;"Transformer FLA: %.1f A\nTransformer-limited terminal fault current: %.0f A\nCombined estimate: %.0f A".format(fla,t,combined)}.getOrElse{"Check inputs."}}){Text("Estimate fault current")}
            if(result.isNotBlank()) Text(result,color=WorkspaceText,fontFamily=FontFamily.Monospace)
        }
    }
}

@Composable
private fun GroundBondTool() {
    val items=listOf("Service grounding conductor","Equipment bonding conductor","Transformer secondary bonding jumper","Grounding electrode conductor","Ground rod / plate","Grounding electrode ring","HV station grounding grid")
    var item by remember { mutableStateOf(items.first()) }
    val answer=when(item){
        "Equipment bonding conductor"->"Start with Section 10 equipment bonding requirements and the applicable bonding-conductor sizing table. Identify the circuit/feeder OCPD and conductor material before sizing."
        "Transformer secondary bonding jumper"->"Use the separately derived system / transformer secondary bonding provisions in Section 10. Determine derived conductor size, transformer arrangement and connection point before selecting the jumper."
        "Ground rod / plate"->"Use the electrode installation requirements for the selected electrode type, including dimensions, burial/location and connection method."
        "Grounding electrode ring"->"Confirm ring-electrode construction, conductor material/size and burial conditions under Section 10."
        "HV station grounding grid"->"High-voltage station grounding is a specialized design task. Use applicable high-voltage grounding provisions and engineering criteria; touch/step potential may require engineering analysis."
        else->"Identify service/derived-system rating, electrode type, conductor material and the applicable Section 10 sizing rule/table. Verify the final size against CEC 2024."
    }
    FeaturePage("Grounding & Bonding Wizard","Choose the installation element to identify the calculation/reference path."){
        FeatureCard("Sizing path"){
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){items.forEach{s->OutlinedButton(onClick={item=s}){Text(s)}}}
            Text(item,color=WorkspaceCyan,fontWeight=FontWeight.Bold);Text(answer,color=WorkspaceText)
        }
    }
}

@Composable
private fun ProjectsWorkspace(username: String) {
    val context=LocalContext.current;val store=remember(username){MobileWorkspaceStore(context,username)};var tab by remember{mutableIntStateOf(0)};val names=listOf("Project Log","Takeoff","Records","Cable Plan")
    Column(Modifier.fillMaxSize()){
        SectionHeader("Projects","Offline project, material and cable-planning records.")
        ScrollableTabRow(selectedTabIndex=tab,edgePadding=8.dp,containerColor=WorkspaceCard2){names.forEachIndexed{i,n->Tab(tab==i,{tab=i},text={Text(n)})}}
        when(tab){0->ProjectLog(store);1->MaterialTakeoff(store);2->SavedRecordSummary(store);else->CablePlanner()}
    }
}

@Composable
private fun ProjectLog(store: MobileWorkspaceStore){
    var name by remember{mutableStateOf("")};var client by remember{mutableStateOf("")};var location by remember{mutableStateOf("")};var system by remember{mutableStateOf("347/600 V 3φ 4W")};var notes by remember{mutableStateOf("")};var status by remember{mutableStateOf("")}
    FeaturePage("Project Workspace","Keep project, panel, equipment and field notes together locally."){
        FeatureCard("New project"){
            LabeledField("Project name",name){name=it};LabeledField("Client / employer",client){client=it};LabeledField("Location",location){location=it};LabeledField("Electrical system",system){system=it};LabeledField("Notes",notes){notes=it}
            Button(onClick={store.append("projects",JSONObject().put("created",LocalDate.now().toString()).put("name",name).put("client",client).put("location",location).put("system",system).put("notes",notes));status="Project saved."},modifier=Modifier.fillMaxWidth()){Text("Save project")}
            if(status.isNotBlank())Text(status,color=WorkspaceGood)
        }
    }
}

@Composable
private fun MaterialTakeoff(store: MobileWorkspaceStore){
    var lines by remember{mutableStateOf("12/2 NMD90,100,1.25\n4 in square boxes,12,4.50\nDuplex receptacles,10,3.25")};var result by remember{mutableStateOf("")};var total by remember{mutableStateOf(0.0)}
    fun calc():String{var sum=0.0;val b=StringBuilder();var count=0;lines.lines().forEach{line->if(line.isNotBlank()){val p=line.split(',');val desc=p[0].trim();val qty=p.getOrNull(1)?.trim()?.toDoubleOrNull()?:1.0;val cost=p.getOrNull(2)?.trim()?.toDoubleOrNull()?:0.0;val ext=qty*cost;sum+=ext;count++;b.append("$desc : %.2f × %.2f = %.2f\n".format(qty,cost,ext))}};total=sum;b.append("\nItems: $count\nEstimated material total: $%.2f".format(sum));return b.toString()}
    FeaturePage("Material Takeoff / Job Planner","One item per line: description, quantity, unit cost."){
        FeatureCard("Takeoff"){
            OutlinedTextField(value=lines,onValueChange={lines=it},label={Text("Material lines")},minLines=8,modifier=Modifier.fillMaxWidth())
            Button(onClick={result=calc()},modifier=Modifier.fillMaxWidth()){Text("Calculate takeoff")}
            OutlinedButton(onClick={result=calc();store.append("materials",JSONObject().put("date",LocalDate.now().toString()).put("lines",lines).put("estimatedTotal",total))},modifier=Modifier.fillMaxWidth()){Text("Save takeoff")}
            if(result.isNotBlank())Text(result,color=WorkspaceText,fontFamily=FontFamily.Monospace,fontSize=12.sp)
        }
    }
}

@Composable
private fun SavedRecordSummary(store: MobileWorkspaceStore){
    val collections=listOf("projects","nameplates","commissioning","inspections","hours","credentials","quizHistory","materials","unsafeConditions","rubberGloves","safetyMaintenance")
    FeaturePage("Saved Local Records","Summary of offline records used by Projects, Field, Safety and Career."){
        collections.forEach{c->FeatureCard(c.replaceFirstChar{it.uppercase()}){Text("${store.records(c).size} saved record(s)",color=WorkspaceCyan,fontSize=20.sp,fontWeight=FontWeight.Bold)}}
    }
}

@Composable
private fun CablePlanner(){
    var current by remember{mutableStateOf("100")};var length by remember{mutableStateOf("50")};var k by remember{mutableStateOf("12.9")};var cm by remember{mutableStateOf("133100")};var phase by remember{mutableStateOf("3")};var voltage by remember{mutableStateOf("600")};var result by remember{mutableStateOf("")}
    FeaturePage("Cable & Raceway Planner","Voltage-drop worksheet to combine load, run length and conductor geometry. Verify final ampacity/raceway selection in the Workbench."){
        FeatureCard("Voltage drop"){
            LabeledField("Load current (A)",current){current=it};LabeledField("One-way length (m)",length){length=it};LabeledField("K factor",k){k=it};LabeledField("Circular mil area",cm){cm=it};LabeledField("Phases (1 or 3)",phase){phase=it};LabeledField("System voltage",voltage){voltage=it}
            Button(onClick={result=runCatching{val i=current.toDouble();val lFt=length.toDouble()*3.28084;val factor=if(phase.trim()=="3")sqrt(3.0) else 2.0;val vd=factor*k.toDouble()*i*lFt/cm.toDouble();"Voltage drop: %.2f V\nPercent: %.2f%%\n\nUse CEC 2024 Table D3/notes or the applicable engineering method for final conductor selection.".format(vd,vd/voltage.toDouble()*100)}.getOrElse{"Check inputs."}},modifier=Modifier.fillMaxWidth()){Text("Calculate voltage drop")}
            if(result.isNotBlank())Text(result,color=WorkspaceText,fontFamily=FontFamily.Monospace)
        }
    }
}

@Composable
private fun CareerWorkspace(username:String){
    val context=LocalContext.current;val store=remember(username){MobileWorkspaceStore(context,username)};var tab by remember{mutableIntStateOf(0)};val names=listOf("Hours","Credentials","Wage","Jobs")
    Column(Modifier.fillMaxSize()){
        SectionHeader("Career & Apprenticeship","Track hours, credentials, wages and official job resources.")
        ScrollableTabRow(selectedTabIndex=tab,edgePadding=8.dp,containerColor=WorkspaceCard2){names.forEachIndexed{i,n->Tab(tab==i,{tab=i},text={Text(n)})}}
        when(tab){0->HoursTracker(store);1->CredentialWallet(store);2->WageCalculator();else->JobResources()}
    }
}

@Composable
private fun HoursTracker(store:MobileWorkspaceStore){
    var employer by remember{mutableStateOf("")};var category by remember{mutableStateOf("Construction / maintenance")};var hours by remember{mutableStateOf("")};var notes by remember{mutableStateOf("")};var revision by remember{mutableIntStateOf(0)}
    val total=remember(revision){store.records("hours").sumOf{it.optDouble("hours",0.0)}}
    FeaturePage("Apprenticeship / Trade Hours","Track work hours locally by employer and work category."){
        FeatureCard("Current total"){Text("%.1f h".format(total),color=WorkspaceCyan,fontSize=26.sp,fontWeight=FontWeight.Black)}
        FeatureCard("Add hours"){
            LabeledField("Employer",employer){employer=it};LabeledField("Work category",category){category=it};LabeledField("Hours",hours){hours=it};LabeledField("Notes",notes){notes=it}
            Button(onClick={val h=hours.toDoubleOrNull()?:0.0;if(h>0){store.append("hours",JSONObject().put("date",LocalDate.now().toString()).put("employer",employer).put("category",category).put("hours",h).put("notes",notes));revision++;hours=""}},modifier=Modifier.fillMaxWidth()){Text("Save hours")}
        }
    }
}

@Composable
private fun CredentialWallet(store:MobileWorkspaceStore){
    var name by remember{mutableStateOf("")};var issuer by remember{mutableStateOf("")};var expiry by remember{mutableStateOf("")};var notes by remember{mutableStateOf("")};var revision by remember{mutableIntStateOf(0)}
    val records=remember(revision){store.records("credentials").takeLast(20).reversed()}
    FeaturePage("Credential Wallet","Keep certification and training reminders locally."){
        FeatureCard("Add credential"){LabeledField("Credential / training",name){name=it};LabeledField("Issuer",issuer){issuer=it};LabeledField("Expiry / renewal date",expiry){expiry=it};LabeledField("Notes",notes){notes=it};Button(onClick={store.append("credentials",JSONObject().put("name",name).put("issuer",issuer).put("expiry",expiry).put("notes",notes));revision++},modifier=Modifier.fillMaxWidth()){Text("Save credential")}}
        FeatureCard("Saved credentials"){if(records.isEmpty())Text("No credentials saved yet.",color=WorkspaceMuted);records.forEach{Text("${it.optString("name")} · ${it.optString("issuer")} · ${it.optString("expiry")}",color=WorkspaceText,fontSize=12.sp)}}
    }
}

@Composable
private fun WageCalculator(){
    var rate by remember{mutableStateOf("45")};var regular by remember{mutableStateOf("40")};var overtime by remember{mutableStateOf("0")};var otMultiplier by remember{mutableStateOf("1.5")};var weeks by remember{mutableStateOf("52")};var result by remember{mutableStateOf("")}
    FeaturePage("Wage Calculator","Simple gross-pay estimate; payroll deductions, union agreements and employment standards may change actual pay."){
        FeatureCard("Gross pay worksheet"){LabeledField("Hourly rate",rate){rate=it};LabeledField("Regular hours / week",regular){regular=it};LabeledField("Overtime hours / week",overtime){overtime=it};LabeledField("OT multiplier",otMultiplier){otMultiplier=it};LabeledField("Weeks / year",weeks){weeks=it};Button(onClick={result=runCatching{val weekly=rate.toDouble()*regular.toDouble()+rate.toDouble()*otMultiplier.toDouble()*overtime.toDouble();"Weekly gross: $%.2f\nAnnualized gross: $%.2f".format(weekly,weekly*weeks.toDouble())}.getOrElse{"Check inputs."}},modifier=Modifier.fillMaxWidth()){Text("Calculate")};if(result.isNotBlank())Text(result,color=WorkspaceText,fontFamily=FontFamily.Monospace)}
    }
}

@Composable
private fun JobResources(){
    val context=LocalContext.current
    FeaturePage("Official Job & Trade Resources","Open public sources explicitly in your browser."){
        HubCard("Government of Canada Job Bank","Search electrician and technologist postings across Canada."){openExternal(context,"https://www.jobbank.gc.ca/")}
        HubCard("Skilled Trades Ontario","Ontario trade registration, certification and apprenticeship information."){openExternal(context,"https://www.skilledtradesontario.ca/")}
        HubCard("Red Seal Program","Interprovincial Red Seal trade information."){openExternal(context,"https://www.red-seal.ca/")}
    }
}

@Composable
private fun FeaturePage(title:String,subtitle:String,content:@Composable Column.()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(title,color=WorkspaceText,fontSize=24.sp,fontWeight=FontWeight.Black);Text(subtitle,color=WorkspaceMuted,fontSize=13.sp,lineHeight=18.sp);content();Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionHeader(title:String,subtitle:String){Column(Modifier.padding(horizontal=16.dp,vertical=10.dp)){Text(title,color=WorkspaceText,fontSize=25.sp,fontWeight=FontWeight.Black);Text(subtitle,color=WorkspaceMuted,fontSize=13.sp)}}

@Composable
private fun FeatureCard(title:String,content:@Composable Column.()->Unit){Card(colors=CardDefaults.cardColors(containerColor=WorkspaceCard),border=BorderStroke(1.dp,Color(0xFF29455E)),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(title,color=WorkspaceCyan,fontWeight=FontWeight.Bold,fontSize=16.sp);content()}}}

@Composable
private fun HubCard(title:String,description:String,onClick:()->Unit){FeatureCard(title){Text(description,color=WorkspaceMuted);Button(onClick=onClick,modifier=Modifier.fillMaxWidth()){Text("Open")}}}

@Composable
private fun LabeledField(label:String,value:String,onValueChange:(String)->Unit){OutlinedTextField(value=value,onValueChange=onValueChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=false)}

@Composable
private fun CheckLine(label:String,checked:Boolean,onChecked:(Boolean)->Unit){Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=checked,onCheckedChange=onChecked);Text(label,color=WorkspaceText,modifier=Modifier.weight(1f))}}

@Composable
private fun StatLine(label:String,value:String,good:Boolean){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,color=WorkspaceMuted,modifier=Modifier.weight(1f));Text(value,color=if(good)WorkspaceGood else WorkspaceDanger,fontSize=20.sp,fontWeight=FontWeight.Black)}}

private fun parseDate(text:String):LocalDate?=runCatching{LocalDate.parse(text)}.getOrNull()
private fun openExternal(context:Context,url:String){runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))} }
