package com.gfh.electricalworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import java.util.Locale
import kotlin.math.sqrt

/** Native/offline equivalent of the load-based desktop transformer size selector. */
class TransformerSizingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF22D3EE),
                background = Color(0xFF06111F),
                surface = Color(0xFF10253A)
            )) {
                TransformerSizingScreen()
            }
        }
    }
}

private val txServiceTypes = listOf(
    "Service — calculated demand",
    "Service — residential",
    "Service — apartments / multi-unit",
    "Service — commercial / ICI",
    "Service — school",
    "Service — hospital",
    "Service — hotel / motel",
    "Service — industrial"
)
private val txEquipmentTypes = listOf(
    "General", "Lighting", "Receptacles", "Motor / HVAC",
    "Heating", "EV charging", "Controls", "Other"
)
private val txUnits = listOf("A", "VA", "kVA", "W", "kW")
private val txOnePhaseSizes = doubleArrayOf(0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 5.0, 7.5, 10.0, 15.0, 25.0, 37.5, 50.0, 75.0, 100.0, 167.0, 250.0, 333.0, 500.0)
private val txThreePhaseSizes = doubleArrayOf(3.0, 6.0, 9.0, 15.0, 30.0, 45.0, 75.0, 112.5, 150.0, 225.0, 300.0, 500.0, 750.0, 1000.0, 1500.0, 2000.0, 2500.0, 3000.0)

private data class TxLoad(
    val id: Long,
    val name: String = "New load",
    val type: String = "General",
    val quantity: String = "1",
    val amount: String = "10",
    val unit: String = "kVA",
    val pf: String = "0.90",
    val demand: String = "100",
    val continuous: Boolean = false
) {
    val isService: Boolean get() = type in txServiceTypes
}

