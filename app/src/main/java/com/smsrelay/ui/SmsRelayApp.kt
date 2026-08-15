@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class AppScreen { RULES, HISTORY, SETTINGS, EDITOR, TESTER, DETAILS, ONBOARDING }
private enum class HistoryFilter { ALL, SENT, FAILED, BLOCKED }

@Composable
fun SmsRelayApp() {
    var screen by remember { mutableStateOf(AppScreen.RULES) }
    var automationEnabled by remember { mutableStateOf(true) }
    var deleteDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(hostState = remember { androidx.compose.material3.SnackbarHostState() }) },
        bottomBar = {
            if (screen == AppScreen.RULES || screen == AppScreen.HISTORY || screen == AppScreen.SETTINGS) {
                MainNavigation(selected = screen, onSelect = { screen = it })
            }
        },
    ) { innerPadding ->
        when (screen) {
            AppScreen.RULES -> RulesScreen(
                contentPadding = innerPadding,
                automationEnabled = automationEnabled,
                onAutomationChanged = { automationEnabled = it },
                onCreate = { screen = AppScreen.EDITOR },
                onEdit = { screen = AppScreen.EDITOR },
                onDelete = { deleteDialogVisible = true },
            )
            AppScreen.HISTORY -> HistoryScreen(innerPadding, onOpenDetails = { screen = AppScreen.DETAILS })
            AppScreen.SETTINGS -> SettingsScreen(
                contentPadding = innerPadding,
                automationEnabled = automationEnabled,
                onAutomationChanged = { automationEnabled = it },
                onOpenOnboarding = { screen = AppScreen.ONBOARDING },
            )
            AppScreen.EDITOR -> RuleEditorScreen(
                onBack = { screen = AppScreen.RULES },
                onTest = { screen = AppScreen.TESTER },
            )
            AppScreen.TESTER -> RuleTesterScreen(onBack = { screen = AppScreen.EDITOR })
            AppScreen.DETAILS -> ExecutionDetailsScreen(onBack = { screen = AppScreen.HISTORY })
            AppScreen.ONBOARDING -> PermissionOnboardingScreen(onBack = { screen = AppScreen.SETTINGS })
        }
    }

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text("Delete this rule?") },
            text = { Text("Bank Credit Alert will no longer run. This does not delete its history.") },
            confirmButton = {
                TextButton(onClick = { deleteDialogVisible = false }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteDialogVisible = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MainNavigation(selected: AppScreen, onSelect: (AppScreen) -> Unit) {
    NavigationBar {
        NavigationBarItem(selected == AppScreen.RULES, { onSelect(AppScreen.RULES) }, icon = { Icon(Icons.Filled.List, "Rules") }, label = { Text("Rules") })
        NavigationBarItem(selected == AppScreen.HISTORY, { onSelect(AppScreen.HISTORY) }, icon = { Icon(Icons.Filled.History, "History") }, label = { Text("History") })
        NavigationBarItem(selected == AppScreen.SETTINGS, { onSelect(AppScreen.SETTINGS) }, icon = { Icon(Icons.Filled.Settings, "Settings") }, label = { Text("Settings") })
    }
}

@Composable
private fun RulesScreen(
    contentPadding: PaddingValues,
    automationEnabled: Boolean,
    onAutomationChanged: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(title = "SMS Rules") },
        floatingActionButton = { Button(onClick = onCreate) { Text("＋  New rule") } },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Automatically send an SMS when an incoming message matches your conditions.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            AutomationCard(automationEnabled, onAutomationChanged)
            Spacer(Modifier.height(20.dp))
            Text("Your rules", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            RuleCard(onEdit, onDelete)
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun AutomationCard(enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (enabled) "Enabled rules can respond to new incoming SMS messages." else "Incoming messages will not trigger any rules.", style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (enabled) "ON" else "OFF", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Switch(checked = enabled, onCheckedChange = onCheckedChange)
            }
        }
    }
}

@Composable
private fun RuleCard(onEdit: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bank Credit Alert", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    StatusPill("Enabled", MaterialTheme.colorScheme.primary)
                }
                Switch(checked = true, onCheckedChange = {})
                TextButton(onClick = onEdit) { Text("Edit") }
            }
            RuleValue("Incoming", "AD-HDFCBK")
            RuleValue("Pattern", "credited.*INR\\s*([\\d,.]+)", mono = true)
            RuleValue("Send to", "••••••3210")
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Today, 8:42 PM · Sent", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) { Text("More") }
        }
    }
}

