# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

StreakCounter: a personal streak tracker. Kotlin Multiplatform + Compose Multiplatform with shared UI for Android and iOS, plus two Android-only Glance home-screen widgets. Streaks are stored locally in an encrypted file. There is no network, no accounts and no sync.

Source-of-truth documents (read these before changing behavior):
- Spec: `docs/superpowers/specs/2026-11-27-streak-counter-design.md` covers behavior per streak type, goals, editing rules (§4.1a), storage and encryption (§6.3) and state management (§6.4).
- Plan: `docs/superpowers/plans/2026-11-27-streak-counter.md` (Global Constraints, Review Focus).
- Visual design: `docs/design/design.html` (open in a browser; every screen state), `docs/design/tokens.json` (Dusk Ember colors/type/radii), `docs/design/brief.md`.
- Project conventions live as skills in `.claude/skills/` (`kmp-module-structure`, `kmp-presentation-mvi`, `kmp-data-layer`, `kmp-di-koin`, `kmp-error-handling`, `kmp-unit-testing`, `kmp-compose-*`). Follow them. The one intentional deviation is that screen state comes from AppState `transform`, not `StateFlow` (see Architecture).

## Commands

Machine requirements: JDK 21 toolchain (resolved via foojay), Android SDK with platform 37, Xcode (iOS), and about 24 GB RAM. `gradle.properties` sets `org.gradle.jvmargs=-Xmx8g` and `kotlin.native.jvmArgs=-Xmx12g`, because the release iOS framework link runs out of heap at lower values. Lower them only on smaller machines, and expect release iOS links to fail.

```bash
# Full verification gate (what every task was verified with)
./gradlew allTests :androidApp:testDebugUnitTest :androidApp:assembleDebug :composeApp:assemble :composeApp:linkDebugFrameworkIosSimulatorArm64

# Per-module tests (KMP modules: Android host + iOS simulator)
./gradlew :feature:streaks:domain:allTests
./gradlew :feature:streaks:presentation:testAndroidHostTest     # Android host (Robolectric) only
./gradlew :feature:streaks:presentation:iosSimulatorArm64Test   # iOS simulator only
./gradlew :androidApp:testDebugUnitTest                         # widgets, AppKoin, backup-rule tests

# Single test class / method
./gradlew :feature:streaks:domain:testAndroidHostTest --tests "*StreakRulesTest*"
./gradlew :feature:streaks:presentation:testAndroidHostTest --tests "*StreakListViewModelTest.double_save_creates_one_streak"

# Instrumented device tests (Android Keystore etc.; needs a running emulator, AVD "Pixel_10_Pro")
./gradlew :core:data:connectedAndroidDeviceTest

# Apps
./gradlew :androidApp:installDebug
cd iosApp && xcodegen generate     # after editing iosApp/project.yml (the .xcodeproj is generated, keep both committed)
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```

Notes:
- `:core:data:allTests` also runs `iosSimulatorKeychainTest`. This custom Exec task wraps the iOS test binary in a signed simulator `.app`, because Keychain access needs entitlements. It is macOS-only (guarded) and ordered after KGP's simulator test task.
- The iOS build only works for arm64 simulators. Gradle builds no x86_64 slice, so generic simulator destinations fail.
- The KMP convention plugin applies `dev.whyoleg.cryptography` with `configureSwiftLinkerOpts`. Without it, iOS links depend on Xcode living at `/Applications/Xcode.app`.
- Android host tests use `isReturnDefaultValues = true` (Compose runtime calls `android.os.Trace`). Stubbed Android APIs return defaults instead of failing.

## Architecture

### Modules (Gradle 9, convention plugins in `build-logic/`, type-safe project accessors)
- `:androidApp` is the Android application module. It contains `Application` (starts Koin via `startAppKoin`), `MainActivity`, the manifest, backup rules, launcher icon and all widget code (`com.streakcounter.widget`).
- `:composeApp` is a KMP library. It holds `App()` (theme + list root), `initKoin()` (idempotent and lock-guarded, assembling all modules), and the iOS `MainViewController`. `iosApp` (Swift, xcodegen) calls `doInitKoin` once in `iOSApp.init`.
- `:core:domain` holds `Result`/`DataError` and the `Today` interface. `:core:data` holds encryption (`Aead`, `EncryptingSerializer`, `KeystoreAead`, `KeychainAead`), the encrypted DataStore factory and `SystemToday`. `:core:designsystem` holds the theme from tokens, Readex Pro, `StreakCardFrame` with outline progress, and the steppers. `:core:presentation` holds locale formatting (`formatCount`, `formatShortDate`). `:core:testing` holds shared fakes (`FakeStreakRepository`, `FakeToday`, `FakeWidgetUpdater`, `FakeAead`, `TestClock`).
- `:feature:streaks:domain` holds the pure rules, `:feature:streaks:data` the repository, and `:feature:streaks:presentation` the ViewModel, session, list, sheet and celebrations.

