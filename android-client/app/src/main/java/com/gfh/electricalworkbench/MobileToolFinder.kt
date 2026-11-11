package com.gfh.electricalworkbench

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    MobileTask("power", "Power System Analysis - Web", "Per-unit, short-circuit and sequence studies (connection required)", "Power & Protection", "fault impedance", "native:power"),
    MobileTask("fault-offline", "Equipment Troubleshooting - Offline", "Fault isolation and work orders", "Troubleshooting", "vfd plc mine motor", "native:fault", true),
    MobileTask("fault", "Equipment Troubleshooting Guides", "Detailed motors, PLC, VFD and relay methods", "Troubleshooting", "diagnostic procedures", "/troubleshooting-kb.html"),
    MobileTask("jobsite", "Jobsite Tools", "Nameplates, commissioning and inspections", "Projects & Learning", "field records", "native:field", true),
    MobileTask("quote", "Build a Job Quote", "Materials, labour and taxes", "Projects & Learning", "estimate contractor", "/field-suite.html#quote"),
    MobileTask("local-projects", "Saved Projects", "Local projects and material takeoff", "Projects & Learning", "cable records", "native:projects", true),
    MobileTask("quiz", "Red Seal Daily Quiz", "309A practice questions", "Projects & Learning", "exam learning", "native:learn", true),
    MobileTask("career", "Trade Hours & Career", "Hours, skills and credentials", "Projects & Learning", "apprentice", "native:career", true),
    MobileTask("safety", "Electrical Safety", "PPE, approach and inspection guidance", "Projects & Learning", "loto", "native:safety", true),
    MobileTask("saved", "Saved Cloud Work", "Cloud-synced records and exports", "Projects & Learning", "sync backup", "/account-records.html"),
    MobileTask("code-updates", "Code Updates & Bulletins", "CEC/OESC notices and ESA bulletins", "Projects & Learning", "electrical code ontario news", "/news.html")
)