@Composable
private fun RuleValue(label: String, value: String, mono: Boolean = false) {
    Spacer(Modifier.height(10.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RuleEditorScreen(onBack: () -> Unit, onTest: () -> Unit) {
    var name by remember { mutableStateOf("Bank Credit Alert") }
    var sender by remember { mutableStateOf("AD-HDFCBK") }
    var pattern by remember { mutableStateOf("credited.*INR\\s*([\\d,.]+)") }
    var destination by remember { mutableStateOf("+91 98765 43210") }
    var message by remember { mutableStateOf("Payment received: ₹{{match_1}}\nFrom: {{sender}}") }
    var enabled by remember { mutableStateOf(true) }
    val patternError = remember(pattern) { runCatching { Regex(pattern) }.exceptionOrNull()?.message }
    val broadRule = sender.isBlank() && pattern.trim() == ".*"

    Scaffold(topBar = { AppTopBar("Create Rule", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionTitle("Basic information")
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Rule name") }, singleLine = true)
                SectionTitle("When this SMS arrives")
                OutlinedTextField(sender, { sender = it }, Modifier.fillMaxWidth(), label = { Text("Sender") }, placeholder = { Text("Any sender") }, supportingText = { Text("Leave empty to match SMS from any sender.") }, singleLine = true)
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message pattern  ·  Regex") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    isError = patternError != null,
                    supportingText = { Text(patternError?.let { "Invalid regular expression: $it" } ?: "The rule runs when this pattern appears in the incoming SMS.") },
                )
                TextButton(onClick = onTest) { Text("Test pattern") }
                SectionTitle("Then send an SMS")
                OutlinedTextField(destination, { destination = it }, Modifier.fillMaxWidth(), label = { Text("Destination number") }, singleLine = true)
                OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), label = { Text("Message") }, minLines = 4, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                Text("Available variables", style = MaterialTheme.typography.labelLarge)
                VariableChips(onInsert = { message += it })
                SectionTitle("Rule status")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable this rule", style = MaterialTheme.typography.titleMedium)
                        Text("Enabled rules can automatically send SMS messages.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(enabled, { enabled = it })
                }
                if (broadRule) WarningCard()
                Spacer(Modifier.height(12.dp))
            }
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = onBack, modifier = Modifier.weight(1f), enabled = patternError == null && name.isNotBlank() && destination.isNotBlank() && message.isNotBlank()) { Text("Save rule") }
                }
            }
        }
    }
}

@Composable
private fun VariableChips(onInsert: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("{{message}}", "{{sender}}", "{{match_0}}", "{{match_1}}", "{{timestamp}}").forEach { variable ->
            AssistChip(onClick = { onInsert(variable) }, label = { Text(variable, fontFamily = FontFamily.Monospace) })
        }
    }
}

@Composable
private fun WarningCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text("This rule may match almost every SMS", fontWeight = FontWeight.SemiBold)
            Text("Review the destination and pattern before enabling it.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RuleTesterScreen(onBack: () -> Unit) {
    var sender by remember { mutableStateOf("AD-HDFCBK") }
    var body by remember { mutableStateOf("Your account has been credited INR 5,000.00.") }
    var tested by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Test Rule", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text("Testing does not send a real SMS.", Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
            }
            SectionTitle("Sample sender")
            OutlinedTextField(sender, { sender = it }, Modifier.fillMaxWidth(), singleLine = true)
            SectionTitle("Sample SMS")
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), minLines = 4, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
            Button(onClick = { tested = true }, modifier = Modifier.fillMaxWidth()) { Text("Run test") }
            if (tested) TestResultCard(sender = sender, message = body)
        }
    }
}

@Composable
private fun TestResultCard(sender: String, message: String) {
    val matched = sender.trim().equals("AD-HDFCBK", ignoreCase = true) && Regex("credited.*INR\\s*([\\d,.]+)", RegexOption.IGNORE_CASE).containsMatchIn(message)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (matched) "Rule matched" else "Rule did not match", style = MaterialTheme.typography.titleLarge, color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (matched) {
                Text("Sender  ·  Matched")
                Text("Regex  ·  Matched")
                Text("Captured values", style = MaterialTheme.typography.labelLarge)
                CodeText("match_0: credited INR 5,000.00")
                CodeText("match_1: 5,000.00")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Outgoing SMS preview", style = MaterialTheme.typography.titleMedium)
                CodeText("Payment received: ₹5,000.00\nFrom: AD-HDFCBK")
            } else {
                Text("Sender condition: ${if (sender.equals("AD-HDFCBK", true)) "Matched" else "Did not match"}")
                Text("Regex: ${if (message.contains("credited", true)) "Matched" else "Did not match"}")
            }
        }
    }
}

