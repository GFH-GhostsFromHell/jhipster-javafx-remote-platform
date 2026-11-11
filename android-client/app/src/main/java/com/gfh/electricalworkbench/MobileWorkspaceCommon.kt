package com.gfh.electricalworkbench

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal val MWCard = EWColors.card
internal val MWCard2 = EWColors.raised
internal val MWCyan = EWColors.primary
internal val MWAmber = EWColors.warning
internal val MWText = EWColors.text
internal val MWMuted = EWColors.muted
internal val MWGood = EWColors.good
internal val MWDanger = EWColors.danger

internal class MobileWorkspaceStore(private val context: Context, username: String) {
    private val safeUser = username.ifBlank { "default" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private val prefs = context.getSharedPreferences("electrical_workbench_$safeUser", Context.MODE_PRIVATE)

    fun records(name: String): List<JSONObject> {
        val raw = prefs.getString("records_$name", "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(it) }
        }
    }

    fun append(name: String, value: JSONObject) {
        val array = JSONArray()
        records(name).forEach { array.put(it) }
        if (value.optString("_syncId").isBlank()) value.put("_syncId", java.util.UUID.randomUUID().toString())
        array.put(value)
        prefs.edit().putString("records_$name", array.toString()).apply()
        MobileAccountSync.schedule(context)
    }

    fun putString(key: String, value: String?) {
        val edit = prefs.edit()
        if (value == null) edit.remove(key) else edit.putString(key, value)
        edit.apply()
    }

    fun getString(key: String): String? = prefs.getString(key, null)
}

@Composable
internal fun MWPage(title: String, subtitle: String, content: @Composable Column.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal=14.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text(title,color=MWText,fontSize=22.sp,fontWeight=FontWeight.Bold,lineHeight=27.sp)
        if(subtitle.isNotBlank()) Text(subtitle,color=MWMuted,fontSize=13.sp,lineHeight=18.sp)
        content()
        Spacer(Modifier.height(12.dp))
    }
}
@Composable
internal fun MWSectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal=14.dp,vertical=5.dp)) {
        Text(title,color=MWText,fontSize=20.sp,fontWeight=FontWeight.Bold)
        if(subtitle.isNotBlank()) Text(subtitle,color=MWMuted,fontSize=12.sp,
            maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
@Composable
internal fun MWCard(title: String, content: @Composable Column.() -> Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=MWCard),
        border=BorderStroke(1.dp,EWColors.outline.copy(alpha=.48f)),
        shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,color=MWText,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
            content()
        }
    }
}
/** One large tap target, instead of an Open button in every big card. */
@Composable
internal fun MWHubCard(title:String,description:String,onClick:()->Unit) {
    Card(onClick=onClick,colors=CardDefaults.cardColors(containerColor=MWCard),
        border=BorderStroke(1.dp,EWColors.outline.copy(alpha=.55f)),
        shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=11.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,color=MWText,fontWeight=FontWeight.SemiBold,fontSize=15.sp,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(description,color=MWMuted,fontSize=12.sp,lineHeight=16.sp,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Text("›",color=MWCyan,fontWeight=FontWeight.Bold,fontSize=25.sp)
        }
    }
}
/** Single section chooser removes sideways tab scrolling. */
@Composable
internal fun MWSectionPicker(title:String,names:List<String>,selected:Int,onSelect:(Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(title,color=MWText,fontWeight=FontWeight.SemiBold,fontSize=16.sp,
            modifier=Modifier.weight(.75f),maxLines=1,overflow=TextOverflow.Ellipsis)
        androidx.compose.foundation.layout.Box(Modifier.weight(1.3f)) {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {
                Text((names.getOrNull(selected)?:"Choose")+" ▾",maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                names.forEachIndexed { index,name ->
                    DropdownMenuItem(text={Text(name)},onClick={expanded=false;onSelect(index)})
                }
            }
        }
    }
}

@Composable
internal fun MWField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun MWCheck(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label, color = MWText, modifier = Modifier.weight(1f))
    }
}

internal fun parseLocalDate(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

internal fun openExternal(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
