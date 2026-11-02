package com.gfh.electricalworkbench

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val OPENAI_AUTH = "https://auth.openai.com/api/accounts/authorize"
private const val OPENAI_TOKEN = "https://auth.openai.com/api/accounts/oauth/token"
private const val OPENAI_REVOKE = "https://auth.openai.com/api/accounts/oauth/revoke"
private const val OPENAI_JWKS = "https://auth.openai.com/.well-known/jwks.json"
private const val OPENAI_ISSUER = "https://auth.openai.com"
private const val OPENAI_RESOURCE = "https://api.openai.com/v1"
private const val OPENAI_MODELS = "https://api.openai.com/v1/models"
private const val OPENAI_RESPONSES = "https://api.openai.com/v1/responses"
private const val OPENAI_SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
private const val OPENAI_DYNAMIC_CLIENT = "dynamic_agent_client"
private const val OPENAI_APP_NAME = "Electrical Workbench"
private const val OPENAI_MANAGE_USAGE = "https://chatgpt.com/settings/usage"

private data class ChatGptProfile(
    val clientId: String,
    val hostId: String,
    val subject: String,
    val email: String,
    val name: String,
    val idToken: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val scopes: Set<String>,
    val expiresAtMs: Long
) {
    val sharingEnabled: Boolean
        get() = !accessToken.isNullOrBlank() && scopes.contains("chatgpt.tokens.use.direct")
}

private data class ChatGptModel(val slug: String, val displayName: String)
private data class ChatGptLine(val role: String, val text: String)

