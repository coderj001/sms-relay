@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.smsrelay.ui

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsrelay.data.AppSettings
import com.smsrelay.data.SmsRelayDatabaseProvider
import com.smsrelay.data.SmsRuleEntity
import com.smsrelay.data.settingsDataStore
import com.smsrelay.ui.theme.AppColorPalette
import kotlinx.coroutines.launch

private enum class AppScreen { RULES, HISTORY, SETTINGS, EDITOR, TESTER, DETAILS, ONBOARDING }

@Composable
fun SmsRelayApp(colorPalette: AppColorPalette, onColorPaletteChanged: (AppColorPalette) -> Unit) {
    var screen by remember { mutableStateOf(AppScreen.RULES) }
    val context = LocalContext.current
    val dao = remember(context) { SmsRelayDatabaseProvider.get(context).dao() }
    val scope = rememberCoroutineScope()
    val rules by remember(dao) { dao.observeRules() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences by remember(context) { context.settingsDataStore.data }
        .collectAsStateWithLifecycle(initialValue = emptyPreferences())
    val automationEnabled = preferences[AppSettings.MASTER_AUTOMATION] ?: true
    val defaultSimId = preferences[AppSettings.DEFAULT_SIM_SUBSCRIPTION_ID] ?: AppSettings.AUTO_SIM
    var editingRule by remember { mutableStateOf<SmsRuleEntity?>(null) }
    var testerDraft by remember { mutableStateOf<RuleDraft?>(null) }
    var receiveAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var sendAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var ruleToDelete by remember { mutableStateOf<SmsRuleEntity?>(null) }

    val historyItems by remember(dao) { dao.allExecutionLogs() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedHistory by remember { mutableStateOf<HistoryItem?>(null) }
    var phoneStateAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
    val phoneStateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { phoneStateAllowed = it }
    val activeSims = remember(phoneStateAllowed) {
        if (!phoneStateAllowed) emptyList()
        else runCatching { SubscriptionManager.from(context).activeSubscriptionInfoList.orEmpty() }.getOrDefault(emptyList())
    }

    BackHandler(enabled = screen != AppScreen.RULES) {
        when (screen) {
            AppScreen.TESTER -> screen = AppScreen.EDITOR
            AppScreen.DETAILS -> screen = AppScreen.HISTORY
            AppScreen.ONBOARDING -> screen = AppScreen.SETTINGS
            else -> screen = AppScreen.RULES
        }
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
                onAutomationChanged = { enabled ->
                    scope.launch {
                        context.settingsDataStore.edit { it[AppSettings.MASTER_AUTOMATION] = enabled }
                    }
                },
                onOpenOnboarding = { screen = AppScreen.ONBOARDING },
                defaultSimId = defaultSimId,
                phoneStateAllowed = phoneStateAllowed,
                activeSims = activeSims,
                onRequestPhoneState = { phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                onDefaultSimChanged = { id -> scope.launch { context.settingsDataStore.edit { it[AppSettings.DEFAULT_SIM_SUBSCRIPTION_ID] = id } } },
                colorPalette = colorPalette,
                onColorPaletteChanged = onColorPaletteChanged,
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
