@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

private enum class AppScreen { RULES, HISTORY, SETTINGS, EDITOR, TESTER, DETAILS, ONBOARDING }
private enum class HistoryFilter { ALL, SENT, FAILED, BLOCKED, MATCHED }

@Composable
fun SmsRelayApp() {
    var screen by remember { mutableStateOf(AppScreen.RULES) }
    var automationEnabled by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var receiveAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var sendAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }
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
                receiveAllowed = receiveAllowed,
                sendAllowed = sendAllowed,
                onOpenPermissions = { screen = AppScreen.ONBOARDING },
            )
            AppScreen.HISTORY -> HistoryScreen(innerPadding, automationEnabled, receiveAllowed && sendAllowed, onReviewPermissions = { screen = AppScreen.ONBOARDING }, onOpenDetails = { screen = AppScreen.DETAILS })
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
            AppScreen.ONBOARDING -> PermissionOnboardingScreen(
                receiveAllowed = receiveAllowed,
                sendAllowed = sendAllowed,
                onReceiveAllowed = { receiveAllowed = true },
                onSendAllowed = { sendAllowed = true },
                onBack = { screen = AppScreen.SETTINGS },
            )
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
        NavigationBarItem(selected == AppScreen.RULES, { onSelect(AppScreen.RULES) }, icon = { Icon(Icons.Filled.Rule, "Rules") }, label = { Text("Rules") })
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
    receiveAllowed: Boolean,
    sendAllowed: Boolean,
    onOpenPermissions: () -> Unit,
) {
    var otpRuleEnabled by remember { mutableStateOf(true) }
    var creditRuleEnabled by remember { mutableStateOf(true) }
    Scaffold(
        topBar = { AppTopBar(title = "SMS Rules") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add Rule") })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Automatically send an SMS when an incoming message matches your conditions.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            AutomationCard(automationEnabled, onAutomationChanged)
            Spacer(Modifier.height(20.dp))
            PermissionStatusCard(receiveAllowed, sendAllowed, onOpenPermissions)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Your rules", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onCreate, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Rule")
                }
            }
            Spacer(Modifier.height(8.dp))
            RuleCard("Bank OTP Forward", "+91 98765 43210", "OTP\\s*is\\s*(\\d{6})", "••••••7890", otpRuleEnabled, { otpRuleEnabled = it }, onEdit, onDelete)
            Spacer(Modifier.height(12.dp))
            RuleCard("Bank Credit Alert", "AD-HDFCBK", "credited.*INR...", "••••••4321", creditRuleEnabled, { creditRuleEnabled = it }, onEdit, onDelete)
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
private fun PermissionStatusCard(receiveAllowed: Boolean, sendAllowed: Boolean, onOpenPermissions: () -> Unit) {
    val ready = receiveAllowed && sendAllowed
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning, null)
                Spacer(Modifier.width(8.dp))
                Text(if (ready) "SMS automation ready" else "SMS permissions required", fontWeight = FontWeight.SemiBold)
            }
            if (!ready) {
                Text("Receive SMS: ${if (receiveAllowed) "Granted" else "Not granted"}")
                Text("Send SMS: ${if (sendAllowed) "Granted" else "Not granted"}")
                OutlinedButton(onClick = onOpenPermissions) { Text("Configure Permissions") }
            } else {
                Text("Both permissions are granted and rules can process incoming messages.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RuleCard(
    title: String,
    sender: String,
    pattern: String,
    destination: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sms, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    StatusPill(if (enabled) "Enabled" else "Disabled", if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit rule") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete rule") }
            }
            RuleValue("Incoming", sender, icon = Icons.Filled.Phone)
            RuleValue("Pattern", pattern, mono = true, icon = Icons.Filled.FilterAlt)
            RuleValue("Send to", destination, icon = Icons.Filled.Send)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Today, 8:42 PM · Sent", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Filled.MoreVert, "More options") }
        }
    }
}

