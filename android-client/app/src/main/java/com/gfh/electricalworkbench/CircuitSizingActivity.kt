package com.gfh.electricalworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

/** One-screen native offline planning worksheet. Never certifies Code compliance. */
class CircuitSizingActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(
                primary=Color(0xFF22D3EE),background=Color(0xFF06111F),
                surface=Color(0xFF10253A),onSurface=Color(0xFFF1F7FC)
            )) { CircuitSizingScreen() }
        }
    }
}

@Composable
private fun CircuitSizingScreen() {
    var load by remember {mutableStateOf("40")}
    var voltage by remember {mutableStateOf("600")}
    var phases by remember {mutableStateOf("3")}
    var run by remember {mutableStateOf("40")}
    var device by remember {mutableStateOf("50")}
    var multiplier by remember {mutableStateOf("1.25")}
    var description by remember {mutableStateOf("Selected conductor")}
    var table by remember {mutableStateOf("Table 2")}
    var terminal by remember {mutableStateOf("75")}
    var insulation by remember {mutableStateOf("90")}
    var base by remember {mutableStateOf("85")}
    var ambient by remember {mutableStateOf("1.00")}
    var group by remember {mutableStateOf("1.00")}
    var cm by remember {mutableStateOf("66360")}
    var k by remember {mutableStateOf("12.9")}
    var dropLimit by remember {mutableStateOf("3")}
    var showVD by remember {mutableStateOf(false)}
    var result by remember {mutableStateOf("Replace sample inputs with your actual circuit data, then calculate.")}

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Circuit + Conductor Review",style=MaterialTheme.typography.headlineSmall,
            fontWeight=FontWeight.Bold)
        Text("OFFLINE · Enter one circuit; review corrected conductor ampacity, voltage drop, a conservative device screen and maximum one-way run.",
            color=Color(0xFFB6C4D4))
        CircuitInputCard("1 · Circuit") {
            CircuitNumber("Actual load current A",load){load=it}
            CircuitNumber("System voltage V",voltage){voltage=it}
            CircuitSelect("Phases",phases,listOf("1","3")){phases=it}
            CircuitNumber("One-way wire length m",run){run=it}
            CircuitNumber("Entered breaker / fuse A",device){device=it}
            CircuitSelect("General load-sizing multiplier (assumption)",multiplier,
                listOf("1.00","1.25")){multiplier=it}
        }
        CircuitInputCard("2 · Conductor and conditions") {
            Text("Use the equipment TERMINATION-temperature column, not automatically the wire's 90°C insulation column.",
                style=MaterialTheme.typography.bodySmall,color=Color(0xFFFFCF75))
            CircuitNumber("Selected conductor label",description,false){description=it}
            CircuitSelect("Applicable ampacity reference",table,
                listOf("Table 1","Table 2","Table 3","Table 4")){table=it}
            CircuitSelect("Verified termination °C",terminal,
                listOf("60","75","90")){terminal=it}
            CircuitSelect("Insulation °C",insulation,listOf("60","75","90")){insulation=it}
            CircuitNumber("Ampacity from termination column A",base){base=it}
            CircuitNumber("Verified ambient correction factor",ambient){ambient=it}
            CircuitNumber("Verified grouping correction factor",group){group=it}
            OutlinedButton(onClick={showVD=!showVD},modifier=Modifier.fillMaxWidth()) {
                Text(if(showVD)"Hide voltage-drop inputs" else "Edit voltage-drop inputs")
            }
            if(showVD) {
                CircuitNumber("Selected area (circular mils)",cm){cm=it}
                CircuitNumber("K resistance factor (ohm·cmil/ft)",k){k=it}
                CircuitNumber("Maximum entered voltage drop %",dropLimit){dropLimit=it}
            }else{
                Text("VD sample: $cm circular mils, K $k, limit $dropLimit%. Open inputs to verify.",
                    style=MaterialTheme.typography.bodySmall,color=Color(0xFFB6C4D4))
            }
        }
        Button(onClick={
            result=runCatching {
                val inputs=CircuitReviewMath.Input(
                    load.toDouble(),multiplier.toDouble(),voltage.toDouble(),
                    phases.toInt(),run.toDouble(),cm.toDouble(),k.toDouble(),base.toDouble(),
                    terminal.toInt(),insulation.toInt(),ambient.toDouble(),group.toDouble(),
                    device.toDouble(),dropLimit.toDouble()
                )
                val r=CircuitReviewMath.calculate(inputs)
                fun fmt(v:Double)=String.format(Locale.CANADA,"%.2f",v)
                buildString {
                    appendLine(description+" · "+table+" / "+terminal+"°C termination")
                    appendLine()
                    appendLine("1 · LOAD & CONDUCTOR")
                    appendLine("Required sizing current = "+fmt(inputs.loadA)+" × "+
                        fmt(inputs.designMultiplier)+" = "+fmt(r.designCurrentA)+" A")
                    appendLine("Corrected ampacity = "+fmt(inputs.terminalColumnAmpacityA)+" × "+
                        fmt(inputs.ambientFactor)+" × "+fmt(inputs.groupingFactor)+" = "+
                        fmt(r.adjustedAmpacityA)+" A")
                    appendLine("Ampacity arithmetic: "+if(r.ampacityArithmeticFits)"WITHIN ENTERED LIMIT" else "FAIL")
                    appendLine("Margin: "+fmt(r.conductorMarginA)+" A")
                    appendLine()
                    appendLine("2 · VOLTAGE DROP")
                    appendLine("Estimated drop: "+fmt(r.voltageDropV)+" V ("+fmt(r.voltageDropPercent)+"%)")
                    appendLine("Entered limit: "+fmt(r.allowableDropV)+" V ("+fmt(inputs.maxVoltageDropPercent)+"%)")
                    appendLine("VD screen: "+if(r.voltageDropArithmeticFits)"WITHIN ENTERED LIMIT" else "EXCEEDS LIMIT")
                    appendLine("Minimum area for entered VD target: "+fmt(r.minimumCircularMilsForDrop)+" circular mils")
                    appendLine("Maximum one-way length for selected area: "+fmt(r.maximumOneWayLengthM)+" m")
                    appendLine()
                    appendLine("3 · GENERAL-PURPOSE OCPD ARITHMETIC")
                    appendLine("Selected device: "+fmt(inputs.selectedOcpdA)+" A")
                    appendLine(if(r.protectionScreenFits)"Within conservative entered bounds" else "Review device/load/ampacity")
                    appendLine()
                    appendLine("NOT CODE VERIFIED. Confirm CEC/OESC Table 1–4 and 5A/5C data, terminal marks, cable method, neutral counts, actual OCPD and special load provisions. No legal conductor size or device is automatically selected. K-factor VD is approximate; also check upstream feeder drop.")
                }.trim()
            }.getOrElse{ "Check inputs: "+(it.message?:"Enter finite positive measurements.") }
        },modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){
            Text("Review circuit")
        }
        CircuitInputCard("3 · Worked result"){
            SelectionContainer {Text(result,style=MaterialTheme.typography.bodyMedium)}
        }
        Text("Sample numbers are placeholders. Use verified equipment and conductor data. Special motor, transformer or other exceptions need a separate Code assessment.",
            color=Color(0xFFFFCF75),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CircuitInputCard(title:String,content:@Composable ColumnScope.()->Unit) {
    Card(modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(
        containerColor=Color(0xFF10253A))) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CircuitNumber(label:String,value:String,numeric:Boolean=true,update:(String)->Unit){
    OutlinedTextField(value=value,onValueChange=update,label={Text(label)},
        modifier=Modifier.fillMaxWidth(),singleLine=true,
        keyboardOptions=KeyboardOptions(
            keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text))
}
@Composable
private fun CircuitSelect(label:String,value:String,options:List<String>,update:(String)->Unit){
    var expanded by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(label,style=MaterialTheme.typography.labelMedium,color=Color(0xFFB6C4D4))
        Box(Modifier.fillMaxWidth()){
            OutlinedButton(onClick={expanded=true},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(value)}
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                options.forEach{choice->
                    DropdownMenuItem(text={Text(choice)},onClick={expanded=false;update(choice)})
                }
            }
        }
    }
}
