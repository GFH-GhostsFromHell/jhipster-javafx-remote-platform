package com.gfh.electricalworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.*

/** Native, local-first field sizing worksheets (no WebView or network dependency). */
class FieldSizingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF22D3EE),background=Color(0xFF06111F),surface=Color(0xFF10253A))) {
                var tab by remember { mutableStateOf(intent.getStringExtra("toolTab") ?: "Pull box") }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Electrical Workbench · Field Tools",style=MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={tab="Pull box"}){Text("Pull box")}
                        OutlinedButton(onClick={tab="Bender"}){Text("Conduit bender")}
                        OutlinedButton(onClick={tab="Fabrication"}){Text("Gain & racks")}
                    }
                    OutlinedButton(onClick={tab="Calibration"},modifier=Modifier.fillMaxWidth()) { Text("Bender Calibration · offline") }
                    OutlinedButton(onClick={tab="Troubleshooting"},modifier=Modifier.fillMaxWidth()) { Text("Industrial Troubleshooting · offline knowledge base") }
                    when(tab) {
                        "Pull box" -> PullBoxMobile()
                        "Fabrication" -> ConduitFabricationMobile()
                         "Calibration" -> BenderCalibrationMobile()
                         "Troubleshooting" -> TroubleshootingKnowledgeMobile()
                        else -> BenderMobile()
                    }
                    Text("Advisory worksheets. Verify the applicable CEC/OESC edition, manufacturer instructions, actual conduit and cable details, and AHJ requirements.",color=Color(0xFFADBDCD),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
private data class PipeEntry(val wall:String,val size:Int)
private val sizes=listOf(16,21,27,35,41,53,63,78,91,103,129,155)
private val walls=listOf("Left","Right","Top","Bottom","Back")
private val layouts=listOf("Straight","Angle","U","Mixed","Back wall","Splice review")
private fun defaultPipes(t:String):List<PipeEntry> = when(t) {
    "Straight"->listOf(PipeEntry("Left",53),PipeEntry("Right",53))
    "Angle"->listOf(PipeEntry("Left",53),PipeEntry("Bottom",53))
    "U"->listOf(PipeEntry("Bottom",53),PipeEntry("Bottom",53))
    "Mixed"->listOf(PipeEntry("Left",53),PipeEntry("Right",53),PipeEntry("Bottom",35))
    "Back wall"->listOf(PipeEntry("Back",53))
    else->listOf(PipeEntry("Left",35),PipeEntry("Top",35))
}
@Composable private fun FieldDropdown(label:String,value:String,choices:List<String>,change:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label,style=MaterialTheme.typography.labelMedium,color=Color(0xFFB1C7DA))
        Box {
            OutlinedButton(onClick={expanded=true}){Text(value+" ▾")}
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                choices.forEach { choice ->
                    DropdownMenuItem(text={Text(choice)},onClick={expanded=false;change(choice)})
                }
            }
        }
    }
}
@Composable private fun NumberInput(label:String,value:String,change:(String)->Unit) {
    OutlinedTextField(value=value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true)
}
@Composable private fun PullBoxMobile() {
    var layout by remember { mutableStateOf("Straight") }
    var wall by remember { mutableStateOf("Left") }
    var trade by remember { mutableStateOf("53") }
    var od by remember { mutableStateOf("20") }
    var units by remember { mutableStateOf("mm") }
    val entries=remember { mutableStateListOf<PipeEntry>().also{it.addAll(defaultPipes("Straight"))} }
    var results by remember { mutableStateOf("Choose layout and calculate.") }
    Text("Pull-box dimensions · CEC 2024 Rule 12-3036",style=MaterialTheme.typography.titleLarge)
    Text("Applies to boxes with insulated No. 4 AWG or larger conductors. Enter Canadian conduit TRADE sizes in millimetres.")
    FieldDropdown("Pull arrangement",layout,layouts) { layout=it;entries.clear();entries.addAll(defaultPipes(it));results="Calculate new arrangement." }
    FieldDropdown("Results",units,listOf("mm","in")){units=it}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FieldDropdown("Entry wall",wall,walls){wall=it}
        FieldDropdown("Trade size",trade,sizes.map{it.toString()}){trade=it}
    }
    Button(onClick={entries.add(PipeEntry(wall,trade.toInt()))}){Text("Add entry")}
    entries.forEachIndexed { i,p ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(p.wall+" wall · "+p.size+" mm trade",Modifier.weight(1f).padding(top=12.dp))
            OutlinedButton(onClick={entries.removeAt(i)}){Text("Remove")}
        }
    }
    NumberInput("Largest insulated conductor OD in mm (back-wall entry)",od){od=it}
    Button(onClick={results=runCatching{pullBoxMath(entries.toList(),layout,units,od.toDouble())}.getOrElse{"Check inputs: "+it.message}},modifier=Modifier.fillMaxWidth()){Text("Calculate box dimensions")}
    Text(results,style=MaterialTheme.typography.bodyMedium)
    Text("Nearest-edge spacing between entries containing the SAME conductor: at least 6 × the larger conduit trade size. Confirm fitting room, bend radius, access and splice volume independently.",color=Color(0xFFFFD68A))
}
private fun mmString(x:Double,unit:String):String {
    if(!x.isFinite())return "Not determined"
    return if(unit=="in")String.format(Locale.CANADA,"%.2f in (%.0f mm)",x/25.4,x)
           else String.format(Locale.CANADA,"%.0f mm (%.2f in)",x,x/25.4)
}
private fun pullBoxMath(entries:List<PipeEntry>,type:String,u:String,od:Double):String {
    require(entries.isNotEmpty()){"Add raceways."}
    val group=walls.associateWith{key->entries.filter{it.wall==key}.map{it.size}}
    var x=Double.NaN;var y=Double.NaN;var z=Double.NaN
    val explanations=mutableListOf<String>()
    fun greater(old:Double,n:Double):Double=max(if(old.isNaN())0.0 else old,n)
    if(type=="Straight"||type=="Mixed"){
        val l=group.getValue("Left");val r=group.getValue("Right")
        if(l.isNotEmpty()&&r.isNotEmpty()){
            val biggest=(l+r).max();x=greater(x,8.0*biggest)
            explanations.add("Left↔right straight: 8 × "+biggest+" = "+mmString(8.0*biggest,u))
        }
        val top=group.getValue("Top");val bottom=group.getValue("Bottom")
        if(top.isNotEmpty()&&bottom.isNotEmpty()){
            val biggest=(top+bottom).max();y=greater(y,8.0*biggest)
            explanations.add("Top↔bottom straight: 8 × "+biggest+" = "+mmString(8.0*biggest,u))
        }
    }
    if(type in listOf("Angle","U","Mixed","Splice review")){
        listOf("Left","Right","Top","Bottom").forEach { wall ->
            val a=group.getValue(wall)
            if(a.isNotEmpty()){
                val largest=a.max();val other=a.sum()-largest;val v=6.0*largest+other
                if(wall=="Left"||wall=="Right")x=greater(x,v)else y=greater(y,v)
                explanations.add(wall+" wall: 6 × "+largest+" + "+other+" = "+mmString(v,u))
            }
        }
        if(type=="U"){
            val pairWall=walls.firstOrNull{group.getValue(it).size>=2 && it!="Back"}
            if(pairWall!=null){
                val p=group.getValue(pairWall).take(2)
                val span=p.sum()+6.0*p.max()
                if(pairWall=="Top"||pairWall=="Bottom")x=greater(x,span)else y=greater(y,span)
                explanations.add("U-pair opening span to maintain nearest-edge separation: "+mmString(span,u)+" (before edge/fitting clearance).")
            }
        }
        explanations.add("Same-conductor entry/exit nearest-edge separation: 6 × larger trade size.")
    }
    if(type=="Back wall"||group.getValue("Back").isNotEmpty()){
        require(group.getValue("Back").isNotEmpty()){"Add a Back-wall conduit."}
        require(od.isFinite()&&od>0){"Specify actual insulated conductor OD."}
        val big=group.getValue("Back").max();z=big+6*od
        explanations.add("Back wall → front removable cover: "+big+" + 6 × "+od+" = "+mmString(z,u))
    }
    if(type=="Splice review")explanations.add("Splices: check Rule 12-3034, usable volume, connectors and conductor bending; this is only a geometry screen.")
    return "MINIMUM INSIDE DIMENSIONS\nLeft↔right: "+mmString(x,u)+"\nTop↔bottom: "+mmString(y,u)+
        "\nBack→cover: "+mmString(z,u)+"\n\n"+explanations.joinToString("\n")+
        "\n\nNot determined does not mean 0. Verify physical fit and installation requirements."
}
@Composable private fun BendHookFacingCards(bend: String, angle: String, saddleMethod: String) {
    val verifiedSaddle = saddleMethod.startsWith("Traditional") && (angle == "30" || angle == "45")
    // LEFT / RIGHT are fixed to the straight-stock tape zero, not the user's walking position.
    val cards: List<Triple<String, String, String>> = when (bend) {
        "Offset", "Rolling offset" -> listOf(
            Triple("MARK A · ARROW", "← HOOK FACES LEFT", "Away from mark B; toward the tape-zero end."),
            Triple("MARK B · ARROW", "← HOOK FACES LEFT", "Klein-style floor-to-air return: flip bender and rotate conduit in cradle; preserve the original bending plane.")
        )
        "90° stub" -> listOf(
            Triple("MARK A · ARROW", "← HOOK FACES LEFT", "Toward the short stub / original measurement end.")
        )
        "Back-to-back 90s" -> listOf(
            Triple("MARK A · ARROW", "← HOOK FACES LEFT", "First 90°: hook toward the original stub end."),
            Triple("MARK B · STAR (RELATIVE)", "HOOK FACES RIGHT →", "Second 90°: hook toward remaining free end. Measure B from BACK of completed first 90°, not from straight-stock zero.")
        )
        "Kicked 90°" -> listOf(
            Triple("MARK A · ARROW", "← HOOK FACES LEFT", "First 90°: hook toward the stub end."),
            Triple("KICK MARK · MANUAL", "? VERIFY HOOK DIRECTION", "Choose a physical kick mark and tool direction from the shoe and job layout, not rise alone.")
        )
        "Three-point saddle" -> listOf(
            Triple("MARK B · CENTRE NOTCH · FIRST", "? VERIFY HOOK DIRECTION", "Align the actual centre notch and check the manufacturer's shoe orientation."),
            Triple("MARK A · OUTER ARROW · SECOND", if(verifiedSaddle) "HOOK FACES RIGHT →" else "? VERIFY HOOK DIRECTION", "Outside return: toward centre mark B only for the documented Klein-style method."),
            Triple("MARK C · OUTER ARROW · THIRD", if(verifiedSaddle) "← HOOK FACES LEFT" else "? VERIFY HOOK DIRECTION", "Outside return: toward centre mark B only for the documented Klein-style method.")
        )
        "Four-point saddle" -> listOf("A", "B", "C", "D").map { mark ->
            Triple("MARK $mark · GEOMETRY ONLY", "? VERIFY HOOK DIRECTION", "Physical arrow placement and hook facing require a tool-specific four-point layout.")
        }
        else -> emptyList()
    }
    Text("HOOK FACING · EVERY BEND MARK", style = MaterialTheme.typography.titleMedium)
    Text("LEFT ← is the original tape-zero end of straight conduit. RIGHT → is the far end. Direction is separate from flipping the tool or rolling the conduit.", style = MaterialTheme.typography.bodySmall)
    cards.forEach { (mark, direction, note) ->
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(mark, style = MaterialTheme.typography.labelLarge)
                Text(direction, style = MaterialTheme.typography.titleLarge)
                Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
private val bends=listOf("Offset","Rolling offset","90° stub","Three-point saddle","Four-point saddle","Back-to-back 90s","Kicked 90°")
@Composable private fun BenderMobile() {
    val context=LocalContext.current
    var bend by remember{mutableStateOf("Offset")}
    var units by remember{mutableStateOf("mm")}
    var angle by remember{mutableStateOf("30")}
    var rise by remember{mutableStateOf("150")}
    var roll by remember{mutableStateOf("100")}
    var distance by remember{mutableStateOf("600")}
    var stub by remember{mutableStateOf("500")}
    var takeup by remember{mutableStateOf("127")}
    var width by remember{mutableStateOf("200")}
    var centre by remember{mutableStateOf("600")}
    var minimumShoeGap by remember { mutableStateOf("") }
    var result by remember{mutableStateOf("Select a bend and calculate marks.") }
    var offsetReference by remember { mutableStateOf("Near bend critical") }
    var saddleMethod by remember { mutableStateOf("Traditional notch & outer arrow") }
    var precision by remember { mutableStateOf("1 mm") }
    Text("Conduit bending · field guide",style=MaterialTheme.typography.titleLarge)
    Text("Uses geometric centreline dimensions. Always confirm shoe deduct, arrow/star/saddle-notch alignment and springback.")
    FieldDropdown("Bend",bend,bends){bend=it}
    FieldDropdown("Input units",units,listOf("mm","in")){next->
        if(next!=units){
            val factor=if(next=="in")1/25.4 else 25.4
            fun cv(s:String)=s.toDoubleOrNull()?.let{String.format(Locale.ROOT,"%.3f",it*factor)}?:s
            rise=cv(rise);roll=cv(roll);distance=cv(distance);stub=cv(stub);takeup=cv(takeup);width=cv(width);centre=cv(centre);minimumShoeGap=cv(minimumShoeGap);units=next
            precision=if(next=="in")"1/16 in" else "1 mm"
        }
    }
    FieldDropdown("Tape precision",precision,if(units=="mm")listOf("Exact","1 mm","5 mm") else listOf("Exact","1/16 in","1/8 in","1/4 in")){precision=it}
    if(bend!="90° stub"&&bend!="Back-to-back 90s")FieldDropdown("Bend angle",angle,listOf("10","15","22.5","30","45","60")){angle=it}
    if(bend in listOf("Offset","Rolling offset"))FieldDropdown("Critical obstruction reference",offsetReference,listOf("Near bend critical","Far bend critical")){offsetReference=it}
    if(bend=="Three-point saddle")FieldDropdown("Saddle marking method",saddleMethod,listOf("Traditional notch & outer arrow","Geometric bend centres ONLY")){saddleMethod=it}
    if(bend in listOf("Offset","Rolling offset","Three-point saddle","Four-point saddle","Kicked 90°"))NumberInput("Rise/height ("+units+")",rise){rise=it}
    if(bend=="Rolling offset")NumberInput("Horizontal roll ("+units+")",roll){roll=it}
    if(bend in listOf("Offset","Rolling offset","Four-point saddle"))NumberInput("Reference / obstacle distance ("+units+")",distance){distance=it}
    if(bend in listOf("90° stub","Back-to-back 90s","Kicked 90°")){
        NumberInput("Desired stub ("+units+")",stub){stub=it}
        NumberInput("Take-up / deduct from your bender ("+units+")",takeup){takeup=it}
         OutlinedButton(onClick={
             val saved=LocalBenderCalibration.measured(context,"deduct",units)
             if(saved==null) result="No saved shoe deduct. Open Bender Calibration, measure a 90° test stub and save it."
             else {takeup=String.format(Locale.ROOT,"%.4f",saved);result="Loaded "+LocalBenderCalibration.description(context)+". Confirm this is the same physical bender and conduit."}
         },modifier=Modifier.fillMaxWidth()) { Text("Use saved bender calibration") }
        Text("Common hand-bender starting points: 1/2 EMT 5 in, 3/4 EMT 6 in, 1 EMT 8 in. The actual shoe stamp controls.",style=MaterialTheme.typography.bodySmall)
    }
    if(bend=="Three-point saddle")NumberInput("Obstruction centre from datum ("+units+")",centre){centre=it}
    if(bend in listOf("Four-point saddle","Back-to-back 90s"))NumberInput("Obstacle width / back-to-back distance ("+units+")",width){width=it}
    Text("10 ft EMT stock limit: "+if(units=="mm")"3,048 mm" else "120 in",color=Color(0xFF91E5D5))
    if(bend in listOf("Offset","Rolling offset","Three-point saddle","Four-point saddle")) {
        NumberInput("Minimum same-datum mark spacing for YOUR shoe (optional, "+units+")",minimumShoeGap){minimumShoeGap=it}
        Text("Leave blank when unknown: shoe clearance will be marked NOT VERIFIED, not assumed safe.",style=MaterialTheme.typography.bodySmall)
    }
    Button(onClick={
        result=runCatching{
            val gap=if(minimumShoeGap.isBlank())null
                    else minimumShoeGap.toDoubleOrNull()?:error("Enter a valid shoe gap, or leave blank.")
            bendMath(bend,units,angle.toDouble(),rise.toDouble(),roll.toDouble(),distance.toDouble(),stub.toDouble(),takeup.toDouble(),width.toDouble(),centre.toDouble(),offsetReference,saddleMethod,precision,gap)
        }
            .getOrElse{"Check inputs: "+it.message}
    },modifier=Modifier.fillMaxWidth()){Text("Calculate marks & instructions")}
    Text(result,style=MaterialTheme.typography.bodyMedium)
    BendHookFacingCards(bend,angle,saddleMethod)
    Text("Field check: use the correct-size shoe and firm foot pressure, keep bend planes aligned, check springback and avoid conduit flattening. Test a piece before cutting to final length.",color=Color(0xFFFFD68A))
}
private fun bendMath(type:String,u:String,angle:Double,rise:Double,roll:Double,dist:Double,stub:Double,takeup:Double,width:Double,centre:Double,offsetReference:String,saddleMethod:String,precision:String,minShoeGap:Double?):String {
    require(angle.isFinite()&&angle>0&&angle<90){"Angle must be between 0° and 90°."}
    require(listOf(rise,roll,dist,stub,takeup,width,centre).all{it.isFinite()&&it>=0}){"Measurements must be finite and nonnegative."}
    val radians=Math.toRadians(angle);val mult=1/sin(radians)
    val stock=if(u=="mm")3048.0 else 120.0
    require(minShoeGap==null||(minShoeGap.isFinite()&&minShoeGap>=0)){"Minimum shoe spacing must be zero or greater."}
    fun f(x:Double):String=mobileBendMeasure(x,u,precision)
    fun checkMarks(vararg marks:Double) {
        require(marks.all { it.isFinite()&&it>=0&&it<=stock+0.000001 }) {
            "CALCULATION NOT POSSIBLE on one 10 ft EMT: at least one mark lies outside "+f(stock)+" of stock."
        }
        if(minShoeGap!=null&&minShoeGap>0) {
            val sorted=marks.sorted()
            for(i in 1 until sorted.size) require(sorted[i]-sorted[i-1]+0.000001>=minShoeGap) {
                "BENDS TOO CLOSE: "+f(sorted[i]-sorted[i-1])+" between marks is less than your shoe's "+f(minShoeGap)+" minimum."
            }
        }
    }
    val output=when(type) {
        "Offset","Rolling offset"->{
            require(rise>0&&dist>0&&roll>=0){"Rise and reference must be positive."}
            val h=if(type=="Rolling offset")hypot(rise,roll)else rise
            val spacing=h*mult;val shrink=h*tan(radians/2)
            val a=if(offsetReference.startsWith("Far"))dist+shrink-spacing else dist+shrink
            val b=a+spacing
            require(a>=0){"Lead is too short for the calculated mark."}
            checkMarks(a,b)
            (if(type=="Rolling offset")"True offset: "+f(h)+"\nRotate plane from vertical: "+String.format(Locale.CANADA,"%.1f",Math.toDegrees(atan2(roll,rise)))+"°\n" else "")+
            "Critical reference: "+offsetReference+"\nMultiplier: "+String.format(Locale.CANADA,"%.3f",mult)+"\nSpacing: "+f(spacing)+"\nEstimated geometric shrink: "+f(shrink)+
            "\nA near mark: "+f(a)+"\nB far mark: "+f(b)+"\n\n"+
            "HOOK FACING BY MARK:\nA · ARROW · ← HOOK toward tape zero.\nB · ARROW · ← HOOK after bender flip / cradle rotation (Klein method).\n\n1. Mark the reference end, A and B.\n2. Align shoe ARROW to A; bend "+angle+"°.\n3. Flip bender and rotate conduit 180° IN ITS CRADLE as the shoe specifies; align ARROW at B and keep the bend plane unchanged.\n4. Check both ends are parallel; verify rise, roll and obstacle clearance."
        }
        "90° stub","Back-to-back 90s","Kicked 90°"->{
            require(stub>takeup&&takeup>0){"Desired stub must exceed the actual shoe deduct."}
            checkMarks(stub-takeup)
            require(stub<=stock){"CALCULATION NOT POSSIBLE: desired stub "+f(stub)+" is longer than the 10 ft stock "+f(stock)+"."}
            var steps="First arrow mark: "+f(stub-takeup)+"\nMARK A · ARROW · ← HOOK toward original datum / short end.\n1. Measure from square reference end to arrow mark.\n2. Align bender ARROW and bend a 90° with steady foot pressure.\n3. Check finished stub and springback."
            if(type=="Back-to-back 90s"){
                require(width>0){"Back-to-back distance required."}
                require(width<=stock){"CALCULATION NOT POSSIBLE: second 90 relative reference exceeds 10 ft stock."}
                steps+="\nMARK B · STAR · HOOK FACES RIGHT → toward remaining free end (Klein method).\nBack of first 90° to second STAR reference: "+f(width)+" (adjust for shoe if instructed).\n4. Measure from the back of the first completed 90°; use STAR/back-of-90 reference for second bend, not the arrow deduct.\n5. Bend in one plane, verify leg separation."
            }else if(type=="Kicked 90°"){
                require(rise>0){"Kick rise required."}
                steps+="\nKICK MARK: ? VERIFY HOOK FACING and shoe reference; no physical kick mark calculated.\nKick diagonal travel: "+f(rise*mult)+"\nProjected run: "+f(rise/tan(radians))+"\n4. Choose actual kick location along straight leg and mark according to your shoe.\n5. Make "+angle+"° kick in required plane; check rise. Location is NOT a universal second mark."
            }
            steps
        }
        "Three-point saddle"->{
            require(rise>0&&centre>0){"Height and centre required."}
            val side=angle/2
            val geometric=1/sin(Math.toRadians(side))
            val traditional=saddleMethod.startsWith("Traditional")
            val calibrated=traditional&&(angle==30.0||angle==45.0)
            val multiplier=if(traditional&&angle==45.0)2.5 else if(traditional&&angle==30.0)3.8 else geometric
            val spacing=rise*multiplier;val shift=rise*tan(Math.toRadians(side/2))
            val b=centre+shift;val a=b-spacing;val c=b+spacing
            require(a>=0){"Not enough lead for first mark."}
            checkMarks(a,b,c)
            "Center "+angle+"°, side "+side+"°\nMarking method: "+saddleMethod+
            "\nSpacing multiplier: "+String.format(Locale.CANADA,"%.3f",multiplier)+" (geometric bend-centre = "+String.format(Locale.CANADA,"%.3f",geometric)+")"+
            "\nSide spacing: "+f(spacing)+"\nCenter shift: "+f(shift)+
            "\nA / B / C: "+f(a)+" / "+f(b)+" / "+f(c)+
            "\n\n1. Mark centre B at obstruction centre plus shift.\n2. Mark outside A and C equal spacing from B.\n3. Make centre bend "+angle+"° and two opposite outer bends "+side+"° in the same plane.\n4. Confirm shoe reference, gain and test-fit over the obstruction.\n"+
            (if(calibrated)"Traditional 2.5/3.8 field factors use centre NOTCH and outer ARROW; do not interchange with geometric centre positions." else "WARNING: THESE ARE GEOMETRIC BEND-CENTRE PLANNING POSITIONS, NOT VERIFIED ARROW/NOTCH SHOE MARKS.")
        }
        "Four-point saddle"->{
            require(rise>0&&width>0&&dist>0){"Height, width and reference required."}
            val spacing=rise*mult;val shrink=rise*tan(radians/2)
            val b=dist+shrink;val a=b-spacing;val c=b+width;val d=c+spacing
            require(a>=0){"Not enough lead from reference."}
            checkMarks(a,b,c,d)
            "Side spacing: "+f(spacing)+"\nShrink per offset: "+f(shrink)+
            "\nA/B/C/D: "+f(a)+" / "+f(b)+" / "+f(c)+" / "+f(d)+
            "\n\n1. Mark A,B,C,D from SAME end.\n2. Bend A and B opposite directions to rise over the obstruction.\n3. Bend C and D as a reverse offset back to original plane.\n4. Check all bends are level, parallel and clear of the obstacle."
        }
        else->"Choose a bend type."
    }
    val shoeCheck=if(type in listOf("Offset","Rolling offset","Three-point saddle","Four-point saddle"))
        if(minShoeGap==null)"Shoe spacing NOT VERIFIED: enter your bender minimum to check close marks."
        else "Mark gaps checked against your entered minimum of "+f(minShoeGap)+"."
        else "Additional shoe clearance and remaining developed material length NOT VERIFIED for this layout."
    return "10 FT EMT STOCK CHECK · 120 in / 3,048 mm maximum. Plotted same-datum marks are within stock.\n"+output+
        "\n\n"+shoeCheck+" Physical shoe radius, gain, head access and remaining straight legs still need confirmation."
}

private fun mobileBendMeasure(value:Double, units:String, precision:String):String {
    if(precision=="Exact")return String.format(Locale.CANADA,"%.3f %s",value,units)
    if(units=="mm") {
        val increment=if(precision=="5 mm")5.0 else 1.0
        return String.format(Locale.CANADA,"%.0f mm",round(value/increment)*increment)
    }
    val d=when(precision){"1/4 in"->4;"1/8 in"->8;else->16}
    val ticks=round(abs(value)*d).toLong()
    val whole=ticks/d
    val part=(ticks%d).toInt()
    if(part==0)return (if(value<0)"−" else "")+whole+" in"
    fun gcd(a:Int,b:Int):Int{var x=a;var y=b;while(y!=0){val t=x%y;x=y;y=t};return x}
    val divisor=gcd(part,d)
    return (if(value<0)"−" else "")+(if(whole==0L)"" else whole.toString()+" ")+(part/divisor)+"/"+(d/divisor)+" in"
}
