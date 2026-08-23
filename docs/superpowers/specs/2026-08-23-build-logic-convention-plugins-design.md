# Build-Logic Convention Plugins

Centralize shared Android build configuration (compileSdk, minSdk, JVM toolchain, packaging) in a single convention plugin, eliminating duplication from per-module `build.gradle.kts` files.

## Scope

Single convention plugin: `myapp.android.application`. One module (`:app`), so this is a forward-looking structural refactor — it sets up the composite-build pattern for future multi-module growth without overbuilding now.

## What the plugin sets

| Config | Value |
|---|---|
| compileSdk | 35 |
| minSdk | 26 |
| JVM toolchain | 21 (sets both Kotlin jvmTarget and Java source/target compatibility) |
| packaging excludes | `/META-INF/{AL2.0,LGPL2.1}` |

## What stays in app/build.gradle.kts

- `namespace` (per-module)
- `applicationId`, `targetSdk`, `versionCode`, `versionName`
- `buildFeatures { compose = true }`
- All `dependencies` (implementation, ksp, debug, test, androidTest)
- KSP plugin (applied per-module)

## File structure

```
build-logic/
  settings.gradle.kts                          — version catalog recreation + include(":convention")
  convention/
    build.gradle.kts                           — kotlin-dsl plugin only
    src/main/kotlin/
      myapp.android.application.gradle.kts     — the convention plugin body
```

## File details

### New: `build-logic/settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

### New: `build-logic/convention/build.gradle.kts`

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.plugins.android.application.toDep())
}

fun Provider<PluginDependency>.toDep() = map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
```

### New: `build-logic/convention/src/main/kotlin/myapp.android.application.gradle.kts`

Precompiled script plugin — the filename becomes the plugin ID.

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(21)
}
```

### Modified: root `settings.gradle.kts`

Add before the `plugins` block:

```kotlin
includeBuild("build-logic")
```

### Modified: `gradle/libs.versions.toml`

Add to `[plugins]`:

```toml
myapp-android-application = { id = "myapp.android.application" }
```

### Modified: root `build.gradle.kts`

Add to `[plugins]`:

```kotlin
alias(libs.plugins.myapp.android.application) apply false
```

### Modified: `app/build.gradle.kts`

Before (60 lines):
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.smsrelay"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.smsrelay"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies { ... }
```

After (~40 lines, 5 lines removed):
```kotlin
plugins {
    alias(libs.plugins.myapp.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.smsrelay"
    defaultConfig {
        applicationId = "com.smsrelay"
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
}

dependencies { ... }
```

## Verification

1. `./gradlew :app:assembleDebug` — full build succeeds
2. `./gradlew :app:testDebugUnitTest` — all tests pass
3. `./gradlew :app:installDebug` — installs and launches on device
4. Spot-check: `app/build.gradle.kts` no longer contains `compileSdk`, `minSdk`, `compileOptions`, `kotlinOptions`, or `packaging`
