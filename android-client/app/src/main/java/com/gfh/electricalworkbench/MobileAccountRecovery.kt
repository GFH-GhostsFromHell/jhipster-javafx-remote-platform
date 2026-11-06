package com.gfh.electricalworkbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

private const val ACCOUNT_RECOVERY_BASE_URL = "https://remote-javafx-portal.onrender.com"

private enum class RecoveryDialog { USERNAME, PASSWORD }

@Composable
internal fun MobileAccountRecovery() {
    var dialog by remember { mutableStateOf<RecoveryDialog?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Trouble signing in?",
            color = MWMuted,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { dialog = RecoveryDialog.USERNAME },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Find username") }
            OutlinedButton(
                onClick = { dialog = RecoveryDialog.PASSWORD },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reset password") }
        }
    }

    when (dialog) {
        RecoveryDialog.USERNAME -> UsernameReminderDialog(onDismiss = { dialog = null })
        RecoveryDialog.PASSWORD -> PasswordResetDialog(onDismiss = { dialog = null })
        null -> Unit
    }
}

@Composable
private fun UsernameReminderDialog(onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Find your username") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the email address on your Electrical Workbench account. We’ll email the username associated with it.")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; status = null },
                    label = { Text("Email address") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                status?.let {
                    Text(
                        it,
                        color = if (success) MWGood else MWDanger,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (success) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(
                    enabled = !busy && email.isNotBlank(),
                    onClick = {
                        busy = true
                        status = "Sending username reminder…"
                        Thread {
                            try {
                                val body = MobileRecoveryApi.post(
                                    "/api/public/recovery/username",
                                    JSONObject().put("email", email.trim())
                                )
                                val message = JSONObject(body).optString(
                                    "message",
                                    "If that email is associated with an account, a username reminder has been sent."
                                )
                                recoveryMainThread {
                                    busy = false
                                    success = true
                                    status = message
                                }
                            } catch (ex: Exception) {
                                recoveryMainThread {
                                    busy = false
                                    success = false
                                    status = ex.message ?: "Username reminder could not be sent."
                                }
                            }
                        }.start()
                    }
                ) { Text(if (busy) "Sending…" else "Send reminder") }
            }
        },
        dismissButton = {
            if (!success) TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PasswordResetDialog(onDismiss: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var successStatus by remember { mutableStateOf(false) }

    fun sendCode() {
        if (account.isBlank()) return
        busy = true
        status = "Sending reset code…"
        successStatus = false
        Thread {
            try {
                val body = MobileRecoveryApi.post(
                    "/api/public/recovery/password/start",
                    JSONObject().put("account", account.trim())
                )
                val message = JSONObject(body).optString(
                    "message",
                    "If a password account matches, a reset code has been sent to its email address."
                )
                recoveryMainThread {
                    if (!account.contains('@')) username = account.trim()
                    codeSent = true
                    busy = false
                    successStatus = true
                    status = message
                }
            } catch (ex: Exception) {
                recoveryMainThread {
                    busy = false
                    successStatus = false
                    status = ex.message ?: "Reset code could not be sent."
                }
            }
        }.start()
    }

    fun completeReset() {
        if (username.isBlank() || code.length != 6 || newPassword.length < 8) return
        busy = true
        status = "Resetting password…"
        successStatus = false
        Thread {
            try {
                val body = MobileRecoveryApi.post(
                    "/api/public/recovery/password/complete",
                    JSONObject()
                        .put("username", username.trim())
                        .put("code", code.trim())
                        .put("newPassword", newPassword)
                )
                val message = JSONObject(body).optString(
                    "message",
                    "Your password has been reset. You can sign in now."
                )
                recoveryMainThread {
                    busy = false
                    completed = true
                    successStatus = true
                    status = message
                }
            } catch (ex: Exception) {
                recoveryMainThread {
                    busy = false
                    successStatus = false
                    status = ex.message ?: "Password could not be reset."
                }
            }
        }.start()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Reset your password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!codeSent) {
                    Text("Enter your username or email address. We’ll send a 6-digit reset code to the email on the account.")
                    OutlinedTextField(
                        value = account,
                        onValueChange = { account = it; status = null },
                        label = { Text("Username or email") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (!completed) {
                    Text("Enter the username shown in the reset email, the 6-digit code, and a new password.")
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("6-digit reset code") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New password (8+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Google-sign-in accounts use Google account recovery instead of a local password reset.", color = MWMuted)
                }
                status?.let {
                    Text(
                        it,
                        color = if (successStatus) MWGood else MWDanger,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            when {
                completed -> TextButton(onClick = onDismiss) { Text("Back to sign in") }
                !codeSent -> TextButton(
                    enabled = !busy && account.isNotBlank(),
                    onClick = { sendCode() }
                ) { Text(if (busy) "Sending…" else "Send reset code") }
                else -> TextButton(
                    enabled = !busy && username.isNotBlank() && code.length == 6 && newPassword.length >= 8,
                    onClick = { completeReset() }
                ) { Text(if (busy) "Resetting…" else "Reset password") }
            }
        },
        dismissButton = {
            if (!completed) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (codeSent) {
                        TextButton(enabled = !busy, onClick = { sendCode() }) { Text("Resend code") }
                    }
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    )
}

private object MobileRecoveryApi {
    fun post(path: String, payload: JSONObject): String {
        val connection = (URL(ACCOUNT_RECOVERY_BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use {
                it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching {
                    JSONObject(body).optString("detail").ifBlank {
                        JSONObject(body).optString("message")
                    }
                }.getOrDefault("")
                throw IllegalStateException(
                    detail.ifBlank { "Account recovery returned HTTP $code." }
                )
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}

private fun recoveryMainThread(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}
