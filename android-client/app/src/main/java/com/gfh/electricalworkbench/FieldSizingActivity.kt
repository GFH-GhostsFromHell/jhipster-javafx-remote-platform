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
                var tab by remember { mutableStateOf("Pull box") }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Electrical Workbench · Field Tools",style=MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={tab="Pull box"}){Text("Pull box")}
                        OutlinedButton(onClick={tab="Bender"}){Text("Conduit bender")}
                    }
                    if(tab=="Pull box") PullBoxMobile() else BenderMobile()
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
private val bends=listOf("Offset","Rolling offset","90° stub","Three-point saddle","Four-point saddle","Back-to-back 90s","Kicked 90°")
@Composable private fun BenderMobile() {
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
    var result by remember{mutableStateOf("Select a bend and calculate marks.") }
    Text("Conduit bending · field guide",style=MaterialTheme.typography.titleLarge)
    Text("Uses geometric centreline dimensions. Always confirm shoe deduct, arrow/star/saddle-notch alignment and springback.")
    FieldDropdown("Bend",bend,bends){bend=it}
    FieldDropdown("Input units",units,listOf("mm","in")){next->
        if(next!=units){
            val factor=if(next=="in")1/25.4 else 25.4
            fun cv(s:String)=s.toDoubleOrNull()?.let{String.format(Locale.ROOT,"%.3f",it*factor)}?:s
            rise=cv(rise);roll=cv(roll);distance=cv(distance);stub=cv(stub);takeup=cv(takeup);width=cv(width);centre=cv(centre);units=next
        }
    }
    if(bend!="90° stub"&&bend!="Back-to-back 90s")FieldDropdown("Bend angle",angle,listOf("10","15","22.5","30","45","60")){angle=it}
    if(bend in listOf("Offset","Rolling offset","Three-point saddle","Four-point saddle","Kicked 90°"))NumberInput("Rise/height ("+units+")",rise){rise=it}
    if(bend=="Rolling offset")NumberInput("Horizontal roll ("+units+")",roll){roll=it}
    if(bend in listOf("Offset","Rolling offset","Four-point saddle"))NumberInput("Reference / obstacle distance ("+units+")",distance){distance=it}
    if(bend in listOf("90° stub","Back-to-back 90s","Kicked 90°")){
        NumberInput("Desired stub ("+units+")",stub){stub=it}
        NumberInput("Take-up / deduct from your bender ("+units+")",takeup){takeup=it}
        Text("Common hand-bender starting points: 1/2 EMT 5 in, 3/4 EMT 6 in, 1 EMT 8 in. The actual shoe stamp controls.",style=MaterialTheme.typography.bodySmall)
    }
    if(bend=="Three-point saddle")NumberInput("Obstruction centre from datum ("+units+")",centre){centre=it}
    if(bend in listOf("Four-point saddle","Back-to-back 90s"))NumberInput("Obstacle width / back-to-back distance ("+units+")",width){width=it}
    Button(onClick={
        result=runCatching{bendMath(bend,units,angle.toDouble(),rise.toDouble(),roll.toDouble(),distance.toDouble(),stub.toDouble(),takeup.toDouble(),width.toDouble(),centre.toDouble())}
            .getOrElse{"Check inputs: "+it.message}
    },modifier=Modifier.fillMaxWidth()){Text("Calculate marks & instructions")}
    Text(result,style=MaterialTheme.typography.bodyMedium)
    Text("Field check: use the correct-size shoe and firm foot pressure, keep bend planes aligned, check springback and avoid conduit flattening. Test a piece before cutting to final length.",color=Color(0xFFFFD68A))
}
private fun bendMath(type:String,u:String,angle:Double,rise:Double,roll:Double,dist:Double,stub:Double,takeup:Double,width:Double,centre:Double):String {
    require(angle>0&&angle<90){"Angle must be between 0° and 90°."}
    val radians=Math.toRadians(angle);val mult=1/sin(radians)
    fun f(x:Double):String=if(u=="mm")String.format(Locale.CANADA,"%.1f mm",x)else String.format(Locale.CANADA,"%.3f in",x)
    return when(type) {
        "Offset","Rolling offset"->{
            require(rise>0&&dist>0&&roll>=0){"Rise and reference must be positive."}
            val h=if(type=="Rolling offset")hypot(rise,roll)else rise
            val spacing=h*mult;val shrink=h*tan(radians/2)
            val a=dist-shrink;val b=a+spacing
            require(a>=0){"Lead is too short for the calculated mark."}
            (if(type=="Rolling offset")"True offset: "+f(h)+"\nRotate plane from vertical: "+String.format(Locale.CANADA,"%.1f",Math.toDegrees(atan2(roll,rise)))+"°\n" else "")+
            "Multiplier: "+String.format(Locale.CANADA,"%.3f",mult)+"\nSpacing: "+f(spacing)+"\nShrink: "+f(shrink)+
            "\nA near mark: "+f(a)+"\nB far mark: "+f(b)+"\n\n"+
            "1. Mark the reference end, A and B.\n2. Align shoe ARROW to A; bend "+angle+"°.\n3. Rotate pipe 180° along its LENGTH. Align ARROW with B; bend the same angle opposite to first bend.\n4. Check both ends are parallel; verify rise, roll and obstacle clearance."
        }
        "90° stub","Back-to-back 90s","Kicked 90°"->{
            require(stub>takeup&&takeup>0){"Desired stub must exceed the actual shoe deduct."}
            var steps="First arrow mark: "+f(stub-takeup)+"\n1. Measure from square reference end to arrow mark.\n2. Align bender ARROW and bend a 90° with steady foot pressure.\n3. Check finished stub and springback."
            if(type=="Back-to-back 90s"){
                require(width>0){"Back-to-back distance required."}
                steps+="\nBack of first 90° to second STAR reference: "+f(width)+" (adjust for shoe if instructed).\n4. Measure from the back of the first completed 90°; use STAR/back-of-90 reference for second bend, not the arrow deduct.\n5. Bend in one plane, verify leg separation."
            }else if(type=="Kicked 90°"){
                require(rise>0){"Kick rise required."}
                steps+="\nKick diagonal travel: "+f(rise*mult)+"\nProjected run: "+f(rise/tan(radians))+"\n4. Choose actual kick location along straight leg and mark according to your shoe.\n5. Make "+angle+"° kick in required plane; check rise. Location is NOT a universal second mark."
            }
            steps
        }
        "Three-point saddle"->{
            require(rise>0&&centre>0){"Height and centre required."}
            val side=angle/2;val spacing=rise/sin(Math.toRadians(side));val shift=rise*tan(Math.toRadians(side/2))
            val b=centre+shift;val a=b-spacing;val c=b+spacing
            require(a>=0){"Not enough lead for first mark."}
            "Center "+angle+"°, side "+side+"°\nSide spacing: "+f(spacing)+"\nCenter shift: "+f(shift)+
            "\nA / B / C: "+f(a)+" / "+f(b)+" / "+f(c)+
            "\n\n1. Mark centre B at obstruction centre plus shift.\n2. Mark outside A and C equal spacing from B.\n3. Align B with the shoe SADDLE/CENTRE notch and bend "+angle+"°.\n4. Rotate conduit 180° on its length; make outer bends "+side+"° opposite to centre.\n5. Test-fit and check manufacturer saddle chart."
        }
        "Four-point saddle"->{
            require(rise>0&&width>0&&dist>0){"Height, width and reference required."}
            val spacing=rise*mult;val shrink=rise*tan(radians/2)
            val b=dist+shrink;val a=b-spacing;val c=b+width;val d=c+spacing
            require(a>=0){"Not enough lead from reference."}
            "Side spacing: "+f(spacing)+"\nShrink per offset: "+f(shrink)+
            "\nA/B/C/D: "+f(a)+" / "+f(b)+" / "+f(c)+" / "+f(d)+
            "\n\n1. Mark A,B,C,D from SAME end.\n2. Bend A and B opposite directions to rise over the obstruction.\n3. Bend C and D as a reverse offset back to original plane.\n4. Check all bends are level, parallel and clear of the obstacle."
        }
        else->"Choose a bend type."
    }
}
