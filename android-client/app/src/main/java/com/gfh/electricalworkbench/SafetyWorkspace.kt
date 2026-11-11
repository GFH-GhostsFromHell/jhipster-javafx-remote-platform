package com.gfh.electricalworkbench

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

@Composable
fun SafetyWorkspaceScreen(username: String) {
    val context = LocalContext.current
    val store = remember(username) { MobileWorkspaceStore(context, username) }
    var tab by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    val tabs = listOf("Dashboard", "Thunderstorm", "PPE", "Approach", "Unsafe", "Gloves", "Maintenance")

    Column(Modifier.fillMaxSize()) {
        MWSectionPicker("Safety", tabs, tab) { tab = it }
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> SafetyDashboard(store, revision)
                1 -> ThunderstormSafety(store) { revision++ }
                2 -> PpePlanner(store) { revision++ }
                3 -> ApproachPlanner()
                4 -> UnsafeConditions(store) { revision++ }
                5 -> RubberGloves(store) { revision++ }
                else -> SafetyMaintenance(store) { revision++ }
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
    val overdueGloves = gloves.count { parseLocalDate(it.optString("nextTestDue"))?.isBefore(today) == true }
    val overdueMaintenance = maintenance.count { parseLocalDate(it.optString("nextDue"))?.isBefore(today) == true }

    MWPage("Safety Dashboard", "Current status from safety records stored locally for this user.") {
        MWCard("Current status") {
            StatusRow("Open unsafe-condition records", openHazards)
            StatusRow("Overdue glove tests", overdueGloves)
            StatusRow("Overdue equipment maintenance", overdueMaintenance)
        }
        MWCard("Saved records") {
            Text("PPE / arc-flash assessments: ${assessments.size}", color = MWText)
            Text("Thunderstorm / stand-down events: ${storms.size}", color = MWText)
            Text("Unsafe-condition records: ${unsafe.size}", color = MWText)
            Text("Rubber-glove records: ${gloves.size}", color = MWText)
            Text("Safety-equipment records: ${maintenance.size}", color = MWText)
        }
        MWCard("Safety limitation") {
            Text(
                "This app does not authorize energized work, determine a complete CSA Z462 PPE ensemble, or replace a supervisor/work authority. Use current employer procedures, equipment labels/studies and applicable law/standards.",
                color = MWMuted
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MWMuted)
        Text(count.toString(), color = if (count == 0) MWGood else MWDanger, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

@Composable
private fun ThunderstormSafety(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    val context = LocalContext.current
    var site by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("30") }
    var distanceResult by remember { mutableStateOf("") }
    var clearAfter by remember { mutableStateOf(store.getString("storm_clear_after")?.toLongOrNull()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(clearAfter) {
        while (clearAfter != null) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val remainingMs = ((clearAfter ?: 0L) - now).coerceAtLeast(0L)
    val remainingSeconds = remainingMs / 1000L
    val history = remember(revision) { store.records("stormStandDowns").takeLast(12).reversed() }

    fun recordEvent(type: String, startTimer: Boolean) {
        if (startTimer) {
            clearAfter = System.currentTimeMillis() + 30L * 60L * 1000L
            store.putString("storm_clear_after", clearAfter.toString())
        }
        store.append(
            "stormStandDowns",
            JSONObject()
                .put("timestamp", System.currentTimeMillis())
                .put("eventType", type)
                .put("site", site)
                .put("notes", notes)
        )
        revision++
        onChanged()
    }

    MWPage(
        "Thunderstorm / Lightning Safety",
        "Stand-down record, public 30-minute shelter timer and lightning context for electrical/substation work. Timer expiry is never automatic permission to restart."
    ) {
        MWCard("Stand-down control") {
            MWField("Site / substation", site) { site = it }
            MWField("Observation / clearance notes", notes) { notes = it }
            if (clearAfter == null) {
                Text("No stand-down timer is active.", color = MWGood, fontWeight = FontWeight.Bold)
            } else if (remainingMs > 0L) {
                Text(
                    "STAND DOWN — %02d:%02d remaining".format(remainingSeconds / 60, remainingSeconds % 60),
                    color = MWDanger,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            } else {
                Text(
                    "30 minutes have elapsed. Do not restart automatically; employer/site procedure and the responsible work authority still control restart.",
                    color = MWAmber,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(onClick = { recordEvent("THUNDER_HEARD", true) }, modifier = Modifier.fillMaxWidth()) {
                Text("🔊 Thunder heard — START / RESET")
            }
            Button(onClick = { recordEvent("LIGHTNING_SEEN", true) }, modifier = Modifier.fillMaxWidth()) {
                Text("⚡ Lightning seen — START / RESET")
            }
            OutlinedButton(onClick = { recordEvent("SUPERVISOR_SITE_STAND_DOWN", true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Supervisor / site stand-down")
            }
            OutlinedButton(
                onClick = {
                    recordEvent("RESTART_AUTHORIZATION_RECORDED", false)
                    clearAfter = null
                    store.putString("storm_clear_after", null)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Record restart authorization / clearance") }
        }

        MWCard("Live Canadian lightning context") {
            Text(
                "Use the official Environment and Climate Change Canada lightning map as supplemental context. Thunder, visible lightning, detector alarms and the site procedure take precedence.",
                color = MWMuted
            )
            OutlinedButton(onClick = { openExternal(context, "https://weather.gc.ca/?layers=lightning") }, modifier = Modifier.fillMaxWidth()) {
                Text("Open official ECCC Lightning Map")
            }
        }

        MWCard("Flash-to-thunder estimate") {
            MWField("Seconds from flash to thunder", seconds) { seconds = it }
            Button(onClick = {
                distanceResult = runCatching {
                    val s = seconds.toDouble()
                    require(s >= 0.0)
                    val km = s / 3.0
                    String.format(
                        Locale.CANADA,
                        "Approximate lightning distance: %.1f km. %s",
                        km,
                        if (s < 30.0) "UNDER 30 SECONDS — seek shelter under public 30–30 guidance." else "This estimate does not authorize work."
                    )
                }.getOrElse { "Enter a valid non-negative time." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Estimate lightning distance") }
            if (distanceResult.isNotBlank()) Text(distanceResult, color = MWText)
        }

        MWCard("Recent stand-down events") {
            if (history.isEmpty()) Text("No stand-down events saved yet.", color = MWMuted)
            history.forEach { item ->
                Text(
                    "${item.optString("eventType")} · ${item.optString("site")} · ${item.optString("notes")}",
                    color = MWText,
                    fontSize = 12.sp
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PpePlanner(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    var task by remember { mutableStateOf("Diagnostic testing / voltage verification") }
    var equipment by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf("600") }
    var incidentEnergy by remember { mutableStateOf("8") }
    var garment by remember { mutableStateOf("12") }
    var face by remember { mutableStateOf("12") }
    var glove by remember { mutableStateOf("1000") }
    var source by remember { mutableStateOf("Arc-flash label / employer study") }
    var result by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    fun calculate(): String = runCatching {
        val systemV = voltage.toDouble()
        val incident = incidentEnergy.toDouble()
        val garmentRating = garment.toDouble()
        val faceRating = face.toDouble()
        val gloveVoltage = glove.toDouble()
        require(systemV > 0 && incident > 0 && garmentRating > 0 && faceRating > 0 && gloveVoltage > 0)
        String.format(
            Locale.CANADA,
            "Incident energy: %.2f cal/cm²\nGarment: %.2f → %s\nFace/head: %.2f → %s\nGlove max-use voltage: %.0f V vs %.0f V system → %s\n\nRating comparison only. Use the full employer hazard assessment and current PPE requirements.",
            incident,
            garmentRating,
            if (garmentRating >= incident) "MEETS entered rating" else "INSUFFICIENT",
            faceRating,
            if (faceRating >= incident) "MEETS entered rating" else "INSUFFICIENT",
            gloveVoltage,
            systemV,
            if (gloveVoltage >= systemV) "not below entered system voltage" else "INSUFFICIENT"
        )
    }.getOrElse { "Check all numeric inputs." }

    MWPage("PPE / Arc Flash", "Enter incident energy from an authorized label/study and compare it with entered PPE ratings.") {
        MWCard("PPE rating comparison") {
            MWField("Task", task) { task = it }
            MWField("Equipment / location", equipment) { equipment = it }
            MWField("Nominal system voltage (V)", voltage) { voltage = it }
            MWField("Incident energy (cal/cm²)", incidentEnergy) { incidentEnergy = it }
            MWField("Garment arc rating (cal/cm²)", garment) { garment = it }
            MWField("Face/head arc rating (cal/cm²)", face) { face = it }
            MWField("Glove maximum-use voltage (V)", glove) { glove = it }
            MWField("Hazard-data source", source) { source = it }
            Button(onClick = { result = calculate() }, modifier = Modifier.fillMaxWidth()) { Text("Check entered ratings") }
            OutlinedButton(
                onClick = {
                    result = calculate()
                    store.append(
                        "safetyAssessments",
                        JSONObject()
                            .put("date", LocalDate.now().toString())
                            .put("task", task)
                            .put("equipment", equipment)
                            .put("voltage", voltage)
                            .put("incidentEnergyCalCm2", incidentEnergy)
                            .put("garmentArcRating", garment)
                            .put("faceArcRating", face)
                            .put("gloveMaxUseVoltage", glove)
                            .put("source", source)
                    )
                    status = "Assessment record saved locally."
                    onChanged()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save assessment record") }
            if (status.isNotBlank()) Text(status, color = MWGood)
            if (result.isNotBlank()) Text(result, color = MWText, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ApproachPlanner() {
    var voltage by remember { mutableStateOf("13800") }
    var overheadResult by remember { mutableStateOf("") }
    var limited by remember { mutableStateOf("1.0") }
    var restricted by remember { mutableStateOf("0.3") }
    var arc by remember { mutableStateOf("1.5") }
    var planned by remember { mutableStateOf("2.0") }
    var source by remember { mutableStateOf("Employer shock/arc-flash assessment") }
    var boundaryResult by remember { mutableStateOf("") }

    MWPage("Approach Distance", "Ontario construction overhead-conductor planning aid plus an entered-boundary checker.") {
        MWCard("Ontario construction — energized overhead conductors") {
            MWField("Nominal phase-to-phase voltage (V)", voltage) { voltage = it }
            Button(onClick = {
                overheadResult = runCatching {
                    val v = voltage.toDouble()
                    val distance = when {
                        v < 750.0 -> "The public construction table starts at 750 V; use the applicable site/employer procedure for lower-voltage proximity work."
                        v <= 150000.0 -> "Minimum distance: 3.0 m"
                        v <= 250000.0 -> "Minimum distance: 4.5 m"
                        else -> "Minimum distance: 6.0 m"
                    }
                    "$distance\nOntario construction reference: O. Reg. 213/91 s.188, subject to the regulation and applicable conditions."
                }.getOrElse { "Enter a valid voltage." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Calculate Ontario overhead minimum") }
            if (overheadResult.isNotBlank()) Text(overheadResult, color = MWText)
        }

        MWCard("Authorized shock / arc-flash boundary checker") {
            MWField("Limited approach boundary (m)", limited) { limited = it }
            MWField("Restricted approach boundary (m)", restricted) { restricted = it }
            MWField("Arc-flash boundary (m)", arc) { arc = it }
            MWField("Planned closest distance (m)", planned) { planned = it }
            MWField("Boundary source", source) { source = it }
            Button(onClick = {
                boundaryResult = runCatching {
                    val l = limited.toDouble(); val r = restricted.toDouble(); val a = arc.toDouble(); val p = planned.toDouble()
                    val shock = when {
                        p < r -> "INSIDE entered restricted boundary"
                        p < l -> "INSIDE entered limited boundary"
                        else -> "OUTSIDE entered limited/restricted boundaries"
                    }
                    val arcState = if (p < a) "INSIDE entered arc-flash boundary" else "OUTSIDE entered arc-flash boundary"
                    "Shock status: $shock\nArc-flash status: $arcState\nSource: $source\n\nAuthorized boundary values must come from the employer assessment, label/study or other approved source."
                }.getOrElse { "Check boundary inputs." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Check planned working distance") }
            if (boundaryResult.isNotBlank()) Text(boundaryResult, color = MWText)
        }
    }
}

@Composable
private fun UnsafeConditions(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    var project by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Electrical hazard") }
    var severity by remember { mutableStateOf("High") }
    var description by remember { mutableStateOf("") }
    var immediateAction by remember { mutableStateOf("") }
    var recordStatus by remember { mutableStateOf("OPEN") }
    var photoUri by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val records = remember(revision) { store.records("unsafeConditions").takeLast(15).reversed() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> photoUri = uri?.toString().orEmpty() }

    MWPage("Unsafe Conditions", "Save hazards, near misses and optional photos locally on the phone.") {
        MWCard("New unsafe-condition record") {
            MWField("Project / site", project) { project = it }
            MWField("Specific location", location) { location = it }
            MWField("Category", category) { category = it }
            MWField("Severity", severity) { severity = it }
            MWField("Description", description) { description = it }
            MWField("Immediate control / action", immediateAction) { immediateAction = it }
            MWField("Status", recordStatus) { recordStatus = it }
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (photoUri.isBlank()) "Attach unsafe-condition photo" else "Photo attached — change")
            }
            Button(onClick = {
                store.append(
                    "unsafeConditions",
                    JSONObject()
                        .put("date", LocalDate.now().toString())
                        .put("project", project)
                        .put("location", location)
                        .put("category", category)
                        .put("severity", severity)
                        .put("description", description)
                        .put("immediateAction", immediateAction)
                        .put("status", recordStatus)
                        .put("photoUri", photoUri)
                )
                message = "Unsafe-condition record saved locally."
                revision++
                onChanged()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save unsafe-condition record") }
            if (message.isNotBlank()) Text(message, color = MWGood)
        }

        MWCard("Saved records") {
            if (records.isEmpty()) Text("No unsafe-condition records saved yet.", color = MWMuted)
            records.forEach { item ->
                Text(
                    "${item.optString("date")} · ${item.optString("severity")} · ${item.optString("status")}\n${item.optString("project")} — ${item.optString("description")}",
                    color = MWText,
                    fontSize = 12.sp
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RubberGloves(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    var asset by remember { mutableStateOf("GLV-001") }
    var manufacturer by remember { mutableStateOf("") }
    var gloveClass by remember { mutableStateOf("") }
    var maxVoltage by remember { mutableStateOf("") }
    var lastTest by remember { mutableStateOf(LocalDate.now().toString()) }
    var nextDue by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val records = remember(revision) { store.records("rubberGloves").takeLast(15).reversed() }

    MWPage("Rubber Gloves", "Track glove identification, voltage rating and test dates. Required intervals come from the applicable employer program/standard.") {
        MWCard("Glove record") {
            MWField("Asset / serial", asset) { asset = it }
            MWField("Manufacturer", manufacturer) { manufacturer = it }
            MWField("Glove class", gloveClass) { gloveClass = it }
            MWField("Maximum-use voltage", maxVoltage) { maxVoltage = it }
            MWField("Last test date (YYYY-MM-DD)", lastTest) { lastTest = it }
            MWField("Next test due (YYYY-MM-DD)", nextDue) { nextDue = it }
            MWField("Notes", notes) { notes = it }
            Button(onClick = {
                store.append(
                    "rubberGloves",
                    JSONObject()
                        .put("asset", asset)
                        .put("manufacturer", manufacturer)
                        .put("class", gloveClass)
                        .put("maxVoltage", maxVoltage)
                        .put("lastTest", lastTest)
                        .put("nextTestDue", nextDue)
                        .put("notes", notes)
                )
                revision++
                onChanged()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save glove record") }
        }
        MWCard("Glove status") {
            if (records.isEmpty()) Text("No glove records saved yet.", color = MWMuted)
            records.forEach { item ->
                val overdue = parseLocalDate(item.optString("nextTestDue"))?.isBefore(LocalDate.now()) == true
                Text(
                    "${item.optString("asset")} · Class ${item.optString("class")} · Due ${item.optString("nextTestDue", "not set")}${if (overdue) " · OVERDUE" else ""}",
                    color = if (overdue) MWDanger else MWText,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SafetyMaintenance(store: MobileWorkspaceStore, onChanged: () -> Unit) {
    var asset by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("Electrical safety equipment") }
    var lastService by remember { mutableStateOf(LocalDate.now().toString()) }
    var nextDue by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val records = remember(revision) { store.records("safetyMaintenance").takeLast(15).reversed() }

    MWPage("Equipment Maintenance", "Track inspection/service due dates for electrical safety equipment.") {
        MWCard("Maintenance record") {
            MWField("Asset / equipment ID", asset) { asset = it }
            MWField("Equipment", equipment) { equipment = it }
            MWField("Last inspection / service", lastService) { lastService = it }
            MWField("Next due", nextDue) { nextDue = it }
            MWField("Notes", notes) { notes = it }
            Button(onClick = {
                store.append(
                    "safetyMaintenance",
                    JSONObject()
                        .put("asset", asset)
                        .put("equipment", equipment)
                        .put("lastService", lastService)
                        .put("nextDue", nextDue)
                        .put("notes", notes)
                )
                revision++
                onChanged()
            }, modifier = Modifier.fillMaxWidth()) { Text("Save maintenance record") }
        }
        MWCard("Maintenance status") {
            if (records.isEmpty()) Text("No equipment records saved yet.", color = MWMuted)
            records.forEach { item ->
                val overdue = parseLocalDate(item.optString("nextDue"))?.isBefore(LocalDate.now()) == true
                Text(
                    "${item.optString("asset")} · ${item.optString("equipment")} · Due ${item.optString("nextDue", "not set")}${if (overdue) " · OVERDUE" else ""}",
                    color = if (overdue) MWDanger else MWText,
                    fontSize = 12.sp
                )
            }
        }
    }
}
