# StreakCounter: design

Date: 2026-11-27 · Status: draft for review
Visual design (source of truth for look and states): `docs/design/design.html`, tokens `docs/design/tokens.json`, brief `docs/design/brief.md`.

## 1. Intent

A personal streak tracker for one person (the owner). They track as many streaks as they like and bump them with one tap, from the app or from Android home-screen widgets. It is a personal tool, not a product for the store: no accounts, no sync, no onboarding.

Built with Kotlin Multiplatform and Compose Multiplatform. The shared UI runs on Android and iOS; Android also gets two Glance widgets. iOS widgets are out of scope.

Why this shape: the owner wants a calm, encouraging counter they can hit in a second, that keeps private habits (for example "No smoking") private on disk, and that celebrates goals without nagging.

## 2. Goals, non-goals, anti-goals

**Goals**
- Unlimited streaks of three types (Check-in days, Days since, Times), each with an optional name, starting value and optional goal.
- One screen: a list of streak cards with +, −, reset; tap a card to edit it in a bottom sheet.
- Goal progress drawn as the card outline; a Lottie celebration when a goal is reached.
- Two Android widgets: a small one for one chosen streak (2×1 and 2×2), and a list widget.
- Data stored locally and encrypted at rest.
- English, German and Arabic (RTL), following the phone language.
- Light and dark theme following the system.

**Non-goals**
- Cloud sync, accounts, multi-device, export/import, backup/restore.
- History, charts, statistics beyond the current count.
- Reminders or notifications.
- iOS widgets. In-app language picker.
- Store-release polish (privacy policy, store listing, analytics, crash reporting).

**Anti-goals** (failures even if everything "works")
- Silently losing streaks: wiping the data file on a read/decrypt error, or a widget tap writing over real data before it loaded.
- Copy or visuals that scold the user when a streak breaks.
- Streak names or counts ending up in logs, crash reports or unencrypted files.

## 3. Constraints (decided by the owner)

| Constraint | Source |
|---|---|
| KMP + Compose Multiplatform, shared UI for Android and iOS; iOS widget ignored | user |
| State management MUST use Jetpack AppState (`androidx.appstate.transform:transform`, `androidx.appstate.statestore:statestore`, 1.0.0-alpha03 or newer). Both are KMP (iOS artifacts verified on Google Maven) | user |
| Local storage with DataStore, encrypted | user |
| Calf (`com.mohamedrejeb.calf:calf-ui`) for the adaptive bottom sheet, where it helps | user (optional lib) |
| Lottie-style celebration on goal reached | user |
| Project conventions from `.claude/skills/kmp-*` (module structure, MVI, Koin, data layer, error handling, tests) | repo |

Calf 0.14.0 requires Kotlin 2.4.20, Compose Multiplatform 1.12.0 and compileSdk 37 (AGP 9.4+). If that clashes with the rest of the toolchain, fall back to the Material 3 `ModalBottomSheet`; the sheet's design does not depend on Calf.

## 4. Behavior

### 4.1 Streak types

| Type | Number shown | + | − | Reset | Auto-reset toggle |
|---|---|---|---|---|---|
| Check-in days (gym) | days in a row | "done today", at most once per local day | if done today: undo today's check-in; otherwise: correct the count down by 1 (floor 0) | count to 0 | on: a missed full day drops it to 0 |
| Days since (no smoking) | whole days since the start date; grows by itself | start date one day earlier | start date one day later (floor: today) | "I slipped": start date = today | not shown, doesn't apply |
| Times (water) | count | +1 | −1, floor 0 (button disabled at 0) | count to 0 | on: back to 0 at local midnight |

- **Starting value** (default 0): Check-in = days already done, today not yet checked; Days since = how many days ago it started; Times = starting count.
- **Name** is optional. Unnamed streaks display the type name, muted ("Check-in streak", "Days-since streak", "Count").
- **Type is fixed after creation.** Changing it would silently reinterpret the number.
- **"Today"** is the phone's current local date. Travel across time zones moves the day with the user. Recompute on local midnight and on time-zone or clock changes.
- **Check-in with auto-reset on**: if the last check-in is before yesterday, the streak has ended. It shows 0 and the card shows a one-time note "Ended 25 Nov after 12 days. A new streak starts with your next check-in." The note goes away with the next tap on that card.
- **Limits:** starting value, current count and goal are whole numbers 0–99,999.

### 4.1a Editing an existing streak (designer's call, see 9)

Edits never lose a count silently. Rules applied when the edit sheet is saved:

| Edit | Result |
|---|---|
| Check-in: auto-reset switched **on** while the last check-in is older than yesterday | Not retroactive. The rule starts from today: `lastCheckIn` is moved to yesterday so the count survives and today is still open. |
| Check-in: auto-reset switched **off** | Nothing else changes. A streak that already ended stays ended (its old count lives only in the ended note). |
| Check-in: "Current count" edited after checking in today | Today's check-in is kept; the count becomes the typed number. |
| Times: "Reset at midnight" switched **on** with a running total of 40 | Today's count stays 40 (`countDate = today`), it resets at the next midnight. The goal becomes a daily goal; the sheet relabels the field "Daily goal" live, so the change is visible before saving. |
| Times: "Reset at midnight" switched **off** | Today's count becomes the running total from here on; the goal becomes a total goal (label "Goal"). |
| Days since: "Days so far" edited | `startDate = today − N`. |
| Goal edited | Validated only if the goal field changed: must be more than the current count (a total goal) or ≥ 1 (a daily goal). An untouched goal that is already reached doesn't block saving. Clearing it removes the goal and the outline. |

When an auto-reset check-in streak ends, `normalize` sets `count = 0` and stores `EndedNote(endedOn, previousCount)` for the one-time card note.

### 4.2 Goals

- Optional per streak. For Times with midnight reset it's a **daily goal**; otherwise a total.
- Must be greater than the current count when saved (field error "Make it more than 12").
- Progress = min(value / goal, 1), drawn as the card outline (start at the top-start corner, clockwise; mirrored in RTL). Subtitle shows "12 of 30, 18 to go"; when reached, "goal 30 reached". Rows without a goal have no outline.
- **After reaching the goal the count keeps going.** There is no "Set next goal" control on the card. The user changes the goal in the edit sheet, which shows "Goal reached on 24 Nov. Type a bigger number for a new one."
- **Celebrations:**
  - Day streaks (Check-in, Days since, Times without reset): big celebration, a centered card over a dimmed screen with the Lottie animation, the number, a heading built from the name and unit ("No smoking · 30 days"; unnamed: "30 days"; Times: "40 times"), *Keep going* and *Set a new goal* (opens the edit sheet). Shown once per goal value.
  - Times with daily goal: small burst around the card plus a snackbar "Glasses of water: 8 today. That's your goal." (name + count, no invented units). Once per day.
  - Goals reached while the app was closed (Days since crossing over, a widget tap) celebrate the next time the app is in the foreground. Several pending celebrations play one after another, big ones first.
  - Reduced motion: show the last frame, no movement.
- Widgets can't animate: when reached, the widget outline closes and a check badge appears.

### 4.3 List screen

Order is manual (drag to reorder via long-press or the reorder button; reorder mode compacts cards to one line). New streaks go to the bottom.

Actions and feedback (error tiers from `docs/design`, T1 field … T6 dialog):

| Action | Behavior | Feedback |
|---|---|---|
| Tap card (outside its buttons) | open edit sheet | |
| Reset | apply immediately | T3 snackbar "Gym reset to 0 · Undo", 8 s |
| Swipe card toward the start edge past 40 % | delete | T3 "Deleted 'Gym' · Undo", 10 s |
| Delete in edit sheet | delete, close sheet | same T3 |
| Write fails | the screen keeps showing what is on disk (the change is rolled back; disk stays the only truth, also for widgets) | T3 "Couldn't save that change. · Try again". *Try again* replays the same command against the current file, never writes an in-memory list. Repeated failures: T4 banner "Changes aren't being saved. Free up some storage." |
| Data file can't be read or decrypted | show nothing else | T5 "Couldn't open your streaks" with *Try again* and *Start fresh* (T6 confirm, deletes the file) |

The full state list for every screen is in `docs/design/brief.md` ("State plan") and drawn in `docs/design/design.html`.

### 4.4 Create / edit sheet

Fields: Name (optional) · Type (segmented: Check-in / Days since / Times; locked text when editing) · Starting value (label per type; "Current count" / "Days so far" when editing) · Goal (optional) · Auto-reset switch (Check-in: "Break the streak if I miss a day"; Times: "Reset at midnight"; hidden for Days since). Save button; in edit mode also "Delete streak".

Validation on blur and on save, T1 under the field, focus to the first invalid field. Closing with typed changes asks "Discard this streak?" (T6); an untouched sheet closes silently.

### 4.5 Widgets (Android only)