@Composable
private fun TransformerSizingScreen() {
    var phase by remember { mutableStateOf("3φ") }
    var primary by remember { mutableStateOf("600") }
    var secondary by remember { mutableStateOf("208") }
    var spare by remember { mutableStateOf("20") }
    var latest by remember { mutableStateOf("Enter the loads served by the transformer, then calculate.") }
    var idCounter by remember { mutableLongStateOf(4L) }
    val rows = remember {
        mutableStateListOf(
            TxLoad(1, "Lighting", "Lighting", "1", "12", "kW", "0.95", "100", true),
            TxLoad(2, "Receptacles", "Receptacles", "1", "8", "kVA", "1.00", "100", false),
            TxLoad(3, "Mechanical equipment", "Motor / HVAC", "1", "18", "kVA", "0.90", "100", false)
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Transformer Size Selector", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Size by connected equipment loads or pre-calculated service demand. Runs locally on Android without a network connection.",
            color = Color(0xFFB6C4D4))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10253A))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("1. Transformer system", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TxDropDown("Transformer phase", phase, listOf("3φ", "1φ")) { phase = it }
                TxInput("Primary line voltage (V)", primary) { primary = it }
                TxInput("Secondary line voltage (V)", secondary) { secondary = it }
                TxInput("Design spare / future capacity (%)", spare) { spare = it }
            }
        }

        Text("2. Transformer loads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Calculated service demand should already include applicable Section 8 demand factors. Service rows use 100% demand and no extra 125% continuous factor. Don't also add equipment already included in the service total.",
            color = Color(0xFFF5B942), style = MaterialTheme.typography.bodySmall)
        rows.forEachIndexed { index, load ->
            TxLoadCard(
                load = load,
                onChange = { newLoad -> rows[index] = newLoad },
                onDelete = { rows.removeAt(index) }
            )
        }
        Button(onClick = { rows.add(TxLoad(idCounter++)) }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add equipment load")
        }
        Button(onClick = {
            rows.add(TxLoad(idCounter++, "Calculated service load", txServiceTypes[0],
                "1", "100", "kVA", "1.00", "100", false))
        }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add service demand")
        }
        OutlinedButton(onClick = {
            rows.clear()
            rows.add(TxLoad(idCounter++, "Calculated commercial service demand", txServiceTypes[3],
                "1", "150", "kVA", "1.00", "100", false))
            latest = "Service example loaded. Replace 150 kVA with your actual calculated service demand."
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Load service example")
        }
        OutlinedButton(onClick = {
            rows.clear()
            latest = "No loads. Add equipment loads or a calculated service demand."
        }, modifier = Modifier.fillMaxWidth()) { Text("Clear loads") }

        Button(
            onClick = {
                latest = runCatching {
                    computeTransformer(phase, primary, secondary, spare, rows.toList())
                }.getOrElse { ex -> "Check inputs: ${ex.message ?: "Invalid transformer load data"}" }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Select transformer size") }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10253A))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("3. Transformer recommendation", style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(latest, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Text("Planning worksheet only. Verify the actual CEC/OESC service-demand calculation, transformer selection, voltage, primary and secondary protection, grounding/bonding, ampacity, manufacturer data and applicable installation rules.",
            color = Color(0xFFB6C4D4), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TxLoadCard(load: TxLoad, onChange: (TxLoad) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10253A))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (load.isService) "Calculated service load" else "Equipment load", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onDelete) { Text("Remove") }
            }
            TxInput("Description", load.name) { onChange(load.copy(name = it)) }
            TxDropDown("Load type", load.type, txEquipmentTypes + txServiceTypes) { type ->
                onChange(load.copy(
                    type = type,
                    demand = if (type in txServiceTypes) "100" else load.demand,
                    continuous = if (type in txServiceTypes) false else load.continuous
                ))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { TxInput("Quantity", load.quantity) { onChange(load.copy(quantity = it)) } }
                Box(Modifier.weight(1f)) { TxInput("Value", load.amount) { onChange(load.copy(amount = it)) } }
            }
            TxDropDown("Load unit", load.unit, txUnits) { onChange(load.copy(unit = it)) }
            TxInput("Power factor (used for kW / W)", load.pf) { onChange(load.copy(pf = it)) }
            if (load.isService) {
                Text("Service load is already calculated: 100% demand; no additional continuous multiplier.",
                    color = Color(0xFFF5B942), style = MaterialTheme.typography.bodySmall)
            } else {
                TxInput("Demand (%)", load.demand) { onChange(load.copy(demand = it)) }
                Row {
                    Checkbox(checked = load.continuous, onCheckedChange = { onChange(load.copy(continuous = it)) })
                    Text("Apply 125% continuous-load design factor", modifier = Modifier.padding(top = 11.dp))
                }
            }
        }
    }
}

@Composable
private fun TxInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun TxDropDown(label: String, selected: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB6C4D4))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("$selected ▾", maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        onChange(option)
                        expanded = false
                    })
                }
            }
        }
    }
}

