# SMS Rule Relay

An Android app for forwarding selected incoming SMS messages using explicit, user-created rules. Processing stays on-device; the MVP does not require internet access or read the historical SMS inbox.

## Status

The repository contains the Android project foundation, a Material 3 UI prototype,
and pure Kotlin rule-engine components. SMS receiving/sending and persistence are
not wired yet, so this baseline cannot forward messages.

## Prerequisites

- JDK 17
- Android SDK Platform 35 and Build-Tools 35.x
- Android Studio (recommended) or a compatible Gradle installation

## Commands

```bash
gradle test
gradle lint
gradle assembleDebug
```

Open the root folder in Android Studio to sync the project. A Gradle wrapper is not
checked in because build tooling was intentionally not downloaded during setup.

See [`AGENT.md`](AGENT.md) and the documents in [`docs/`](docs/) for product, privacy, and implementation requirements.
