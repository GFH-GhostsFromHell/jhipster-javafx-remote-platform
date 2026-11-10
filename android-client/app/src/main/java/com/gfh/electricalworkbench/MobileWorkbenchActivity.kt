package com.gfh.electricalworkbench

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val MOBILE_PORTAL_URL = "https://remote-javafx-portal.onrender.com"
private const val UPDATE_CHECK_INTERVAL_MS = 30L * 60L * 1000L
private val MobileNavy = Color(0xFF06111F)
private val MobileNavy2 = Color(0xFF0A1C2F)
private val MobileSteel = Color(0xFF152A3D)
private val MobileCyan = Color(0xFF22D3EE)
private val MobileBlue = Color(0xFF3B82F6)
private val MobileAmber = Color(0xFFF5B942)
private val MobileText = Color(0xFFF1F5F9)
private val MobileMuted = Color(0xFFB6C4D4)
private val MobileGood = Color(0xFF5EEAD4)

class MobileWorkbenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MobileWorkbenchTheme { FullMobileWorkbench() } }
    }
}

private data class MobileSession(
    val username: String,
    val password: String,
    val displayName: String,
    val email: String,
    val tier: String,
    val loginProvider: String,
    val featureCount: Int
)

private enum class MobileTab { Home, Workbench, Safety, ChatGPT, More, Profile }

@Composable
private fun MobileWorkbenchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MobileCyan,
            secondary = MobileBlue,
            tertiary = MobileAmber,
            background = MobileNavy,
            surface = MobileNavy2,
            surfaceVariant = MobileSteel,
            onPrimary = MobileNavy,
            onSecondary = MobileText,
            onBackground = MobileText,
            onSurface = MobileText,
            onSurfaceVariant = MobileMuted,
            outline = Color(0xFF829BAF),
            error = Color(0xFFFF9A9A),
            onError = MobileNavy
        ),
        content = content
    )
}

@Composable
private fun FullMobileWorkbench() {
    var session by remember { mutableStateOf<MobileSession?>(null) }
    var loggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var cloudOk by remember { mutableStateOf<Boolean?>(null) }
    var cloudStatus by remember { mutableStateOf("Checking cloud…") }
    var tab by remember { mutableStateOf(MobileTab.Home) }
    var webPath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateScope = rememberCoroutineScope()
    var availableUpdate by remember { mutableStateOf<AndroidRelease?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("Automatic update checks start after sign-in.") }
    var lastUpdateCheckAt by remember { mutableLongStateOf(0L) }
    var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }

    fun checkUpdates(force: Boolean) {
        if (session == null || updateChecking) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastUpdateCheckAt < UPDATE_CHECK_INTERVAL_MS) return
        updateChecking = true
        if (force) updateStatus = "Checking for updates…"
        val userAtStart = session?.username
        updateScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AndroidUpdateClient.check() }
            }
            lastUpdateCheckAt = SystemClock.elapsedRealtime()
            updateChecking = false
            if (session?.username != userAtStart) return@launch
            result.fold(
                onSuccess = { release ->
                    if (compareVersions(release.version, BuildConfig.VERSION_NAME) > 0) {
                        availableUpdate = release
                        updateStatus = "Version ${release.version} is available on the ${release.channel} channel."
                    } else {
                        availableUpdate = null
                        updateStatus = "You're up to date · v${BuildConfig.VERSION_NAME}."
                    }
                },
                onFailure = {
                    updateStatus = "Update check unavailable. Your installed app still works; retry from Settings."
                }
            )
        }
    }

    // Check after successful sign-in, at regular intervals while visible,
    // and upon returning to the app if the last check is due.
    val latestUpdateCheck by rememberUpdatedState(newValue = { checkUpdates(false) })
    LaunchedEffect(session?.username, lifecycleOwner) {
        if (session == null) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                latestUpdateCheck()
                delay(UPDATE_CHECK_INTERVAL_MS)
            }
        }
    }

    LaunchedEffect(session?.username, lifecycleOwner) {
        if (session == null) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                withContext(Dispatchers.IO) {
                    runCatching { MobileAccountSync.syncAll(context.applicationContext) }
                }
                delay(5L * 60L * 1000L)
            }
        }
    }

    DisposableEffect(Unit) {
        Thread {
            val ok = runCatching { MobilePortalApi.health() }.getOrDefault(false)
            mobileMainThread {
                cloudOk = ok
                cloudStatus = if (ok) "Cloud connected" else "Cloud waking or unavailable"
            }
        }.start()
        onDispose { }
    }

    if (session == null) {
        MobileLoginScreen(loggingIn, loginError, cloudStatus, cloudOk) { username, password ->
            loggingIn = true
            loginError = null
            Thread {
                try {
                    val bootstrap = MobilePortalApi.authorizedGet("/api/client/bootstrap", username, password)
                    val parsed = parseMobileSession(username, password, bootstrap)
                    mobileMainThread {
                        session = parsed
                        MobileAccountSync.configure(username, password)
                        MobileAccountSync.schedule(context)
                        lastUpdateCheckAt = 0L
                        availableUpdate = null
                        dismissedUpdateVersion = null
                        loggingIn = false
                        tab = MobileTab.Home
                    }
                } catch (ex: Exception) {
                    mobileMainThread {
                        loggingIn = false
                        loginError = ex.message ?: "Sign-in failed"
                    }
                }
            }.start()
        }
        return
    }

    val active = session!!
    if (webPath != null) {
        MobileAuthenticatedWeb(active, webPath!!) { webPath = null }
        return
    }

    MobileShell(
        session = active,
        selected = tab,
        availableUpdate = availableUpdate,
        updateChecking = updateChecking,
        updateStatus = updateStatus,
        showUpdateBanner = availableUpdate?.version != dismissedUpdateVersion,
        onCheckUpdates = { checkUpdates(true) },
        onReviewUpdate = {
            context.startActivity(Intent(context, UpdateGateActivity::class.java)
                .putExtra(UPDATE_FROM_WORKSPACE, true))
        },
        onDismissUpdate = { dismissedUpdateVersion = availableUpdate?.version },
        onSelected = { tab = it },
        onOpenWeb = { webPath = it },
        onLogout = {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            MobileAccountSync.disconnect()
            session = null
            availableUpdate = null
            dismissedUpdateVersion = null
            updateStatus = "Automatic update checks start after sign-in."
            tab = MobileTab.Home
        }
    )
}