@Composable
internal fun MobileToolFinder(username:String,onOpenWeb:(String)->Unit,
    onOpenMore:(MoreArea)->Unit,onOpenSafety:()->Unit) {
    val context=LocalContext.current
    val preferences=remember(username) {
        context.getSharedPreferences("ewb.tool.shortcuts.v1."+username,Context.MODE_PRIVATE)
    }
    var query by remember(username){mutableStateOf("")}
    var group by remember(username){mutableStateOf("All")}
    var menu by remember{mutableStateOf(false)}
    var offlineOnly by remember{mutableStateOf(false)}
    var showAll by remember{mutableStateOf(false)}
    var favourites by remember(username) {
        mutableStateOf(preferences.getStringSet("favourites",emptySet())?.toSet()?:emptySet())
    }
    var recent by remember(username) {
        mutableStateOf(preferences.getString("recent","")?.split("|")?.filter{it.isNotBlank()}?:emptyList())
    }
    fun open(task:MobileTask) {
        recent=(listOf(task.id)+recent.filter{it!=task.id}).take(8)
        preferences.edit().putString("recent",recent.joinToString("|")).apply()
        when(task.destination) {
            "native:pull"->context.startActivity(Intent(context,FieldSizingActivity::class.java))
            "native:circuit"->context.startActivity(Intent(context,CircuitSizingActivity::class.java).putExtra("accountUsername",username))
            "native:transformer"->context.startActivity(Intent(context,TransformerSizingActivity::class.java))
            "native:relay"->context.startActivity(Intent(context,IndustrialFieldActivity::class.java))
            "native:power"->context.startActivity(Intent(context,EngineeringPowerActivity::class.java))
            "native:fault"->context.startActivity(Intent(context,FieldSizingActivity::class.java).putExtra("toolTab","Troubleshooting"))
            "native:field"->onOpenMore(MoreArea.FIELD)
            "native:projects"->onOpenMore(MoreArea.PROJECTS)
            "native:learn"->onOpenMore(MoreArea.LEARN)
            "native:career"->onOpenMore(MoreArea.CAREER)
            "native:safety"->onOpenSafety()
            else->onOpenWeb(task.destination)
        }
    }
    val categories=listOf("All","Installation","Calculations","Power & Protection","Troubleshooting","Projects & Learning")
    val filtered=allTasks.filter{ task->
        (group=="All"||group==task.group)&&(!offlineOnly||task.offline)&&
            query.trim().lowercase().split(Regex("\\s+")).all{word->
                word.isBlank()||(task.title+" "+task.detail+" "+task.search).lowercase().contains(word)
            }
    }.sortedWith(compareBy<MobileTask>{if(favourites.contains(it.id))0 else 1}
        .thenBy{val n=recent.indexOf(it.id);if(n<0)1000 else n}.thenBy{it.title})
    val featured=setOf("conduit-marks","conductor-drop","pull-box","motor","fault-offline","jobsite")
    val compact=query.isBlank()&&group=="All"&&!offlineOnly&&!showAll
    val visibleTasks=if(compact)filtered.filter{it.id in featured||it.id in favourites}.take(8) else filtered
    val pinned=if(compact)favourites.mapNotNull{id->allTasks.find{it.id==id}}.take(3) else emptyList()
    val recentlyUsed=if(compact)recent.firstNotNullOfOrNull{id->allTasks.find{it.id==id}} else null

    MWPage("Find a tool","Search by task, then tap the result.") {
        OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,
            label={Text("Search tools by job or calculation")},
            placeholder={Text("Offset, motor fuse, voltage drop")},
            modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Box(Modifier.weight(1f)){
                OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()) {
                    Text(group+" ▾",maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                    categories.forEach{item->
                        DropdownMenuItem(text={Text(item)},onClick={group=item;menu=false})
                    }
                }
            }
            FilterChip(selected=offlineOnly,onClick={offlineOnly=!offlineOnly},label={Text("Offline")})
        }
        if(pinned.isNotEmpty()){
            Text("★ Favourites",color=MWText,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
            pinned.forEach{task->MWHubCard(task.title,task.detail){open(task)}}
        }
        if(recentlyUsed!=null&&recentlyUsed.id !in favourites) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Recent",color=MWMuted,fontSize=12.sp)
                OutlinedButton(onClick={open(recentlyUsed)},modifier=Modifier.weight(1f)){
                    Text(recentlyUsed.title,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
        Text(if(compact)"Common tasks" else "Matching tools",color=MWText,
            fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        if(visibleTasks.isEmpty())MWCard("No results"){
            Text("Try a shorter search, All categories or disable Offline.",color=MWMuted)
        }
        visibleTasks.forEach{task->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Column(Modifier.weight(1f)){
                    MWHubCard(task.title,task.detail+(if(task.offline)" · Offline" else " · Online")){open(task)}
                }
                OutlinedButton(onClick={
                    favourites=if(favourites.contains(task.id))favourites-task.id else favourites+task.id
                    preferences.edit().putStringSet("favourites",favourites.toSet()).apply()
                },modifier=Modifier.semantics{
                    contentDescription=if(favourites.contains(task.id))
                        "Remove "+task.title+" from favourites" else "Add "+task.title+" to favourites"
                }) {Text(if(favourites.contains(task.id))"★" else "☆",fontSize=19.sp)}
            }
        }
        if(compact) OutlinedButton(onClick={showAll=true},modifier=Modifier.fillMaxWidth()){
            Text("Show all "+allTasks.size+" tools")
        } else if(query.isBlank()&&group=="All"&&!offlineOnly)
            OutlinedButton(onClick={showAll=false},modifier=Modifier.fillMaxWidth()){
                Text("Show common tasks only")
            }
        Text(visibleTasks.size.toString()+" of "+filtered.size+" tools · Shortcuts stay on this device.",
            color=MWMuted,fontSize=12.sp)
        if(!compact&&recent.isNotEmpty())
            OutlinedButton(onClick={
                recent=emptyList()
                preferences.edit().remove("recent").apply()
            },modifier=Modifier.fillMaxWidth()){Text("Clear recent tools")}
        Text("Verify applicable CEC/OESC rules and equipment data before field use.",color=MWMuted,fontSize=12.sp)
    }
}
