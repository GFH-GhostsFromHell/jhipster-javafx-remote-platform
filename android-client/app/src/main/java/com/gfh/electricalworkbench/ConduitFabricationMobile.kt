package com.gfh.electricalworkbench

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import kotlin.math.*

/** Independent geometry and measured tool-specific gain; no assumed shoe gain. */
internal object MobileConduitMath {
    fun gain(stock: Double, first: Double, second: Double): Double {
        require(listOf(stock, first, second).all { it.isFinite() && it > 0 }) { "Measurements must be positive." }
        val g = first + second - stock
        require(g >= 0) { "Negative gain: check the back-of-bend measurement reference." }
        return g
    }
    fun cut(first: Double, second: Double, span: Double, bends: Int, gain: Double): Double {
        require(bends in 1..2 && listOf(first, second, span, gain).all { it.isFinite() }
            && first > 0 && second > 0 && gain >= 0 && (bends == 1 || span > 0)) {
            "Enter valid finished dimensions and measured gain."
        }
        val value = first + second + (if (bends == 2) span else 0.0) - bends * gain
        require(value > 0) { "Calculated stock length must be positive." }
        return value
    }
    fun rack(spacing: Double, angle: Double, start: Double, count: Int, increase: Boolean): List<Double> {
        require(spacing.isFinite() && angle.isFinite() && start.isFinite() &&
            spacing > 0 && angle > 0 && angle < 90 && start >= 0 && count in 2..20) {
            "Use positive spacing, a valid angle, a nonnegative datum and 2–20 conduits."
        }
        val step = spacing * tan(Math.toRadians(angle / 2))
        return List(count) { start + (if (increase) 1 else -1) * it * step }
    }
}

private fun fabMark(x: Double, units: String, precision: String): String {
    if (precision == "Exact") return String.format(Locale.CANADA, "%.3f %s", x, units)
    if (units == "mm") {
        val step = if (precision == "5 mm") 5.0 else 1.0
        return String.format(Locale.CANADA, "%.0f mm", round(x / step) * step)
    }
    val denominator = when (precision) { "1/4 in" -> 4; "1/8 in" -> 8; else -> 16 }
    val total = round(abs(x) * denominator).toLong()
    val whole = total / denominator
    val numerator = (total % denominator).toInt()
    if (numerator == 0) return (if (x < 0) "−" else "") + whole + " in"
    fun gcd(a: Int, b: Int): Int {
        var p = a; var q = b
        while (q != 0) { val t = p % q; p = q; q = t }
        return p
    }
    val g = gcd(numerator, denominator)
    return (if (x < 0) "−" else "") + (if (whole == 0L) "" else whole.toString() + " ") +
        (numerator / g) + "/" + (denominator / g) + " in"
}

