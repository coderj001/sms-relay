# Security and Privacy

## 1. Security Objective

The app processes highly sensitive communication. Its safest default is local, narrow, explicit automation.

Primary invariant:

```text
Only an enabled user-created rule may authorize an outgoing SMS.
```

## 2. Data Classification

Treat these as sensitive:

- full incoming SMS body
- OTPs and authentication codes
- account and transaction details
- phone numbers and sender IDs
- outgoing destination
- regex rules that reveal business logic
- execution history

## 3. Default Privacy Model

```text
Processing: on device
Rules: local Room database
History: local Room database
Cloud: none
Backend: none
Analytics: none for MVP
Advertising SDK: none
Internet permission: unnecessary
```

Do not introduce a network permission without a documented feature and privacy review.

## 4. Logging Rules

Release builds must never log:

- full SMS body
- OTP values
- full phone numbers
- rendered outgoing body
- complete regex captures containing private data

Allowed masked examples:

```text
sender=BANK
bodyLength=84
destination=******3210
ruleId=7
result=NO_SERVICE
```

Crash reporting, if introduced later, must scrub breadcrumbs and exceptions.

## 5. History Storage

Recommended default:

```text
sender preview: masked or short label
message preview: truncated and redacted
destination: masked
full body: not stored
retention: 30 days
```

Provide deletion and retention controls before storing more detail.

## 6. Threat Model

### Broad forwarding rule

Threat:

```text
sender=any
regex=.*
```

Impact: nearly every SMS may be forwarded, including OTPs and private messages.

Mitigation:

- explicit warning before enable
- destination confirmation
- rate limit
- master switch
- clear rule summary

### Forwarding loop

Threat: two devices forward matching messages to each other.

Mitigation:

- persistent event dedupe
- per-rule cooldown
- global rate limit
- optional marker strategy only if product requirements allow modified content

Do not rely solely on outgoing-number checks; loops can involve several devices.

### Duplicate platform delivery

Threat: one logical SMS causes multiple sends.

Mitigation:

- canonical event fingerprint
- atomic reserve-if-new transaction
- bounded fingerprint retention

### Regex denial of service

Threat: user-supplied regex causes catastrophic backtracking.

Mitigation:

- prefer a linear-time regex engine
- validate on save
- evaluate away from main thread
- cap message size
- reject or warn about unsupported constructs
- include abuse regression tests

### Malicious SMS content

Threat: crafted Unicode, very long messages, unexpected control characters, or malformed multipart input.

Mitigation:

- treat all content as untrusted
- bound processing
- normalize only where semantics are clear
- never execute content
- robust parser tests

### Unauthorized device access

Threat: another person with unlocked-device access reads rules or history.

MVP mitigation:

- Android application sandbox
- avoid full body retention

Possible later mitigation:

- application lock
- encrypted sensitive fields
- biometric gate for history

Do not add security theater without defining the protected threat.

## 7. Destination Validation

Validate before save and before send.

Requirements:

- trim whitespace
- reject empty destination
- reject obvious invalid characters
- preserve leading `+`
- avoid aggressive country conversion without country context

Consider libphonenumber only when international normalization is required.

Always show enough final digits for the user to verify the destination.

## 8. Rate Limiting

Minimum protections:

```text
per-rule cooldown
global sends per minute
global sends per hour
```

Use persistent, atomic reservations. Process restarts must not reset protection immediately.

Suggested MVP defaults:

```text
5 per minute
30 per hour
```

## 9. Permission Discipline

Request only permissions needed by active features.

MVP:

```text
RECEIVE_SMS
SEND_SMS
```

Not needed:

```text
READ_SMS
READ_CONTACTS
INTERNET
ACCESS_FINE_LOCATION
```

A later feature must justify any additional permission in product and security documentation.

## 10. Template Safety

The template engine must be substitution-only.

It must not support:

- scripting
- reflection
- file reads
- shell commands
- URLs fetched at render time
- environment access
- arbitrary expressions

Unknown variables should not crash the app.

## 11. Database Security

Use Room and the Android sandbox.

Requirements:

- no real data in seed databases
- no destructive migrations after release
- bounded history retention
- safe deletion behavior
- no raw SQL built from SMS or regex input

If full bodies are ever stored, conduct a separate encryption and key-management review.

## 12. Release Security Checklist

Before release:

- inspect manifest permissions
- confirm no INTERNET permission unless intentional
- search release logs for message and number output
- test broad-rule warning
- test master switch
- test duplicate event handling
- test rate-limit boundaries
- test permission revocation
- inspect exported components
- validate PendingIntent mutability and uniqueness
- verify no signing secrets are committed
- verify backup behavior for sensitive local data
- review current SMS store policy