- **Small widget** (`SizeMode.Responsive`, 2×1 and 2×2): one chosen streak. Name, number, − and + (48 dp targets). Days since shows number and name only. 2×2 adds "18 to go". Goal outline as in the app. Tap name/number opens the app.
- **Setup screen** when the small widget is placed: pick a streak (radio list) → Add widget. No streaks yet: "Create a streak first". If the chosen streak is deleted later, the widget shows "Streak removed. Tap to pick another." and opens setup.
- **List widget** (4×2 and larger): all streaks in app order, rows with outline, number, −/+ (Days since: number only). Empty: "No streaks yet" + "Open StreakCounter".
- Widgets follow the system light/dark theme using the same tokens.
- If the data file can't be read, widgets show "Open StreakCounter" and their buttons do nothing; they never write.
- Widgets refresh after every change (in app or widget) and at local midnight.

**Security note for the owner:** encryption protects the file on disk, not the screen. A widget showing "No smoking · 47 days" is readable by anyone who sees the home screen. The owner chooses which streaks go on widgets; there is no hidden mode.

## 5. Approaches considered

| Approach | Trade-off | Decision |
|---|---|---|
| **A. StateStore as the source of truth**, persisted with `syncToDataStore` into an encrypted DataStore | One API. But `getState` returns the default (empty list) until the file is restored, so an early widget tap could write "1" over real data unless a hand-built "loaded" guard is added. Google positions StateStore for transient session state. | rejected |
| **B. Encrypted typed DataStore as the source of truth; AppState `transform` derives UI state; `StateStore` holds session state** | DataStore reads wait for the file naturally, so the restore race can't happen. AppState is used as designed. Session state (undo, sheet draft) is lost on process death, which is acceptable. | **chosen by the owner** |
| C. Room KMP + SQLCipher | Heavier, migrations, native SQLCipher builds, for one small list; owner asked for DataStore | dropped |

## 6. Architecture (approach B)

### 6.1 Modules

Follow `kmp-module-structure` (Gradle 9, convention plugins in `build-logic`, `:composeApp` KMP library + thin `:androidApp`). Suggested layout:

```
:androidApp                   Activity, Application (startKoin), manifest, Glance widgets + receivers, setup Activity, midnight alarm
:composeApp                   app root composable, theme wiring, Koin assembly, iOS MainViewController
:core:domain                  Result/DataError, Clock/date abstractions
:core:data                    encrypted DataStore factory, Aead expect/actual, key management
:core:designsystem            Dusk Ember theme from tokens.json, Readex Pro, components (StreakCard, Stepper, sheet, snackbar)
:core:presentation            UiText, shared UI helpers
:feature:streaks:domain       Streak model, StreakRepository interface, value/goal rules (pure functions)
:feature:streaks:data         DataStore-backed repository, DTOs/mappers, schema version
:feature:streaks:presentation ViewModel(s), list screen, sheet, celebrations, session state
```

Widgets can live in `:androidApp` or in an Android-only `:feature:streaks:widget` module; builder's call. They depend on `:feature:streaks:domain` and get the repository from Koin. Module granularity beyond this is the builder's call within the skill rules.

### 6.2 Domain model (sketch)

```kotlin
enum class StreakType { CHECK_IN, DAYS_SINCE, TIMES }

data class Streak(
    val id: Uuid,
    val name: String?,               // null = unnamed
    val type: StreakType,
    val autoReset: Boolean,          // ignored for DAYS_SINCE
    val goal: Int?,                  // null = no goal
    val position: Int,
    // type-specific anchors; the shown value is computed from these and today
    val count: Int,                  // CHECK_IN: days in a row as of lastCheckIn; TIMES: count for countDate
    val lastCheckIn: LocalDate?,     // CHECK_IN
    val startDate: LocalDate?,       // DAYS_SINCE
    val countDate: LocalDate?,       // TIMES with autoReset: the day `count` belongs to
    val goalReachedOn: LocalDate?,
    val celebrated: CelebrationMark?, // last goal (and day, for daily goals) already celebrated
    val endedNote: EndedNote?,       // CHECK_IN: "ended on X after N days", shown once
)

// Pure, fully unit-tested
fun Streak.valueOn(today: LocalDate): Int
fun Streak.isDoneToday(today: LocalDate): Boolean
fun Streak.progressOn(today: LocalDate): Float?          // null without goal
fun Streak.pendingCelebration(today: LocalDate): Celebration?
fun Streak.increment(today: LocalDate): Streak            // and decrement, reset, normalize(today)
```

