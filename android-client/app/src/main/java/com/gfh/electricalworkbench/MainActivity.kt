package com.gfh.electricalworkbench

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private const val PORTAL_BASE_URL = "https://remote-javafx-portal.onrender.com"

private val Navy = Color(0xFF06111F)
private val Navy2 = Color(0xFF0A1C2F)
private val Steel = Color(0xFF152A3D)
private val Steel2 = Color(0xFF20384D)
private val ElectricCyan = Color(0xFF22D3EE)
private val ElectricBlue = Color(0xFF3B82F6)
private val SafetyAmber = Color(0xFFF5B942)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFFB6C4D4)
private val TextMuted = Color(0xFFA6B6C8)
private val Good = Color(0xFF5EEAD4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkbenchTheme {
                WorkbenchApp()
            }
        }
    }
}

private data class Session(
    val username: String,
    val password: String,
    val displayName: String,
    val email: String,
    val tier: String,
    val loginProvider: String,
    val featureCount: Int
)

private enum class AppTab { Dashboard, Calculators, Profile }

@Composable
private fun WorkbenchTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = ElectricCyan,
        secondary = ElectricBlue,
        tertiary = SafetyAmber,
        background = Navy,
        surface = Navy2,
        surfaceVariant = Steel,
        outline = Color(0xFF71869C),
        onPrimary = Navy,
        onSecondary = TextPrimary,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun WorkbenchApp() {
    var session by remember { mutableStateOf<Session?>(null) }
    var loggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var cloudStatus by remember { mutableStateOf("Checking cloud…") }
    var cloudOk by remember { mutableStateOf<Boolean?>(null) }
    var tab by remember { mutableStateOf(AppTab.Dashboard) }
    var webPath by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        Thread {
            val ok = runCatching { PortalApi.health() }.getOrDefault(false)
            mainThread {
                cloudOk = ok
                cloudStatus = if (ok) "Cloud connected" else "Cloud waking or unavailable"
            }
        }.start()
        onDispose { }
    }

    if (session == null) {
        LoginScreen(
            loggingIn = loggingIn,
            error = loginError,
            cloudStatus = cloudStatus,
            cloudOk = cloudOk,
            onLogin = { username, password ->
                loggingIn = true
                loginError = null
                Thread {
                    try {
                        val bootstrap = PortalApi.authorizedGet("/api/client/bootstrap", username, password)
                        val parsed = parseSession(username, password, bootstrap)
                        mainThread {
                            session = parsed
                            loggingIn = false
                            tab = AppTab.Dashboard
                        }
                    } catch (ex: Exception) {
                        mainThread {
                            loggingIn = false
                            loginError = ex.message ?: "Sign-in failed"
                        }
                    }
                }.start()
            }
        )
        return
    }

    val activeSession = session!!
    if (webPath != null) {
        AuthenticatedWebScreen(
            session = activeSession,
            path = webPath!!,
            onClose = { webPath = null }
        )
        return
    }

    MainShell(
        session = activeSession,
        selected = tab,
        onSelected = { tab = it },
        onOpenWeb = { webPath = it },
        onLogout = {
            session = null
            tab = AppTab.Dashboard
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    )
}

