@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import android.telephony.SubscriptionInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smsrelay.data.AppSettings
import com.smsrelay.ui.theme.AppColorPalette

@Composable
internal fun SettingsScreen(
    contentPadding: PaddingValues,
    automationEnabled: Boolean,
    onAutomationChanged: (Boolean) -> Unit,
    onOpenOnboarding: () -> Unit,
    defaultSimId: Int,
    phoneStateAllowed: Boolean,
    activeSims: List<SubscriptionInfo>,
    onRequestPhoneState: () -> Unit,
    onDefaultSimChanged: (Int) -> Unit,
    colorPalette: AppColorPalette,
    onColorPaletteChanged: (AppColorPalette) -> Unit,
) {
    var storeFullContent by remember { mutableStateOf(false) }
    var showSimDialog by remember { mutableStateOf(false) }
    val darkMode = isSystemInDarkTheme()
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { AppTopBar("Settings") },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingGroup("Appearance") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Color palette", style = MaterialTheme.typography.titleSmall)
                    Text("Applies across the app. Light and dark mode follow your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppColorPalette.entries.chunked(2).forEach { palettes ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            palettes.forEach { palette ->
                                FilterChip(
                                    selected = colorPalette == palette,
                                    onClick = { onColorPaletteChanged(palette) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(palette.label) },
                                    leadingIcon = {
                                        Surface(Modifier.size(16.dp), shape = CircleShape, color = if (darkMode) palette.darkAccent else palette.lightAccent) { }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            SettingGroup("Automation") {
                SettingToggle("Master automation", "Stop all automatic sends immediately", automationEnabled, onAutomationChanged)
                SettingRow(
                    "Default SIM",
                    when {
                        !phoneStateAllowed -> "Permission required"
                        else -> activeSims.firstOrNull { it.subscriptionId == defaultSimId }?.let { "SIM ${it.simSlotIndex + 1} · ${it.displayName}" } ?: "Auto · reply on receiving SIM"
                    },
                    onClick = { if (phoneStateAllowed) showSimDialog = true else onRequestPhoneState() },
                )
            }
            SettingGroup("Safety") {
                SettingRow("Automatic send limit", "5 per minute")
                SettingRow("History retention", "30 days")
            }
            SettingGroup("Permissions") {
                SettingRow("Receive SMS", "Not granted", onClick = onOpenOnboarding)
                SettingRow("Send SMS", "Not granted", onClick = onOpenOnboarding)
            }
            SettingGroup("Privacy") { SettingToggle("Store full SMS content", "Off by default", storeFullContent) { storeFullContent = it } }
            SettingGroup("About") {
                SettingRow("App version", "0.1.0")
                SettingRow("Privacy information", "Local-only processing")
                SettingRow("Open source licenses", "View")
            }
        }
    }
    if (showSimDialog && phoneStateAllowed) DefaultSimDialog(defaultSimId, activeSims, { onDefaultSimChanged(it) }, { showSimDialog = false })
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column { content() } }
    }
}

@Composable
private fun SettingToggle(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        AppSwitch(checked, onCheckedChange)
    }
}

@Composable
private fun DefaultSimDialog(current: Int, sims: List<SubscriptionInfo>, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default SIM") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RadioSettingRow("Auto", "Reply on the SIM that received the SMS", current == AppSettings.AUTO_SIM) { onSelect(AppSettings.AUTO_SIM); onDismiss() }
                sims.forEach { info ->
                    RadioSettingRow("SIM ${info.simSlotIndex + 1}", info.displayName?.toString().orEmpty(), current == info.subscriptionId) { onSelect(info.subscriptionId); onDismiss() }
                }
                if (sims.isEmpty()) Text("No active SIM detected.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { },
    )
}

@Composable
private fun RadioSettingRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick == null) Modifier else Modifier.fillMaxWidth()
    TextButton(onClick = { onClick?.invoke() }, modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
