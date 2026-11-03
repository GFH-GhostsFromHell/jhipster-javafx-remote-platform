package com.gfh.electricalworkbench

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AccountEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AccountEntryScreen() }
    }

    @Composable
    private fun AccountEntryScreen() {
        val navy = Color(0xFF06111F)
        val panel = Color(0xFF10263A)
        val cyan = Color(0xFF22D3EE)
        val text = Color(0xFFF1F5F9)
        val muted = Color(0xFFB6C4D4)
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = cyan,
                background = navy,
                surface = panel,
                onPrimary = navy,
                onBackground = text,
                onSurface = text
            )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF030712), navy, Color(0xFF0A3150))))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("⚡ ELECTRICAL WORKBENCH", color = cyan, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                Text("Account access", color = text, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text("Sign in, create an account, verify your email, or recover your username/password.", color = muted)
                Spacer(Modifier.height(22.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = panel),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Trouble signing in?", color = cyan, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { openAccount("forgot-username") },
                                modifier = Modifier.weight(1f)
                            ) { Text("Find username") }
                            OutlinedButton(
                                onClick = { openAccount("forgot-password") },
                                modifier = Modifier.weight(1f)
                            ) { Text("Reset password") }
                        }

                        Button(
                            onClick = { startActivity(Intent(this@AccountEntryActivity, MobileWorkbenchActivity::class.java)); finish() },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Sign in") }

                        OutlinedButton(onClick = { openAccount("signup") }, modifier = Modifier.fillMaxWidth()) {
                            Text("Create account")
                        }
                        OutlinedButton(onClick = { openAccount("verify") }, modifier = Modifier.fillMaxWidth()) {
                            Text("Verify email code")
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Tier selection is available during registration. Paid tiers only activate after confirmed checkout.", color = muted, fontSize = 12.sp)
            }
        }
    }

    private fun openAccount(tab: String) {
        startActivity(Intent(this, AccountAccessActivity::class.java).putExtra("tab", tab))
    }
}