@Composable
private fun LoginScreen(
    loggingIn: Boolean,
    error: String?,
    cloudStatus: String,
    cloudOk: Boolean?,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF030712), Navy, Color(0xFF0A3150))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ElectricBolt,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "ELECTRICAL WORKBENCH",
                        color = TextPrimary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Text("CEC 2024 · ANDROID", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Field-ready electrical tools.",
                color = TextPrimary,
                fontSize = 29.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your calculator workspace, account and cloud tools in a clean mobile layout.",
                color = TextSecondary,
                lineHeight = 21.sp
            )
            Spacer(Modifier.height(20.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10263A)),
                border = BorderStroke(1.dp, Color(0xFF29455E)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("SECURE ACCOUNT ACCESS", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(7.dp))
                    Text("Sign in", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Use the same account as the desktop and web portal.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !loggingIn,
                        colors = loginFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !loggingIn,
                        colors = loginFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF451D2A)),
                            border = BorderStroke(1.dp, Color(0xFF7F3548)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                error,
                                color = Color(0xFFFFCDD5),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(11.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onLogin(username.trim(), password) },
                        enabled = !loggingIn && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF334155),
                            disabledContentColor = Color(0xFFD5DEE8)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (loggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Connecting…", color = Color.White)
                        } else {
                            Text(
                                "Sign in to Electrical Workbench",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(15.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (cloudOk == true) Good else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            cloudStatus,
                            color = if (cloudOk == true) Good else TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Google sign-in can be added to Android after an Android OAuth client is registered. The current build uses the existing secure portal credentials.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    disabledTextColor = TextSecondary,
    cursorColor = ElectricCyan,
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = Color(0xFF71869C),
    disabledBorderColor = Color(0xFF516579),
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = Color(0xFFD6E0EA),
    disabledLabelColor = TextSecondary,
    focusedContainerColor = Color(0xFF0A1928),
    unfocusedContainerColor = Color(0xFF0A1928),
    disabledContainerColor = Color(0xFF0D1B29)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    session: Session,
    selected: AppTab,
    onSelected: (AppTab) -> Unit,
    onOpenWeb: (String) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        containerColor = Navy,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Electrical Workbench", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("CEC 2024 mobile", color = ElectricCyan, fontSize = 10.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy2, titleContentColor = TextPrimary)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Navy2) {
                NavigationBarItem(
                    selected = selected == AppTab.Dashboard,
                    onClick = { onSelected(AppTab.Dashboard) },
                    icon = { Icon(Icons.Default.Dashboard, null) },
                    label = { Text("Dashboard") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selected == AppTab.Calculators,
                    onClick = { onSelected(AppTab.Calculators) },
                    icon = { Icon(Icons.Default.Calculate, null) },
                    label = { Text("Calculators") },
                    colors = navColors()
                )
                NavigationBarItem(
                    selected = selected == AppTab.Profile,
                    onClick = { onSelected(AppTab.Profile) },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Profile") },
                    colors = navColors()
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selected) {
                AppTab.Dashboard -> DashboardScreen(session, onOpenWeb)
                AppTab.Calculators -> CalculatorsScreen(onOpenWeb)
                AppTab.Profile -> ProfileScreen(session, onOpenWeb, onLogout)
            }
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Navy,
    selectedTextColor = ElectricCyan,
    indicatorColor = ElectricCyan,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary
)

@Composable
private fun DashboardScreen(session: Session, onOpenWeb: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Welcome, ${session.displayName}", fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("Your electrical workspace is connected and ready.", color = TextSecondary)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Plan", session.tier.replace('_', ' '), Modifier.weight(1f))
            StatCard("Features", session.featureCount.toString(), Modifier.weight(1f))
        }

        ElectricalCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDone, null, tint = Good)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cloud connected", fontWeight = FontWeight.Bold)
                    Text(
                        PORTAL_BASE_URL.removePrefix("https://"),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        ElectricalCard {
            Text("CEC Calculator Workspace", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text("Open every calculator currently available on the web portal inside the Android app.", color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            Button(onClick = { onOpenWeb("/calculators.html") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Calculate, null)
                Spacer(Modifier.width(8.dp))
                Text("Open calculator workspace", fontWeight = FontWeight.Bold)
            }
        }

        ElectricalCard {
            Text("Reports & Statistics", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Mobile report history, saved calculations and usage statistics will appear here as those backend features are added.", color = TextSecondary, fontSize = 13.sp)
        }

        ElectricalCard {
            Text("News & Product Updates", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("A placeholder for future CEC study updates, calculator releases and app announcements.", color = TextSecondary, fontSize = 13.sp)
        }

        OutlinedButton(onClick = { onOpenWeb("/portal.html") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Launch, null)
            Spacer(Modifier.width(8.dp))
            Text("Open full web dashboard")
        }
    }
}

@Composable
private fun CalculatorsScreen(onOpenWeb: (String) -> Unit) {
    val sections = listOf(
        "General Rules", "Section 4 · Conductors / Busbars", "Section 8 · Services & Demand", "Section 12 · Wiring Methods",
        "Section 16 · Class 1 & Class 2", "Section 24 · Patient Care", "Section 26 · Electrical Equipment",
        "Section 28 · AC & DC Motors", "Section 32 · Fire Alarm", "Section 42 · Welders",
        "Conduit Bending", "Supports & Straps"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("CEC Calculators", fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("The Android app uses the same live calculation backend and workspace as desktop/web.", color = TextSecondary)

        ElectricalCard {
            Text("Available workspace areas", color = ElectricCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            sections.forEachIndexed { index, name ->
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (index != sections.lastIndex) HorizontalDivider(color = Color(0x223B82F6), modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Button(onClick = { onOpenWeb("/calculators.html") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Calculate, null)
            Spacer(Modifier.width(8.dp))
            Text("Launch calculator workspace", fontWeight = FontWeight.Bold)
        }

        Text(
            "Future Android releases can move the most-used calculators to fully native screens one section at a time while keeping the same server calculation logic.",
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ProfileScreen(session: Session, onOpenWeb: (String) -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Profile & Settings", fontSize = 26.sp, fontWeight = FontWeight.Black)
        ElectricalCard {
            ProfileLine("Display name", session.displayName)
            ProfileLine("Username", session.username)
            ProfileLine("Email", session.email.ifBlank { "Not set" })
            ProfileLine("Subscription", session.tier.replace('_', ' '))
            ProfileLine("Sign-in method", session.loginProvider.ifBlank { "LOCAL" })
        }
        Button(onClick = { onOpenWeb("/portal.html") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Person, null)
            Spacer(Modifier.width(8.dp))
            Text("Manage profile & account")
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Sign out")
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Steel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                color = ElectricCyan,
                fontSize = 19.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ElectricalCard(content: @Composable Column.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Steel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp), content = content)
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Text(label.uppercase(), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedWebScreen(session: Session, path: String, onClose: () -> Unit) {
    var sessionCookie by remember(path, session.username) { mutableStateOf<String?>(null) }
    var error by remember(path, session.username) { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    BackHandler { onClose() }

    DisposableEffect(path, session.username) {
        Thread {
            try {
                val cookie = PortalApi.webSession(session.username, session.password)
                mainThread { sessionCookie = cookie }
            } catch (ex: Exception) {
                mainThread { error = ex.message ?: "Unable to open web workspace" }
            }
        }.start()
        onDispose { }
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Electrical Workbench",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy2,
                    navigationIconContentColor = TextPrimary,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                error != null -> {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Could not open the workspace", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(error ?: "Unknown error", color = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onClose) { Text("Return to app") }
                    }
                }
                sessionCookie == null -> {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ElectricCyan)
                        Spacer(Modifier.height(12.dp))
                        Text("Opening secure workspace…", color = TextSecondary)
                    }
                }
                else -> {
                    key(sessionCookie, path) {
                        val webView = remember {
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.textZoom = 105
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        return if (url.startsWith(PORTAL_BASE_URL)) {
                                            false
                                        } else {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                            true
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.let(::injectMobileWebCss)
                                    }
                                }
                            }
                        }
                        DisposableEffect(webView) {
                            CookieManager.getInstance().setCookie(PORTAL_BASE_URL, sessionCookie)
                            CookieManager.getInstance().flush()
                            webView.loadUrl(PORTAL_BASE_URL + path)
                            onDispose {
                                webView.stopLoading()
                                webView.destroy()
                            }
                        }
                        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

private fun injectMobileWebCss(webView: WebView) {
    val css = """
        @media (max-width: 700px) {
          html, body { max-width: 100vw !important; overflow-x: hidden !important; }
          .top { position: relative !important; top: 0 !important; flex-direction: column !important; align-items: stretch !important; gap: 10px !important; padding: 12px 14px !important; }
          .top > div:last-child { display: flex !important; flex-wrap: wrap !important; gap: 8px !important; }
          .top a, .top button { flex: 1 1 130px !important; min-height: 44px !important; margin: 0 !important; text-align: center !important; white-space: normal !important; }
          .brand { font-size: 18px !important; line-height: 1.2 !important; }
          .shell { display: block !important; width: 100% !important; }
          .nav { position: relative !important; top: 0 !important; min-height: auto !important; padding: 10px 8px !important; display: flex !important; overflow-x: auto !important; gap: 5px !important; border-right: 0 !important; }
          .nav button { flex: 0 0 auto !important; min-height: 42px !important; white-space: nowrap !important; }
          .main { padding: 12px !important; width: 100% !important; }
          .tabs { gap: 6px !important; }
          .tabs button { flex: 1 1 132px !important; min-height: 42px !important; white-space: normal !important; line-height: 1.2 !important; }
          .workspace, .grid, .refgrid, .welder-row { grid-template-columns: minmax(0, 1fr) !important; }
          .welder-row > *, .welder-row > div:last-child { grid-column: auto !important; }
          .card, .ref { min-width: 0 !important; overflow-wrap: anywhere !important; }
          .field input, .field select, .field textarea { min-width: 0 !important; width: 100% !important; font-size: 16px !important; }
          .btn { width: 100% !important; min-height: 46px !important; margin-top: 4px !important; white-space: normal !important; }
          .result { min-width: 0 !important; min-height: 220px !important; white-space: pre-wrap !important; overflow-wrap: anywhere !important; }
        }
    """
    val script = """
        (function() {
          var id = 'electrical-workbench-mobile-fixes';
          var existing = document.getElementById(id);
          if (existing) existing.remove();
          var style = document.createElement('style');
          style.id = id;
          style.textContent = `${css}`;
          document.head.appendChild(style);
        })();
    """
    webView.evaluateJavascript(script, null)
}

private fun parseSession(username: String, password: String, json: String): Session {
    val root = JSONObject(json)
    val user = root.optJSONObject("user") ?: JSONObject()
    val features = root.opt("features")
    val featureCount = when (features) {
        is JSONObject -> features.length()
        is org.json.JSONArray -> features.length()
        else -> 0
    }
    return Session(
        username = user.optString("username", username),
        password = password,
        displayName = user.optString("displayName", username).ifBlank { username },
        email = user.optString("email", ""),
        tier = user.optString("subscriptionTier", "TIER_1"),
        loginProvider = user.optString("loginProvider", "LOCAL"),
        featureCount = featureCount
    )
}

private object PortalApi {
    fun health(): Boolean {
        val connection = (URL("$PORTAL_BASE_URL/api/public/client-health").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 90_000
        }
        return try {
            connection.responseCode == 200
        } finally {
            connection.disconnect()
        }
    }

    fun authorizedGet(path: String, username: String, password: String): String {
        val connection = (URL(PORTAL_BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", basic(username, password))
            setRequestProperty("Accept", "application/json")
        }
        return read(connection)
    }

    fun webSession(username: String, password: String): String {
        val body = "username=${encode(username)}&password=${encode(password)}"
        val connection = (URL("$PORTAL_BASE_URL/login").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        try {
            val code = connection.responseCode
            if (code !in 200..399) throw IllegalStateException("Portal login returned HTTP $code")
            val cookies = connection.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values
                .flatten()
                .map { it.substringBefore(';') }
                .filter { it.contains('=') }
            if (cookies.isEmpty()) throw IllegalStateException("Portal did not return a secure session")
            return cookies.joinToString("; ")
        } finally {
            connection.disconnect()
        }
    }

    private fun read(connection: HttpURLConnection): String {
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401 || code == 403) throw SecurityException("Username/password was not accepted or the account is disabled")
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

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

private fun mainThread(block: () -> Unit) {
    Handler(Looper.getMainLooper()).post(block)
}