private fun computeTransformer(phase: String, primary: String, secondary: String, spare: String, loads: List<TxLoad>): String {
    fun validPositive(value: String, title: String): Double {
        val v = value.trim().toDoubleOrNull()
        require(v != null && v.isFinite() && v > 0) { "$title must be greater than zero." }
        return v
    }
    val pv = validPositive(primary, "Primary voltage")
    val sv = validPositive(secondary, "Secondary voltage")
    val reserve = spare.trim().toDoubleOrNull()
    require(reserve != null && reserve.isFinite() && reserve in 0.0..200.0) { "Spare capacity must be 0–200%." }
    require(loads.isNotEmpty()) { "Add at least one load." }
    val factor = if (phase == "3φ") sqrt(3.0) else 1.0
    val standard = if (phase == "3φ") txThreePhaseSizes else txOnePhaseSizes
    var connected = 0.0
    var demanded = 0.0
    var design = 0.0
    var serviceTotal = 0.0
    var serviceRows = 0
    var otherRows = 0
    val detail = StringBuilder()

    for (row in loads) {
        val qty = validPositive(row.quantity, "Quantity for ${row.name}")
        val amount = validPositive(row.amount, "Load value for ${row.name}")
        val pf = validPositive(row.pf, "Power factor for ${row.name}")
        require(pf <= 1.0) { "Power factor cannot exceed 1.00 for ${row.name}." }
        val percentage = if (row.isService) 100.0 else {
            val pct = row.demand.trim().toDoubleOrNull()
            require(pct != null && pct.isFinite() && pct in 0.0..100.0) {
                "Demand percentage must be 0–100% for ${row.name}."
            }
            pct
        }
        val base = when (row.unit) {
            "VA" -> amount * qty / 1000.0
            "kVA" -> amount * qty
            "W" -> amount * qty / (1000.0 * pf)
            "kW" -> amount * qty / pf
            "A" -> factor * sv * amount * qty / 1000.0
            else -> error("Invalid load unit")
        }
        require(base.isFinite()) { "Load too large for ${row.name}." }
        val demandKva = base * percentage / 100.0
        val adjusted = demandKva * (if (!row.isService && row.continuous) 1.25 else 1.0)
        connected += base
        demanded += demandKva
        design += adjusted
        if (row.isService) {
            serviceTotal += demandKva
            serviceRows++
        } else otherRows++
        detail.append(String.format(Locale.CANADA,
            "• %s [%s]: %.2f kVA × %.1f%% = %.2f kVA design%s\n",
            row.name.ifBlank { "Load" }, row.type, base, percentage, adjusted,
            if (row.isService) " (service demand)" else if (row.continuous) " (×1.25 continuous)" else ""))
    }
    val required = design * (1.0 + reserve / 100.0)
    require(required.isFinite()) { "Total load is too large." }
    val selected = standard.firstOrNull { it + 1e-9 >= required }
    val result = StringBuilder()
    result.append(String.format(Locale.CANADA, "CALCULATED LOAD\nConnected: %.2f kVA\nAfter demand: %.2f kVA\nAdjusted design load: %.2f kVA\nWith %.1f%% future spare: %.2f kVA\n\n",
        connected, demanded, design, reserve, required))
    if (selected == null) {
        result.append("No common standard transformer in this list is large enough. Use an engineered arrangement or manufacturer-specific rating.\n")
    } else {
        val next = standard.firstOrNull { it > selected + 1e-9 }
        result.append(String.format(Locale.CANADA,
            "RECOMMENDED: %s kVA (%s)\nPrimary full-load current: %.2f A at %.0f V\nSecondary full-load current: %.2f A at %.0f V\nAdjusted-load utilization: %.1f%%\n",
            String.format(Locale.CANADA, "%.2f", selected).trimEnd('0').trimEnd('.'),
            phase, selected * 1000.0 / (factor * pv), selected * 1000.0 / (factor * sv),
            design / selected * 100.0))
        if (next != null) result.append("Next larger common size: $next kVA\n")
    }
    result.append("\nLOAD BREAKDOWN\n").append(detail)
    if (serviceRows > 0) {
        result.append(String.format(Locale.CANADA,
            "\nSERVICE DEMAND: %d row(s) = %.2f kVA. Service rows bypass demand and 125%% adjustments.\n",
            serviceRows, serviceTotal))
        if (otherRows > 0) result.append("WARNING: Check for duplicate equipment already included in the service demand.\n")
    }
    result.append("\nCEC/OESC CHECKS\n")
    result.append("• Service demand must be calculated separately using the applicable rules; do not enter breaker rating as demand current.\n")
    result.append("• kW and W are converted to kVA using entered power factor. Ampere inputs use secondary voltage and selected phase.\n")
    result.append("• Verify continuous-load treatment, actual standard ratings, transformer OCPD, conductors and installation requirements.\n")
    return result.toString()
}
