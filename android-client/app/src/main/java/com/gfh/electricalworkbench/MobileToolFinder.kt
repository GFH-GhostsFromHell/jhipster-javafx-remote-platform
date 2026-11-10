package com.gfh.electricalworkbench

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Native task navigation; favourite and recent IDs stay on this device and account. */
private data class MobileTask(
    val id: String, val title: String, val detail: String,
    val group: String, val search: String, val destination: String,
    val offline: Boolean = false
)

private val allTasks = listOf(
    MobileTask("conduit-marks", "Conduit Bend Marks + 3D", "Tape marks, facing and a finished bend preview", "Installation", "conduit offset saddle kick 90 hook shoe", "/bend-instruction-lab.html"),
    MobileTask("bend-guide", "Conduit Bend Diagrams", "Bend layouts and mark instructions", "Installation", "emt pipe bend offset saddle", "/bend-calcs.html"),
    MobileTask("bender-calibrate", "Calibrate a Conduit Bender", "Measure shoe deduct and gain", "Installation", "shoe profile", "/bender-calibration.html"),
    MobileTask("pull-box", "Pull Box Dimensions - Offline", "Straight, angle and U-pull sizing", "Installation", "junction box wall", "native:pull", true),
    MobileTask("conductor-drop", "Circuit Sizing & Voltage Drop - Offline", "Ampacity, derating, voltage drop and length", "Calculations", "wire cable table d3", "native:circuit", true),
    MobileTask("service-demand", "Service Load Calculations", "Demand for hospital, schools, apartments and homes", "Calculations", "residential hotel feeder demand", "/guided-calculators.html?tool=service"),
    MobileTask("transformer", "Transformer & Load Sizing - Offline", "Transformer currents and loads", "Calculations", "kva primary secondary", "native:transformer", true),
    MobileTask("motor", "Motor Protection & Starter Sizing", "Fuse, breaker, overload and starter checks", "Calculations", "motor ocpd flc", "/calculators.html?section=s28"),
    MobileTask("refrigeration", "Refrigeration Motor Sizing", "Compressor RLA/LRA and MCA/MOCP", "Calculations", "hvac compressor", "/guided-calculators.html?tool=refrig"),
    MobileTask("grounding", "Grounding & Bonding", "Conductors and electrodes", "Calculations", "cec section 10", "/guided-calculators.html?tool=ground"),
    MobileTask("fire-pump", "Fire Pump Circuit", "Pump motor and controller worksheet", "Calculations", "jockey locked rotor", "/guided-calculators.html?tool=firepump"),
    MobileTask("advanced-code", "Advanced CEC Calculators", "Complex Code worksheets", "Calculations", "neutral hvac derating", "/advanced-calculations.html"),
    MobileTask("relay", "Protection & Relay Calculators - Offline", "CT/PT ratios and relay pickup", "Power & Protection", "4 20ma", "native:relay", true),
    MobileTask("power", "Power System Analysis", "Per-unit, short-circuit and sequence studies", "Power & Protection", "fault impedance", "native:power", true),
    MobileTask("fault", "Equipment Troubleshooting - Offline", "Fault isolation and work orders", "Troubleshooting", "vfd plc mine motor", "native:fault", true),
    MobileTask("troubleshooting-web", "Equipment Troubleshooting Guides", "Detailed motors, PLC, VFD and relay methods", "Troubleshooting", "diagnostic procedures", "/troubleshooting-kb.html"),
    MobileTask("jobsite", "Jobsite Tools", "Nameplates, commissioning and inspections", "Projects & Learning", "field records", "native:field", true),
    MobileTask("quote", "Build a Job Quote", "Materials, labour and taxes", "Projects & Learning", "estimate contractor", "/field-suite.html#quote"),
    MobileTask("saved", "Saved Projects", "Local projects and material takeoff", "Projects & Learning", "cable records", "native:projects", true),
    MobileTask("quiz", "Red Seal Daily Quiz", "309A practice questions", "Projects & Learning", "exam learning", "native:learn", true),
    MobileTask("career", "Trade Hours & Career", "Hours, skills and credentials", "Projects & Learning", "apprentice", "native:career", true),
    MobileTask("safety", "Electrical Safety", "PPE, approach and inspection guidance", "Projects & Learning", "loto", "native:safety", true),
    MobileTask("cloud-records", "Saved Cloud Work", "Cloud-synced records and exports", "Projects & Learning", "sync backup", "/account-records.html")
)