@Composable
private fun HistoryScreen(contentPadding: PaddingValues, onOpenDetails: () -> Unit) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    Scaffold(topBar = { AppTopBar("History") }) { padding ->
        Column(Modifier.fillMaxSize().padding(contentPadding).padding(padding)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { item -> FilterChip(filter == item, { filter = item }, label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryEntry("Bank Credit Alert", "SMS sent successfully", "Today · 8:42 PM", "AD-HDFCBK → ••••••3210", MaterialTheme.colorScheme.primary, onOpenDetails)
                HistoryEntry("Server Alert", "Failed to send", "Today · 7:16 PM", "Reason: No mobile network", MaterialTheme.colorScheme.error, onOpenDetails)
                HistoryEntry("Payment Forward", "Blocked by rate limit", "Yesterday · 6:03 PM", "No SMS was sent", MaterialTheme.colorScheme.tertiary, onOpenDetails)
            }
        }
    }
}

@Composable
private fun HistoryEntry(title: String, result: String, time: String, detail: String, color: Color, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(Modifier.width(12.dp).height(12.dp).padding(top = 3.dp), shape = MaterialTheme.shapes.extraSmall, color = color) {}
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(result, color = color, style = MaterialTheme.typography.bodyMedium)
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExecutionDetailsScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppTopBar("Execution Details", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailSection("Rule") { Text("Bank Credit Alert", style = MaterialTheme.typography.titleMedium) }
            DetailSection("Incoming message") {
                DetailRow("Sender", "AD-HDFCBK")
                DetailRow("Received", "16 Aug 2026, 8:42 PM")
                CodeText("Your account has been credited INR •••••")
            }
            DetailSection("Match") { DetailRow("Sender condition", "Matched"); DetailRow("Regex", "Matched") }
            DetailSection("Outgoing") {
                DetailRow("Destination", "••••••3210")
                CodeText("Payment received: ₹5,000")
                StatusPill("Sent successfully", MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PermissionOnboardingScreen(onBack: () -> Unit) {
    var receiveAllowed by remember { mutableStateOf(false) }
    var sendAllowed by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Set up SMS automation", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("SMS Rule Relay needs access to incoming SMS messages so it can evaluate your rules, and permission to send SMS when a rule matches.", style = MaterialTheme.typography.bodyLarge)
            PermissionCard("Receive SMS", "Incoming message access", receiveAllowed) { receiveAllowed = true }
            PermissionCard("Send SMS", "Send a new SMS after a matching rule", sendAllowed) { sendAllowed = true }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Your messages stay on your device", fontWeight = FontWeight.SemiBold)
                    Text("SMS Rule Relay does not require a cloud account or upload your SMS messages.")
                }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = receiveAllowed && sendAllowed) { Text("Continue") }
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, granted: Boolean, onAllow: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
                Text(if (granted) "Granted" else "Not granted", color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
            }
            if (!granted) FilledTonalButton(onClick = onAllow) { Text("Allow") }
        }
    }
}

@Composable
private fun SettingsScreen(contentPadding: PaddingValues, automationEnabled: Boolean, onAutomationChanged: (Boolean) -> Unit, onOpenOnboarding: () -> Unit) {
    var storeFullContent by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Settings") }) { padding ->
        Column(Modifier.fillMaxSize().padding(contentPadding).padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingGroup("Automation") {
                SettingToggle("Master automation", "Stop all automatic sends immediately", automationEnabled, onAutomationChanged)
                SettingRow("Default SIM", "SIM 1")
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
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary); ElevatedCard(Modifier.fillMaxWidth()) { Column { content() } } }
}

@Composable
private fun SettingToggle(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onCheckedChange)
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

@Composable
private fun AppTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SectionTitle(value: String) = Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun StatusPill(value: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small, modifier = Modifier.padding(top = 6.dp).wrapContentWidth()) {
        Text(value, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } } }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Medium) }
}

@Composable
private fun CodeText(value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) { Text(value, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
}