@Composable private fun FabChoice(title: String, current: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(current) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}
@Composable private fun FabInput(title: String, current: String, change: (String) -> Unit) {
    OutlinedTextField(value = current, onValueChange = change, label = { Text(title) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
internal fun ConduitFabricationMobile() {
    var mode by remember { mutableStateOf("Gain & cut length") }
    var units by remember { mutableStateOf("mm") }
    var precision by remember { mutableStateOf("1 mm") }
    var arrangement by remember { mutableStateOf("Single 90°") }
    var first by remember { mutableStateOf("450") }
    var second by remember { mutableStateOf("600") }
    var span by remember { mutableStateOf("750") }
    var gain by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("900") }
    var testA by remember { mutableStateOf("375") }
    var testB by remember { mutableStateOf("600") }
    var angle by remember { mutableStateOf("30") }
    var spacing by remember { mutableStateOf("50") }
    var datum by remember { mutableStateOf("600") }
    var count by remember { mutableStateOf("4") }
    var direction by remember { mutableStateOf("Increasing marks") }
    var result by remember { mutableStateOf("Enter your dimensions and calculate.") }
    val context = LocalContext.current

    Text("Conduit fabrication · offline", fontSize = 19.sp)
    FabChoice("Tool", mode, listOf("Gain & cut length", "Parallel rack offsets")) { mode = it; result = "" }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("MEASURE FIRST", color = Color(0xFF91EEE4), fontSize = 13.sp)
            if (mode == "Gain & cut length") {
                Text("1. Cut a test piece and record its original straight length.", fontSize = 13.sp)
                Text("2. Bend a 90° using YOUR shoe; measure both finished legs with the same back-of-bend references.", fontSize = 13.sp)
                Text("3. Gain = finished leg 1 + finished leg 2 − original stock. Use that gain to calculate cut length.", fontSize = 13.sp)
            } else {
                Text("1. Measure centre-to-centre spacing between neighbouring conduit runs.", fontSize = 13.sp)
                Text("2. Select the angle for EACH bend; mark tape zero on every individual pipe.", fontSize = 13.sp)
                Text("3. Enter the first reference mark; both marks on each next pipe shift by the calculated stagger.", fontSize = 13.sp)
            }
            Text("These are measurement conventions, not assumed shoe marks. Replace example values before use.",
                color = Color(0xFFF2C87F), fontSize = 12.sp)
        }
    }
    FabChoice("Units", units, listOf("mm", "in")) { next ->
        if (next != units) {
            val multiplier = if (next == "mm") 25.4 else 1.0 / 25.4
            fun convert(s: String) = s.toDoubleOrNull()?.let { String.format(Locale.ROOT, "%.4f", it * multiplier) } ?: s
            first = convert(first); second = convert(second); span = convert(span); gain = convert(gain)
            stock = convert(stock); testA = convert(testA); testB = convert(testB)
            spacing = convert(spacing); datum = convert(datum)
            units = next; precision = if (next == "mm") "1 mm" else "1/16 in"; result = ""
        }
    }
    FabChoice("Tape precision", precision,
        if (units == "mm") listOf("Exact", "1 mm", "5 mm") else listOf("Exact", "1/16 in", "1/8 in", "1/4 in")
    ) { precision = it }

    if (mode == "Gain & cut length") {
        FabChoice("Bends", arrangement, listOf("Single 90°", "Back-to-back 90s")) { arrangement = it }
        FabInput("First finished leg (" + units + ")", first) { first = it }
        FabInput("Second finished leg (" + units + ")", second) { second = it }
        if (arrangement == "Back-to-back 90s") FabInput("Between backs of bends (" + units + ")", span) { span = it }
        FabInput("Measured gain PER 90° (" + units + ") — required", gain) { gain = it }
        OutlinedButton(onClick = {
            val saved = LocalBenderCalibration.measured(context, "gain", units)
            if (saved == null) result = "No saved measured gain. Open the Bender Calibration tab and save a scrap test."
            else {
                gain = String.format(Locale.ROOT, "%.4f", saved)
                result = "Loaded " + LocalBenderCalibration.description(context) + ". Confirm matching shoe, conduit and bend radius."
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Use saved bender gain") }
        Button(onClick = {
            result = runCatching {
                val bends = if (arrangement == "Single 90°") 1 else 2
                val calibrated = gain.toDoubleOrNull() ?: error("Calibrate shoe gain on scrap first.")
                val cut = MobileConduitMath.cut(first.toDouble(), second.toDouble(),
                    if (bends == 2) span.toDouble() else 0.0, bends, calibrated)
                "STRAIGHT STOCK TO CUT: " + fabMark(cut, units, precision) +
                    "\nGain allowance: " + fabMark(calibrated * bends, units, precision) +
                    "\n\nCut length = sum of finished dimensions − (number of 90s × measured gain)." +
                    "\nUse the separate stub/back-to-back tool for the physical ARROW and STAR marks."
            }.getOrElse { "Check cut: " + it.message }
        }, modifier = Modifier.fillMaxWidth()) { Text("Calculate precut length") }
        HorizontalDivider()
        Text("Measure gain with a scrap 90°", style = MaterialTheme.typography.titleMedium)
        FabInput("Original straight stock (" + units + ")", stock) { stock = it }
        FabInput("Finished first leg (" + units + ")", testA) { testA = it }
        FabInput("Finished second leg (" + units + ")", testB) { testB = it }
        OutlinedButton(onClick = {
            result = runCatching {
                val measured = MobileConduitMath.gain(stock.toDouble(), testA.toDouble(), testB.toDouble())
                gain = String.format(Locale.ROOT, "%.4f", measured)
                "Measured gain: " + fabMark(measured, units, precision) +
                    " per 90°. This has been entered above. Use the same back-of-bend datum for the job."
            }.getOrElse { "Check calibration: " + it.message }
        }, modifier = Modifier.fillMaxWidth()) { Text("Calculate & use measured gain") }
    } else {
        FabChoice("Angle of EACH offset bend", angle, listOf("5", "10", "15", "22.5", "30", "45", "60")) { angle = it }
        FabInput("Adjacent centre spacing (" + units + ")", spacing) { spacing = it }
        FabInput("First pipe mark from datum (" + units + ")", datum) { datum = it }
        FabInput("Number of pipes (2–20)", count) { count = it }
        FabChoice("Direction", direction, listOf("Increasing marks", "Decreasing marks")) { direction = it }
        Button(onClick = {
            result = runCatching {
                val marks = MobileConduitMath.rack(spacing.toDouble(), angle.toDouble(),
                    datum.toDouble(), count.toInt(), direction.startsWith("Increasing"))
                val stagger = spacing.toDouble() * tan(Math.toRadians(angle.toDouble() / 2))
                "Adjacent stagger: " + fabMark(stagger, units, precision) +
                    "\nFirst-to-last stagger: " + fabMark(stagger * (marks.size - 1), units, precision) + "\n\n" +
                    marks.mapIndexed { i, mark -> "Pipe " + (i + 1) + ": " +
                        (if (mark < 0) "BEFORE DATUM — revise layout" else fabMark(mark, units, precision)) }.joinToString("\n") +
                    "\n\nShift BOTH marks on each adjacent conduit. Verify offset plane, shoe radius and installation clearances."
            }.getOrElse { "Check rack: " + it.message }
        }, modifier = Modifier.fillMaxWidth()) { Text("Calculate each pipe mark") }
    }
    HorizontalDivider()
    Text(result, fontSize = 13.sp, lineHeight = 19.sp)
    OutlinedButton(onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Conduit fabrication", result))
    }, enabled = result.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Copy measurements") }
    Text("FIELD CHECK: verify actual shoe, radius, gain, deduct, straight lead, bend plane, springback, conduit OD and applicable code. Test on scrap before a production run.",
        style = MaterialTheme.typography.bodySmall)
}