@Composable
internal fun AskChatGptWorkspace(username: String) {
    val context = LocalContext.current
    val store = remember(username) { ChatGptCredentialStore(context, username) }
    val client = remember(username) { ChatGptDirectClient(store) }
    var profile by remember { mutableStateOf(store.loadProfile()) }
    var models by remember { mutableStateOf(emptyList<ChatGptModel>()) }
    var selectedModel by remember { mutableStateOf("") }
    var modelMenu by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var streamingText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatGptLine>() }
    val chatScroll = rememberScrollState()

    fun refreshModels() {
        val current = store.loadProfile()
        if (current?.sharingEnabled != true || busy) return
        busy = true
        error = ""
        status = "Loading models…"
        Thread {
            try {
                val found = client.listModels()
                chatGptMain {
                    profile = store.loadProfile()
                    models = found
                    if (selectedModel.isBlank() || found.none { it.slug == selectedModel }) {
                        selectedModel = found.firstOrNull()?.slug.orEmpty()
                    }
                    status = if (found.isEmpty()) "No models were returned for this ChatGPT account." else "Connected · ${found.size} model(s) available"
                    busy = false
                }
            } catch (ex: Exception) {
                chatGptMain {
                    error = ex.message ?: "Unable to load ChatGPT models."
                    status = ""
                    busy = false
                }
            }
        }.start()
    }

    fun startSignIn(forceNewRegistration: Boolean = false) {
        if (busy) return
        busy = true
        error = ""
        status = "Opening ChatGPT sign-in…"
        val existing = if (forceNewRegistration) null else store.loadProfile()
        Thread {
            try {
                val connected = ChatGptOAuth.connect(context, store, existing) { message ->
                    chatGptMain { status = message }
                }
                chatGptMain {
                    profile = connected
                    models = emptyList()
                    selectedModel = ""
                    status = if (connected.sharingEnabled) "ChatGPT account connected. Loading models…" else "Signed in, but ChatGPT plan usage was not enabled."
                    busy = false
                    if (connected.sharingEnabled) refreshModels()
                }
            } catch (ex: Exception) {
                chatGptMain {
                    error = ex.message ?: "ChatGPT sign-in failed."
                    status = ""
                    busy = false
                }
            }
        }.start()
    }

    fun disconnect() {
        if (busy) return
        busy = true
        error = ""
        status = "Disconnecting ChatGPT…"
        Thread {
            val remoteRevoked = runCatching { client.disconnect() }.getOrDefault(false)
            chatGptMain {
                profile = store.loadProfile()
                models = emptyList()
                selectedModel = ""
                messages.clear()
                streamingText = ""
                busy = false
                status = if (remoteRevoked) "ChatGPT session disconnected." else "Disconnected locally. Remote revocation could not be confirmed; you can also disconnect it in ChatGPT settings."
            }
        }.start()
    }

    fun send() {
        val text = input.trim()
        val model = selectedModel
        if (text.isBlank() || model.isBlank() || busy) return
        val userLine = ChatGptLine("user", text)
        messages.add(userLine)
        input = ""
        busy = true
        error = ""
        status = "ChatGPT is responding…"
        streamingText = ""
        val requestHistory = messages.takeLast(20)
        Thread {
            try {
                val finalText = client.streamResponse(model, requestHistory) { delta ->
                    chatGptMain { streamingText += delta }
                }
                chatGptMain {
                    val answer = finalText.ifBlank { streamingText }.trim()
                    if (answer.isNotBlank()) messages.add(ChatGptLine("assistant", answer))
                    streamingText = ""
                    profile = store.loadProfile()
                    status = "Using ChatGPT plan"
                    busy = false
                }
            } catch (ex: Exception) {
                chatGptMain {
                    error = ex.message ?: "ChatGPT request failed."
                    streamingText = ""
                    status = ""
                    busy = false
                }
            }
        }.start()
    }

    LaunchedEffect(profile?.clientId, profile?.accessToken) {
        if (profile?.sharingEnabled == true && models.isEmpty() && !busy) refreshModels()
    }
    LaunchedEffect(messages.size, streamingText.length) {
        if (chatScroll.maxValue > 0) chatScroll.animateScrollTo(chatScroll.maxValue)
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Ask ChatGPT", color = MWText, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text("Connect a ChatGPT account and use an eligible ChatGPT plan for AI requests directly inside Electrical Workbench.", color = MWMuted, fontSize = 13.sp)

        MWCard("Important") {
            Text("ChatGPT can make mistakes. For electrical work, verify CEC/OESC requirements, manufacturer instructions, engineering documents and site safety procedures before acting on an answer.", color = MWAmber, fontSize = 12.sp)
            Text("Connecting does not give Electrical Workbench access to your existing ChatGPT conversations or ChatGPT memory.", color = MWMuted, fontSize = 12.sp)
        }

        val active = profile
        if (active == null || active.accessToken.isNullOrBlank()) {
            MWCard("Connect your ChatGPT account") {
                Text("Use OpenAI's Continue with ChatGPT authorization. No OpenAI API key is entered or stored in the app.", color = MWText)
                Button(onClick = { startSignIn(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (active == null) "Continue with ChatGPT" else "Reconnect ChatGPT")
                }
                if (active != null) {
                    OutlinedButton(onClick = { startSignIn(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Connect a different ChatGPT account") }
                }
            }
        } else {
            MWCard("ChatGPT connection") {
                Text(active.name.ifBlank { active.email.ifBlank { "Connected account" } }, color = MWText, fontWeight = FontWeight.Bold)
                if (active.email.isNotBlank()) Text(active.email, color = MWMuted, fontSize = 12.sp)
                Text(if (active.sharingEnabled) "ChatGPT plan usage enabled" else "Identity connected · plan usage not enabled", color = if (active.sharingEnabled) MWGood else MWAmber, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { refreshModels() }, enabled = !busy && active.sharingEnabled, modifier = Modifier.weight(1f)) { Text("Refresh models") }
                    OutlinedButton(onClick = { openExternal(context, OPENAI_MANAGE_USAGE) }, modifier = Modifier.weight(1f)) { Text("Manage usage") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { startSignIn(false) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Reconnect") }
                    OutlinedButton(onClick = { disconnect() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Disconnect") }
                }
            }
        }

        if (profile?.sharingEnabled == true) {
            MWCard("Model") {
                OutlinedButton(onClick = { modelMenu = true }, enabled = models.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                    val label = models.firstOrNull { it.slug == selectedModel }?.displayName ?: if (models.isEmpty()) "Loading models…" else "Choose model"
                    Text(label)
                }
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    models.forEach { model ->
                        DropdownMenuItem(text = { Text(model.displayName) }, onClick = { selectedModel = model.slug; modelMenu = false })
                    }
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(chatScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty() && streamingText.isBlank()) {
                    MWCard("Start a conversation") {
                        Text("Ask about electrical theory, troubleshooting, calculations, study topics, project planning, or anything else you would normally ask ChatGPT.", color = MWMuted)
                    }
                }
                messages.forEach { line ->
                    MWCard(if (line.role == "user") "You" else "ChatGPT") {
                        Text(line.text, color = MWText)
                    }
                }
                if (streamingText.isNotBlank()) {
                    MWCard("ChatGPT") { Text(streamingText, color = MWText) }
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Ask ChatGPT") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { messages.clear(); streamingText = "" }, enabled = !busy && messages.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Clear chat") }
                Button(onClick = { send() }, enabled = !busy && input.isNotBlank() && selectedModel.isNotBlank(), modifier = Modifier.weight(1f)) { Text(if (busy) "Working…" else "Send") }
            }
        }

        if (status.isNotBlank()) Text(status, color = MWCyan, fontSize = 12.sp)
        if (error.isNotBlank()) Text(error, color = MWDanger, fontSize = 12.sp)
    }
}

private object ChatGptOAuth {
    fun connect(
        context: Context,
        store: ChatGptCredentialStore,
        existing: ChatGptProfile?,
        onStatus: (String) -> Unit
    ): ChatGptProfile {
        val callback = LoopbackCallback()
        try {
            val state = randomUrlSafe(32)
            val nonce = randomUrlSafe(32)
            val verifier = randomUrlSafe(64)
            val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
            val hostId = store.hostId()
            val requestedClient = existing?.clientId ?: OPENAI_DYNAMIC_CLIENT
            val builder = Uri.parse(OPENAI_AUTH).buildUpon()
                .appendQueryParameter("client_id", requestedClient)
                .appendQueryParameter("ext_agent_host_id", hostId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", callback.redirectUri)
                .appendQueryParameter("scope", OPENAI_SCOPES)
                .appendQueryParameter("resource", OPENAI_RESOURCE)
                .appendQueryParameter("state", state)
                .appendQueryParameter("nonce", nonce)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
            if (existing == null) builder.appendQueryParameter("agent_name_hint", OPENAI_APP_NAME)
            if (existing != null && !existing.idToken.isNullOrBlank()) builder.appendQueryParameter("id_token_hint", existing.idToken)
            if (existing != null && existing.email.isNotBlank()) builder.appendQueryParameter("login_hint", existing.email)
            val authUri = builder.build()

            onStatus("Waiting for ChatGPT authorization in your browser…")
            chatGptMain {
                val intent = Intent(Intent.ACTION_VIEW, authUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }

            val params = callback.await()
            if (params["state"] != state) throw SecurityException("ChatGPT sign-in returned an invalid state value.")
            params["error"]?.let { throw SecurityException(if (it == "access_denied") "ChatGPT authorization was cancelled or not approved." else "ChatGPT authorization failed: $it") }
            val code = params["code"] ?: throw IllegalStateException("ChatGPT did not return an authorization code.")
            val issuedClient = params["client_id"] ?: existing?.clientId ?: throw IllegalStateException("ChatGPT registration did not return an issued client ID.")
            if (existing != null && issuedClient != existing.clientId) throw SecurityException("ChatGPT returned a different client registration than expected.")

            onStatus("Verifying your ChatGPT connection…")
            val token = postForm(
                OPENAI_TOKEN,
                linkedMapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to issuedClient,
                    "code" to code,
                    "code_verifier" to verifier,
                    "redirect_uri" to callback.redirectUri,
                    "resource" to OPENAI_RESOURCE
                )
            )
            val idToken = token.optString("id_token").takeIf { it.isNotBlank() } ?: throw SecurityException("ChatGPT did not return a verifiable identity token.")
            val identity = verifyIdToken(idToken, issuedClient, nonce)
            if (existing != null && existing.subject.isNotBlank() && identity.subject != existing.subject) throw SecurityException("The authorized ChatGPT account did not match the saved connection.")

            val scopeText = token.optString("scope").ifBlank { params["scope"].orEmpty() }
            val scopes = scopeText.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
            val access = token.optString("access_token").takeIf { it.isNotBlank() }
            val refresh = token.optString("refresh_token").takeIf { it.isNotBlank() }
            val expiresIn = token.optLong("expires_in", 3600L)
            val profile = ChatGptProfile(
                clientId = issuedClient,
                hostId = hostId,
                subject = identity.subject,
                email = identity.email,
                name = identity.name,
                idToken = idToken,
                accessToken = access,
                refreshToken = refresh,
                scopes = scopes,
                expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L
            )
            store.saveProfile(profile)
            return profile
        } finally {
            callback.close()
        }
    }
}

private class ChatGptDirectClient(private val store: ChatGptCredentialStore) {
    fun listModels(): List<ChatGptModel> {
        val profile = ensureAccess()
        val connection = (URL(OPENAI_MODELS).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer ${profile.accessToken}")
            setRequestProperty("Accept", "application/json")
        }
        val body = readHttp(connection)
        val root = JSONObject(body)
        val array = root.optJSONArray("models") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val visibility = item.optString("visibility")
                val slug = item.optString("slug")
                if (slug.isNotBlank() && (visibility.isBlank() || visibility == "list")) {
                    add(ChatGptModel(slug, item.optString("display_name").ifBlank { slug }))
                }
            }
        }
    }

    fun streamResponse(model: String, history: List<ChatGptLine>, onDelta: (String) -> Unit): String {
        val profile = ensureAccess()
        val input = JSONArray()
        history.forEach { line -> input.put(JSONObject().put("role", line.role).put("content", line.text)) }
        val request = JSONObject()
            .put("model", model)
            .put("instructions", "You are ChatGPT inside Electrical Workbench. Answer normally. When discussing electrical code, safety, energized work, protection settings, or field procedures, clearly distinguish general guidance from authoritative requirements and encourage verification against the applicable CEC/OESC, manufacturer instructions, engineering documents, and site procedures.")
            .put("input", input)
            .put("store", false)
            .put("stream", true)

        val connection = (URL(OPENAI_RESPONSES).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 180_000
            setRequestProperty("Authorization", "Bearer ${profile.accessToken}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
        }
        connection.outputStream.use { it.write(request.toString().toByteArray(StandardCharsets.UTF_8)) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            throw IllegalStateException(openAiError(code, body))
        }

        val collected = StringBuilder()
        var completed = false
        connection.inputStream.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isBlank() || data == "[DONE]") continue
                val event = runCatching { JSONObject(data) }.getOrNull() ?: continue
                when (event.optString("type")) {
                    "response.output_text.delta" -> {
                        val delta = event.optString("delta")
                        if (delta.isNotEmpty()) {
                            collected.append(delta)
                            onDelta(delta)
                        }
                    }
                    "response.completed" -> {
                        completed = true
                        if (collected.isEmpty()) {
                            val fallback = extractCompletedText(event.optJSONObject("response"))
                            if (fallback.isNotBlank()) collected.append(fallback)
                        }
                    }
                    "response.failed" -> {
                        val response = event.optJSONObject("response")
                        val err = response?.optJSONObject("error")
                        val errorCode = err?.optString("code").orEmpty()
                        val message = err?.optString("message").orEmpty()
                        throw IllegalStateException(shareErrorMessage(errorCode, message))
                    }
                    "response.incomplete" -> throw IllegalStateException("ChatGPT ended the response before completion. Please try again.")
                    "error" -> throw IllegalStateException(event.optString("message").ifBlank { "ChatGPT returned an error." })
                }
            }
        }
        connection.disconnect()
        if (!completed) throw IllegalStateException("ChatGPT stream ended before response.completed.")
        return collected.toString()
    }

    fun disconnect(): Boolean {
        val profile = store.loadProfile() ?: return true
        var revoked = true
        if (!profile.refreshToken.isNullOrBlank()) {
            revoked = runCatching {
                postForm(
                    OPENAI_REVOKE,
                    linkedMapOf(
                        "token" to profile.refreshToken,
                        "token_type_hint" to "refresh_token",
                        "client_id" to profile.clientId
                    )
                )
                true
            }.getOrDefault(false)
        }
        store.saveProfile(profile.copy(idToken = null, accessToken = null, refreshToken = null, scopes = emptySet(), expiresAtMs = 0L))
        return revoked
    }

    private fun ensureAccess(): ChatGptProfile {
        var profile = store.loadProfile() ?: throw IllegalStateException("Connect a ChatGPT account first.")
        if (!profile.sharingEnabled) throw IllegalStateException("ChatGPT plan usage is not enabled for this connection. Reconnect and approve plan usage.")
        if (profile.expiresAtMs > System.currentTimeMillis() + 60_000L) return profile
        val refresh = profile.refreshToken ?: throw IllegalStateException("Your ChatGPT session expired. Reconnect the account.")
        val token = postForm(
            OPENAI_TOKEN,
            linkedMapOf(
                "grant_type" to "refresh_token",
                "client_id" to profile.clientId,
                "refresh_token" to refresh,
                "resource" to OPENAI_RESOURCE
            )
        )
        val access = token.optString("access_token").takeIf { it.isNotBlank() } ?: throw IllegalStateException("ChatGPT token refresh did not return an access token.")
        val newRefresh = token.optString("refresh_token").takeIf { it.isNotBlank() } ?: refresh
        val newId = token.optString("id_token").takeIf { it.isNotBlank() } ?: profile.idToken
        val scopeText = token.optString("scope")
        val newScopes = if (scopeText.isBlank()) profile.scopes else scopeText.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        profile = profile.copy(
            accessToken = access,
            refreshToken = newRefresh,
            idToken = newId,
            scopes = newScopes,
            expiresAtMs = System.currentTimeMillis() + token.optLong("expires_in", 3600L) * 1000L
        )
        store.saveProfile(profile)
        return profile
    }
}

private class ChatGptCredentialStore(context: Context, username: String) {
    private val appContext = context.applicationContext
    private val safeUser = username.ifBlank { "default" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private val prefs = appContext.getSharedPreferences("chatgpt_connection_$safeUser", Context.MODE_PRIVATE)
    private val keyAlias = "electrical_workbench_chatgpt_${MessageDigest.getInstance("SHA-256").digest(safeUser.toByteArray()).take(8).joinToString("") { "%02x".format(it) }}"

    fun hostId(): String {
        val saved = prefs.getString("host_id", null)
        if (!saved.isNullOrBlank()) return saved
        val generated = "urn:uuid:${UUID.randomUUID()}"
        prefs.edit().putString("host_id", generated).apply()
        return generated
    }

    fun saveProfile(profile: ChatGptProfile) {
        val scopes = JSONArray().apply { profile.scopes.sorted().forEach { put(it) } }
        val json = JSONObject()
            .put("client_id", profile.clientId)
            .put("host_id", profile.hostId)
            .put("subject", profile.subject)
            .put("email", profile.email)
            .put("name", profile.name)
            .put("id_token", profile.idToken ?: JSONObject.NULL)
            .put("access_token", profile.accessToken ?: JSONObject.NULL)
            .put("refresh_token", profile.refreshToken ?: JSONObject.NULL)
            .put("scopes", scopes)
            .put("expires_at", profile.expiresAtMs)
        prefs.edit().putString("profile", encrypt(json.toString())).apply()
    }

    fun loadProfile(): ChatGptProfile? {
        val raw = prefs.getString("profile", null) ?: return null
        return runCatching {
            val json = JSONObject(decrypt(raw))
            val array = json.optJSONArray("scopes") ?: JSONArray()
            val scopes = buildSet { for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
            ChatGptProfile(
                clientId = json.getString("client_id"),
                hostId = json.optString("host_id").ifBlank { hostId() },
                subject = json.optString("subject"),
                email = json.optString("email"),
                name = json.optString("name"),
                idToken = json.optString("id_token").takeIf { it.isNotBlank() && it != "null" },
                accessToken = json.optString("access_token").takeIf { it.isNotBlank() && it != "null" },
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() && it != "null" },
                scopes = scopes,
                expiresAtMs = json.optLong("expires_at", 0L)
            )
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$encrypted"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }
}

private data class OpenAiIdentity(val subject: String, val email: String, val name: String)

private fun verifyIdToken(token: String, clientId: String, nonce: String): OpenAiIdentity {
    val parts = token.split('.')
    if (parts.size != 3) throw SecurityException("ChatGPT returned an invalid identity token.")
    val header = JSONObject(String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8))
    if (header.optString("alg") != "RS256") throw SecurityException("Unsupported ChatGPT identity signature algorithm.")
    val kid = header.optString("kid")
    if (kid.isBlank()) throw SecurityException("ChatGPT identity token did not identify a signing key.")

    val jwks = JSONObject(httpGet(OPENAI_JWKS)).getJSONArray("keys")
    var jwk: JSONObject? = null
    for (i in 0 until jwks.length()) {
        val item = jwks.optJSONObject(i) ?: continue
        if (item.optString("kid") == kid && item.optString("kty") == "RSA") { jwk = item; break }
    }
    val key = jwk ?: throw SecurityException("ChatGPT signing key was not found.")
    val modulus = BigInteger(1, base64UrlDecode(key.getString("n")))
    val exponent = BigInteger(1, base64UrlDecode(key.getString("e")))
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initVerify(publicKey)
    signature.update("${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.US_ASCII))
    if (!signature.verify(base64UrlDecode(parts[2]))) throw SecurityException("ChatGPT identity signature validation failed.")

    val claims = JSONObject(String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8))
    if (claims.optString("iss") != OPENAI_ISSUER) throw SecurityException("ChatGPT identity issuer did not match OpenAI.")
    val aud = claims.opt("aud")
    val audienceMatches = when (aud) {
        is String -> aud == clientId
        is JSONArray -> (0 until aud.length()).any { aud.optString(it) == clientId }
        else -> false
    }
    if (!audienceMatches) throw SecurityException("ChatGPT identity token was not issued for this app connection.")
    if (claims.optString("nonce") != nonce) throw SecurityException("ChatGPT identity nonce did not match the sign-in attempt.")
    val now = System.currentTimeMillis() / 1000L
    if (claims.optLong("exp", 0L) < now - 5L) throw SecurityException("ChatGPT identity token has expired.")
    if (claims.optLong("iat", 0L) <= 0L || claims.optLong("iat", 0L) > now + 300L) throw SecurityException("ChatGPT identity token has an invalid issue time.")
    val subject = claims.optString("sub")
    if (subject.isBlank()) throw SecurityException("ChatGPT identity token did not contain a subject.")
    return OpenAiIdentity(subject, claims.optString("email"), claims.optString("name"))
}

private class LoopbackCallback : AutoCloseable {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 300_000 }
    val redirectUri: String = "http://127.0.0.1:${server.localPort}/auth/callback"

    fun await(): Map<String, String> {
        val socket = server.accept()
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.UTF_8))
            val request = reader.readLine() ?: throw IllegalStateException("ChatGPT callback was empty.")
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
            }
            val target = request.split(' ').getOrNull(1) ?: throw IllegalStateException("ChatGPT callback was malformed.")
            val uri = Uri.parse("http://127.0.0.1$target")
            val result = uri.queryParameterNames.associateWith { key -> uri.getQueryParameter(key).orEmpty() }
            val body = "<html><body style='font-family:sans-serif;background:#06111f;color:#f1f5f9;padding:32px'><h2>Electrical Workbench connected</h2><p>You can return to the app now.</p></body></html>"
            val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\nConnection: close\r\n\r\n$body"
            it.getOutputStream().use { output -> output.write(response.toByteArray(StandardCharsets.UTF_8)); output.flush() }
            return result
        }
    }

    override fun close() { runCatching { server.close() } }
}

