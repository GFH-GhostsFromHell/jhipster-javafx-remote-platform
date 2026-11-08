package com.gfh.electricalworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject

/** One-screen native offline planning worksheet. Never certifies Code compliance. */
class CircuitSizingActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(
                primary=Color(0xFF22D3EE),background=Color(0xFF06111F),
                surface=Color(0xFF10253A),onSurface=Color(0xFFF1F7FC)
            )) { CircuitSizingScreen(intent.getStringExtra("accountUsername").orEmpty()) }
        }
    }
}

@Composable
private fun CircuitSizingScreen(username:String) {
    val context=LocalContext.current
    var pdfClient by remember {mutableStateOf("")}
    var pdfSite by remember {mutableStateOf("")}
    var pdfReviewer by remember {mutableStateOf("")}
    var pdfStatus by remember {mutableStateOf("Select a saved revision, then export its complete calculation PDF.")}
    var selectedRevision by remember {mutableStateOf<JSONObject?>(null)}
    var pendingPdf by remember {mutableStateOf<ByteArray?>(null)}
    val savePdf=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ){ uri ->
        val bytes=pendingPdf
        if(uri!=null&&bytes!=null){
            pdfStatus=runCatching {
                val out=context.contentResolver.openOutputStream(uri)
                    ?: error("Unable to open selected destination.")
                out.use{it.write(bytes)}
                "PDF saved to selected location. Preliminary report; not Code verified."
            }.getOrElse { "PDF could not be saved: "+(it.message ?: "Write failed.") }
        } else if(uri==null) {
            pdfStatus="PDF export cancelled."
        }
        pendingPdf=null
    }
    var project by remember {mutableStateOf("Field project")}
    var worksheetTitle by remember {mutableStateOf("Circuit 1")}
    var fieldNotes by remember {mutableStateOf("")}
    var saveStatus by remember {mutableStateOf("Each save creates a dated revision. Project records sync through your signed-in account when online.")}
    var savedRows by remember(username) {mutableStateOf(
        if(username.isBlank()) emptyList() else CircuitWorksheetStore.records(context,username)
    )}
    var lastInputs by remember {mutableStateOf<CircuitReviewMath.Input?>(null)}
    var lastResults by remember {mutableStateOf<CircuitReviewMath.Result?>(null)}
    var lastDescription by remember {mutableStateOf("")}
    var lastTable by remember {mutableStateOf("")}
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
    fun currentInputs()=CircuitReviewMath.Input(
        load.toDouble(),multiplier.toDouble(),voltage.toDouble(),
        phases.toInt(),run.toDouble(),cm.toDouble(),k.toDouble(),base.toDouble(),
        terminal.toInt(),insulation.toInt(),ambient.toDouble(),group.toDouble(),
        device.toDouble(),dropLimit.toDouble()
    )

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Circuit + Conductor Review",style=MaterialTheme.typography.headlineSmall,
            fontWeight=FontWeight.Bold)
        Text("OFFLINE · Enter one circuit; review corrected conductor ampacity, voltage drop, a conservative device screen and maximum one-way run.",
            color=Color(0xFFB6C4D4))
        CircuitInputCard("1 · Circuit") {
            Text("Quick system presets · only voltage and phase change",
                style=MaterialTheme.typography.bodySmall,color=Color(0xFFB6C4D4))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf(
                    Triple("120 V · 1φ","120","1"),
                    Triple("208 V · 3φ","208","3"),
                    Triple("347 V · 1φ","347","1"),
                    Triple("600 V · 3φ","600","3")
                ).forEach { (label,volts,phase)->
                    OutlinedButton(onClick={
                        voltage=volts;phases=phase
                        lastInputs=null;lastResults=null
                        result="System selected. Confirm actual load, conductor data and limits; calculate again."
                    },modifier=Modifier.heightIn(min=48.dp)){Text(label)}
                }
            }
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
            lastInputs=null;lastResults=null
            result=runCatching {
                val inputs=currentInputs()
                val r=CircuitReviewMath.calculate(inputs)
                lastInputs=inputs;lastResults=r;lastDescription=description;lastTable=table
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
        CircuitInputCard("4 · Project worksheet archive") {
            Text("Saved revisions include full entered measurements, numeric results, code assumptions, timestamps and the worked explanation. Each save is a new revision, never an overwrite.",
                style=MaterialTheme.typography.bodySmall,color=Color(0xFFB6C4D4))
            CircuitNumber("Project",project,false){project=it}
            CircuitNumber("Worksheet title",worksheetTitle,false){worksheetTitle=it}
            CircuitNumber("Field notes / verification outstanding",fieldNotes,false){fieldNotes=it}
            Button(onClick={
                saveStatus=runCatching {
                    require(username.isNotBlank()){"Sign in before saving account-linked worksheets."}
                    val inputs=currentInputs()
                    val output=lastResults
                    require(lastInputs==inputs && output!=null &&
                        lastDescription==description && lastTable==table) {
                        "Calculate or review the current fields before saving; an earlier result may be stale."
                    }
                    val data=JSONObject().apply {
                        put("loadA",inputs.loadA);put("designMultiplier",inputs.designMultiplier)
                        put("voltageV",inputs.voltageV);put("phases",inputs.phases)
                        put("oneWayLengthM",inputs.oneWayLengthM);put("circularMils",inputs.circularMils)
                        put("kOhmCircularMilPerFoot",inputs.kOhmCircularMilPerFoot)
                        put("terminalColumnAmpacityA",inputs.terminalColumnAmpacityA)
                        put("terminalC",inputs.terminalC);put("insulationC",inputs.insulationC)
                        put("ambientFactor",inputs.ambientFactor);put("groupingFactor",inputs.groupingFactor)
                        put("selectedOcpdA",inputs.selectedOcpdA)
                        put("maxVoltageDropPercent",inputs.maxVoltageDropPercent)
                        put("conductorDescription",description);put("tableReference",table)
                    }
                    val numeric=JSONObject().apply {
                        put("designCurrentA",output.designCurrentA)
                        put("adjustedAmpacityA",output.adjustedAmpacityA)
                        put("conductorMarginA",output.conductorMarginA)
                        put("voltageDropV",output.voltageDropV)
                        put("voltageDropPercent",output.voltageDropPercent)
                        put("allowableDropV",output.allowableDropV)
                        put("minimumCircularMilsForDrop",output.minimumCircularMilsForDrop)
                        put("maximumOneWayLengthM",output.maximumOneWayLengthM)
                        put("ampacityArithmeticFits",output.ampacityArithmeticFits)
                        put("voltageDropArithmeticFits",output.voltageDropArithmeticFits)
                        put("protectionScreenFits",output.protectionScreenFits)
                        put("codeComplianceVerified",false)
                    }
                    val row=CircuitWorksheetStore.save(context,username,project,worksheetTitle,
                        fieldNotes,data,numeric,result)
                    savedRows=CircuitWorksheetStore.records(context,username)
                    "Saved "+row.optString("project")+" · "+row.optString("title")+
                        " · rev "+row.optInt("revision")+" (local first; sync on connection)."
                }.getOrElse{ "Not saved: "+(it.message?:"Storage unavailable.") }
            },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Save new worksheet revision")
            }
            Text(saveStatus,style=MaterialTheme.typography.bodySmall,color=Color(0xFFFFCF75))
            OutlinedButton(onClick={
                if(username.isNotBlank()) {
                    MobileAccountSync.schedule(context.applicationContext)
                    savedRows=CircuitWorksheetStore.records(context,username)
                    saveStatus="Showing current locally stored revisions. Account sync requested; tap Refresh after reconnection. " +
                        MobileAccountSync.status
                }else{
                    saveStatus="Sign in to refresh account-linked project revisions."
                }
            },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Refresh saved projects / request sync")
            }
            Text("Saved revisions for this project",style=MaterialTheme.typography.titleSmall)
            savedRows.filter{it.optString("project")==project.trim()}.take(15).forEach { row->
                OutlinedButton(
                    onClick={
                        val data=row.optJSONObject("inputs")
                        if(data!=null){
                            load=data.optString("loadA",load);voltage=data.optString("voltageV",voltage)
                            phases=data.optString("phases",phases);run=data.optString("oneWayLengthM",run)
                            device=data.optString("selectedOcpdA",device)
                            multiplier=data.optString("designMultiplier",multiplier)
                            description=data.optString("conductorDescription",description)
                            table=data.optString("tableReference",table)
                            terminal=data.optString("terminalC",terminal)
                            insulation=data.optString("insulationC",insulation)
                            base=data.optString("terminalColumnAmpacityA",base)
                            ambient=data.optString("ambientFactor",ambient)
                            group=data.optString("groupingFactor",group)
                            cm=data.optString("circularMils",cm)
                            k=data.optString("kOhmCircularMilPerFoot",k)
                            dropLimit=data.optString("maxVoltageDropPercent",dropLimit)
                            selectedRevision=row
                            worksheetTitle=row.optString("title",worksheetTitle)
                            fieldNotes=row.optJSONObject("assumptions")?.optString("notes").orEmpty()
                            result=row.optString("result")+"\\n\\nLoaded archived revision "+
                                row.optInt("revision")+". Recalculate before saving changes."
                            lastInputs=null;lastResults=null
                            saveStatus="Opened "+row.optString("title")+" rev "+row.optInt("revision")+
                                " from "+row.optString("saved")+"."
                        }
                    },
                    modifier=Modifier.fillMaxWidth()
                ) {
                    Text(row.optString("title")+" · rev "+row.optInt("revision")+" · "+
                        row.optString("saved").take(10))
                }
            }
            Text("PDF calculation report",style=MaterialTheme.typography.titleSmall)
            Text("Exports a saved revision, not unsaved inputs. The PDF includes the record ID, source assumptions, worked results and reviewer/signature lines.",
                style=MaterialTheme.typography.bodySmall,color=Color(0xFFB6C4D4))
            CircuitNumber("Client / employer (PDF)",pdfClient,false){pdfClient=it}
            CircuitNumber("Site / location (PDF)",pdfSite,false){pdfSite=it}
            CircuitNumber("Reviewer (optional)",pdfReviewer,false){pdfReviewer=it}
            Button(
                enabled=selectedRevision!=null,
                onClick={
                    try{
                        val row=requireNotNull(selectedRevision)
                        pendingPdf=CircuitWorksheetPdf.render(row,pdfClient,pdfSite,pdfReviewer)
                        val title=(row.optString("project")+"-"+
                            row.optString("title")+"-rev"+row.optInt("revision"))
                            .replace(Regex("[^A-Za-z0-9._-]"),"-")
                        savePdf.launch(title.take(90)+".pdf")
                    }catch(e:Exception){
                        pdfStatus="PDF creation failed: "+(e.message?:"Invalid saved record")
                    }
                },
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ){Text("Export selected revision PDF")}
            Text(pdfStatus,style=MaterialTheme.typography.bodySmall,
                color=Color(0xFFFFCF75))
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
