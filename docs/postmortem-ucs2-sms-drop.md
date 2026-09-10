# Postmortem: Long Bengali SMS silently dropped

Date: 2026-08-25
Status: Fixed and verified on test device (Samsung dual-SIM, MTK ROM)

## Symptom

The relay app matched incoming bank SMS, rendered the template, called the SMS API
without any exception, and logged **`SENT`** — but the recipient's phone never
received anything for long (Bengali) messages.

## Root cause

1. **Encoding math.** Per 3GPP TS 23.038, a single SMS carries at most 160 septets of
   GSM-7-bit text — but only **70 characters of UCS-2** (140-byte TPDU limit). The
   Kotak template renders Bengali text (`Kotak Bank: আপনি ...`), which is non-GSM, so
   it must be encoded UCS-2. At 77–84 chars it needed ~168 bytes — over the limit.
2. **Wrong API for the content.** `ProcessIncomingSmsUseCase` called
   `SmsManager.sendTextMessage(...)`, which builds exactly one submit PDU. On this
   Samsung/MTK ROM, `SmsMessage.getSubmitPdu()` returned `null` for the oversized
   UCS-2 payload and the framework dropped the message outright:

   ```
   E SmsMessage    : Message too long (168 bytes)
   E SmsDispatcher : sendText(): getSubmitPdu() returned null
   ```

3. **Silent at every layer the app can see.**
   - Those errors appear **only in the radio buffer** (`logcat -b radio`) — the
     main/default logcat buffers are clean, which is why the app looked healthy.
   - The call was made with `sentIntent = null`, so there was no callback channel at
     all, and `sendTextMessage` throws nothing here — the drop happens *after* the API
     returns.

   Result: exception-free return → app logged a false `SENT`.

## Fix

### 1. Segmentation planner (core fix)

New pure-Kotlin `app/src/main/java/com/smsrelay/domain/sms/SmsTextPlanner.kt`,
implementing TS 23.038 segmentation rules:

| Encoding | Single-part limit | Multipart segment |
|---|---|---|
| GSM-7-bit | 160 septets | 153 septets |
| UCS-2 | 70 code units | 67 code units |

It detects whether every character fits the GSM-7 basic/extension alphabets (escape
chars weigh 2 septets), then returns a `Single` plan or splits the text into
`Multipart` parts. Built test-first (TDD): 7 JVM unit tests — including the production
case, an 84-char Bengali string → `[67 + 17]` parts — watched failing against a stub,
then implemented to green.

### 2. Send path

`ProcessIncomingSmsUseCase.kt` now plans before sending:

- 1 part → `sendTextMessage`
- N parts → `sendMultipartTextMessage` (framework submits one PDU per segment)

Each part is now within limits, so the modem accepts them — verified in the radio
buffer during testing: `SMS_SEND_REQ` ×2 → `SMS_SEND_CNF` ×2 (`status=1 reason=0`) →
`Persist SMS into SENT`, and the recipient received the full reassembled message.

### 3. Failure-surfacing attempt (added, then removed with evidence)

We also tried attaching mutable `sentIntent`s and awaiting results via a dynamically
registered receiver (30 s timeout). Radio logs proved sends succeeded, but callbacks
never arrived through dynamic receivers on this ROM — under both
`RECEIVER_NOT_EXPORTED` and `RECEIVER_EXPORTED` + nonce validation. Awaiting them only
produced false `FAILED | No SMS send confirmation within 30s` rows for successful
sends and stalled rule processing. So the mechanism was removed; final semantics:

- `SENT` = exception-free submission
- `FAILED` = API-level rejection (invalid number, permission, template error, …)
- `RATE_LIMITED` / `PERMISSION_MISSING` unchanged

## Outcome

- Long Bengali relays deliver end-to-end (confirmed by recipient and radio logs).
- Unit suite green; debug logging stripped; deployed to the test device.

## Known limitations / follow-ups

- True radio-level failures remain invisible to the app on this ROM; a
  manifest-declared receiver is the untested alternative if delivery confirmation is
  ever required.
- PendingIntent gotchas if revisiting: `FLAG_MUTABLE` is required for the framework's
  `errorCode` extra; PI broadcasts fire as our UID but are sent by uid 1001 (phone),
  which dynamic receivers never saw here.
