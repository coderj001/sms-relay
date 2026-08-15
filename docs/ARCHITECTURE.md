# Architecture

## 1. Architectural Goals

The architecture must make SMS automation:

- deterministic
- testable
- resilient to process restarts
- independent from UI state
- privacy-conscious
- easy to inspect and maintain

Avoid adding layers that do not improve these properties.

## 2. High-Level Components

```text
SmsBroadcastReceiver
  -> IncomingSmsParser
  -> ProcessIncomingSmsUseCase
       -> EventDeduplicator
       -> RuleRepository
       -> RuleMatcher
       -> TemplateRenderer
       -> SendRateLimiter
       -> SmsSender
       -> ExecutionLogRepository
```

UI flow:

```text
Compose screen
  -> ViewModel
  -> Use case or repository
  -> Room
```

## 3. Layer Responsibilities

### UI

Responsible for:

- rendering state
- collecting user input
- showing validation and errors
- permission explanation and user actions

Not responsible for:

- regex execution policy
- SMS sending
- database transactions
- deduplication

### ViewModel

Responsible for:

- screen state
- invoking use cases
- combining flows
- translating domain results into UI state

### Domain

Contains pure Kotlin logic:

- sender comparison
- regex evaluation
- capture groups
- template rendering
- rate-limit decisions
- fingerprint construction

### Data

Responsible for:

- Room entities and DAOs
- repository implementations
- settings persistence
- transactions

### SMS Infrastructure

Responsible for:

- Android broadcast parsing
- subscription selection
- SmsManager calls
- sent and delivery callbacks
- mapping platform errors to domain results

## 4. Suggested Interfaces

```kotlin
interface RuleRepository {
    suspend fun getEnabledRules(): List<SmsRule>
    fun observeRules(): Flow<List<SmsRule>>
    suspend fun save(rule: SmsRule): Long
    suspend fun delete(ruleId: Long)
}

interface RuleMatcher {
    fun evaluate(rule: SmsRule, sms: IncomingSms): RuleEvaluation
}

interface TemplateRenderer {
    fun render(
        template: String,
        sms: IncomingSms,
        match: RuleMatch
    ): TemplateResult
}

interface EventDeduplicator {
    suspend fun reserveIfNew(sms: IncomingSms): Boolean
}

interface SendRateLimiter {
    suspend fun reserveSend(ruleId: Long, now: Long): RateLimitResult
}

interface SmsSender {
    suspend fun send(request: SmsSendRequest): SmsSendResult
}
```

Use reservation-style dedupe and rate-limit APIs so concurrent workers cannot both pass a check and then send.

## 5. Processing Orchestrator

The central use case owns the business flow.

```kotlin
suspend fun processIncomingSms(sms: IncomingSms) {
    if (!settings.automationEnabled()) return
    if (!deduplicator.reserveIfNew(sms)) return

    val rules = ruleRepository.getEnabledRules()

    for (rule in rules) {
        val evaluation = ruleMatcher.evaluate(rule, sms)
        if (!evaluation.matched) continue

        val limit = rateLimiter.reserveSend(rule.id, clock.now())
        if (!limit.allowed) {
            logRateLimited(rule, sms, limit)
            continue
        }

        val rendered = templateRenderer.render(
            rule.outputTemplate,
            sms,
            evaluation.match
        )

        if (rendered is TemplateResult.Error) {
            logTemplateFailure(rule, sms, rendered)
            continue
        }

        val result = smsSender.send(
            SmsSendRequest(
                destination = rule.destinationNumber,
                body = rendered.value,
                subscriptionId = selectSubscription(rule, sms)
            )
        )

        logResult(rule, sms, result)
    }
}
```

Keep this use case free of Compose and Activity dependencies.

## 6. Data Model

### SmsRuleEntity

```text
id INTEGER PRIMARY KEY
name TEXT NOT NULL
enabled INTEGER NOT NULL
sender_filter TEXT
message_regex TEXT NOT NULL
destination_number TEXT NOT NULL
output_template TEXT NOT NULL
created_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
```

### ExecutionLogEntity

```text
id INTEGER PRIMARY KEY
rule_id INTEGER
received_at INTEGER NOT NULL
sender_preview TEXT
message_preview TEXT
destination_masked TEXT
match_status TEXT NOT NULL
send_status TEXT NOT NULL
failure_code TEXT
failure_message TEXT
created_at INTEGER NOT NULL
```

### ProcessedEventEntity

```text
fingerprint TEXT PRIMARY KEY
processed_at INTEGER NOT NULL
```

### SendReservationEntity

Optional table for atomic rate limiting:

```text
id INTEGER PRIMARY KEY
rule_id INTEGER NOT NULL
reserved_at INTEGER NOT NULL
```

## 7. Transaction Boundaries

Use Room transactions for:

- reserving a dedupe fingerprint
- checking and reserving rate-limit capacity
- updating a send result tied to a pending execution

Do not implement check-then-write behavior in separate unprotected calls.

## 8. Fingerprinting

A logical event fingerprint may be based on:

```text
normalized sender
normalized body
stable timestamp bucket
subscription id when available
```

Hash the canonical representation with SHA-256 before storing it.

Do not include unstable process-specific values.

Retention should be bounded. Clean old fingerprints periodically.

## 9. Regex Strategy

Preferred design:

- compile on rule save
- validate before enabling
- evaluate off the main thread
- cap processed body size
- use a linear-time engine where practical

If Java/Kotlin regex is used, document unsupported pathological patterns and add regression tests for backtracking risk.

## 10. Background Execution

`BroadcastReceiver` should:

1. verify the action
2. parse messages
3. call `goAsync()` when needed
4. delegate to the processing use case
5. complete promptly

Use WorkManager only for durable retry or work that cannot safely finish inside receiver processing. Do not run a permanent foreground service for the MVP.

## 11. Dependency Injection

Manual constructor injection is acceptable and preferred for a small project.

Use Hilt only when:

- it already exists in the project, or
- object graph complexity clearly justifies it

Do not let framework choice obscure the domain flow.

## 12. Migrations

Once real users exist:

- never use destructive migration in release builds
- add explicit Room migrations
- test migrations from every supported schema version
- preserve rules and execution settings

## 13. Error Model

Infrastructure errors should map into stable domain codes. Avoid exposing Android result integers throughout the app.

Example:

```kotlin
sealed interface SmsSendResult {
    data object Requested : SmsSendResult
    data object Sent : SmsSendResult
    data object Delivered : SmsSendResult
    data class Failed(val code: SendFailureCode) : SmsSendResult
}
```

## 14. Observability

Use structured, masked development logs.

Good:

```text
RuleEngine evaluated=4 matched=1
SmsSender ruleId=7 result=NO_SERVICE
```

Bad:

```text
OTP 123456 received from BANK and sent to +919876543210
```