@Composable
private fun RuleValue(label: String, value: String, mono: Boolean = false, icon: ImageVector? = null) {
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, modifier = Modifier.width(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RuleEditorScreen(onBack: () -> Unit, onTest: () -> Unit) {
    var incomingNumber by remember { mutableStateOf("+91 98765 43210") }
    var anyNumber by remember { mutableStateOf(false) }
    var pattern by remember { mutableStateOf("OTP\\s*is\\s*(\\d{6})") }
    var destination by remember { mutableStateOf("+91 91234 56789") }
    var message by remember { mutableStateOf("OTP received: {{match_1}}") }
    var enabled by remember { mutableStateOf(true) }
    var regexTested by remember { mutableStateOf(false) }
    val patternError = remember(pattern) { runCatching { Regex(pattern) }.exceptionOrNull()?.message }
    val sampleMatch = remember(pattern) { runCatching { Regex(pattern).find("OTP is 123456") }.getOrNull() }
    val broadRule = anyNumber && pattern.trim() == ".*"

    Scaffold(topBar = { AppTopBar("Create SMS Rule", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowStep("1", "Receive SMS from")
                OutlinedTextField(
                    value = incomingNumber,
                    onValueChange = { incomingNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Incoming phone number") },
                    placeholder = { Text("Any number") },
                    enabled = !anyNumber,
                    supportingText = { Text("Only SMS messages received from this number will be checked.") },
                    singleLine = true,
                )
                FilterChip(selected = anyNumber, onClick = { anyNumber = !anyNumber }, label = { Text("Any number") })
                FlowArrow()
                FlowStep("2", "Match message with regex")
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it; regexTested = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message Regex  ·  Regex") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    isError = patternError != null,
                    supportingText = { Text(patternError?.let { "Invalid regular expression: $it" } ?: "The SMS will trigger this rule only when the message matches this pattern.") },
                )
                TextButton(onClick = { regexTested = true }, enabled = patternError == null) { Text("Test Regex") }
                if (regexTested) RegexResultCard(match = sampleMatch)
                FlowArrow()
                FlowStep("3", "Send SMS to")
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Forward to phone number") },
                    supportingText = { Text("When the incoming SMS matches, an SMS will automatically be sent to this number.") },
                    singleLine = true,
                )
                FlowArrow()
                FlowStep("4", "Outgoing message")
                OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), label = { Text("Message to send") }, minLines = 4, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                Text("Available variables", style = MaterialTheme.typography.labelLarge)
                VariableChips(onInsert = { message += it })
                HorizontalDivider(Modifier.padding(top = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable Rule", style = MaterialTheme.typography.titleMedium)
                        Text("Enabled rules can automatically send SMS messages.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(enabled, { enabled = it })
                }
                if (broadRule) WarningCard()
                Spacer(Modifier.height(12.dp))
            }
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onBack, modifier = Modifier.weight(0.8f)) { Text("Cancel") }
                    OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f)) { Text("Test Rule") }
                    Button(onClick = onBack, modifier = Modifier.weight(1f), enabled = patternError == null && (anyNumber || incomingNumber.isNotBlank()) && destination.isNotBlank() && message.isNotBlank()) { Text("Save Rule") }
                }
            }
        }
    }
}

