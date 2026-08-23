# Default SIM Switcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Settings → "Default SIM" a functional switcher: Auto (reply on receiving SIM) or force a specific SIM for all forwarded sends.

**Architecture:** Shared DataStore accessor module (`AppSettings`) holds the new `default_sim_subscription_id` key (-1 = Auto); the send path resolves the SmsManager through it; the Settings screen gets a radio-dialog picker backed by `SubscriptionManager` behind a lazily-requested `READ_PHONE_STATE` permission.

**Tech Stack:** Jetpack Compose M3, DataStore Preferences, Android telephony (`SubscriptionManager`, `SmsManager`).

## Global Constraints

- No unit tests for Tasks 1–2: only plain JUnit is available (no mocking); both tasks touch Android framework statics (`SmsManager`, `SubscriptionManager`). Verified by compile + on-device checks instead (same justification as prior UI plans).
- Sentinel: `AUTO_SIM = -1`. Absent DataStore key reads as Auto.
- Do NOT restructure existing files beyond what tasks specify.
- Build command: `./gradlew :app:installDebug` (system gradle unavailable; wrapper only).
- Spec: `docs/superpowers/specs/2026-08-23-default-sim-design.md`.
- Master automation persistence fix is OUT OF SCOPE (flagged separately in spec).

---

### Task 1: AppSettings module + manifest permission + send-path override

**Files:**
- Create: `app/src/main/java/com/smsrelay/data/AppSettings.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add one line after line 3)
- Modify: `app/src/main/java/com/smsrelay/sms/ProcessIncomingSmsUseCase.kt`

**Interfaces:**
- Produces: `com.smsrelay.data.settingsDataStore` (extension val on Context), `com.smsrelay.data.AppSettings.AUTO_SIM` (= -1), `AppSettings.MASTER_AUTOMATION` (existing key, relocated), `AppSettings.DEFAULT_SIM_SUBSCRIPTION_ID` (intPreferencesKey). Task 2 consumes all of these.
- Produces: `ProcessIncomingSmsUseCase.process(sms)` behavior — honors stored SIM preference. No public signature change.

- [ ] **Step 1: Create AppSettings.kt**

```kotlin
package com.smsrelay.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore by preferencesDataStore("settings")

object AppSettings {
    const val AUTO_SIM = -1
    val MASTER_AUTOMATION = booleanPreferencesKey("master_automation")
    val DEFAULT_SIM_SUBSCRIPTION_ID = intPreferencesKey("default_sim_subscription_id")
}
```

- [ ] **Step 2: Point ProcessIncomingSmsUseCase at AppSettings**

Replace lines 9–10 (the two datastore imports) with:

```kotlin
import com.smsrelay.data.AppSettings
import com.smsrelay.data.settingsDataStore
```

Delete line 21 (`private val Context.settingsDataStore by preferencesDataStore("settings")`) and delete the entire `companion object` block (lines 78–80).

Replace `process()` (lines 28–33) with:

```kotlin
    suspend fun process(sms: IncomingSms) {
        val prefs = context.settingsDataStore.data.first()
        if (!(prefs[AppSettings.MASTER_AUTOMATION] ?: true)) return
        val preferredSim = prefs[AppSettings.DEFAULT_SIM_SUBSCRIPTION_ID] ?: AppSettings.AUTO_SIM
        val rules = dao.enabledRules()
        for (rule in rules) processRule(rule, sms, preferredSim)
    }