private fun postForm(url: String, params: Map<String, String>): JSONObject {
    val body = params.entries.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 20_000
        readTimeout = 60_000
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        setRequestProperty("Accept", "application/json")
    }
    connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
    val text = readHttp(connection)
    return if (text.isBlank()) JSONObject() else JSONObject(text)
}

private fun httpGet(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/json")
    }
    return readHttp(connection)
}

private fun readHttp(connection: HttpURLConnection): String {
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException(openAiError(code, body))
        return body
    } finally {
        connection.disconnect()
    }
}

private fun openAiError(code: Int, body: String): String {
    val root = runCatching { JSONObject(body) }.getOrNull()
    val error = root?.optJSONObject("error")
    val errorCode = error?.optString("code").orEmpty()
    val message = error?.optString("message").orEmpty()
    if (errorCode == "subscription_sharing_usage_limit_exceeded" || errorCode == "subscription_sharing_usage_unavailable") {
        return shareErrorMessage(errorCode, message)
    }
    return message.ifBlank { "OpenAI returned HTTP $code${if (errorCode.isBlank()) "" else " ($errorCode)"}." }
}

private fun shareErrorMessage(code: String, message: String): String = when (code) {
    "subscription_sharing_usage_limit_exceeded" -> "Your ChatGPT app usage limit has been reached. Open Manage usage to review or change the limit."
    "subscription_sharing_usage_unavailable" -> "ChatGPT plan usage is currently unavailable for this account or app connection."
    else -> message.ifBlank { if (code.isBlank()) "ChatGPT request failed." else "ChatGPT request failed: $code" }
}

private fun extractCompletedText(response: JSONObject?): String {
    val output = response?.optJSONArray("output") ?: return ""
    val text = StringBuilder()
    for (i in 0 until output.length()) {
        val item = output.optJSONObject(i) ?: continue
        val content = item.optJSONArray("content") ?: continue
        for (j in 0 until content.length()) {
            val part = content.optJSONObject(j) ?: continue
            if (part.optString("type") == "output_text") text.append(part.optString("text"))
        }
    }
    return text.toString()
}

private fun randomUrlSafe(bytes: Int): String {
    val data = ByteArray(bytes)
    SecureRandom().nextBytes(data)
    return base64Url(data)
}

private fun base64Url(data: ByteArray): String = Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
private fun base64UrlDecode(text: String): ByteArray = Base64.decode(text, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
private fun chatGptMain(block: () -> Unit) { Handler(Looper.getMainLooper()).post(block) }