@Composable
private fun MobileLoginScreen(
    loggingIn: Boolean,
    error: String?,
    cloudStatus: String,
    cloudOk: Boolean?,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    Box(
        Modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF030712), MobileNavy, Color(0xFF0A3150))))
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.Center).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ElectricBolt, null, tint = MobileCyan, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("ELECTRICAL WORKBENCH", color = MobileText, fontWeight = FontWeight.Black)
                    Text("CEC 2024 · FULL ANDROID WORKSPACE", color = MobileCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Field-ready electrical tools.", color = MobileText, fontSize = 29.sp, fontWeight = FontWeight.Black)
            Text("Calculators, study, field records, safety, projects and career tools in one app.", color = MobileMuted)
            Spacer(Modifier.height(18.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10263A)),
                border = BorderStroke(1.dp, Color(0xFF29455E)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign in", color = MobileText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, enabled = !loggingIn, singleLine = true, colors = mobileLoginColors(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), enabled = !loggingIn, singleLine = true, colors = mobileLoginColors(), modifier = Modifier.fillMaxWidth())
                    if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFFCDD5), fontSize = 15.sp)
                    Button(
                        onClick = { onLogin(username.trim(), password) },
                        enabled = !loggingIn && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MobileBlue),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    ) {
                        if (loggingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Connecting…")
                        } else Text("Sign in to Electrical Workbench", fontWeight = FontWeight.Bold)
                    }
                    Text("Account access", color = MobileCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(context, AccountAccessActivity::class.java).putExtra("tab", "signup")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Create account") }
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(context, AccountAccessActivity::class.java).putExtra("tab", "verify")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Verify email") }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(context, AccountAccessActivity::class.java).putExtra("tab", "forgot-password")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reset password") }
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(context, AccountAccessActivity::class.java).putExtra("tab", "forgot-username")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Forgot username") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, null, tint = if (cloudOk == true) MobileGood else MobileMuted)
                        Spacer(Modifier.width(8.dp))
                        Text(cloudStatus, color = if (cloudOk == true) MobileGood else MobileMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun mobileLoginColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MobileText,
    unfocusedTextColor = MobileText,
    cursorColor = MobileCyan,
    focusedBorderColor = MobileCyan,
    unfocusedBorderColor = Color(0xFF71869C),
    focusedLabelColor = MobileCyan,
    unfocusedLabelColor = Color(0xFFD6E0EA),
    focusedContainerColor = Color(0xFF0A1928),
    unfocusedContainerColor = Color(0xFF0A1928)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileShell(
    session: MobileSession,
    selected: MobileTab,
    availableUpdate: AndroidRelease?,
    updateChecking: Boolean,
    updateStatus: String,
    showUpdateBanner: Boolean,
    onCheckUpdates: () -> Unit,
    onReviewUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onSelected: (MobileTab) -> Unit,
    onOpenWeb: (String) -> Unit,
    onLogout: () -> Unit
) {
    var initialMoreArea by remember { mutableStateOf(MoreArea.HUB) }
    Scaffold(
        containerColor = MobileNavy,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ElectricBolt, null, tint = MobileCyan)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Electrical Workbench", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("CEC 2024 mobile", color = MobileCyan, fontSize = 10.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MobileNavy2, titleContentColor = MobileText)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MobileNavy2) {
                MobileNavItem(selected == MobileTab.Home, { onSelected(MobileTab.Home) }, Icons.Default.Dashboard, "Home")
                MobileNavItem(selected == MobileTab.Workbench, { onSelected(MobileTab.Workbench) }, Icons.Default.Calculate, "Tools")
                MobileNavItem(selected == MobileTab.Safety, { onSelected(MobileTab.Safety) }, Icons.Default.ElectricBolt, "Safety")
                MobileNavItem(selected == MobileTab.More, { initialMoreArea = MoreArea.HUB; onSelected(MobileTab.More) }, Icons.Default.Launch, "More")
                MobileNavItem(selected == MobileTab.Profile, { onSelected(MobileTab.Profile) }, Icons.Default.Person, "Profile")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (availableUpdate != null && showUpdateBanner) {
                MobileUpdateBanner(availableUpdate, onReviewUpdate, onDismissUpdate)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (selected) {
                    MobileTab.Home -> MobileHome(session, onSelected, onOpenWeb) { area ->
                        initialMoreArea = area
                        onSelected(MobileTab.More)
                    }
                    MobileTab.Workbench -> MobileWorkbenchPage(session.username, onOpenWeb)
                    MobileTab.Safety -> SafetyWorkspaceScreen(session.username)
                    MobileTab.ChatGPT -> AskChatGptWorkspace(session.username)
                    MobileTab.More -> MoreWorkspaceScreen(session.username, onOpenWeb, initialMoreArea)
                    MobileTab.Profile -> MobileProfile(
                        session, onOpenWeb, onLogout, availableUpdate,
                        updateChecking, updateStatus, onCheckUpdates, onReviewUpdate
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileUpdateBanner(
    release: AndroidRelease,
    onReview: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF103F4A))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Update available · v${release.version}",
            color = MobileGood, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                Text("Review update", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Later", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MobileNavItem(selected: Boolean, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null) },
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MobileNavy,
            selectedTextColor = MobileCyan,
            indicatorColor = MobileCyan,
            unselectedIconColor = MobileMuted,
            unselectedTextColor = MobileMuted
        )
    )
}

@Composable
private fun MobileHome(session: MobileSession, onSelected: (MobileTab) -> Unit, onOpenWeb: (String) -> Unit, onOpenMore: (MoreArea) -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Welcome, ${session.displayName}", color = MobileText, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("Choose a task to get started", color = MobileCyan, fontWeight = FontWeight.Bold)
        Text("Start with the job you need to do. Open Tools for calculations or More for projects, learning and field records.", color = MobileMuted)
        MobileFeatureCard("🧮 Electrical Calculators", "Full calculator workspace, advanced calculation pack and conduit bending") { onSelected(MobileTab.Workbench) }
        MobileFeatureCard("⚙ Engineering Calculators", "Per-unit, transformer impedance, transmission lines, symmetrical components, sequence faults and short-circuit tools") {
            context.startActivity(Intent(context, EngineeringPowerActivity::class.java))
        }
        MobileFeatureCard("🦺 Electrical Safety", "Thunderstorm stand-down, PPE rating checks, approach planning, unsafe conditions, glove tests and maintenance") { onSelected(MobileTab.Safety) }
        MobileFeatureCard("🤖 Ask ChatGPT", "Connect an eligible ChatGPT account and ask questions directly inside Electrical Workbench") { onSelected(MobileTab.ChatGPT) }
        MobileFeatureCard("🎓 Red Seal Study", "Daily 25, CEC navigator, formulas, control trainer and province/code profile") { onOpenMore(MoreArea.LEARN) }
        MobileFeatureCard("🛠 Jobsite Tools", "Troubleshooting, nameplates, commissioning, inspection, blueprint helper, fault current and grounding/bonding") { onOpenMore(MoreArea.FIELD) }
        MobileFeatureCard("📁 Saved Projects", "Project log, material takeoff, records and cable planning") { onOpenMore(MoreArea.PROJECTS) }
        MobileFeatureCard("📈 Career & Trade Hours", "Trade hours, credentials, wage estimate and official trade/job resources") { onOpenMore(MoreArea.CAREER) }
        MobileFeatureCard("⚡ CEC / OESC Code News", "ESA/OESC public bulletins and code notices") { onOpenWeb("/news.html") }
    }
}

@Composable
private fun MobileFeatureCard(title: String, detail: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MobileSteel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MobileCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(detail, color = MobileMuted, fontSize = 13.sp)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Open") }
        }
    }
}

