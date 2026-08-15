# Testing Strategy

## 1. Goals

Testing must prove that:

- matching is deterministic
- no-match paths never send
- duplicate events do not double-send
- rate limits are atomic
- template rendering is safe
- Android receiver and sender boundaries map platform behavior correctly
- sensitive data does not leak into logs or fixtures

## 2. Test Pyramid

```text
many pure unit tests
some Room and component integration tests
focused Android instrumentation tests
mandatory physical-device telephony tests
```

Most business behavior should be covered without an emulator.

## 3. Unit Tests

### Sender matching

Cover:

```text
empty filter matches any sender
exact alphanumeric sender match
wrong sender
leading and trailing whitespace
phone number separators
case policy
null or missing originating address
```

### Regex matching

Cover:

```text
matching body
no match
capture groups
match_0
multiple groups
multiline body
Unicode body
empty body
anchored pattern
invalid pattern
large body
pathological pattern regression
```

### Template rendering

Cover:

```text
{{message}}
{{sender}}
{{match_0}}
{{match_1}}
multiple variables
repeated variable
missing group
unknown variable
Unicode
literal braces
empty template
```

### Deduplication

Cover:

```text
same canonical event twice
different sender
different body
different subscription
timestamp bucket boundary
concurrent reserve attempts
retention cleanup
process restart simulation
```

Exactly one concurrent reservation should succeed.

### Rate limiting

Test exact boundaries:

```text
first 5 sends in one minute allowed
6th blocked
capacity returns after window
30 per hour boundary
separate rule cooldowns
concurrent reservations
process restart persistence
```

### Processing use case

Use fakes for repositories, clock, sender, dedupe, and limiter.

Cover:

```text
automation off
duplicate event
zero enabled rules
one matching rule
one non-matching rule
several matching rules
template failure
rate-limited rule
send failure
all rules execute in deterministic order
```

## 4. Room Tests

Use an in-memory database for DAO and repository tests.

Cover:

- rule ordering
- enabled-rule query
- insert and update timestamps
- delete behavior
- execution-log writes
- atomic fingerprint reservation
- atomic rate-limit reservation
- retention cleanup

After schema version 1 is released, add migration tests for every supported version path.

## 5. Android Component Tests

### Receiver parser

Build intents or use platform-supported fixtures to verify:

- action filtering
- empty message list
- multipart reconstruction
- sender extraction
- timestamp extraction
- subscription metadata

### SmsSender

Abstract `SmsManager` behind a boundary so logic can be tested with a fake.

Test:

- one-part selection
- multipart selection
- invalid destination
- no subscription
- callback aggregation
- failure-code mapping

### Compose UI

Focus on high-value behavior:

- invalid regex blocks save
- tester shows captures
- enable switch requires valid rule
- broad-rule warning appears
- permission state is visible
- master switch is obvious
- delete confirmation or undo works

## 6. Manual Physical-Device Tests

A real phone is required for release confidence.

Test matrix:

```text
single SIM
dual SIM if available
screen on
screen off
app foreground
app background
process previously killed
short incoming message
multipart incoming message
Unicode incoming message
alphanumeric sender ID
normal phone number
short outgoing message
multipart outgoing message
send permission denied
receive permission denied
permission revoked after setup
no service
airplane mode
radio restored after failure
rapid message burst
same logical message delivered twice
```

Record device model, Android version, carrier, and SIM configuration for failures.

## 7. MVP End-to-End Scenarios

### Basic success

```text
Rule sender: BANK
Regex: credited.*INR\s*(\d+)
Template: Credit received: {{match_1}}
Incoming: Account credited INR 500
```

Expected:

```text
one match
match_1=500
one send request
history records sent or failure result
```

### No match

Expected: no send request.

### Disabled rule

Expected: no send request.

### Duplicate

Expected: at most one send.

### Missing permission

Expected: safe failure, visible status, no crash.

### Multipart incoming

Expected: one reconstructed evaluation.

### Multipart outgoing

Expected: platform division and one logical history item.

### Master switch off

Expected: zero sends.

### Rate-limit burst

Expected: sends above limit are blocked and explained.

## 8. Test Fixtures

Never use real OTPs, account numbers, or personal phone numbers.

Use reserved examples:

```text
+15550000001
BANK-DEMO
Your demo account was credited INR 500
```

Keep fixture content clearly synthetic.

## 9. CI Checks

Recommended pull-request checks:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Add instrumentation checks only when a stable emulator environment is available.

A failing test, lint error, or compile error blocks merge unless explicitly documented and approved.

## 10. Completion Report

Every implementation task should state:

```text
tests added
tests executed
results
manual tests not performed
known hardware or carrier gaps
```
