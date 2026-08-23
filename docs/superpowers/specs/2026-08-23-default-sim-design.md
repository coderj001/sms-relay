# Default SIM Switcher — Design

Date: 2026-08-23
Status: Approved (user confirmed)

## Problem

Settings → Automation → "Default SIM" renders a hardcoded `SettingRow("Default SIM", "SIM 1")`
(SmsRelayApp.kt) that is not clickable and controls nothing. Outgoing forwarded SMS always go
out on the subscription that received the incoming SMS (ProcessIncomingSmsUseCase), falling back
to `SmsManager.getDefault()` when unknown.

## Goal

Make Default SIM a functional setting: the user can choose which SIM forwards are sent from,
or leave it on Auto.

## Decisions (from user)

- Semantics: **Auto + explicit override**.
  - Auto (default): reply on the SIM that received the SMS; device default if unknown. Preserves
    current behavior.
  - Explicit SIM 1 / SIM 2: forces all forwards out that subscription.
- UI: tapping the row opens a Material3 AlertDialog with a radio list (Auto, then each active SIM).

## Behavior Spec

### Data

- New DataStore (existing `"settings"` preferencesDataStore) key:
  `DEFAULT_SIM_SUBSCRIPTION_ID: Int`, sentinel `-1` = Auto. Absent key reads as Auto.

### Send path (`ProcessIncomingSmsUseCase`)

- Read the key once per `process()` alongside `MASTER_AUTOMATION`.
- If id >= 0: use `SmsManager.getSmsManagerForSubscriptionId(id)`.
- Else: existing behavior (`sms.subscriptionId` → per-subscription manager, else default).
- Stale/invalid stored id (SIM swapped/removed): `sendTextMessage` failure is already caught and
  logged as FAILED with the exception message; additionally, if the stored id is not present in
  the active subscription list at send time, fall back to the default manager instead of failing.

### UI (`SettingsScreen`)

- Row subtitle reflects live value:
  - Auto → "Auto · reply on receiving SIM"
  - Explicit → "SIM <slot+1> · <carrier display name>"
- Tap → AlertDialog titled "Default SIM":
  - Option "Auto — reply on receiving SIM"
  - One option per active subscription: "SIM <slot+1> — <carrier name>"
  - Radio selection commits immediately on tap (no extra confirm button); dialog dismisses.
- Single-SIM devices: list shows Auto + SIM 1. No SIMs detected: Auto-only with explanatory text.

### Permission

- Enumerating subscriptions requires `READ_PHONE_STATE`; add to AndroidManifest and request
  lazily when the row is first tapped.
- Denied: row subtitle shows "Permission required"; tapping re-launches the request.
- Granted: read `SubscriptionManager.activeSubscriptionInfoList`.

## Wiring

- `SmsRelayApp`: collect `context.settingsDataStore.data` mapping to the sim id (collectAsState);
  pass `defaultSimId` + `onDefaultSimChanged { scope.launch { write } }` into `SettingsScreen`.
- Subscription list is read fresh each time the dialog opens via `SubscriptionManager.from(context)`
  (needs Context wrapper; fine on main thread for active list).
- Send path keeps reading DataStore directly (same pattern as `MASTER_AUTOMATION`). No Room changes.

## Error Handling

| Case | Behavior |
|---|---|
| READ_PHONE_STATE denied | Subtitle "Permission required", tap re-requests |
| No active subscriptions | Dialog shows Auto-only note |
| Stored id no longer active | Send falls back to default SmsManager |
| Send fails | Existing FAILED log path unchanged |

## Out of Scope / Flagged

- **Master automation toggle is not persisted** (UI `remember` only; `MASTER_AUTOMATION` DataStore
  key is written by nothing). Same SettingGroup, same fix shape — recommended follow-up or fold-in.
- Rate limit ("5 per minute") and history retention ("30 days") rows remain static labels
  (rate limit value is hardcoded in the use case; retention unimplemented).
- Actual outbound SMS delivery cannot be exercised without triggering a real forward; verification
  is UI-level plus logcat inspection of chosen subscription.

## Verification Plan

1. Build + install on device.
2. Settings → tap Default SIM → grant READ_PHONE_STATE → dialog lists Auto + both SIMs.
3. Select SIM 2 → subtitle updates; force-stop + relaunch → selection persists (DataStore).
4. UI dump checks: row text, dialog contents, radio state.
5. Confirm compile of send path; logcat shows no new exceptions.