@Composable
private fun MobileWorkbenchPage(username: String, onOpenWeb: (String) -> Unit) {
    val context = LocalContext.current
    MWPage("Electrical Calculators", "Choose a task below. Start with conduit bends, circuit sizing or a guided worksheet.") {
        MWSectionHeader("Start here", "The fastest way to complete common field calculations.")
        MWHubCard("↪ Conduit Bend Marks + 3D", "Choose your bend, enter only its required dimensions, then read the tape marks and shoe orientation. Optional 3D preview.") { onOpenWeb("/bend-instruction-lab.html") }
        MWHubCard("Step-by-Step Calculators", "Default guided workflow with worked steps, Why-this-answer explanations, conductor + voltage drop, D3/K, grounding/bonding, transformer, motor taps, refrigeration, fire pump, neutral, fire alarm, service demand, presets and saved solutions") { onOpenWeb("/guided-calculators.html") }
        MWSectionHeader("Electrical sizing & protection", "Use native offline tools when working without a connection.")
        MWHubCard("Protection & Relay Calculators · Offline", "Three inputs: instrument transformer ratios, 3-phase power, specified relay pickup translation, ideal transformer fault estimate and 4–20 mA scaling") {
            context.startActivity(Intent(context, IndustrialFieldActivity::class.java))
        }
        MWHubCard("Engineering Power Systems", "Per-unit, base values, transformer impedance, transmission lines, symmetrical components, sequence faults and short-circuit tools") {
            context.startActivity(Intent(context, EngineeringPowerActivity::class.java))
        }
        MWHubCard("Circuit Sizing & Voltage Drop · Offline", "One set of inputs for termination-column ampacity, ambient/grouping, voltage drop, maximum length and an OCPD arithmetic screen") {
            context.startActivity(Intent(context, CircuitSizingActivity::class.java)
                .putExtra("accountUsername", username))
        }
        MWHubCard("Transformer & Load Sizing · Offline", "Offline load-by-load transformer sizing including calculated residential, apartment, commercial, school, hospital, hotel and industrial service demands") {
            context.startActivity(Intent(context, TransformerSizingActivity::class.java))
        }
        MWHubCard("Motor Protection & Starter Sizing", "Motor type, starting method, time-delay/non-time-delay fuse, inverse-time breaker, overload and disconnect selections") { onOpenWeb("/calculators.html?section=s28") }
        MWHubCard("Refrigeration Motor Sizing", "Multiple hermetic/semi-hermetic compressors with RLA/LRA, failed-start 50/65% protection, overload type, equipment MCA/MOCP and group conductor sizing") { onOpenWeb("/calculators.html?section=s28&tab=6") }
        MWSectionHeader("More tools & reference", "Browse additional calculations, bending guides, and saved projects.")
        MWHubCard("All CEC Section Calculators", "Selection-driven services, conductors, transformers, motors, refrigeration, fire alarm, welders and other section-based calculations") { onOpenWeb("/calculators.html") }
        MWHubCard("Engineering Calculators", "Grounding, fire pump, neutral, services, HVAC, fire alarm and derating worksheets") { onOpenWeb("/advanced-calculations.html") }
        MWHubCard("Pull Boxes & Bend Marks · Offline", "Offline field tools with CEC 2024 straight, angle, U and back-cover dimensions, plus measured bend marks and step-by-step instructions") { context.startActivity(Intent(context, FieldSizingActivity::class.java)) }
        MWHubCard("Conduit Bend Diagrams", "Interactive diagrams for 14 bend layouts and detailed field marking instructions") { onOpenWeb("/bend-calcs.html") }
         MWHubCard("Calibrate a Conduit Bender", "Measure actual stub deduct and 90° gain on scrap conduit, then save this bender in the web workspace") { onOpenWeb("/bender-calibration.html") }
         MWHubCard("Jobsite Tools & Projects", "Blueprint takeoff, 3D bends, motor-control logic, one-line diagrams, estimating, commissioning and camera nameplates") { onOpenWeb("/field-suite.html") }
         MWHubCard("Equipment Troubleshooting Guides", "23 structured Epiroc-style diagnostics covering motors, VFDs, PLCs, mining vehicles, protection, HVAC and site safety") { onOpenWeb("/troubleshooting-kb.html") }
         MWHubCard("Equipment Troubleshooting · Offline", "Safety-first checklists, fault decision methods and locally saved work orders without internet") { context.startActivity(Intent(context, FieldSizingActivity::class.java).putExtra("toolTab", "Troubleshooting")) }
    }
}

