@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun PermissionOnboardingScreen(
    receiveAllowed: Boolean,
    sendAllowed: Boolean,
    onReceiveAllowed: () -> Unit,
    onSendAllowed: () -> Unit,
    onBack: () -> Unit,
) {
    val receivePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onReceiveAllowed() }
    val sendPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onSendAllowed() }

    Scaffold(topBar = { AppTopBar("SMS Permissions", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("SMS Rule Relay needs access to incoming SMS messages so it can evaluate your rules, and permission to send SMS when a rule matches.", style = MaterialTheme.typography.bodyLarge)
            PermissionCard(Icons.Filled.Inbox, "Receive SMS", "Required to detect new incoming SMS messages and check them against your rules.", receiveAllowed) {
                receivePermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }
            PermissionCard(Icons.Filled.Send, "Send SMS", "Required to automatically send an SMS when a rule matches.", sendAllowed) {
                sendPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            }
            Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("[ LOCAL-ONLY ]", style = MaterialTheme.typography.labelLarge)
                    Text("Your messages remain on this device. No cloud account or upload is required.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = receiveAllowed && sendAllowed) { Text("Continue") }
        }
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, description: String, granted: Boolean, onAllow: () -> Unit) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
                Text(if (granted) "Granted" else "Not granted", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            }
            if (!granted) FilledTonalButton(onClick = onAllow) { Text("Allow") }
        }
    }
}
