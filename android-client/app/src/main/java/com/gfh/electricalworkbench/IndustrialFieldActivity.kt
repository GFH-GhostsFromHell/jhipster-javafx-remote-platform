package com.gfh.electricalworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Three-input offline industrial arithmetic for field review. */
class IndustrialFieldActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(
                primary=Color(0xFF29D4E9),background=Color(0xFF071725),
                surface=Color(0xFF112E40),onSurface=Color(0xFFF2F9FF)
            )) { IndustrialFieldScreen() }
        }
    }
}

@Composable
private fun IndustrialFieldScreen() {
    val modes=IndustrialFieldMath.modes
    var selected by remember { mutableStateOf(modes[0]) }
    var menu by remember { mutableStateOf(false) }
    val fields=remember { mutableStateMapOf<String,String>() }
    var output by remember { mutableStateOf("Choose a calculation and replace the example values with actual nameplate or measured data.") }
    var status by remember { mutableStateOf("Sample inputs are NOT verified.") }
    LaunchedEffect(selected.id) {
        fields.clear()
        selected.fields.forEach { fields[it.id]=it.example }
        status="Example values shown — verify against the job."
        output="No calculation performed."
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .imePadding().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Text("Industrial Field Worksheets",style=MaterialTheme.typography.headlineSmall,
            fontWeight=FontWeight.Bold)
        Text("Offline CT/PT ratio, balanced power, relay-current translation, ideal transformer fault estimate and 4–20 mA scaling.",
            color=Color(0xFFC5DAE9))
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF112E40))){
            Column(Modifier.fillMaxWidth().padding(13.dp),
                verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text("1 · Select calculation",style=MaterialTheme.typography.titleMedium)
                Box(Modifier.fillMaxWidth()){
                    OutlinedButton(
                        onClick={menu=true},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)
                    ){ Text(selected.title) }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
                        modes.forEach { choice->
                            DropdownMenuItem(text={Text(choice.title)},onClick={
                                menu=false;selected=choice
                            })
                        }
                    }
                }
            }
        }
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF112E40))){
            Column(Modifier.fillMaxWidth().padding(13.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("2 · Field measurements",style=MaterialTheme.typography.titleMedium)
                Text("Defaults are examples only. Verify actual CT/VT labels and equipment ratings.",
                    color=Color(0xFFFFD996),style=MaterialTheme.typography.bodySmall)
                selected.fields.forEach { field->
                    OutlinedTextField(
                        value=fields[field.id].orEmpty(),
                        onValueChange={fields[field.id]=it},
                        modifier=Modifier.fillMaxWidth(),
                        label={Text(field.label)},
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                        singleLine=true
                    )
                }
            }
        }
        Button(onClick={
            output=runCatching {
                val values=selected.fields.associate { field->
                    field.id to (fields[field.id]?.toDouble()
                        ?:error("Enter ${field.label}."))
                }
                val r=IndustrialFieldMath.calculate(selected.id,values)
                status="Arithmetic calculated — NOT CODE VERIFIED."
                buildString {
                    appendLine(selected.title)
                    appendLine()
                    r.values.forEach { (name,value)->
                        appendLine(name+": "+String.format(Locale.CANADA,"%.4f",value))
                    }
                    appendLine()
                    appendLine("REVIEW LIMITATIONS")
                    appendLine(r.caution)
                    appendLine()
                    appendLine("OESC_ON_2024 · Code table verification required")
                    appendLine("NOT CODE VERIFIED. Not an approved relay, fault-current or equipment setting.")
                }.trim()
            }.getOrElse {
                status="Cannot calculate — correct the field inputs."
                it.message?:"Invalid circuit inputs."
            }
        },modifier=Modifier.fillMaxWidth().heightIn(min=51.dp)){
            Text("Calculate")
        }
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF112E40))){
            Column(Modifier.fillMaxWidth().padding(13.dp),
                verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text("3 · Field result",style=MaterialTheme.typography.titleMedium)
                Text(status,color=Color(0xFFFFD996))
                SelectionContainer {Text(output,style=MaterialTheme.typography.bodyMedium)}
            }
        }
        Text("Safety: These ideal arithmetic methods are not protective-relay coordination, available fault-current analysis, arc-flash verification, or installation approval.",
            style=MaterialTheme.typography.bodySmall,color=Color(0xFFFFD996))
    }
}