@Composable
private fun FlowStep(step: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusPill(step, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FlowArrow() {
    Text("↓", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun RegexResultCard(match: MatchResult?) {
    val matched = match != null
    Card(colors = CardDefaults.cardColors(containerColor = if (matched) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (matched) "✓ Pattern matched" else "Pattern did not match", fontWeight = FontWeight.SemiBold)
            Text("Sample checked: OTP is 123456", style = MaterialTheme.typography.bodySmall)
            if (matched && match.groups.size > 1) {
                Text("Captured groups", style = MaterialTheme.typography.labelMedium)
                match.groups.drop(1).forEachIndexed { index, group ->
                    CodeText("match_" + (index + 1) + " = " + group?.value.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun VariableChips(onInsert: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("{{sender}}", "{{message}}", "{{match_0}}", "{{match_1}}").forEach { variable ->
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
    var sender by remember { mutableStateOf("+91 98765 43210") }
    var body by remember { mutableStateOf("OTP is 123456") }
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
    val matched = sender.trim() == "+91 98765 43210" && Regex("OTP\\s*is\\s*(\\d{6})", RegexOption.IGNORE_CASE).containsMatchIn(message)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (matched) "Rule matched" else "Rule did not match", style = MaterialTheme.typography.titleLarge, color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (matched) {
                Text("Sender  ·  Matched")
                Text("Regex  ·  Matched")
                Text("Captured values", style = MaterialTheme.typography.labelLarge)
                CodeText("match_0: OTP is 123456")
                CodeText("match_1: 123456")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Outgoing SMS preview", style = MaterialTheme.typography.titleMedium)
                CodeText("OTP received: 123456")
            } else {
                Text(if (sender == "+91 98765 43210") "Sender condition: Matched" else "Sender condition: Did not match")
                Text(if (message.contains("OTP", true)) "Regex: Matched" else "Regex: Did not match")
            }
        }
    }
}

private data class HistoryItem(val rule: String, val status: HistoryFilter, val sender: String, val destination: String, val time: String, val group: String, val detail: String = "")

@Composable
private fun HistoryScreen(contentPadding: PaddingValues, automationEnabled: Boolean, permissionsReady: Boolean, onReviewPermissions: () -> Unit, onOpenDetails: () -> Unit) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var items by remember {
        mutableStateOf(listOf(
            HistoryItem("Bank OTP Forward", HistoryFilter.SENT, "+91 98765 43210", "••••••7890", "Today, 10:42 PM", "Today"),
            HistoryItem("Bank Credit Alert", HistoryFilter.MATCHED, "AD-HDFCBK", "••••••3210", "Today, 8:42 PM", "Today"),
            HistoryItem("Server Alert", HistoryFilter.FAILED, "Alert service", "••••••4567", "Today, 7:16 PM", "Today", "No mobile network"),
            HistoryItem("Payment Forward", HistoryFilter.BLOCKED, "+91 90000 11111", "••••••2222", "Yesterday, 6:03 PM", "Yesterday", "Rate limit"),
        ))
    }
    val visible = items.filter { (filter == HistoryFilter.ALL || it.status == filter) && (query.isBlank() || it.rule.contains(query, true) || it.sender.contains(query, true) || it.destination.contains(query)) }
    Scaffold(topBar = {
        TopAppBar(
            title = { if (searching) OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search history") }) else Column { Text("History", fontWeight = FontWeight.SemiBold); Text("View matched rules, sent messages, failures, and blocked actions.", style = MaterialTheme.typography.labelSmall) } },
            actions = {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Filled.Search, "Search history") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "History options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { DropdownMenuItem(text = { Text("Clear History") }, onClick = { menuOpen = false; clearDialog = true }) }
                }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(contentPadding).padding(padding)) {
            if (!permissionsReady) CompactBanner("SMS permissions are incomplete", "Review Permissions", MaterialTheme.colorScheme.tertiary, onReviewPermissions)
            if (!automationEnabled) CompactBanner("Automation is currently off", null, MaterialTheme.colorScheme.onSurfaceVariant, {})
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { item -> FilterChip(filter == item, { filter = item }, label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
            }
            if (visible.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.primary)
                    Text("No activity yet", style = MaterialTheme.typography.titleMedium)
                    Text("When an SMS matches one of your rules, its execution result will appear here.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visible.groupBy { it.group }.forEach { (group, records) ->
                        Text(group, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        records.forEach { HistoryEntry(it, onOpenDetails) }
                    }
                }
            }
        }
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("Clear execution history?") }, text = { Text("This removes local history records. Your SMS rules will not be deleted.") }, confirmButton = { TextButton(onClick = { items = emptyList(); clearDialog = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } })
}

@Composable
private fun HistoryEntry(item: HistoryItem, onClick: () -> Unit) {
    val (icon, color, label) = when (item.status) {
        HistoryFilter.SENT -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, "SMS sent successfully")
        HistoryFilter.FAILED -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Failed to send")
        HistoryFilter.BLOCKED -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary, "Blocked")
        HistoryFilter.MATCHED -> Triple(Icons.Filled.Rule, MaterialTheme.colorScheme.secondary, "Rule matched")
        HistoryFilter.ALL -> Triple(Icons.Filled.History, MaterialTheme.colorScheme.onSurfaceVariant, "Activity")
    }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color); Spacer(Modifier.width(8.dp)); Text(item.rule, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            Text(label, color = color, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Sms, null, modifier = Modifier.width(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text("From: ${item.sender}") }
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Send, null, modifier = Modifier.width(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text("To: ${item.destination}") }
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Schedule, null, modifier = Modifier.width(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Text(item.time, style = MaterialTheme.typography.bodySmall) }
            if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CompactBanner(message: String, action: String?, color: Color, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Warning, null, tint = color); Spacer(Modifier.width(8.dp)); Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); if (action != null) TextButton(onClick = onAction) { Text(action) } }
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
private fun PermissionOnboardingScreen(
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
private fun PermissionCard(icon: ImageVector, title: String, description: String, granted: Boolean, onAllow: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
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
