# Rules Screen Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strip the Rules screen down to a clean list of rule cards, removing all decorative/excess strings and dead UI elements.

**Architecture:** All changes are in one file (`SmsRelayApp.kt`). The screen keeps its top bar, FAB, empty state, and rule cards; everything decorative (hero stat block, description paragraph, AutomationCard, always-visible PermissionStatusCard, "YOUR RULES"/"+ ADD" header row) is deleted. A permission warning is kept but only rendered when permissions are actually missing — silently broken relaying is worse than a warning card. Master automation remains reachable in Settings → Automation; onboarding remains reachable via Settings → Permissions.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), single-module Android app (`:app`).

## Global Constraints

- Do NOT add comments to code.
- Do NOT change any behavior outside `RulesScreen`, `RuleCard`, `RuleValue`, and their call sites — History, Editor, Settings, Onboarding screens are out of scope.
- Keep all existing functionality reachable elsewhere: master automation toggle lives in Settings; permission setup lives in Settings → Permissions / Onboarding screen.
- Build verification command is `./gradlew :app:compileDebugKotlin` (wrapper restored at repo root; do not use system `gradle`).
- Final install command is `./gradlew :app:installDebug`; device `A015` connects over wireless ADB (`adb connect <ip>:<port>` if disconnected).
- App id for launching: `com.smsrelay`.

**Note on testing:** This plan contains no unit tests because every change is declarative Compose UI with zero logic to assert against; the project has no Compose UI-test dependency and adding one for string deletion violates YAGNI. Verification per task = compilation passes + final visual inspection of an on-device screenshot (readable as an image file).

---

### Task 1: Remove header clutter and status cards from RulesScreen

**Files:**
- Modify: `app/src/main/java/com/smsrelay/ui/SmsRelayApp.kt` (call site ~lines 159–176; `RulesScreen` ~lines 248–296; delete `AutomationCard` ~lines 298–314; delete `PermissionStatusCard` ~lines 316–337)

**Interfaces:**
- Consumes: existing `receiveAllowed: Boolean`, `sendAllowed: Boolean`, `onOpenPermissions: () -> Unit` params (kept).
- Produces: `RulesScreen` with new signature (drops `automationEnabled` and `onAutomationChanged` params):
  ```kotlin
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
  )
  ```

- [ ] **Step 1: Update the call site** (~line 159) to drop the two automation params:

```kotlin
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
```

- [ ] **Step 2: Replace the RulesScreen body** (whole function from line 248 through the closing brace after the Scaffold) with this version. Removes the `"NN ACTIVE"` hero stat, `"ENABLED RELAY RULES"` label, description paragraph, `AutomationCard`, always-on `PermissionStatusCard`, and the `"YOUR RULES"` / `"+ ADD"` row (the FAB already covers "add"):

```kotlin
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
        topBar = { AppTopBar(title = "SMS Rules") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add Rule") })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(padding)
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
```

- [ ] **Step 3: Delete the now-dead composables** — remove both functions entirely:
  - `AutomationCard` (starts `private fun AutomationCard(enabled: Boolean, ...)` around line 298)
  - `PermissionStatusCard` (starts `private fun PermissionStatusCard(receiveAllowed: Boolean, ...)` around line 317)

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. If it fails with *Unresolved reference 'AutomationCard'* etc., a leftover call site was missed — grep `grep -n "AutomationCard\|PermissionStatusCard" app/src/main/java/com/smsrelay/ui/SmsRelayApp.kt` and remove remaining references.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/smsrelay/ui/SmsRelayApp.kt docs/superpowers/plans/2026-08-23-rules-screen-cleanup.md
git commit -m "refactor: strip Rules screen to clean card list"
```

---

### Task 2: Clean up RuleCard

**Files:**
- Modify: `app/src/main/java/com/smsrelay/ui/SmsRelayApp.kt` (`RuleCard` ~line 340, `RuleValue` ~line 370)

**Interfaces:**
- Consumes: `SmsRuleEntity` fields `name`, `enabled`, `senderFilter`, `messageRegex`, `destinationNumber`.
- Produces: `RuleValue` with simplified signature (unused `icon` param removed):
  ```kotlin
  private fun RuleValue(label: String, value: String, mono: Boolean = false)
  ```

- [ ] **Step 1: Replace RuleCard body** — removes the redundant `StatusPill` (Switch already shows enabled state), the hardcoded fake `"[ SENT ]  TODAY, 8:42 PM"` line, the duplicate bottom `Delete` IconButton, and the pointless trailing `MoreVert` IconButton:

```kotlin
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
```

- [ ] **Step 2: Replace RuleValue** — drops the unused `icon` parameter and the wrapping Row/Column that existed only to host icons:

```kotlin
@Composable
private fun RuleValue(label: String, value: String, mono: Boolean = false) {
    Spacer(Modifier.height(10.dp))
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/smsrelay/ui/SmsRelayApp.kt
git commit -m "refactor: remove redundant elements from RuleCard"
```

---

### Task 3: Install and visually verify on device

**Files:**
- None modified (verification only).

- [ ] **Step 1: Confirm device is connected**

Run: `adb devices`
Expected: a line ending in `device` containing `A015`. If empty, ask user to re-establish wireless debugging (`adb connect <ip>:<port>`) — do not proceed without a device or skip verification.

- [ ] **Step 2: Build and install**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL` and `Installed on 1 device.`

- [ ] **Step 3: Launch app and capture screenshot**

```bash
adb shell monkey -p com.smsrelay 1 > /dev/null 2>&1; sleep 3; adb exec-out screencap -p > /tmp/opencode/rules-clean.png
```
Expected: no error output.

- [ ] **Step 4: Inspect the screenshot**

Read `/tmp/opencode/rules-clean.png` as an image. Verify:
- No "ACTIVE"/"ENABLED RELAY RULES" hero text, no description paragraph, no MASTER AUTOMATION card, no "[ SYSTEM READY ]" card, no "YOUR RULES"/"+ ADD" row.
- Rule cards show only: name, Switch, edit/delete icons, INCOMING/PATTERN/SEND TO rows — no "Enabled" pill, no "[ SENT ] TODAY..." line, no extra bottom buttons.
- If a rule exists, add one via the FAB first (through the editor UI) so a card is visible in the shot.

- [ ] **Step 5: Report result**

State pass/fail of each checklist item from Step 4. If any check fails, return to the relevant task and fix before declaring done.
