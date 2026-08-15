# SMS Rule Relay

**A small, local-first Android automation tool for forwarding only the SMS messages you explicitly choose.**

Build a rule, test it safely, turn it on when you are ready. SMS Rule Relay matches an incoming sender and message pattern, renders your template, and can relay the result to one destination.

```text
INCOMING SMS
    ↓
sender + regex match
    ↓
template rendering
    ↓
safety checks
    ↓
OUTGOING SMS
```

> [!IMPORTANT]
> No matching enabled rule means no outgoing SMS. That is the product's core invariant.

## Why it exists

Some alerts are worth relaying; most messages are not. SMS Rule Relay is designed for the narrow, understandable middle ground: a bank-credit alert, an operational notification, or another message with a predictable format.

It is not a bulk-messaging tool, a cloud service, or a replacement for your SMS app.

## The rule in one glance

```text
FROM                 +91 98765 43210
        ↓
IF MESSAGE MATCHES   OTP\s*is\s*(\d{6})
        ↓
SEND TO              +91 91234 56789
        ↓
MESSAGE              OTP received: {{match_1}}
```

The built-in rule tester previews the match and outgoing message. It never sends an SMS.

## What the app is designed to do

- Match a sender exactly, or intentionally allow any sender.
- Match the complete incoming SMS body with a regular expression.
- Use capture groups in a simple message template.
- Enable or disable individual rules.
- Stop all automation instantly with a master switch.
- Keep local execution history with masked destinations and privacy-conscious previews.
- Handle multipart messages and messages that exceed one outgoing SMS segment.
- Apply persistent deduplication and per-rule/global send limits before sending.

## Safety and privacy by default

SMS messages can include OTPs, financial information, and personal data. The project is deliberately conservative:

- Processing, rules, and history stay on the device.
- The MVP needs only RECEIVE_SMS and SEND_SMS — never READ_SMS or INTERNET.
- Rules remain disabled until a user enables them.
- Broad rules such as “any sender” + `.*` receive a clear warning.
- Sending is rate-limited and duplicate message events are ignored.
- Release logging must not include message bodies, OTPs, or full phone numbers.
- A successful send request is never presented as delivery confirmation.

See [Security & Privacy](docs/SECURITY_PRIVACY.md) for the full threat model and data-handling rules.

## Current status

The project currently includes:

- A polished Material 3 Compose prototype: Rules, Create Rule, Regex Tester, History, Details, Permission Onboarding, and Settings.
- Pure Kotlin rule matching and template-rendering components with unit tests.
- An Android project baseline with the correct, minimal SMS permissions declared.

The SMS receiver, sender, Room persistence, rule repository, and safety-control persistence are not wired together yet. This branch cannot forward real messages.

## Architecture

```text
Compose UI
  → ViewModel
  → Use cases
  → Repositories
  → Room / Android telephony boundaries
```

The core matching, template, deduplication, and rate-limit logic stays independent of Compose and Android framework classes so it can be tested reliably.

## Development

### Prerequisites

- JDK 17 or newer
- Android SDK Platform 35 and Build-Tools 35.x
- Android Studio (recommended) or a compatible Gradle installation

Open the repository root in Android Studio to sync the project. The project is pinned to Gradle 8.10.2 through gradle/wrapper/gradle-wrapper.properties; Android Studio may download that distribution and project dependencies during the first sync.

### Commands

```bash
gradle test
gradle lint
gradle assembleDebug
```

When telephony code lands, validate it on a physical Android device as well. Emulators do not provide meaningful SMS delivery confidence.

## Documentation

| Document | What it covers |
| --- | --- |
| [Product specification](docs/PRODUCT_SPEC.md) | Product behavior and MVP acceptance criteria |
| [Architecture](docs/ARCHITECTURE.md) | Components, data flow, persistence, and transaction boundaries |
| [Android SMS implementation](docs/ANDROID_SMS.md) | Receive/send boundaries, permissions, multipart, and SIM behavior |
| [Security & privacy](docs/SECURITY_PRIVACY.md) | Privacy model, threat model, and safe defaults |
| [Testing strategy](docs/TESTING.md) | Unit, Room, Android, and physical-device testing |
| [Roadmap](docs/ROADMAP.md) | Milestones and intentionally deferred work |

## Contribution guardrails

Read [AGENT.md](AGENT.md) before making a change. In particular:

- Keep the receiver small and move orchestration into testable use cases.
- Do not add network access, analytics, or a backend to the MVP.
- Do not request READ_SMS for current-message processing.
- Never let the rule tester send a real SMS.
- Treat SMS content, regexes, and destination numbers as sensitive, untrusted input.

---

Built for predictable automation, not surprising behavior.
