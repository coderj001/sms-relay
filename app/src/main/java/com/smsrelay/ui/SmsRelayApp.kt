@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.smsrelay.data.AppSettings
import com.smsrelay.data.SmsRelayDatabaseProvider
import com.smsrelay.data.ExecutionLogWithRule
import com.smsrelay.data.SmsRuleEntity
import com.smsrelay.data.settingsDataStore
import com.smsrelay.domain.model.IncomingSms
import com.smsrelay.domain.model.RuleEvaluation
import com.smsrelay.domain.model.SmsRule
import com.smsrelay.domain.rule.RuleMatcher
import com.smsrelay.domain.template.TemplateRenderer
import com.smsrelay.domain.template.TemplateResult
import androidx.compose.runtime.collectAsState
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class AppScreen { RULES, HISTORY, SETTINGS, EDITOR, TESTER, DETAILS, ONBOARDING }
private enum class HistoryFilter { ALL, SENT, FAILED, BLOCKED, MATCHED }
private val Success = Color(0xFF4A9E5C)
private data class RuleDraft(
    val name: String,
    val senderFilter: String?,
    val messageRegex: String,
    val destinationNumber: String,
    val outputTemplate: String,
    val enabled: Boolean,
)

@Composable
fun SmsRelayApp() {
    var screen by remember { mutableStateOf(AppScreen.RULES) }
    var automationEnabled by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val dao = remember(context) { SmsRelayDatabaseProvider.get(context).dao() }
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf<List<SmsRuleEntity>>(emptyList()) }
    var editingRule by remember { mutableStateOf<SmsRuleEntity?>(null) }
    var testerDraft by remember { mutableStateOf<RuleDraft?>(null) }
    var receiveAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var sendAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var ruleToDelete by remember { mutableStateOf<SmsRuleEntity?>(null) }

    LaunchedEffect(dao) { rules = dao.allRules() }
    val historyItems by remember(dao) { dao.allExecutionLogs() }.collectAsState(initial = emptyList())
    var selectedHistory by remember { mutableStateOf<HistoryItem?>(null) }
    var phoneStateAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
    val phoneStateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { phoneStateAllowed = it }
    val defaultSimId by remember { context.settingsDataStore.data.map { it[AppSettings.DEFAULT_SIM_SUBSCRIPTION_ID] ?: AppSettings.AUTO_SIM } }.collectAsState(initial = AppSettings.AUTO_SIM)
    val activeSims = remember(phoneStateAllowed) {
        if (!phoneStateAllowed) emptyList()
        else runCatching { SubscriptionManager.from(context).activeSubscriptionInfoList.orEmpty() }.getOrDefault(emptyList())
    }

    fun saveRule(existing: SmsRuleEntity?, draft: RuleDraft) {
        scope.launch {
            val now = System.currentTimeMillis()
            val rule = SmsRuleEntity(
                id = existing?.id ?: 0,
                name = draft.name.trim(),
                enabled = draft.enabled,
                senderFilter = draft.senderFilter?.trim()?.takeIf { it.isNotEmpty() },
                messageRegex = draft.messageRegex,
                destinationNumber = draft.destinationNumber.trim(),
                outputTemplate = draft.outputTemplate,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            if (existing == null) dao.insertRule(rule) else dao.updateRule(rule)
            rules = dao.allRules()
            screen = AppScreen.RULES
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (screen == AppScreen.RULES || screen == AppScreen.HISTORY || screen == AppScreen.SETTINGS) {
                MainNavigation(selected = screen, onSelect = { screen = it })
            }
        },
    ) { innerPadding ->
        when (screen) {
            AppScreen.RULES -> RulesScreen(
                contentPadding = innerPadding,
                rules = rules,
                onCreate = { editingRule = null; screen = AppScreen.EDITOR },
                onEdit = { editingRule = it; screen = AppScreen.EDITOR },
                onDelete = { ruleToDelete = it },
                onRuleEnabledChange = { rule, enabled ->
                    scope.launch {
                        dao.updateRule(rule.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
                        rules = dao.allRules()
                    }
                },
                receiveAllowed = receiveAllowed,
                sendAllowed = sendAllowed,
                onOpenPermissions = { screen = AppScreen.ONBOARDING },
            )
            AppScreen.HISTORY -> HistoryScreen(
                contentPadding = innerPadding,
                automationEnabled = automationEnabled,
                permissionsReady = receiveAllowed && sendAllowed,
                items = historyItems.map { it.toHistoryItem() },
                onClearHistory = { scope.launch { dao.clearExecutionLogs() } },
                onReviewPermissions = { screen = AppScreen.ONBOARDING },
                onOpenDetails = { selectedHistory = it; screen = AppScreen.DETAILS },
            )
            AppScreen.SETTINGS -> SettingsScreen(
                contentPadding = innerPadding,
                automationEnabled = automationEnabled,
                onAutomationChanged = { automationEnabled = it },
                onOpenOnboarding = { screen = AppScreen.ONBOARDING },
                defaultSimId = defaultSimId,
                phoneStateAllowed = phoneStateAllowed,
                activeSims = activeSims,
                onRequestPhoneState = { phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                onDefaultSimChanged = { id -> scope.launch { context.settingsDataStore.edit { it[AppSettings.DEFAULT_SIM_SUBSCRIPTION_ID] = id } } },
            )
            AppScreen.EDITOR -> RuleEditorScreen(
                rule = editingRule,
                onBack = { screen = AppScreen.RULES },
                onSave = { saveRule(editingRule, it) },
                onTest = { testerDraft = it; screen = AppScreen.TESTER },
            )
            AppScreen.TESTER -> RuleTesterScreen(draft = testerDraft, onBack = { screen = AppScreen.EDITOR })
            AppScreen.DETAILS -> ExecutionDetailsScreen(item = selectedHistory, onBack = { screen = AppScreen.HISTORY })
            AppScreen.ONBOARDING -> PermissionOnboardingScreen(
                receiveAllowed = receiveAllowed,
                sendAllowed = sendAllowed,
                onReceiveAllowed = { receiveAllowed = true },
                onSendAllowed = { sendAllowed = true },
                onBack = { screen = AppScreen.SETTINGS },
            )
        }
    }

    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete this rule?") },
            text = { Text("${rule.name} will no longer run. This does not delete its history.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dao.deleteRule(rule)
                        rules = dao.allRules()
                        ruleToDelete = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { ruleToDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MainNavigation(selected: AppScreen, onSelect: (AppScreen) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        NavigationBarItem(selected == AppScreen.RULES, { onSelect(AppScreen.RULES) }, icon = {}, label = { NavigationLabel("Rules", selected == AppScreen.RULES) })
        NavigationBarItem(selected == AppScreen.HISTORY, { onSelect(AppScreen.HISTORY) }, icon = {}, label = { NavigationLabel("History", selected == AppScreen.HISTORY) })
        NavigationBarItem(selected == AppScreen.SETTINGS, { onSelect(AppScreen.SETTINGS) }, icon = {}, label = { NavigationLabel("Settings", selected == AppScreen.SETTINGS) })
    }
}

@Composable
private fun NavigationLabel(label: String, selected: Boolean) {
    Text(
        text = if (selected) "[ ${label.uppercase()} ]" else label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RulesScreen(
    contentPadding: PaddingValues,
    rules: List<SmsRuleEntity>,
    onCreate: () -> Unit,
    onEdit: (SmsRuleEntity) -> Unit,
    onDelete: (SmsRuleEntity) -> Unit,
    onRuleEnabledChange: (SmsRuleEntity, Boolean) -> Unit,
    receiveAllowed: Boolean,
    sendAllowed: Boolean,
    onOpenPermissions: () -> Unit,
) {
    val permissionsReady = receiveAllowed && sendAllowed
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { AppTopBar(title = "SMS Rules") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add Rule") })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (!permissionsReady) {
                PermissionWarningCard(receiveAllowed, sendAllowed, onOpenPermissions)
                Spacer(Modifier.height(12.dp))
            }
            if (rules.isEmpty()) {
                Text("No rules yet. Add a rule to start relaying matching SMS messages.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                rules.forEach { rule ->
                    RuleCard(rule, onRuleEnabledChange, onEdit, onDelete)
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun PermissionWarningCard(receiveAllowed: Boolean, sendAllowed: Boolean, onOpenPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Permissions required", style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull("Receive SMS".takeIf { !receiveAllowed }, "Send SMS".takeIf { !sendAllowed }).joinToString(", ") + " not granted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenPermissions) { Text("Fix") }
        }
    }
}

@Composable
private fun RuleCard(
    rule: SmsRuleEntity,
    onEnabledChange: (SmsRuleEntity, Boolean) -> Unit,
    onEdit: (SmsRuleEntity) -> Unit,
    onDelete: (SmsRuleEntity) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = rule.enabled, onCheckedChange = { onEnabledChange(rule, it) })
                IconButton(onClick = { onEdit(rule) }) { Icon(Icons.Filled.Edit, "Edit rule") }
                IconButton(onClick = { onDelete(rule) }) { Icon(Icons.Filled.Delete, "Delete rule") }
            }
            RuleValue("Incoming", rule.senderFilter ?: "Any number")
            RuleValue("Pattern", rule.messageRegex, mono = true)
            RuleValue("Send to", rule.destinationNumber)
        }
    }
}

@Composable
private fun RuleValue(label: String, value: String, mono: Boolean = false) {
    Spacer(Modifier.height(10.dp))
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RuleEditorScreen(
    rule: SmsRuleEntity?,
    onBack: () -> Unit,
    onSave: (RuleDraft) -> Unit,
    onTest: (RuleDraft) -> Unit,
) {
    var name by remember(rule?.id) { mutableStateOf(rule?.name.orEmpty()) }
    var incomingNumber by remember(rule?.id) { mutableStateOf(rule?.senderFilter.orEmpty()) }
    var anyNumber by remember(rule?.id) { mutableStateOf(rule?.senderFilter == null) }
    var pattern by remember(rule?.id) { mutableStateOf(rule?.messageRegex.orEmpty()) }
    var destination by remember(rule?.id) { mutableStateOf(rule?.destinationNumber.orEmpty()) }
    var message by remember(rule?.id) { mutableStateOf(rule?.outputTemplate.orEmpty()) }
    var enabled by remember(rule?.id) { mutableStateOf(rule?.enabled ?: true) }
    var regexTested by remember { mutableStateOf(false) }
    val patternError = remember(pattern) { runCatching { Regex(pattern) }.exceptionOrNull()?.message }
    val sampleMatch = remember(pattern) { runCatching { Regex(pattern).find("OTP is 123456") }.getOrNull() }
    val broadRule = anyNumber && pattern.trim() == ".*"
    val preview = remember(pattern, message, anyNumber, incomingNumber) {
        val sample = IncomingSms("VM-KOTAKB-S", "OTP is 123456", System.currentTimeMillis(), null)
        val draftRule = SmsRule(0, "preview", true, if (anyNumber) null else incomingNumber.trim().takeIf(String::isNotEmpty), pattern, "", message, 0L, 0L)
        when (val evaluation = RuleMatcher().evaluate(draftRule, sample)) {
            is RuleEvaluation.Matched -> when (val rendered = TemplateRenderer().render(message, sample, evaluation.match)) {
                is TemplateResult.Success -> MessagePreview.Rendered(rendered.value)
                is TemplateResult.UnknownVariable -> MessagePreview.Invalid("Unknown variable {{${rendered.variable}}}")
            }
            RuleEvaluation.SenderMismatch -> MessagePreview.SenderMismatch
            RuleEvaluation.MessageMismatch -> MessagePreview.NoMatch
            is RuleEvaluation.InvalidPattern -> MessagePreview.Invalid(evaluation.message)
        }
    }

    val draft = RuleDraft(
        name = name,
        senderFilter = if (anyNumber) null else incomingNumber,
        messageRegex = pattern,
        destinationNumber = destination,
        outputTemplate = message,
        enabled = enabled,
    )
    val canSave = patternError == null && name.isNotBlank() && pattern.isNotBlank() && (anyNumber || incomingNumber.isNotBlank()) && destination.isNotBlank() && message.isNotBlank()

    Scaffold(topBar = { AppTopBar(if (rule == null) "Create SMS Rule" else "Edit SMS Rule", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowStep("1", "Receive SMS from")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Rule name") },
                    supportingText = { Text("Use a short name to identify this relay rule.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = incomingNumber,
                    onValueChange = { incomingNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Incoming phone number") },
                    placeholder = { Text("Any number") },
                    enabled = !anyNumber,
                    supportingText = { Text("Exact sender or wildcard: * = any characters, ? = one. Example: *-KOTAKB-*. Empty = any sender.") },
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
                VariablePicker(pattern = pattern, onInsert = { message += it })
                MessagePreviewCard(preview = preview)
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
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onBack, modifier = Modifier.weight(0.8f)) { Text("Cancel") }
                    OutlinedButton(onClick = { onTest(draft) }, modifier = Modifier.weight(1f), enabled = patternError == null) { Text("Test Rule") }
                    Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f), enabled = canSave) { Text("Save Rule") }
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
    Card(
        border = BorderStroke(1.dp, if (matched) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (matched) "[ PATTERN MATCHED ]" else "[ NO MATCH ]", style = MaterialTheme.typography.labelLarge, color = if (matched) Success else MaterialTheme.colorScheme.error)
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
private fun ChipRow(variables: List<String>, onInsert: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        variables.forEach { variable -> AssistChip(onClick = { onInsert(variable) }, label = { Text(variable, fontFamily = FontFamily.Monospace) }) }
    }
}

@Composable
private fun VariablePicker(pattern: String, onInsert: (String) -> Unit) {
    val namedGroups = remember(pattern) { "\\(\\?<([a-zA-Z][a-zA-Z0-9_]*)>".toRegex().findAll(pattern).map { it.groupValues[1] }.distinct().toList() }
    val groupCount = remember(pattern) {
        val positional = runCatching { "\\((?!\\?)".toRegex().findAll(pattern).count() }.getOrDefault(0)
        positional + namedGroups.size
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Message data", style = MaterialTheme.typography.labelLarge)
        Text("{{sender}} who sent it · {{message}} the original SMS · {{timestamp}} arrival time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(listOf("{{sender}}", "{{message}}", "{{timestamp}}"), onInsert)
        Text("Regex captures", style = MaterialTheme.typography.labelLarge)
        Text("{{match_0}} whole match · {{match_N}} group N · named groups appear here automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow((0..groupCount).map { "{{match_$it}}" }, onInsert)
        if (namedGroups.isNotEmpty()) ChipRow(namedGroups.map { "{{$it}}" }, onInsert)
    }
}

private sealed interface MessagePreview {
    data class Rendered(val text: String) : MessagePreview
    data object SenderMismatch : MessagePreview
    data object NoMatch : MessagePreview
    data class Invalid(val reason: String) : MessagePreview
}

@Composable
private fun MessagePreviewCard(preview: MessagePreview) {
    val (label, value, isError) = when (preview) {
        is MessagePreview.Rendered -> Triple("[ PREVIEW ]", preview.text, false)
        MessagePreview.SenderMismatch -> Triple("[ PREVIEW ]", "Sample sender VM-KOTAKB-S does not match this rule.", true)
        MessagePreview.NoMatch -> Triple("[ PREVIEW ]", "Sample message does not match this pattern.", true)
        is MessagePreview.Invalid -> Triple("[ PREVIEW ]", preview.reason, true)
    }
    Card(
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (isError) MaterialTheme.colorScheme.error else Success)
            Text("Sample: OTP is 123456 · from VM-KOTAKB-S", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WarningCard() {
    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text("This rule may match almost every SMS", fontWeight = FontWeight.SemiBold)
            Text("Review the destination and pattern before enabling it.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RuleTesterScreen(draft: RuleDraft?, onBack: () -> Unit) {
    val rule = draft ?: return
    var sender by remember(rule.senderFilter) { mutableStateOf(rule.senderFilter.orEmpty()) }
    var body by remember { mutableStateOf("OTP is 123456") }
    var tested by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Test Rule", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("[ TEST MODE ]  Testing never sends a real SMS.", Modifier.padding(14.dp), style = MaterialTheme.typography.labelMedium)
            }
            SectionTitle("Sample sender")
            OutlinedTextField(sender, { sender = it }, Modifier.fillMaxWidth(), singleLine = true)
            SectionTitle("Sample SMS")
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), minLines = 4, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
            Button(onClick = { tested = true }, modifier = Modifier.fillMaxWidth()) { Text("Run test") }
            if (tested) TestResultCard(rule = rule, sender = sender, message = body)
        }
    }
}

@Composable
private fun TestResultCard(rule: RuleDraft, sender: String, message: String) {
    val senderMatched = rule.senderFilter.isNullOrBlank() || rule.senderFilter.trim().equals(sender.trim(), ignoreCase = true)
    val regexMatch = runCatching { Regex(rule.messageRegex).find(message) }.getOrNull()
    val matched = senderMatched && regexMatch != null
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (matched) "RULE MATCHED" else "RULE DID NOT MATCH", style = MaterialTheme.typography.headlineSmall, color = if (matched) Success else MaterialTheme.colorScheme.error)
            if (matched) {
                Text("Sender  ·  Matched")
                Text("Regex  ·  Matched")
                Text("Captured values", style = MaterialTheme.typography.labelLarge)
                CodeText("match_0: ${regexMatch?.value.orEmpty()}")
                regexMatch?.groups?.drop(1)?.forEachIndexed { index, group ->
                    CodeText("match_${index + 1}: ${group?.value.orEmpty()}")
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Outgoing SMS preview", style = MaterialTheme.typography.titleMedium)
                CodeText(renderTestTemplate(rule.outputTemplate, sender, message, regexMatch))
            } else {
                Text(if (senderMatched) "Sender condition: Matched" else "Sender condition: Did not match")
                Text(if (regexMatch != null) "Regex: Matched" else "Regex: Did not match")
            }
        }
    }
}

private fun renderTestTemplate(template: String, sender: String, message: String, match: MatchResult?): String =
    Regex("\\{\\{([a-zA-Z0-9_]+)}}").replace(template) { token ->
        when (val variable = token.groupValues[1]) {
            "sender" -> sender
            "message" -> message
            "match_0" -> match?.value.orEmpty()
            else -> variable.removePrefix("match_").toIntOrNull()
                ?.let { index ->
                    val groups = match?.groups
                    if (groups != null && index >= 0 && index < groups.size) {
                        groups[index]?.value.orEmpty()
                    } else {
                        ""
                    }
                }
                ?: token.value
        }
    }

private data class HistoryItem(val rule: String, val status: HistoryFilter, val sender: String, val destination: String, val time: String, val group: String, val detail: String = "")

private fun ExecutionLogWithRule.toHistoryItem(): HistoryItem {
    val created = java.time.Instant.ofEpochMilli(log.createdAt).atZone(java.time.ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    val group = when (created.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> created.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
    return HistoryItem(
        rule = ruleName ?: "Unknown rule",
        status = when (log.status) {
            "SENT" -> HistoryFilter.SENT
            "FAILED" -> HistoryFilter.FAILED
            "RATE_LIMITED", "PERMISSION_MISSING" -> HistoryFilter.BLOCKED
            else -> HistoryFilter.MATCHED
        },
        sender = log.senderPreview ?: "Unknown",
        destination = log.destinationMasked ?: "—",
        time = "$group, ${created.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}",
        group = group,
        detail = log.detail.orEmpty(),
    )
}

@Composable
private fun HistoryScreen(
    contentPadding: PaddingValues,
    automationEnabled: Boolean,
    permissionsReady: Boolean,
    items: List<HistoryItem>,
    onClearHistory: () -> Unit,
    onReviewPermissions: () -> Unit,
    onOpenDetails: (HistoryItem) -> Unit,
) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    val visible = items.filter { (filter == HistoryFilter.ALL || it.status == filter) && (query.isBlank() || it.rule.contains(query, true) || it.sender.contains(query, true) || it.destination.contains(query)) }
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
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
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                        records.forEach { HistoryEntry(it, { onOpenDetails(it) }) }
                    }
                }
            }
        }
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("Clear execution history?") }, text = { Text("This removes local history records. Your SMS rules will not be deleted.") }, confirmButton = { TextButton(onClick = { onClearHistory(); clearDialog = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } })
}

@Composable
private fun HistoryEntry(item: HistoryItem, onClick: () -> Unit) {
    val (_, color, label) = when (item.status) {
        HistoryFilter.SENT -> Triple(Icons.Filled.CheckCircle, Success, "SMS sent successfully")
        HistoryFilter.FAILED -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, "Failed to send")
        HistoryFilter.BLOCKED -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary, "Blocked")
        HistoryFilter.MATCHED -> Triple(Icons.Filled.Rule, MaterialTheme.colorScheme.secondary, "Rule matched")
        HistoryFilter.ALL -> Triple(Icons.Filled.History, MaterialTheme.colorScheme.onSurfaceVariant, "Activity")
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.rule, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("[ ${label.uppercase()} ]", color = color, style = MaterialTheme.typography.labelMedium)
            DetailRow("FROM", item.sender)
            DetailRow("TO", item.destination)
            Text(item.time.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.detail.isNotBlank()) Text("[ ERROR: ${item.detail.uppercase()} ]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CompactBanner(message: String, action: String?, color: Color, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Warning, null, tint = color); Spacer(Modifier.width(8.dp)); Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); if (action != null) TextButton(onClick = onAction) { Text(action) } }
}

@Composable
private fun ExecutionDetailsScreen(item: HistoryItem?, onBack: () -> Unit) {
    if (item == null) { onBack(); return }
    Scaffold(topBar = { AppTopBar("Execution Details", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailSection("Rule") { Text(item.rule, style = MaterialTheme.typography.titleMedium) }
            DetailSection("Execution") {
                DetailRow("From", item.sender)
                DetailRow("To", item.destination)
                DetailRow("Time", item.time)
            }
            DetailSection("Result") {
                DetailRow("Status", item.status.name)
                if (item.detail.isNotBlank()) CodeText(item.detail) else StatusPill("Completed", Success)
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

@Composable
private fun SettingsScreen(
    contentPadding: PaddingValues,
    automationEnabled: Boolean,
    onAutomationChanged: (Boolean) -> Unit,
    onOpenOnboarding: () -> Unit,
    defaultSimId: Int,
    phoneStateAllowed: Boolean,
    activeSims: List<SubscriptionInfo>,
    onRequestPhoneState: () -> Unit,
    onDefaultSimChanged: (Int) -> Unit,
) {
    var storeFullContent by remember { mutableStateOf(false) }
    var showSimDialog by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { AppTopBar("Settings") },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
        Switch(checked, onCheckedChange)
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

@Composable
private fun AppTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("< BACK", style = MaterialTheme.typography.labelMedium) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SectionTitle(value: String) = Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun StatusPill(value: String, color: Color) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color), modifier = Modifier.padding(top = 6.dp).wrapContentWidth()) {
        Text(value.uppercase(), color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } } }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium) }
}

@Composable
private fun CodeText(value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text(value, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
}
