package com.gfh.electricalworkbench

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val UPDATE_BASE_URL = "https://remote-javafx-portal.onrender.com"

class UpdateGateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UpdateGateTheme {
                UpdateGateScreen(
                    onContinue = { openMainApp() },
                    onDownload = { url ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                )
            }
        }
    }

    private fun openMainApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

private data class AndroidRelease(
    val version: String,
    val channel: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val sizeBytes: Long
)

private sealed interface UpdateState {
    data object Checking : UpdateState
    data class Available(val release: AndroidRelease) : UpdateState
    data class Current(val latest: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
private fun UpdateGateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF22D3EE),
            secondary = Color(0xFF3B82F6),
            background = Color(0xFF06111F),
            surface = Color(0xFF0A1C2F),
            onPrimary = Color(0xFF06111F),
            onBackground = Color(0xFFF1F5F9),
            onSurface = Color(0xFFF1F5F9)
        ),
        content = content
    )
}

@Composable
private fun UpdateGateScreen(
    onContinue: () -> Unit,
    onDownload: (String) -> Unit
) {
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }

    DisposableEffect(Unit) {
        Thread {
            val next = runCatching { AndroidUpdateClient.check() }
                .fold(
                    onSuccess = { release ->
                        if (compareVersions(release.version, BuildConfig.VERSION_NAME) > 0) {
                            UpdateState.Available(release)
                        } else {
                            UpdateState.Current(release.version)
                        }
                    },
                    onFailure = { UpdateState.Failed(it.message ?: "Update check failed") }
                )
            runOnMain { state = next }
        }.start()
        onDispose { }
    }

    DisposableEffect(state) {
        if (state is UpdateState.Current || state is UpdateState.Failed) {
            onContinue()
        }
        onDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF030712), Color(0xFF06111F), Color(0xFF0A3150))
                )
            )
            .statusBarsPadding()
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    Icons.Default.ElectricBolt,
                    contentDescription = null,
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("ELECTRICAL WORKBENCH", fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                    Text("ANDROID UPDATE SERVICE", color = Color(0xFF22D3EE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(26.dp))

            when (val current = state) {
                UpdateState.Checking -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(15.dp))
                    Text("Checking for updates…", fontWeight = FontWeight.Bold)
                    Text("Installed version ${BuildConfig.VERSION_NAME}", color = Color(0xFFB6C4D4), fontSize = 12.sp)
                }

                is UpdateState.Available -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xEE0B1E31)),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Icon(Icons.Default.SystemUpdate, null, tint = Color(0xFF22D3EE))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Update available", fontSize = 24.sp, fontWeight = FontWeight.Black)
                                    Text(
                                        "${BuildConfig.VERSION_NAME} → ${current.release.version}",
                                        color = Color(0xFF22D3EE),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "A newer Electrical Workbench Android build is available on the ${current.release.channel} channel.",
                                color = Color(0xFFB6C4D4),
                                lineHeight = 20.sp
                            )
                            if (current.release.sizeBytes > 0) {
                                Spacer(Modifier.height(5.dp))
                                Text("Download size: ${formatBytes(current.release.sizeBytes)}", color = Color(0xFF8396A9), fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = { onDownload(current.release.downloadUrl) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Icon(Icons.Default.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Download update", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                                Text("Continue with current version")
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Android requires you to approve installation of APK updates. The new APK must also be signed with the same signing key as the installed app.",
                                color = Color(0xFF8396A9),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                is UpdateState.Current -> Unit
                is UpdateState.Failed -> Unit
            }
        }
    }
}

private object AndroidUpdateClient {
    fun check(): AndroidRelease {
        val channel = BuildConfig.UPDATE_CHANNEL
        val connection = URL("$UPDATE_BASE_URL/api/public/releases/android?channel=$channel").openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 18_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "ElectricalWorkbench-Android/${BuildConfig.VERSION_NAME}")
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Update service returned HTTP $status")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val version = json.optString("version")
            val downloadUrl = json.optString("downloadUrl")
            if (version.isBlank() || downloadUrl.isBlank()) throw IOException("Update service returned an incomplete release")
            return AndroidRelease(
                version = version,
                channel = json.optString("channel", channel),
                downloadUrl = downloadUrl,
                releaseUrl = json.optString("releaseUrl"),
                sizeBytes = json.optLong("sizeBytes", 0L)
            )
        } finally {
            connection.disconnect()
        }
    }
}

private fun compareVersions(a: String, b: String): Int {
    val aa = a.split('.')
    val bb = b.split('.')
    val count = maxOf(aa.size, bb.size)
    for (index in 0 until count) {
        val av = aa.getOrNull(index)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        val bv = bb.getOrNull(index)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L * 1024L) return "${bytes / 1024L} KB"
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun runOnMain(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}