```

Change `processRule` signature to:

```kotlin
    private suspend fun processRule(rule: SmsRuleEntity, sms: IncomingSms, preferredSimId: Int) {
```

Add imports (alphabetical placement among existing `android.*` imports):

```kotlin
import android.telephony.SubscriptionManager
```

Replace line 59 (`val manager = ...`) with:

```kotlin
            val manager = resolveSmsManager(sms, preferredSimId)
```

Add this method below `processRule`:

```kotlin
    private fun resolveSmsManager(sms: IncomingSms, preferredSimId: Int): SmsManager {
        val canQuerySims = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (preferredSimId != AppSettings.AUTO_SIM && canQuerySims) {
            val active = runCatching { SubscriptionManager.from(context).activeSubscriptionInfoList }.getOrNull()
            if (active?.any { it.subscriptionId == preferredSimId } == true) return SmsManager.getSmsManagerForSubscriptionId(preferredSimId)
        }
        return sms.subscriptionId?.let { SmsManager.getSmsManagerForSubscriptionId(it) } ?: SmsManager.getDefault()
    }
```

- [ ] **Step 3: Add manifest permission**

In `app/src/main/AndroidManifest.xml`, after the SEND_SMS line, add:

```xml
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Fix any unresolved-import fallout (e.g., remove now-unused `androidx.datastore.preferences.core.booleanPreferencesKey` / `preferencesDataStore` imports from the use case).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsrelay/data/AppSettings.kt app/src/main/java/com/smsrelay/sms/ProcessIncomingSmsUseCase.kt app/src/main/AndroidManifest.xml
git commit -m "feat: honor default SIM preference in send path"
```

---

### Task 2: Settings UI — Default SIM row + radio dialog + wiring

**Files:**
- Modify: `app/src/main/java/com/smsrelay/ui/SmsRelayApp.kt`

**Interfaces:**
- Consumes: `AppSettings.AUTO_SIM`, `AppSettings.DEFAULT_SIM_SUBSCRIPTION_ID`, `Context.settingsDataStore` (from Task 1).
- Produces: `SettingsScreen(contentPadding, automationEnabled, onAutomationChanged, onOpenOnboarding, defaultSimId: Int, phoneStateAllowed: Boolean, activeSims: List<SubscriptionInfo>, onRequestPhoneState: () -> Unit, onDefaultSimChanged: (Int) -> Unit)`; new private composables `DefaultSimDialog`, `RadioSettingRow`.

- [ ] **Step 1: Add imports**

Add to the import block of SmsRelayApp.kt (merge alphabetically; skip any already present):

```kotlin
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.edit
import com.smsrelay.data.AppSettings
import com.smsrelay.data.settingsDataStore
import kotlinx.coroutines.flow.map
```

- [ ] **Step 2: State wiring in SmsRelayApp()**

After line 126 (`var selectedHistory by remember ...`), insert:

```kotlin
    var phoneStateAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
    val phoneStateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { phoneStateAllowed = it }
    val defaultSimId by remember { context.settingsDataStore.data.map { it[AppSettings.DEFAULT_SIM_SUBSCRIPTION_ID] ?: AppSettings.AUTO_SIM } }.collectAsState(initial = AppSettings.AUTO_SIM)
    val activeSims = remember(phoneStateAllowed) {
        if (!phoneStateAllowed) emptyList()
        else runCatching { SubscriptionManager.from(context).activeSubscriptionInfoList.orEmpty() }.getOrDefault(emptyList())
    }
```

Update the `AppScreen.SETTINGS ->` call site (~line 186) to pass the new parameters after `onOpenPermissions`:

```kotlin
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
```

(Keep whatever parameter names the current call site uses for the first four; only append the five new ones.)

- [ ] **Step 3: Rewrite SettingsScreen**

Replace the `SettingsScreen` composable (currently lines ~735–762) with:

```kotlin
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
```

- [ ] **Step 4: Add DefaultSimDialog + RadioSettingRow composables**

Insert after the `SettingToggle` composable:

```kotlin
@Composable
private fun DefaultSimDialog(current: Int, sims: List<SubscriptionInfo>, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default SIM") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RadioSettingRow("Auto", "Reply on the SIM that received the SMS", current == AppSettings.AUTO_SIM) { onSelect(AppSettings.AUTO_SIM) }
                sims.forEach { info ->
                    RadioSettingRow("SIM ${info.simSlotIndex + 1}", info.displayName?.toString().orEmpty(), current == info.subscriptionId) { onSelect(info.subscriptionId) }
                }
                if (sims.isEmpty()) Text("No active SIM detected.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
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
```

- [ ] **Step 5: Build and install**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, `Installed on ...` — fix any import errors first (e.g., missing `androidx.compose.runtime.getValue` breaks `by` delegation on collectAsState).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smsrelay/ui/SmsRelayApp.kt
git commit -m "feat: functional Default SIM picker in Settings"
```

---

### Task 3: On-device verification

**Files:** none (verification only)

**Interfaces:**
- Consumes: installed debug build from Task 2. Device connected over wireless ADB (id starts `adb-001236477008227`); screenshots cannot be viewed in-session — verify exclusively via `adb shell uiautomator dump /sdcard/ui.xml` + grep of text/bounds. Bottom nav taps: RULES (173,2232), SETTINGS (900,2232).

- [ ] **Step 1: Grant permission deterministically**

```bash
adb shell pm grant com.smsrelay android.permission.READ_PHONE_STATE
adb shell am force-stop com.smsrelay
adb shell monkey -p com.smsrelay 1
sleep 3
```

- [ ] **Step 2: Open Settings, verify row subtitle lists SIMs**

Tap (900, 2232); dump UI; expect a node containing text matching `Default SIM` whose row shows `Auto · reply on receiving SIM` (or a `SIM n · <carrier>` variant if previously set).

- [ ] **Step 3: Open dialog, verify options**

Tap the Default SIM row (find its bounds center from the Step-2 dump); dump; expect title `Default SIM`, option texts `Auto` plus `SIM 1` / `SIM 2` (count matches device's active subscriptions), each with a radio button.

- [ ] **Step 4: Select an explicit SIM, verify subtitle updates**

Tap the `SIM 2` option (or `SIM 1` on single-SIM hardware); dismiss via `Done`; dump; expect row subtitle now shows `SIM n · <carrier>`.

- [ ] **Step 5: Verify persistence across restart**

```bash
adb shell am force-stop com.smsrelay
adb shell monkey -p com.smsrelay 1
sleep 3
```

Navigate to Settings; dump; expect the same `SIM n · <carrier>` subtitle.

Reset to Auto afterwards: tap row, tap `Auto`, `Done`, verify subtitle returns to `Auto · reply on receiving SIM`.

- [ ] **Step 6: Logcat sanity**

Run: `adb logcat -d | grep -E "(smsrelay|PatternSyntax)" | tail -20`
Expected: no NEW exceptions from this feature. (Pre-existing TemplateRenderer regex crash at startup is known and unrelated.)

- [ ] **Step 7: Record results**

Append outcome to `.superpowers/sdd/progress.md`; commit nothing (verification-only task) unless fixes were required.
