# AGENT.md

## Mission

Build an Android application that receives new SMS messages, evaluates user-defined sender and regex rules, and sends an SMS to a configured destination when an enabled rule matches.

> Use android skills exists in the repo for operations

Core flow:

```text
Incoming SMS
  -> reconstruct multipart message
  -> normalize sender and body
  -> deduplicate event
  -> evaluate enabled rules
  -> render output template
  -> apply safety checks and rate limits
  -> send SMS
  -> record result
```

## Core Product Invariant

```text
No matching enabled rule = no outgoing SMS.
```

Never weaken this invariant for convenience, testing, or background recovery.

## MVP Scope

Included:

- Android only
- SMS receive and send
- Exact incoming-sender filter
- Regex match against the full SMS body
- Regex capture groups
- Output message templates
- One destination per rule
- Rule enable and disable
- Regex test screen
- Local execution history
- Multipart incoming and outgoing SMS
- Persistent deduplication
- Send rate limiting
- Master automation switch
- Basic dual-SIM handling

Excluded from MVP:

- WhatsApp, Telegram, RCS, MMS, or email
- Cloud sync or backend services
- User accounts
- AI classification
- Bulk messaging or marketing campaigns
- Reading historical inbox messages
- iOS support

## Tech Stack

- Kotlin
- Jetpack Compose and Material 3
- AndroidX Navigation Compose
- Room
- Kotlin Coroutines and Flow
- Android Telephony APIs
- DataStore Preferences for small global settings
- JUnit, AndroidX Test, and Compose UI tests

Use manual dependency injection unless the project already uses Hilt.

## Architecture

```text
Compose UI
  -> ViewModel
  -> Use cases
  -> Repositories
  -> Room / Android Telephony boundaries
```

Keep the rule engine, template renderer, deduplicator, and rate limiter testable without Android framework classes.

Recommended package layout:

```text
app/
data/db/
data/repository/
domain/model/
domain/rule/
domain/template/
domain/usecase/
sms/receiver/
sms/parser/
sms/sender/
sms/status/
ui/onboarding/
ui/rules/
ui/editor/
ui/tester/
ui/history/
ui/settings/
util/
```

## Required Domain Models

At minimum:

```text
SmsRule
IncomingSms
RuleEvaluation
RuleMatch
SmsSendResult
ExecutionLog
```

A rule should contain:

```text
id
name
enabled
senderFilter
messageRegex
destinationNumber
outputTemplate
createdAt
updatedAt
```

## Critical Implementation Rules

1. Request only `RECEIVE_SMS` and `SEND_SMS` for the MVP.
2. Do not request `READ_SMS` unless a later feature truly requires historical inbox access.
3. Reconstruct multipart SMS before evaluating rules.
4. Validate every regex before an enabled rule can be saved.
5. Treat regex and SMS body text as untrusted input.
6. Never evaluate rules on the main thread.
7. Never log full SMS bodies, OTPs, or complete phone numbers in release builds.
8. Persist deduplication state; in-memory flags are insufficient.
9. Enforce global and per-rule rate limits before sending.
10. Testing a rule must never send a real SMS.
11. A successful send API call is not the same as confirmed delivery.
12. Show permission, no-service, invalid-destination, and send failures clearly.
13. Keep the receiver small; move orchestration into a testable use case.
14. Do not add network access, analytics, or a backend to the MVP.
15. Keep rules disabled until the user explicitly enables them.

## Rule Semantics

- Empty sender filter means any sender.
- Non-empty sender filter uses exact normalized comparison in the MVP.
- Regex uses find/contains semantics against the complete reconstructed body.
- Users may use `^` and `$` to require a full-body match.
- If several enabled rules match, all matching rules execute in deterministic order.
- Missing capture groups render as an empty string and produce an editor warning.

Supported template variables:

```text
{{message}}
{{sender}}
{{match_0}}
{{match_1}}
{{match_2}}
{{timestamp}}
```

Do not support arbitrary expressions or executable templates.

## Safety Requirements

Mandatory protections:

- Master automation switch
- Persistent event fingerprint deduplication
- Per-rule cooldown
- Global send rate limit
- Warning for broad rules such as sender=any and regex=`.*`
- Masked destinations in history
- No full SMS content in analytics or logs
- Clear emergency disable path

Suggested initial global limits:

```text
5 automatic sends per minute
30 automatic sends per hour
```

These are product defaults, not hard protocol limits.

## Agent Workflow

Before changing code:

1. Read this file.
2. Read the relevant document under `docs/`.
3. Inspect the current architecture and related tests.
4. Identify permission, persistence, privacy, and migration impact.
5. Keep the planned change narrow.

While coding:

- Keep domain logic independent from Compose and Android framework classes.
- Use immutable UI state where practical.
- Use coroutines instead of unmanaged threads.
- Use Room transactions where dedupe or rate-limit atomicity matters.
- Do not store Activity or View references in long-lived objects.
- Use application context only in infrastructure code that requires it.
- Add meaningful errors instead of swallowing exceptions.
- Keep every intermediate commit buildable.

After coding, run when supported:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

For telephony changes, also test on physical Android hardware.

Report:

```text
what changed
tests run
tests not run
known limitations
permission or policy impact
```

## Definition of Done

A feature is complete only when:

- behavior is implemented
- validation is implemented
- error paths are implemented
- applicable tests exist and pass
- the debug build compiles
- lint has been checked
- no sensitive logging was added
- documentation is updated when behavior or architecture changes

Telephony behavior is not release-ready until manually validated on a real device.

## Build Commands

From the repository root:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

When a device or emulator is available:

```bash
./gradlew connectedAndroidTest
```

## Documentation Index

- Product behavior and acceptance criteria: `docs/PRODUCT_SPEC.md`
- Components, data flow, and persistence: `docs/ARCHITECTURE.md`
- Android SMS receive/send implementation: `docs/ANDROID_SMS.md`
- Threat model and privacy requirements: `docs/SECURITY_PRIVACY.md`
- Automated and manual test strategy: `docs/TESTING.md`
- Milestones and future work: `docs/ROADMAP.md`

## Final Reminder

Prefer reliability, privacy, and predictable automation over feature count.