@Composable
private fun MobileProfile(
    session: MobileSession,
    onOpenWeb: (String) -> Unit,
    onLogout: () -> Unit,
    availableUpdate: AndroidRelease?,
    checkingUpdates: Boolean,
    updateStatus: String,
    onCheckUpdates: () -> Unit,
    onReviewUpdate: () -> Unit
) {
    val context = LocalContext.current
    MWPage("Profile & Settings", "Electrical Workbench account and app session.") {
        MWCard("Account") {
            Text("Display name: ${session.displayName}", color = MWText)
            Text("Username: ${session.username}", color = MWText)
            Text("Email: ${session.email.ifBlank { "Not set" }}", color = MWText)
            Text("Subscription: ${session.tier.replace('_', ' ')}", color = MWText)
            Text("Sign-in method: ${session.loginProvider.ifBlank { "LOCAL" }}", color = MWText)
            Text("Cloud features: ${session.featureCount}", color = MWMuted)
        }
        MWCard("Account record synchronization") {
            Text("Cloud sync: " + MobileAccountSync.status, color = MWMuted, fontSize = 13.sp)
            Text("Projects, inspections, safety records, hours and bender profiles use the same signed-in account across devices. Offline changes remain on the phone until a successful sync.", color = MWMuted, fontSize = 12.sp)
            OutlinedButton(onClick = { MobileAccountSync.schedule(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Sync saved records now")
            }
            OutlinedButton(onClick = {
                val backup = MobileAccountSync.conflictExport(context, session.username)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, backup)
                }
                context.startActivity(Intent.createChooser(share, "Export sync conflict backups"))
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Review / share sync conflict backups")
            }
            Button(onClick = { onOpenWeb("/field-suite.html#projects") }, modifier = Modifier.fillMaxWidth()) {
                Text("Open shared project records")
            }
        }
        MWCard("Android app updates") {
            Text("Installed: v${BuildConfig.VERSION_NAME} · ${BuildConfig.UPDATE_CHANNEL} channel",
                color = MWText, fontSize = 13.sp)
            Text(updateStatus, color = if (availableUpdate != null) MobileGood else MWMuted,
                fontSize = 13.sp)
            if (availableUpdate != null) {
                Button(onClick = onReviewUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text("Review v${availableUpdate.version} update")
                }
            }
            OutlinedButton(
                onClick = onCheckUpdates,
                enabled = !checkingUpdates,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (checkingUpdates) "Checking…" else "Check for updates now")
            }
        }
        Button(onClick = { onOpenWeb("/portal.html") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Person, null)
            Spacer(Modifier.width(8.dp))
            Text("Profile, subscription & billing")
        }
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, AccountAccessActivity::class.java).putExtra("tab", "forgot-password")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Account security & recovery")
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Sign out")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileAuthenticatedWeb(session: MobileSession, path: String, onClose: () -> Unit) {
    var cookie by remember(path, session.username) { mutableStateOf<String?>(null) }
    var error by remember(path, session.username) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    BackHandler { onClose() }

    DisposableEffect(path, session.username) {
        Thread {
            try {
                val sessionCookie = MobilePortalApi.webSession(session.username, session.password)
                mobileMainThread { cookie = sessionCookie }
            } catch (ex: Exception) {
                mobileMainThread { error = ex.message ?: "Unable to open workspace" }
            }
        }.start()
        onDispose { }
    }

    Scaffold(
        containerColor = MobileNavy,
        topBar = {
            TopAppBar(
                title = { Text("Electrical Workbench", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { OutlinedButton(onClick = onClose, modifier = Modifier.padding(start = 6.dp)) { Text("Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MobileNavy2, titleContentColor = MobileText)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not open workspace", fontWeight = FontWeight.Bold)
                    Text(error.orEmpty(), color = MobileMuted)
                    OutlinedButton(onClick = onClose) { Text("Return to app") }
                }
                cookie == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MobileCyan)
                    Text("Opening secure workspace…", color = MobileMuted)
                }
                else -> key(cookie, path) {
                    val webView = remember {
                        WebView(context).apply {
                            // Match the portal's navy surfaces so Android never flashes
                            // a white WebView background while pages load or scroll.
                            setBackgroundColor(android.graphics.Color.rgb(7, 21, 35))
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                // Use each page's authored colours, not algorithmic inversion.
                                settings.setAlgorithmicDarkeningAllowed(false)
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.textZoom = 105
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    return if (url.startsWith(MOBILE_PORTAL_URL)) false else {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                        true
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.let(::applyMobileWebStyle)
                                }
                            }
                        }
                    }
                    DisposableEffect(webView) {
                        CookieManager.getInstance().setCookie(MOBILE_PORTAL_URL, cookie)
                        CookieManager.getInstance().flush()
                        webView.loadUrl(MOBILE_PORTAL_URL + path)
                        onDispose { webView.stopLoading(); webView.destroy() }
                    }
                    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun parseMobileSession(username: String, password: String, body: String): MobileSession {
    val root = JSONObject(body)
    val user = root.optJSONObject("user") ?: JSONObject()
    val features = root.optJSONObject("features")
    return MobileSession(
        username = user.optString("username", username),
        password = password,
        displayName = user.optString("displayName", username).ifBlank { username },
        email = user.optString("email", ""),
        tier = user.optString("subscriptionTier", "TIER_1"),
        loginProvider = user.optString("loginProvider", "LOCAL"),
        featureCount = features?.length() ?: 0
    )
}

private object MobilePortalApi {
    fun health(): Boolean {
        val connection = URL("$MOBILE_PORTAL_URL/api/public/client-health").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30_000
        connection.readTimeout = 90_000
        return try { connection.responseCode == 200 } finally { connection.disconnect() }
    }

    fun authorizedGet(path: String, username: String, password: String): String {
        val connection = URL(MOBILE_PORTAL_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("Authorization", basic(username, password))
        connection.setRequestProperty("Accept", "application/json")
        return read(connection)
    }

    fun webSession(username: String, password: String): String {
        val body = "username=${encode(username)}&password=${encode(password)}"
        val connection = URL("$MOBILE_PORTAL_URL/login").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        try {
            val code = connection.responseCode
            if (code !in 200..399) throw IllegalStateException("Portal login returned HTTP $code")
            val cookies = connection.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values.flatten().map { it.substringBefore(';') }.filter { it.contains('=') }
            if (cookies.isEmpty()) throw IllegalStateException("Portal did not return a secure session")
            return cookies.joinToString("; ")
        } finally { connection.disconnect() }
    }

    private fun read(connection: HttpURLConnection): String {
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401 || code == 403) throw SecurityException("Username/password was not accepted or the account is disabled")
            if (code !in 200..299) throw IllegalStateException("Portal returned HTTP $code${if (body.isBlank()) "" else ": $body"}")
            return body
        } finally { connection.disconnect() }
    }

    private fun basic(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(StandardCharsets.UTF_8)
        return "Basic ${Base64.getEncoder().encodeToString(raw)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

private fun mobileMainThread(block: () -> Unit) {
    Handler(Looper.getMainLooper()).post(block)
}