@Composable
internal fun MobileToolFinder(
    username: String,
    onOpenWeb: (String) -> Unit,
    onOpenMore: (MoreArea) -> Unit,
    onOpenSafety: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(username) {
        context.getSharedPreferences("ewb.tool.shortcuts.v1." + username, Context.MODE_PRIVATE)
    }
    var query by remember(username) { mutableStateOf("") }
    var group by remember(username) { mutableStateOf("All") }
    var favourites by remember(username) {
        mutableStateOf(preferences.getStringSet("favourites", emptySet())?.toSet() ?: emptySet())
    }
    var recent by remember(username) {
        mutableStateOf(preferences.getString("recent", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList())
    }

    fun open(task: MobileTask) {
        recent = (listOf(task.id) + recent.filter { it != task.id }).take(8)
        preferences.edit().putString("recent", recent.joinToString("|")).apply()
        when (task.destination) {
            "native:pull" -> context.startActivity(Intent(context, FieldSizingActivity::class.java))
            "native:circuit" -> context.startActivity(Intent(context, CircuitSizingActivity::class.java).putExtra("accountUsername", username))
            "native:transformer" -> context.startActivity(Intent(context, TransformerSizingActivity::class.java))
            "native:relay" -> context.startActivity(Intent(context, IndustrialFieldActivity::class.java))
            "native:power" -> context.startActivity(Intent(context, EngineeringPowerActivity::class.java))
            "native:fault" -> context.startActivity(Intent(context, FieldSizingActivity::class.java).putExtra("toolTab", "Troubleshooting"))
            "native:field" -> onOpenMore(MoreArea.FIELD)
            "native:projects" -> onOpenMore(MoreArea.PROJECTS)
            "native:learn" -> onOpenMore(MoreArea.LEARN)
            "native:career" -> onOpenMore(MoreArea.CAREER)
            "native:safety" -> onOpenSafety()
            else -> onOpenWeb(task.destination)
        }
    }

    val matches = allTasks.filter { task ->
        (group == "All" || group == task.group) &&
            query.trim().lowercase().split(Regex("\\s+")).all { word ->
                word.isBlank() || (task.title + " " + task.detail + " " + task.search).lowercase().contains(word)
            }
    }.sortedWith(
        compareBy<MobileTask> { if (favourites.contains(it.id)) 0 else 1 }
            .thenBy { val n = recent.indexOf(it.id); if (n < 0) 1000 else n }
            .thenBy { it.title }
    )
    MWPage("Find an Electrical Tool", "1. Find the task  ·  2. Enter values  ·  3. Verify  ·  4. Save or export") {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Search tools by job or calculation") },
            placeholder = { Text("Motor fuse, offset bend, voltage drop") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Installation", "Calculations", "Power & Protection", "Troubleshooting", "Projects & Learning").forEach { item ->
                OutlinedButton(onClick = { group = item }) {
                    Text((if (group == item) "✓ " else "") + item)
                }
            }
        }
        if (query.isBlank() && group == "All") {
            MWCard("★ Favourite tools") {
                val chosen = favourites.mapNotNull { id -> allTasks.find { it.id == id } }.take(4)
                if (chosen.isEmpty()) Text("Select ☆ Favourite on a tool to pin it here.", color = MWMuted)
                chosen.forEach { task ->
                    OutlinedButton(onClick = { open(task) }, modifier = Modifier.fillMaxWidth()) { Text(task.title) }
                }
            }
            if (recent.isNotEmpty()) {
                MWCard("Recently opened") {
                    recent.take(3).mapNotNull { id -> allTasks.find { it.id == id } }.forEach { task ->
                        OutlinedButton(onClick = { open(task) }, modifier = Modifier.fillMaxWidth()) { Text(task.title) }
                    }
                    OutlinedButton(onClick = {
                        recent = emptyList()
                        preferences.edit().remove("recent").apply()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Clear recently opened") }
                }
            }
        }
        Text(matches.size.toString() + " tools found", color = MWMuted, fontSize = 13.sp)
        if (matches.isEmpty()) {
            MWCard("No matching tools") { Text("Try fewer words or choose All tools.", color = MWMuted) }
        }
        matches.forEach { task ->
            MWCard(task.title) {
                Text(task.detail, color = MWMuted, fontSize = 13.sp)
                Text(task.group + " · " + (if (task.offline) "Offline" else "Web / cloud"), color = MWCyan, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { open(task) }, modifier = Modifier.weight(1f)) { Text("Open tool") }
                    OutlinedButton(onClick = {
                        favourites = if (favourites.contains(task.id)) favourites - task.id else favourites + task.id
                        preferences.edit().putStringSet("favourites", favourites.toSet()).apply()
                    }) { Text(if (favourites.contains(task.id)) "★ Saved" else "☆ Favourite") }
                }
            }
        }
        Text(
            "Favourites and recent tools stay on this Android device for this account, separate from cloud projects. Verify the CEC/OESC rules and equipment data before field use.",
            color = MWMuted, fontSize = 12.sp
        )
    }
}
