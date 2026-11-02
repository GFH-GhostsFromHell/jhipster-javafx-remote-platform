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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal val MWCard = Color(0xFF152A3D)
internal val MWCard2 = Color(0xFF0F2235)
internal val MWCyan = Color(0xFF22D3EE)
internal val MWAmber = Color(0xFFF5B942)
internal val MWText = Color(0xFFF1F5F9)
internal val MWMuted = Color(0xFFB6C4D4)
internal val MWGood = Color(0xFF5EEAD4)
internal val MWDanger = Color(0xFFFF8A80)

internal class MobileWorkspaceStore(context: Context, username: String) {
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
        array.put(value)
        prefs.edit().putString("records_$name", array.toString()).apply()
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = MWText, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = MWMuted, fontSize = 13.sp, lineHeight = 18.sp)
        content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun MWSectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = MWText, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = MWMuted, fontSize = 13.sp)
    }
}

@Composable
internal fun MWCard(title: String, content: @Composable Column.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MWCard),
        border = BorderStroke(1.dp, Color(0xFF29455E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = MWCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
internal fun MWHubCard(title: String, description: String, onClick: () -> Unit) {
    MWCard(title) {
        Text(description, color = MWMuted)
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Open") }
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