### Data flow and invariants
- **Dates, not running counts.** `Streak` stores anchors (`count`, `lastCheckIn`, `startDate`, `countDate`). The shown value is computed by `valueOn(today)` in `StreakRules.kt`, so midnight resets, missed days and Days-since growth need no background job. `normalize(today)` writes back derived facts (an ended check-in note, `goalReachedOn`) and runs before every command.
- **Single source of truth** is `DataStore<StreakFile>` wrapped in `EncryptingSerializer`. The file format is `[version][12-byte nonce][AES-256-GCM ciphertext+tag]`. The key lives in Android Keystore or the iOS Keychain (`AfterFirstUnlockThisDeviceOnly`). The file is excluded from backup on both platforms.
- **Every mutation** is a `StreakCommand` run through `StreakRepository.execute` inside one `dataStore.updateData { }`. App taps and widget taps therefore serialize, and the call returns the before-snapshot for Undo.
- **Data-safety rules (do not break):**
  - Never install a `ReplaceFileCorruptionHandler`.
  - A non-empty unreadable file throws `CorruptionException` and is never treated as empty.
  - Key access failures are `KeyUnavailableException`, separate from corruption.
  - Nothing writes without a successful read.
  - `startFresh()` and `resetUnreadableStorage()` are the only deletes. Both re-read first and do nothing if the file is readable.
  - Never log streak names or counts.
- **Edits** (`applyEdit` in `StreakEdit.kt`) keep stored anchors unless the user actually edited the count (`countEdited` / `countChanged`). A sheet left open must not undo widget taps or midnight changes.
- `streaks: Flow<Result<…>>` **completes after an error**. Consumers re-subscribe to retry: the ViewModel uses `openAttempt`, and widgets use `WidgetRefreshSignal` with `flatMapLatest`.

### State management (owner requirement: Jetpack AppState)
- `StreakListViewModel.state` is `State<StreakListState>` built with `androidx.appstate` `transform(…, scope = viewModelScope, dispatcher)`. It reads the repository flow, `Today.date` and the session.
- `StreakSession` wraps an app-scoped `StateStore` with private keys for session-only state: undo entry, failed command, sheet draft, shown celebrations, reorder mode. Nothing in it is persisted.
- Tests use `transformTest { }` from `androidx.appstate.transform:transform-testing` and pass `testDispatcher` as the ViewModel dispatcher.

### Time
`SystemToday` emits the local date, ticks at the next local midnight (`atStartOfDayIn`, which is DST-safe) and re-reads on `refresh()`. `refresh()` is called on app ON_START, by Android time, date and time-zone broadcasts, by iOS day and zone notifications, and by widget entry points and the midnight alarm.

### Widgets (Android, Glance)
- `SmallStreakWidget` (2×1 and 2×2, `SizeMode.Exact`) and `StreakListWidget`.
- Taps go through `StreakWidgetAction`, which re-reads and never writes after a failed read.
- A setup activity picks the streak. The goal outline is drawn as an ALPHA_8 bitmap by `RoundedRectOutline`.
- A midnight `AlarmManager` alarm runs `NormalizeAll` and then `updateAll`, and is re-armed on boot, time and time-zone changes.
- Every widget entry point calls `startAppKoin(context)`, because WorkManager can run before `Application.onCreate`.
- The Android Koin config overrides `NoOpWidgetUpdater` with `GlanceWidgetUpdater`, which never throws. Widget strings are Android resources in `androidApp/src/main/res/values{,-de,-ar}`.

### UI and i18n
- Strings are Compose resources in `feature/streaks/presentation/src/commonMain/composeResources/values{,-de,-ar}`.
- `StringResourcesTest` enforces key, placeholder and plural parity, and fails if a de/ar value is still English. When you add a string, add the de and ar versions too. German uses informal "du".
- Arabic uses Arabic-Indic digits, and the number parser accepts Arabic-Indic and Persian digits. Dates are always Gregorian. RTL typography zeroes letter spacing in `StreakTheme`.
- The create/edit sheet is Calf `AdaptiveBottomSheet` (native on iOS). On iOS the discard confirm renders inside the sheet content, because a Compose dialog would appear behind the native sheet.
- Celebrations use Compottie with self-authored Lottie JSON in `composeResources/files/`. Progress is saveable, so a celebration doesn't replay on rotation, and reduced motion shows the last frame. The overlay is modal for accessibility and back dismisses it.

## Repo conventions
- Git identity for this repo is set locally (`Beshoy Samy <beshoy.samy411@gmail.com>`). Don't change it.
- Work happens on feature branches. `spec/streak-counter` holds the initial build.
- `.superpowers/` is git-ignored scratch (brainstorm companion, SDD workspaces).