Storing dates instead of a running count is the key decision: midnight resets, missed days and Days since growth all fall out of `valueOn(today)`, and nothing has to run at midnight to keep data correct. `normalize(today)` writes back derived facts that must be remembered (an ended check-in streak, `goalReachedOn`) and runs on app start, on the midnight tick and before every write.

Starting value mapping on create: Check-in N>0 → `count = N, lastCheckIn = yesterday`; N=0 → `count = 0, lastCheckIn = null`. Days since N → `startDate = today − N`. Times N → `count = N, countDate = today`.

### 6.3 Storage and encryption

- `DataStore<StreakFile>` (androidx DataStore core + okio, KMP) where `StreakFile(schemaVersion: Int, streaks: List<StreakDto>)` is serialized with kotlinx.serialization (JSON or CBOR) and wrapped by an `EncryptingSerializer<T>(delegate, aead)` implementing `OkioSerializer<T>`.
- File format: `[1 byte format version][12-byte nonce][AES-256-GCM ciphertext + tag]`, fresh nonce per write, associated data = file name + format version.
- `Aead` is `expect`/`actual`:
  - Android: a non-exportable AES-256-GCM key in Android Keystore (`KeyGenParameterSpec`, no user-auth requirement so widgets work after first unlock). Tink with a Keystore master key is also fine.
  - iOS: a 256-bit key generated once and stored in Keychain with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`; AES-GCM through a KMP crypto library (e.g. `dev.whyoleg.cryptography` with the Apple provider) or a small Swift bridge. Builder's call.
- **No `ReplaceFileCorruptionHandler`.** A decrypt or parse failure surfaces as `DataError.Local.UNREADABLE` → T5 screen. Only the explicit "Start fresh" deletes the file.
- **Backup off for this data:** Android `dataExtractionRules`/`fullBackupContent` exclude the DataStore file (the key can't follow it, so a restored copy would be unreadable). iOS: set `isExcludedFromBackup` on the file.
- Schema migrations: bump `schemaVersion`, migrate in the serializer read path.
- Never log streak names or counts. Errors log the error type only.

**Security risks, spelled out for the owner:**
- Without encryption, anyone with the app's data folder (rooted phone, an `adb backup`, a copied backup) reads every streak name. With this design they get ciphertext.
- Encryption does not hide the screen or the widgets (see 4.5), and does not protect against someone who has the unlocked phone in hand.
- Turning on backup later would not bring streaks back on a new phone; it would produce the T5 "can't open" screen. Real backup needs export/import, a separate project.
- A bug that treats a read failure as "empty" and saves would overwrite the encrypted file and lose everything. That is why there is no auto-replace handler and why writes only happen after a successful read.

### 6.4 State management with AppState

- `StreakRepository` (domain interface; data implementation wraps the DataStore): `val streaks: Flow<Result<List<Streak>, DataError.Local>>`, plus suspend commands `create`, `update`, `increment(id)`, `decrement(id)`, `reset(id)`, `delete(id)`, `restore(snapshot)`, `reorder(ids)`, `markCelebrated(id, mark)`, `startFresh()`. Every command is one `updateData { }` transaction that first applies `normalize(today)`.
- `Today` source (`core:domain` interface, platform actual): `StateFlow<LocalDate>` that ticks at local midnight and on time-zone/clock change (Android broadcast receivers for `TIME_SET`/`TIMEZONE_CHANGED`/`DATE_CHANGED` while in foreground plus a check on resume; iOS `NSCalendarDayChanged`/`NSSystemTimeZoneDidChange`).
- **Session state** in one app-scoped `StateStore` (Koin `single`), owned by a `StreakSession` class with `private` keys, never exposed to UI directly:
  - `UndoKey`: last undoable action (snapshot + message + expiry).
  - `SheetDraftKey`: the open sheet's draft, so rotation and backgrounding keep it.
  - `CelebrationQueueKey`: celebrations already shown this session (guards double-play before `markCelebrated` lands).
  Nothing in it is persisted (`PersistToDataStore` not used).
- **ViewModel** (MVI per `kmp-presentation-mvi`, with one deliberate deviation): instead of `MutableStateFlow`, the screen state is
  ```kotlin
  val state: State<StreakListState> = transform(initialValue = StreakListState.Opening, scope = viewModelScope) {
      val streaks by repository.streaks.collectAsState(initial = null)
      val today by today.date.collectAsState()
      StreakListState.from(streaks, today, session.undo, session.pendingCelebrations, …)
  }
  ```
  Actions (`OnIncrement(id)`, `OnReset(id)`, `OnCardClick(id)`, `OnUndo`, `OnSave(draft)` …) go through `onAction`. The Root composable reads `viewModel.state.value`. This deviation from the skill's `StateFlow` pattern is required by the owner's AppState constraint; note it in code.
- "Opening" skeleton shows only if the first value takes more than 300 ms.
- Tests use `androidx.appstate.transform:transform-testing` (`transformTest`).

### 6.5 Widgets

- Glance (`androidx.glance:glance-appwidget`), two `GlanceAppWidget`s + receivers in the app process. They get `StreakRepository` and `Today` from Koin (started in `Application`), read with `collectAsState` inside `provideContent`.
- +/− are `ActionCallback`s → repository command → `updateAll()` for both widget types. The app also calls `updateAll()` after every command.
- Small widget's chosen streak id is stored per `appWidgetId` in Glance state (an id only, not sensitive).
- Midnight refresh: an inexact `AlarmManager` alarm at next local midnight (re-armed on boot, time and time-zone change) that runs `normalize` + `updateAll()`. WorkManager is acceptable if the builder prefers.
- Goal reached from a widget marks the celebration pending; the app plays it on next foreground.
- Before first unlock after a reboot the app process can't start (not direct-boot aware); launchers show the last drawn widget. No special state.

### 6.6 UI

- Theme from `docs/design/tokens.json` (Dusk Ember): colors per light/dark, type scale, spacing, radii. Map roles to a Material 3 `ColorScheme` plus app-specific roles (`confetti-*`, outline track).
- Font: Readex Pro (OFL, covers Latin and Arabic) bundled via Compose resources. Numbers use tabular figures.
- Icons: Material Symbols Rounded, imported as vector resources (the names listed on the design system page).
- Card outline progress: draw a rounded-rect path, measure it, draw the track and the progress segment from the top-start corner; mirror for RTL.
- Bottom sheet: Calf `AdaptiveBottomSheet` (fallback Material 3).
- Lottie: Compottie (KMP Lottie player). Two animations: big celebration (~2.5 s, plays once, rests on last frame) and small burst (~1 s). Tint to the theme's confetti and primary colors. Source: LottieFiles free-license files or custom; record the license in the repo.
- Strings in Compose resources: `values/`, `values-de/`, `values-ar/`, with plurals. Numbers and dates formatted per locale; Arabic shows Arabic-Indic digits.
- Accessibility: whole card is a button labelled "Edit Gym"; custom accessibility actions on the card (Check in / Undo / Reset / Delete / Move up / Move down); targets ≥ 48 dp; contrast verified for every pair in `tokens.json`.
- Launcher icon: adaptive icon (plum background, ember outline-with-check foreground, monochrome layer) per the Shell & brand page.

## 7. Testing

- Domain (`commonTest`): `valueOn`, increment/decrement/reset, normalize and celebration rules for each type, across midnight, a missed day, multiple missed days, DST change days, time-zone moves east and west, the 99,999 limit, goal edits below/at/above the count.
- Encryption: round trip; tampered byte fails with `UNREADABLE` (not empty data); wrong key fails; empty file reads as "no streaks yet".
- Repository with a fake `Aead` and in-memory okio file system: commands are atomic, normalize runs before writes, undo restores the snapshot.
- ViewModel with `transformTest`: Opening → loaded, undo expiry, celebration queue order, midnight tick changes values.
- UI tests for the clickable flows in the design: create, check in, reset + undo, swipe delete + undo, edit goal, RTL layout snapshot.
- Widgets: action callback updates the repository; manual check on a device for sizes, dark theme and midnight refresh.

## 8. Left to the builder

- Exact library versions (pick one compatible set; Calf's requirements above).
- Module granularity beyond 6.1; where widgets live.
- Crypto library on iOS; Tink vs raw Keystore on Android.
- Reorder implementation (library or custom).
- Serialization format (JSON vs CBOR) inside the encrypted envelope.
- Lottie files (source and license), as long as they match the brief.
- AlarmManager vs WorkManager for the midnight widget refresh.

## 9. Calls made during the builder check (not owner decisions)

- Editing rules in 4.1a: nothing retroactive, counts kept, label changes visible before saving.
- Save failure rolls the screen back to disk and retries the same command (instead of keeping unsaved changes on screen, as an earlier design note said). Simpler, and it can't overwrite a widget's newer write.
- Goal validation only when the goal field changed.
- Celebration copy built from name + count + type unit, no per-streak custom units.
- Widgets show a passive fallback when the file is unreadable.
- Where `docs/design/brief.md` and this spec differ (Check-in − when today isn't done, ended-note wording), this spec wins.
