package com.gfh.electricalworkbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val ENTERPRISE_PORTAL_BASE = "https://remote-javafx-portal.onrender.com"

@Composable
internal fun EnterpriseMobileScreen(
    username: String,
    password: String,
    onOpenWeb: (String) -> Unit
) {
    var dashboard by remember(username) { mutableStateOf<JSONObject?>(null) }
    var error by remember(username) { mutableStateOf<String?>(null) }

    DisposableEffect(username, password) {
        Thread {
            try {
                val body = MobileEnterpriseApi.get("/api/enterprise", username, password)
                val parsed = JSONObject(body)
                mainThreadEnterprise { dashboard = parsed; error = null }
            } catch (ex: Exception) {
                mainThreadEnterprise { error = ex.message ?: "Business account could not be loaded" }
            }
        }.start()
        onDispose { }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Business Team", fontSize = 26.sp, fontWeight = FontWeight.Black, color = MWText)
        Text(
            "Tier 4 · Business / Enterprise account management for electricians, apprentices and field staff.",
            color = MWMuted,
            lineHeight = 19.sp
        )

        when {
            dashboard != null -> {
                val data = dashboard!!
                MWCard("🏢 ${data.optString("businessName", "Business / Enterprise")}") {
                    Text("Owner: ${data.optString("ownerUsername", username)}", color = MWMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        EnterpriseStat("Seats", data.optInt("seatCount", 0).toString(), Modifier.weight(1f))
                        EnterpriseStat("Tier 1", data.optInt("tier1Count", 0).toString(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        EnterpriseStat("Tier 2", data.optInt("tier2Count", 0).toString(), Modifier.weight(1f))
                        EnterpriseStat("Tier 3", data.optInt("tier3Count", 0).toString(), Modifier.weight(1f))
                    }
                }
                MWCard("Team management") {
                    Text(
                        "Add existing users, assign Tier 1–3 seats, label field roles such as Apprentice or Journeyperson, and remove users from the business account.",
                        color = MWMuted,
                        lineHeight = 19.sp
                    )
                    Button(
                        onClick = { onOpenWeb("/portal.html#enterprise") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Business Team Manager") }
                    Text(
                        "The manager opens securely inside the Android app using this signed-in account.",
                        color = MWMuted,
                        fontSize = 12.sp
                    )
                }
            }
            error != null -> {
                MWCard("Business Team") {
                    Text(error ?: "Unable to load business account", color = MWDanger)
                    Text("Tier 4 is required to manage an enterprise team.", color = MWMuted)
                }
            }
            else -> {
                Card(colors = CardDefaults.cardColors(containerColor = MWCard), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Loading business account…", color = MWMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EnterpriseStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MWCard2)) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), color = MWMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = MWCyan, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun BusinessMembershipCard(username: String, password: String) {
    var membership by remember(username) { mutableStateOf<JSONObject?>(null) }

    DisposableEffect(username, password) {
        Thread {
            val parsed = runCatching {
                JSONObject(MobileEnterpriseApi.get("/api/enterprise/membership", username, password))
            }.getOrNull()
            mainThreadEnterprise { membership = parsed }
        }.start()
        onDispose { }
    }

    val data = membership ?: return
    if (!data.optBoolean("managed", false)) return

    MWCard("🏢 Business-managed account") {
        Text(data.optString("businessName", data.optString("ownerUsername", "Business account")), color = MWText, fontWeight = FontWeight.Bold)
        Text("Assigned seat: ${tierName(data.optString("assignedTier", "TIER_1"))}", color = MWMuted)
        val role = data.optString("roleLabel", "").trim()
        if (role.isNotBlank()) Text("Role: $role", color = MWMuted)
        Text(
            "Your subscription level is managed by the business account while you remain on its team roster.",
            color = MWMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

private fun tierName(tier: String): String = when (tier) {
    "TIER_4" -> "Business / Enterprise"
    else -> tier.replace('_', ' ')
}

private object MobileEnterpriseApi {
    fun get(path: String, username: String, password: String): String {
        val connection = (URL(ENTERPRISE_PORTAL_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", basic(username, password))
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401 || code == 403) throw SecurityException("This account does not have Business Team access")
            if (code !in 200..299) throw IllegalStateException("Portal returned HTTP $code${if (body.isBlank()) "" else ": $body"}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun basic(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(StandardCharsets.UTF_8)
        return "Basic ${Base64.getEncoder().encodeToString(raw)}"
    }
}

private fun mainThreadEnterprise(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}
