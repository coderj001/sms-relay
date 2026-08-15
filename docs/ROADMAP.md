# Roadmap

## Product Strategy

Build the smallest reliable on-device SMS rule engine first. Do not expand into cloud services, additional communication channels, or AI until real-device SMS receive and send behavior is stable.

## Milestone 0 - Project Bootstrap

Deliver:

- Kotlin Android project
- Jetpack Compose app shell
- Material 3 theme
- Room setup
- coroutines and Flow
- package structure
- test setup
- version catalog

Exit criteria:

```text
app launches
unit test runs
lint runs
debug APK builds
```

## Milestone 1 - Pure Rule Engine

Deliver:

- domain models
- sender matcher
- regex validator and evaluator
- capture groups
- template renderer
- error model
- unit tests

Exit criteria:

```text
all rule behavior works without Android telephony classes
invalid regex is represented clearly
```

## Milestone 2 - Rule Management

Deliver:

- Room entities and DAOs
- rule repository
- rules list
- rule editor
- enable and disable
- delete behavior
- regex tester

Exit criteria:

```text
user can create, test, save, edit, disable, and delete a rule
invalid enabled rules cannot be saved
```

## Milestone 3 - Receive SMS

Deliver:

- permission onboarding
- manifest receiver
- incoming message parser
- multipart reconstruction
- processing use case connection
- basic history entry

Exit criteria:

```text
physical device receives a real SMS
a matching rule is detected once
no outgoing SMS is sent yet
```

## Milestone 4 - Send SMS

Deliver:

- SmsManager boundary
- one-part sending
- multipart outgoing sending
- sent callbacks
- failure mapping
- execution history status

Exit criteria:

```text
matching real SMS triggers one configured outgoing SMS
failure is visible and does not crash the app
```

## Milestone 5 - Reliability and Safety

Deliver:

- persistent event dedupe
- atomic rate limiting
- per-rule cooldown
- master automation switch
- broad-rule warning
- permission revocation handling
- basic subscription selection
- log masking audit

Exit criteria:

```text
duplicate events do not double-send
burst protection works
master switch blocks all sends
release logs contain no sensitive content
```

## Milestone 6 - Release Hardening

Deliver:

- physical-device test matrix
- Room migration plan
- accessibility review
- privacy documentation
- exported-component review
- release build
- signing setup outside source control
- current store-policy review

Exit criteria:

```text
unit tests pass
lint passes
debug and release builds compile
manual device scenarios pass
known carrier and device limitations are documented
```

## First Coding Order

For an empty repository:

1. Bootstrap project.
2. Create pure domain models.
3. Implement matcher and renderer.
4. Add unit tests.
5. Add Room persistence.
6. Build rule editor and tester.
7. Add permission flow.
8. Add SMS receiver and parser.
9. Connect processing use case.
10. Add sender and callbacks.
11. Add history.
12. Add dedupe and rate limiting.
13. Test on real hardware.
14. Harden privacy and release behavior.

Keep the project compiling after every step.

## Deferred Features

Consider only after MVP reliability:

- named regex groups
- sender regex
- multiple destinations
- explicit rule priority
- stop after first match
- per-rule SIM selection
- rule import and export
- encrypted backup
- time-window conditions
- local failure notifications
- delivery statistics
- application lock

## Explicitly Not Planned Yet

- WhatsApp or Telegram integration
- RCS automation
- cloud dashboard
- account system
- AI classification
- scheduled campaigns
- bulk SMS
- contact harvesting
- iOS version

## Decision Log Candidates

Record architectural decisions when these are resolved:

- regex engine selection
- missing capture-group behavior
- exact sender normalization policy
- all matching rules versus first match
- default subscription policy
- execution-history detail and retention
- WorkManager retry policy
- public distribution channel

## Release Gates

Do not publish a release that lacks any of these:

```text
master automation switch
persistent dedupe
rate limiting
permission error handling
sensitive log masking
real-device validation
clear rule enable state
```

Reliability and privacy are release requirements, not future enhancements.
