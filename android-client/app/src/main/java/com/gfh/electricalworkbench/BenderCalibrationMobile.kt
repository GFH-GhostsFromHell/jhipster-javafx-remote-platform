package com.gfh.electricalworkbench

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Native/offline calibration. Stored per installation; never assume one shoe's values fit another. */
internal object LocalBenderCalibration {
    private const val FILE = "electrical_workbench_bender_calibration"
    fun read(context: Context, key: String): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(key, "") ?: ""
    fun save(context: Context, values: Map<String, String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            values.forEach { (key, value) -> putString(key, value) }
        }.apply()
    }
    fun measured(context: Context, key: String, destinationUnits: String): Double? {
        val stored = read(context, key).toDoubleOrNull() ?: return null
        if (!stored.isFinite() || stored < 0) return null
        val sourceUnits = read(context, "units")
        if (sourceUnits !in listOf("mm", "in") || destinationUnits !in listOf("mm", "in")) return null
        return if (sourceUnits == destinationUnits) stored
            else if (destinationUnits == "mm") stored * 25.4 else stored / 25.4
    }
    fun description(context: Context): String =
        (read(context, "name").ifBlank { "Saved bender" }) + " · " +
        read(context, "size") + " " + read(context, "material")
}

@Composable
internal fun BenderCalibrationMobile() {
    val context = LocalContext.current
    var name by remember { mutableStateOf(LocalBenderCalibration.read(context, "name")) }
    var model by remember { mutableStateOf(LocalBenderCalibration.read(context, "model")) }
    var material by remember { mutableStateOf(LocalBenderCalibration.read(context, "material").ifBlank { "EMT" }) }
    var size by remember { mutableStateOf(LocalBenderCalibration.read(context, "size").ifBlank { "1/2" }) }
    var units by remember { mutableStateOf(LocalBenderCalibration.read(context, "units").ifBlank { "mm" }) }
    var mark by remember { mutableStateOf(LocalBenderCalibration.read(context, "mark")) }
    var stub by remember { mutableStateOf(LocalBenderCalibration.read(context, "stub")) }
    var stock by remember { mutableStateOf(LocalBenderCalibration.read(context, "stock")) }
    var first by remember { mutableStateOf(LocalBenderCalibration.read(context, "first")) }
    var second by remember { mutableStateOf(LocalBenderCalibration.read(context, "second")) }
    var minimumMarkGap by remember { mutableStateOf(LocalBenderCalibration.read(context, "minMarkGap")) }
    var minimumStraight by remember { mutableStateOf(LocalBenderCalibration.read(context, "minStraight")) }
    var result by remember { mutableStateOf("Measure a scrap bend and save this shoe before using its deduct or gain.") }

    val markValue = mark.toDoubleOrNull()
    val stubValue = stub.toDoubleOrNull()
    val deduct = if (markValue != null && stubValue != null && markValue >= 0 &&
        stubValue > 0 && stubValue >= markValue) stubValue - markValue else null
    val stockValue = stock.toDoubleOrNull()
    val firstValue = first.toDoubleOrNull()
    val secondValue = second.toDoubleOrNull()
    val gain = if (stockValue != null && firstValue != null && secondValue != null &&
        stockValue > 0 && firstValue > 0 && secondValue > 0 &&
        firstValue + secondValue >= stockValue) firstValue + secondValue - stockValue else null

    Text("Bender Calibration · offline", style = MaterialTheme.typography.titleLarge)
    Text("Use a scrap piece from the exact shoe, trade size, and conduit type. Saved measurements remain available offline and sync to your account when signed in.")
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Profile name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Make / model") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(value = material, onValueChange = { material = it }, label = { Text("Conduit material (EMT, IMC, RMC...)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text("Trade size (e.g. 3/4)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = units == "mm", onClick = { units = "mm" }, label = { Text("mm") })
        FilterChip(selected = units == "in", onClick = { units = "in" }, label = { Text("inches") })
    }
    Text("1 · Stub-up deduct test", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(value = mark, onValueChange = { mark = it }, label = { Text("Arrow mark from square starting end (" + units + ")") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = stub, onValueChange = { stub = it }, label = { Text("Measured finished 90° stub height (" + units + ")") }, modifier = Modifier.fillMaxWidth())
    Text("Deduct = measured stub − arrow mark. " + (deduct?.let { String.format(Locale.ROOT, "Deduct: %.3f %s", it, units) } ?: "Enter both valid measurements."))
    HorizontalDivider()
    Text("2 · 90° gain test", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Original straight stock length (" + units + ")") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = first, onValueChange = { first = it }, label = { Text("First finished leg (" + units + ")") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = second, onValueChange = { second = it }, label = { Text("Second finished leg (" + units + ")") }, modifier = Modifier.fillMaxWidth())
    Text("Gain = finished leg A + finished leg B − original stock. " + (gain?.let { String.format(Locale.ROOT, "Gain: %.3f %s", it, units) } ?: "Enter all three valid measurements."))
    HorizontalDivider()
    Text("3 · Verified shoe mark spacing (optional)", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(value = minimumMarkGap, onValueChange = { minimumMarkGap = it },
        label = { Text("Minimum mark spacing for this exact shoe (" + units + ")") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = minimumStraight, onValueChange = { minimumStraight = it },
        label = { Text("Verified straight BETWEEN bend arcs (" + units + ")") },
        modifier = Modifier.fillMaxWidth())
    Text("Record either gap only if confirmed for this exact shoe and conduit. Mark spacing is distinct from straight between arcs, and neither alone proves head access.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = {
        val gap = minimumMarkGap.trim().toDoubleOrNull()
        val arcStraight = minimumStraight.trim().toDoubleOrNull()
        val maxStock = if (units == "mm") 3048.0 else 120.0
        val gapValid = minimumMarkGap.isBlank() ||
            (gap != null && gap.isFinite() && gap > 0 && gap <= maxStock)
        val straightValid = minimumStraight.isBlank() ||
            (arcStraight != null && arcStraight.isFinite() && arcStraight > 0 && arcStraight <= maxStock)
        val deductStarted = mark.isNotBlank() || stub.isNotBlank()
        val gainStarted = stock.isNotBlank() || first.isNotBlank() || second.isNotBlank()
        if (name.isBlank() || model.isBlank() || size.isBlank() || material.isBlank() ||
            (!deductStarted && !gainStarted && minimumMarkGap.isBlank() && minimumStraight.isBlank()) || (deductStarted && deduct == null) ||
            (gainStarted && gain == null) || !gapValid || !straightValid) {
            result = "Enter model, size, material and a complete deduct/gain test. Optional verified mark and arc clearances must be positive and within 10 ft."
        } else {
            LocalBenderCalibration.save(context, mapOf(
                "name" to name, "model" to model, "material" to material, "size" to size,
                "units" to units, "mark" to mark, "stub" to stub, "stock" to stock,
                "first" to first, "second" to second,
                "deduct" to (deduct?.toString() ?: ""), "gain" to (gain?.toString() ?: ""),
                "minMarkGap" to (gap?.toString() ?: ""),
                "minStraight" to (arcStraight?.toString() ?: "")
            ))
            MobileAccountSync.schedule(context)
            result = "Calibration saved locally: " + name + ". Account sync runs when online. In Conduit bender or Gain & racks, tap Use saved calibration."
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Save and use this bender calibration") }
    Text(result)
    Text("Use matching measurement datums, verify manufacturer instructions, bend radius, shoe fit and springback. Changing the units toggle does not convert already entered numbers; remeasure or convert before saving.", style = MaterialTheme.typography.bodySmall)
}
