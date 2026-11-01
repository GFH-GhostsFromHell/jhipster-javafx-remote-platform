package com.gfh.electricalworkbench

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val UPDATE_BASE_URL = "https://remote-javafx-portal.onrender.com"

private val GateNavy = Color(0xFF06111F)
private val GatePanel = Color(0xFF0D2438)
private val GatePanelStrong = Color(0xFF102D45)
private val GateCyan = Color(0xFF22D3EE)
private val GateBlue = Color(0xFF3B82F6)
private val GateText = Color(0xFFF8FBFF)
private val GateSecondary = Color(0xFFD6E3EE)
private val GateMuted = Color(0xFFB8C9D8)
private val GateBorder = Color(0xFF365A75)

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
            primary = GateCyan,
            secondary = GateBlue,
            background = GateNavy,
            surface = GatePanel,
            onPrimary = GateNavy,
            onBackground = GateText,
            onSurface = GateText
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
                    listOf(Color(0xFF020711), GateNavy, Color(0xFF0B3657))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(listOf(GateCyan, GateBlue)),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = GateNavy,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "ELECTRICAL WORKBENCH",
                        color = GateText,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 1.0.sp
                    )
                    Text(
                        "ANDROID · UPDATE CHECK",
                        color = GateCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            when (val current = state) {
                UpdateState.Checking -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GatePanelStrong),
                        border = BorderStroke(1.dp, GateBorder),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = GateCyan,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            CircularProgressIndicator(
                                color = GateCyan,
                                trackColor = Color(0xFF29475F),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                "Checking for updates",
                                color = GateText,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "Installed version ${BuildConfig.VERSION_NAME}",
                                color = GateSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "The app will continue to sign-in automatically when this check is complete.",
                                color = GateMuted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is UpdateState.Available -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GatePanelStrong),
                        border = BorderStroke(1.dp, GateBorder),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = GateCyan,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(Modifier.width(11.dp))
                                Column {
                                    Text(
                                        "Update available",
                                        color = GateText,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        "${BuildConfig.VERSION_NAME}  →  ${current.release.version}",
                                        color = GateCyan,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "A newer Electrical Workbench Android build is ready on the ${current.release.channel} channel.",
                                color = GateSecondary,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                            if (current.release.sizeBytes > 0) {
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    "Download size: ${formatBytes(current.release.sizeBytes)}",
                                    color = GateMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { onDownload(current.release.downloadUrl) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Download update", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(9.dp))
                            OutlinedButton(
                                onClick = onContinue,
                                border = BorderStroke(1.dp, Color(0xFF6E8CA5)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    "Continue with current version",
                                    color = GateText,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Android will ask you to approve installation of the APK. Updates must be signed with the same signing key as the installed app.",
                                color = GateMuted,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
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
