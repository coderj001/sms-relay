# Android SMS Implementation

## 1. Purpose

This document describes the Android-specific boundary for receiving and sending SMS. Keep platform code here; keep business rules in pure Kotlin components.

## 2. Permissions

Expected MVP manifest permissions:

```xml
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.SEND_SMS" />
```

Do not add `READ_SMS` for current-message processing.

Both required dangerous permissions must be requested at runtime. Explain the feature before showing the system prompt.

## 3. Receiver Declaration

The application listens for the platform SMS-received broadcast.

Conceptual manifest entry:

```xml
<receiver
    android:name=".sms.receiver.SmsBroadcastReceiver"
    android:exported="true"
    android:permission="android.permission.BROADCAST_SMS">
    <intent-filter>
        <action android:name="android.provider.Telephony.SMS_RECEIVED" />
    </intent-filter>
</receiver>
```

Verify final manifest requirements against the selected target SDK and current Android documentation.

## 4. Parsing Incoming SMS

Use the platform helper rather than manually decoding PDU extras:

```kotlin
val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
```

Receiver steps:

```text
verify SMS_RECEIVED action
extract SmsMessage parts
reject empty result
reconstruct one logical IncomingSms
hand off to processing use case
```

Do not evaluate every part separately.

## 5. Multipart Reconstruction

A long incoming message may be delivered as several parts.

Reconstruction should preserve:

```text
originating address
body order
message timestamp
subscription information when available
```

Create one `IncomingSms` object per logical message.

Test with:

- two-part GSM text
- Unicode text
- sender IDs
- normal phone numbers

## 6. Receiver Lifetime

A broadcast receiver has a limited execution window.

Use `goAsync()` for short coroutine-based processing:

```kotlin
class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pending = goAsync()
        appScope.launch {
            try {
                val sms = parser.parse(intent) ?: return@launch
                processor.process(sms)
            } finally {
                pending.finish()
            }
        }
    }
}
```

Use a controlled application-level scope. Do not leak an Activity.

If processing becomes durable or retry-heavy, enqueue WorkManager after creating a persistent event record.

## 7. Sending SMS

Use `SmsManager` at the infrastructure boundary.

Flow:

```text
validate destination
select subscription
split body with divideMessage
send one-part or multipart SMS
attach sent callbacks
optionally attach delivery callbacks
map results to domain status
```

Long body behavior:

```kotlin
val parts = smsManager.divideMessage(body)
if (parts.size == 1) {
    smsManager.sendTextMessage(...)
} else {
    smsManager.sendMultipartTextMessage(...)
}
```

Do not manually split on character counts because GSM and Unicode encoding affect segmentation.

## 8. Sent and Delivery Status

Model status separately:

```text
MATCHED
SEND_REQUESTED
SENT
SEND_FAILED
DELIVERED
DELIVERY_FAILED
```

Calling `sendTextMessage` without an immediate exception does not prove delivery.

Use unique PendingIntent identities for each message or part. Avoid reusing identical request codes and intents that overwrite each other.

For multipart sends, decide how part-level callbacks aggregate into one logical result. Recommended behavior:

```text
all parts sent -> SENT
any part failed -> SEND_FAILED
all delivery confirmations received -> DELIVERED
```

Delivery receipts depend on carrier and device support; treat them as optional evidence.

## 9. Multi-SIM Behavior

Do not assume the default SmsManager always chooses the intended SIM.

MVP behavior:

- use the system/default SMS subscription when valid
- record a clear failure when no suitable subscription exists
- do not silently switch after the user selects a specific subscription

Later behavior:

- expose subscription choice per rule
- display SIM label and masked number where available

Test with a dual-SIM physical device when possible.

## 10. Permission Changes

Permissions may be revoked after setup.

Receiver and sender paths must re-check permission state and fail safely.

Do not open an Activity from the receiver. Record the problem and let the foreground UI present the remediation action.

## 11. Process Death and Reboots

The app must not rely on an Activity or ViewModel being alive.

Persist:

- rules
- automation state
- dedupe fingerprints
- rate-limit reservations
- execution status

The platform will deliver future broadcasts according to Android behavior. Do not add a polling service.

## 12. Error Mapping

Map Android send results to domain codes such as:

```text
GENERIC_FAILURE
NO_SERVICE
RADIO_OFF
NULL_PDU
NO_DEFAULT_SUBSCRIPTION
PERMISSION_MISSING
INVALID_DESTINATION
```

Keep raw platform codes in development diagnostics only when useful.

## 13. Physical Device Test Matrix

Test at least:

```text
single SIM
dual SIM if available
screen on
screen off
app foreground
app background
process not running before receive
short incoming SMS
multipart incoming SMS
short outgoing SMS
multipart outgoing SMS
Unicode body
alphanumeric sender ID
normal phone number
permission denied
permission revoked
no service
airplane mode
rate-limited burst
```

## 14. Distribution Note

SMS permissions are sensitive and public app-store distribution may require the app to meet current default-handler or permitted-use requirements.

Treat store release as a compliance milestone. Do not hide or misrepresent SMS permission usage.

Before release, verify:

- current target API requirement
- current SMS permission policy
- current background execution behavior
- manufacturer-specific restrictions on test devices
