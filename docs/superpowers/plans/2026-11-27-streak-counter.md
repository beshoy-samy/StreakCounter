# StreakCounter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A KMP/Compose Multiplatform streak tracker (Android + iOS UI, Android Glance widgets) with encrypted local storage and Jetpack AppState state management.

**Architecture:** Encrypted typed DataStore is the single source of truth. Pure domain rules compute every shown value from stored dates and "today". A ViewModel builds one `State<StreakListState>` with AppState `transform`; an app-scoped `StateStore` holds session-only state (undo, failed command, sheet draft, shown celebrations). Glance widgets use the same repository.

**Tech Stack:** Kotlin 2.4.x, Gradle 9 + AGP 9.4+, Compose Multiplatform 1.12, `androidx.appstate` transform/statestore 1.0.0-alpha03, DataStore core-okio, kotlinx-serialization, kotlinx-datetime, Koin, Calf `calf-ui` 0.14.0, Compottie, Glance 1.1+, kotlin.test + AssertK + Turbine.

**Spec:** `docs/superpowers/specs/2026-11-27-streak-counter-design.md` (read it first). Visual source of truth: `docs/design/design.html` (open locally; every state per screen), tokens `docs/design/tokens.json`, brief `docs/design/brief.md`.

## Global Constraints

- Follow `.claude/skills/kmp-*` conventions (module layout, convention plugins, MVI naming, Koin modules, `Result`/`DataError`, fakes not mocks). Deliberate deviation: screen state is `State<…>` from `transform`, not `StateFlow` (owner requirement). Comment it at the ViewModel.
- AppState: `androidx.appstate.transform:transform`, `androidx.appstate.statestore:statestore`, `androidx.appstate.transform:transform-testing` at `1.0.0-alpha03`. `transform(initialValue, scope, dispatcher = …) { prev -> … }`; tests use `transformTest { … }`.
- Calf 0.14.0 needs Kotlin 2.4.20, CMP 1.12.0, compileSdk 37, AGP 9.4+. If the toolchain can't meet that, use Material 3 `ModalBottomSheet` instead (design unchanged).
- Values: starting value, count and goal are whole numbers `0..99_999` (`MAX_VALUE = 99_999`).
- Undo windows: reset 8 s, delete 10 s. Opening skeleton appears only after 300 ms.
- Never log streak names or counts. Never install a `ReplaceFileCorruptionHandler`. Writes happen only through `DataStore.updateData` after a successful read.
- Data file excluded from backup on Android (`dataExtractionRules` + `fullBackupContent`) and iOS (`isExcludedFromBackup`).
- Copy is exact from the design (`docs/design/design.html`) and spec; en, de, ar strings. Arabic uses locale digits.
- All colors, type, spacing, radii come from `docs/design/tokens.json` (Dusk Ember). Font Readex Pro (OFL). Icons Material Symbols Rounded as vector resources.
- Package root: `com.streakcounter` (builder may rename once, in Task 1).

## Review Focus

1. **Rapid double taps** on Check in (or two widget taps racing the app): a check-in streak must go up by exactly 1 per day; Times goes up by 2. → test in Task 5.
2. **Clock moved backwards or travel west across the date line** (stored `lastCheckIn`/`countDate`/`startDate` after "today"): no negative values, no crash; a check-in dated after today counts as done today; Days since shows 0. → test in Task 2.
3. **Whitespace-only or very long names** (`"   "`, 300 chars, emoji, Arabic): blank becomes unnamed; long names are kept whole and wrap. → test in Task 3 and Task 9.
4. **Midnight passes while the app is open or the sheet is open:** cards recompute without a restart; saving the sheet applies the edit against the new day. → test in Task 8.
5. **Undo after a widget changed the same streak** (reset in app, then + on widget, then Undo): Undo restores the pre-reset snapshot and the widget refreshes; it must not leave the list and widgets disagreeing. → test in Task 8.

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
build-logic/convention/…                         convention plugins per kmp-module-structure
androidApp/                                      Application, MainActivity, manifest, res (icon, backup rules), widget/…
iosApp/                                          Xcode project hosting ComposeApp framework
composeApp/src/{commonMain,androidMain,iosMain}  App(), Koin assembly, MainViewController, platform Today hooks
core/domain/…/core/domain/                       Result.kt, DataError.kt, Today.kt
core/data/…/core/data/                           crypto/Aead.kt (+ actuals), datastore/EncryptingSerializer.kt, datastore/DataStoreFactory.kt (+ actuals), time/SystemToday.kt
core/designsystem/…/core/designsystem/           theme/ (Color.kt, Type.kt, Theme.kt), components/ (StreakCardFrame.kt, OutlineProgress.kt, Stepper.kt), resources (font, icons)
core/presentation/…/core/presentation/           UiText.kt, NumberFormat.kt (+ actuals)
feature/streaks/domain/…/streaks/domain/         Streak.kt, StreakRules.kt, StreakEdit.kt, DraftValidation.kt, StreakCommand.kt, StreakRepository.kt
feature/streaks/data/…/streaks/data/             StreakDto.kt, StreakFile.kt, Mappers.kt, DataStoreStreakRepository.kt, di/StreaksDataModule.kt
feature/streaks/presentation/…/streaks/presentation/
    session/StreakSession.kt                     StateStore keys + accessors
    list/StreakListState.kt, StreakListAction.kt, StreakListViewModel.kt, StreakListRoot.kt, StreakListScreen.kt
    list/components/ (StreakCard.kt, SwipeToDelete.kt, ReorderList.kt, EmptyState.kt, UnreadableState.kt)
    sheet/StreakSheet.kt, celebrate/CelebrationOverlay.kt, celebrate/SmallBurst.kt
    composeResources/ (values, values-de, values-ar strings; files/celebrate_big.json, celebrate_small.json)
```

Widgets live in `androidApp/src/main/kotlin/com/streakcounter/widget/`.

---

### Task 1: Project scaffold that builds on Android and iOS

**Files:**
- Create: root Gradle files, `gradle/libs.versions.toml`, `build-logic/` (plugins `kmp.library`, `cmp.library`, `cmp.feature`, `cmp.application`, `android.application`, `android.application.compose` exactly as in `kmp-module-structure` "Convention Plugin Source Code"), all module `build.gradle.kts` from File Structure, `androidApp` (Application + MainActivity showing `App()`), `composeApp` (`App()` = empty themed `Box`), `iosApp` Xcode project (generate with the JetBrains KMP wizard template, then rename), `.gitignore` additions (`build/`, `.gradle/`, `local.properties`, `*.xcuserstate`).
- Test: `core/domain/src/commonTest/kotlin/…/SmokeTest.kt`

**Interfaces:**
- Produces: module names and the `com.streakcounter` namespace; version catalog aliases `appstate-transform`, `appstate-transform-testing`, `appstate-statestore`, `datastore-core-okio`, `calf-ui`, `compottie`, `glance-appwidget`, `glance-appwidget-testing`, `kotlinx-datetime`, `assertk`, `turbine`.

- [ ] **Step 1:** Write `SmokeTest.smokeTest()` asserting `1 + 1 == 2` and a second test that constructs `androidx.appstate.statestore.StateStore()` (proves the iOS artifact resolves and links).
- [ ] **Step 2:** Run `./gradlew :core:domain:allTests` → expected FAIL (modules don't exist yet), then create the scaffold.
- [ ] **Step 3:** Run `./gradlew :core:domain:allTests :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → all PASS. If an `androidx.appstate` iOS artifact fails to resolve or link, stop and report (the spec depends on it).
- [ ] **Step 4:** Launch on an Android emulator and an iOS simulator (see the `android-cli` / `run` skills): empty plum screen, no crash.
- [ ] **Step 5:** Commit `chore: scaffold KMP project`.

---

### Task 2: Domain model and value rules

**Files:**
- Create: `core/domain/…/Result.kt`, `DataError.kt` (skill's version, plus `Local.UNREADABLE`, `Local.WRITE_FAILED`), `Today.kt`; `feature/streaks/domain/…/Streak.kt`, `StreakRules.kt`
- Test: `feature/streaks/domain/src/commonTest/…/StreakRulesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  interface Today { val date: StateFlow<LocalDate>; fun refresh() }
  enum class StreakType { CHECK_IN, DAYS_SINCE, TIMES }
  data class CelebrationMark(val goal: Int, val day: LocalDate?)   // day != null only for daily goals
  data class EndedNote(val endedOn: LocalDate, val previousCount: Int)
  data class Streak(val id: Uuid, val name: String?, val type: StreakType, val autoReset: Boolean,
      val goal: Int?, val position: Int, val count: Int, val lastCheckIn: LocalDate?,
      val startDate: LocalDate?, val countDate: LocalDate?, val goalReachedOn: LocalDate?,
      val celebrated: CelebrationMark?, val endedNote: EndedNote?)
  const val MAX_VALUE = 99_999
  fun Streak.valueOn(today: LocalDate): Int
  fun Streak.isDoneToday(today: LocalDate): Boolean
  fun Streak.isDailyGoal(): Boolean                       // TIMES && autoReset
  fun Streak.progressOn(today: LocalDate): Float?         // null without goal; min(1f, value/goal)
  fun Streak.normalize(today: LocalDate): Streak
  fun Streak.increment(today: LocalDate): Streak
  fun Streak.decrement(today: LocalDate): Streak
  fun Streak.reset(today: LocalDate): Streak
  ```

Rules to implement (spec 4.1, 6.2). `y = today − 1 day`.
- CHECK_IN `valueOn`: `0` if `autoReset && lastCheckIn != null && lastCheckIn < y`, else `count`. `isDoneToday`: `lastCheckIn != null && lastCheckIn >= today`.
- CHECK_IN `increment`: no-op if done today; else `count = min(MAX, valueOn+1)`, `lastCheckIn = today`, `endedNote = null`.
- CHECK_IN `decrement`: if done today → `count = max(0, count−1)`, `lastCheckIn = if (count>0) y else null`; else `count = max(0, valueOn−1)` (`lastCheckIn = null` when 0). Clears `endedNote`.
- CHECK_IN `normalize`: when it has ended (rule above) and `count > 0` → `count = 0`, `lastCheckIn = null`, `endedNote = EndedNote(lastCheckIn + 1 day, count)`.
- DAYS_SINCE `valueOn = max(0, daysUntil(startDate, today))`; `increment`: `startDate − 1` (value capped at MAX); `decrement`: `startDate + 1`, never after today; `reset`: `startDate = today`.
- TIMES `valueOn`: `0` if `autoReset && countDate != today`, else `count`; `increment`: `count = min(MAX, valueOn+1)`, `countDate = today`; `decrement`: `max(0, valueOn−1)`; `reset`: `0`; `normalize`: zero and `countDate = today` when stale and `autoReset`.
- All: `reset` clears `endedNote`. `normalize` sets `goalReachedOn = today` when `goal != null && !isDailyGoal() && valueOn >= goal && goalReachedOn == null`.

- [ ] **Step 1: Write failing tests** in `StreakRulesTest` (table-driven where one rule meets many dates). Fixed dates: `today = 2026-11-27`.
  - `checkIn_value_keeps_count_when_last_checkin_yesterday` → `valueOn == 12`
  - `checkIn_autoReset_missed_day_reads_zero_and_normalize_writes_note` → value 0; normalized `endedNote == EndedNote(2026-11-25, 12)` for `lastCheckIn = 2026-11-24`
  - `checkIn_without_autoReset_never_ends` → `lastCheckIn = 2026-10-01`, value 12
  - `checkIn_increment_twice_same_day_adds_one` → 12 → 13 → 13
  - `checkIn_decrement_after_checkin_undoes_today` → `isDoneToday == false`, value 12, `lastCheckIn == 2026-11-26`
  - `checkIn_decrement_when_not_done_corrects_down_floor_zero` → 1 → 0 → 0
  - `daysSince_counts_whole_days` → start 2026-10-11 → 47; `decrement` at start == today stays today
  - `times_autoReset_reads_zero_next_day`; `times_without_reset_keeps_running_total`; `times_decrement_floor_zero`; `times_increment_caps_at_99999`
  - `progress_is_null_without_goal_and_capped_at_one` → 18/30 = 0.6f; 31/30 = 1f
  - `normalize_sets_goalReachedOn_once`
  - Review Focus 2: `future_dates_never_go_negative` → `lastCheckIn = 2026-11-30` ⇒ `isDoneToday`, value = count; `startDate = 2026-12-01` ⇒ value 0; `countDate = 2026-11-28` with autoReset ⇒ value 0, no crash.
- [ ] **Step 2:** Run `./gradlew :feature:streaks:domain:allTests` → FAIL (unresolved).
- [ ] **Step 3:** Implement the models and rules above (`kotlinx-datetime` `LocalDate`, `kotlin.uuid.Uuid`).
- [ ] **Step 4:** Run the same command → PASS on JVM/Android and iOS simulator targets.
- [ ] **Step 5:** Commit `feat(domain): streak model and value rules`.

---

### Task 3: Create, edit, celebrations, validation

**Files:**
- Create: `feature/streaks/domain/…/StreakEdit.kt`, `Celebration.kt`, `DraftValidation.kt`
- Test: `…/StreakEditTest.kt`, `…/CelebrationTest.kt`, `…/DraftValidationTest.kt`

**Interfaces:**
- Consumes: Task 2 `Streak`, rules.
- Produces:
  ```kotlin
  data class NewStreak(val name: String?, val type: StreakType, val startValue: Int, val goal: Int?, val autoReset: Boolean)
  fun NewStreak.toStreak(id: Uuid, position: Int, today: LocalDate): Streak
  data class StreakEdit(val name: String?, val count: Int, val goal: Int?, val autoReset: Boolean, val goalChanged: Boolean)
  fun Streak.applyEdit(edit: StreakEdit, today: LocalDate): Streak
  enum class CelebrationSize { BIG, SMALL }
  data class Celebration(val streakId: Uuid, val size: CelebrationSize, val goal: Int, val value: Int)
  fun Streak.pendingCelebration(today: LocalDate): Celebration?
  fun Streak.markCelebrated(today: LocalDate): Streak
  data class StreakDraft(val editingId: Uuid?, val name: String, val type: StreakType, val startValue: String,
      val goal: String, val autoReset: Boolean, val goalEdited: Boolean, val touched: Boolean)
  enum class FieldError : Error { ENTER_NUMBER, WHOLE_NUMBER, OVER_MAX, GOAL_NOT_ABOVE_COUNT, GOAL_ZERO, GOAL_NOT_NUMBER }
  data class DraftValidation(val startError: FieldError?, val goalError: FieldError?) { val isValid: Boolean }
  fun StreakDraft.validate(): DraftValidation
  fun normalizeName(raw: String): String?   // trim; blank → null
  ```

Rules: `toStreak` maps the starting value per spec 6.2 (Check-in N>0 → `lastCheckIn = y`; Days since → `startDate = today − N`; Times → `countDate = today`). `applyEdit` follows spec table 4.1a exactly. `pendingCelebration`: null if no goal or `valueOn < goal`; daily goal → SMALL when `celebrated?.day != today`; otherwise BIG when `celebrated?.goal != goal`. `markCelebrated` writes `CelebrationMark(goal, today if daily else null)`. Changing the goal clears `goalReachedOn`. Validation: start `""` → ENTER_NUMBER; non-integer or negative → WHOLE_NUMBER; `> 99_999` → OVER_MAX. Goal `""` → no error (no goal); non-integer → GOAL_NOT_NUMBER; `0` → GOAL_ZERO; for total goals and `goalEdited`, `goal <= start` → GOAL_NOT_ABOVE_COUNT; daily goals only need ≥ 1.

- [ ] **Step 1: Write failing tests:**
  - `toStreak_checkIn_5_continues_today` → value 5, not done today; `toStreak_daysSince_30_starts_30_days_ago`; `toStreak_times_starts_at_value`
  - `edit_autoReset_on_is_not_retroactive` → last check-in 3 days ago, count 40 ⇒ after edit value 40, `lastCheckIn == y`
  - `edit_count_after_checkin_keeps_today_done`
  - `edit_times_reset_on_keeps_today_total` → total 40 ⇒ value 40 today, 0 tomorrow
  - `edit_goal_unchanged_reached_goal_saves` (goal 30, count 31, `goalChanged = false`)
  - `edit_daysSince_days_so_far_moves_start`
  - `celebration_big_once_per_goal`, `celebration_small_once_per_day` (pending again next day after reaching again), `new_goal_after_reached_can_celebrate_again`
  - `validate_messages` table: `""`→ENTER_NUMBER, `"12.5"`→WHOLE_NUMBER, `"-3"`→WHOLE_NUMBER, `"100000"`→OVER_MAX, goal `"10"` with start `"12"` → GOAL_NOT_ABOVE_COUNT, goal `""` → null
  - Review Focus 3: `normalizeName` → `"   "`→null, `" Gym "`→`"Gym"`, 300-char, `"قراءة ٢٠ صفحة"` and `"💧 Water"` kept intact.
- [ ] **Step 2:** Run `./gradlew :feature:streaks:domain:allTests` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(domain): create, edit, celebration and validation rules`.

---

### Task 4: Encryption envelope and platform keys

**Files:**
- Create: `core/data/src/commonMain/…/crypto/Aead.kt`, `datastore/EncryptingSerializer.kt`; `androidMain/…/crypto/KeystoreAead.kt`; `iosMain/…/crypto/KeychainAead.kt`
- Test: `core/data/src/commonTest/…/EncryptingSerializerTest.kt`, `androidDeviceTest/…/KeystoreAeadTest.kt` (instrumented), `iosTest/…/KeychainAeadTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  interface Aead {
      fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray   // nonce(12) + ciphertext + tag
      fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray  // throws on auth failure
  }
  class EncryptingSerializer<T>(private val delegate: OkioSerializer<T>, private val aead: Aead,
      private val associatedData: ByteArray) : OkioSerializer<T>
  const val ENVELOPE_VERSION: Byte = 1
  ```
- Envelope: `[ENVELOPE_VERSION][aead output]`. Empty source → `delegate.defaultValue`. Unknown version, short input or decrypt failure → `throw CorruptionException(...)` (message without content).
- Android `KeystoreAead(alias = "streaks_key_v1")`: AES-256-GCM key in `AndroidKeyStore`, `setUserAuthenticationRequired(false)`, generated on first use. iOS `KeychainAead(service = "com.streakcounter.key", account = "streaks_v1")`: 32 random bytes via `SecRandomCopyBytes`, stored with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`; AES-GCM via a KMP crypto library or Swift bridge (builder's call, spec 8).

- [ ] **Step 1: Write failing tests** with `FakeAead` (in `core/data/src/commonTest`, XOR with key + 16-byte checksum tag so tampering is detected):
  - `roundTrip_returns_same_value`
  - `empty_file_returns_default`
  - `tampered_byte_throws_CorruptionException`
  - `wrong_key_throws_CorruptionException`
  - `unknown_envelope_version_throws`
  - `ciphertext_does_not_contain_plaintext` (encode `"No smoking"`, assert bytes don't contain its UTF-8)
  - Platform: `KeystoreAeadTest.roundTrip_and_fresh_nonce` (two encryptions of same plaintext differ), `KeychainAeadTest.roundTrip_and_key_persists_across_instances`.
- [ ] **Step 2:** Run `./gradlew :core:data:allTests` → FAIL.
- [ ] **Step 3:** Implement serializer and both actuals.
- [ ] **Step 4:** Run `./gradlew :core:data:allTests` and `./gradlew :core:data:connectedAndroidDeviceTest` (emulator) → PASS.
- [ ] **Step 5:** Commit `feat(data): AES-GCM encrypted DataStore envelope`.

---

### Task 5: Streak repository on encrypted DataStore

**Files:**
- Create: `feature/streaks/domain/…/StreakCommand.kt`, `StreakRepository.kt`; `feature/streaks/data/…/StreakDto.kt`, `StreakFile.kt`, `Mappers.kt`, `DataStoreStreakRepository.kt`, `di/StreaksDataModule.kt`; `core/data/…/datastore/DataStoreFactory.kt` + `androidMain`/`iosMain` path actuals; `androidApp/src/main/res/xml/data_extraction_rules.xml`, `backup_rules.xml`
- Test: `feature/streaks/data/src/commonTest/…/DataStoreStreakRepositoryTest.kt`

**Interfaces:**
- Consumes: Tasks 2–4.
- Produces:
  ```kotlin
  sealed interface StreakCommand {
      data class Create(val new: NewStreak) : StreakCommand
      data class Update(val id: Uuid, val edit: StreakEdit) : StreakCommand
      data class Increment(val id: Uuid) : StreakCommand
      data class Decrement(val id: Uuid) : StreakCommand
      data class Reset(val id: Uuid) : StreakCommand
      data class Delete(val id: Uuid) : StreakCommand
      data class Restore(val snapshot: Streak) : StreakCommand      // reinserts at snapshot.position
      data class Reorder(val ids: List<Uuid>) : StreakCommand
      data class MarkCelebrated(val id: Uuid) : StreakCommand
      data object NormalizeAll : StreakCommand
  }
  interface StreakRepository {
      val streaks: Flow<Result<List<Streak>, DataError.Local>>      // sorted by position
      suspend fun execute(command: StreakCommand): Result<Streak?, DataError.Local>  // returns streak before the change (undo), else null
      suspend fun startFresh(): EmptyResult<DataError.Local>          // deletes the file
  }
  interface WidgetUpdater { suspend fun updateAll() }                 // Android impl in Task 13; iOS/no-op impl `NoOpWidgetUpdater` here
  @Serializable data class StreakFile(val schemaVersion: Int = 1, val streaks: List<StreakDto> = emptyList())
  fun createStreakDataStore(aead: Aead, scope: CoroutineScope, path: Path, fileSystem: FileSystem = FileSystem.SYSTEM): DataStore<StreakFile>
  const val STREAK_FILE_NAME = "streaks.enc"
  ```
- Every command runs inside one `updateData { file -> … }`: first `normalize(today)` on all streaks, then the command. `IOException` → `WRITE_FAILED`; `CorruptionException` → `UNREADABLE`; unknown id → `Success(null)` (no-op). `streaks` maps a read `CorruptionException` to `Error(UNREADABLE)` and never emits an empty list for an unreadable file. Android path: `context.filesDir/datastore/streaks.enc`; iOS: Application Support + `isExcludedFromBackup = true`. Backup XML excludes `file/datastore/streaks.enc`.

- [ ] **Step 1: Write failing tests** with okio `FakeFileSystem`, `FakeAead`, `FakeToday(2026-11-27)`:
  - `create_then_read_returns_streak_at_bottom`
  - `increment_persists_across_new_datastore_instance`
  - `delete_returns_snapshot_and_restore_puts_it_back_at_same_position`
  - `reorder_rewrites_positions`
  - `command_normalizes_before_applying` (ended check-in gets `endedNote` on any command)
  - `unreadable_file_emits_UNREADABLE_and_commands_do_not_overwrite` → write garbage bytes, `execute(Increment)` returns `Error(UNREADABLE)`, file bytes unchanged
  - `write_failure_returns_WRITE_FAILED` (FakeFileSystem that throws on write)
  - `startFresh_deletes_file_then_reads_empty`
  - Review Focus 1: `concurrent_checkins_add_one` → launch 2 × `Increment` on a CHECK_IN in parallel ⇒ value +1; same for TIMES ⇒ +2.
- [ ] **Step 2:** Run `./gradlew :feature:streaks:data:allTests` → FAIL.
- [ ] **Step 3:** Implement DTOs (dates ISO strings, `Uuid` string), mappers, repository, factory, Koin `streaksDataModule` (`single<StreakRepository>`, platform `single<Aead>`, `single<DataStore<StreakFile>>`), backup rules wired in the manifest.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(data): encrypted streak repository`.

---

### Task 6: Today source

**Files:**
- Create: `core/data/…/time/SystemToday.kt`; `composeApp/src/androidMain/…/TimeChangeObserver.kt` (receiver for `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, `ACTION_DATE_CHANGED` while started); `composeApp/src/iosMain/…/TimeChangeObserver.kt` (`NSCalendarDayChangedNotification`, `NSSystemTimeZoneDidChangeNotification`)
- Test: `core/data/src/commonTest/…/SystemTodayTest.kt`

**Interfaces:**
- Produces: `class SystemToday(clock: Clock, timeZone: () -> TimeZone, scope: CoroutineScope) : Today` — emits current local date, schedules the next emission at next local midnight (`delay` until then), `refresh()` re-reads clock and zone and reschedules. `App()` calls `today.refresh()` on `ON_START` (multiplatform `LocalLifecycleOwner`).

- [ ] **Step 1: Write failing tests** with `TestClock` + `runTest` scheduler: `emits_next_day_after_midnight`, `refresh_after_zone_change_moves_day` (Berlin 23:30 → Tokyo next day), `no_duplicate_emission_within_day`.
- [ ] **Step 2:** Run `./gradlew :core:data:allTests` → FAIL.
- [ ] **Step 3:** Implement; provide in Koin as `single<Today>`.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(data): today source with midnight ticks`.

---

### Task 7: Design system from tokens

**Files:**
- Create: `core/designsystem/…/theme/Color.kt` (light + dark roles from `tokens.json`, including `confetti1..3`, `wallpaper`, `onWallpaper`), `Type.kt` (8 styles, Readex Pro, display 56/56 −1.5 tracking, tabular figures), `Theme.kt` (`StreakTheme(content)`, `LocalStreakColors`), `Spacing.kt`/`Shape.kt` (radii sm 12, md 18, lg 28, xl 32), `components/OutlineProgress.kt`, `components/Stepper.kt`, `components/StreakCardFrame.kt`; resources `font/readex_pro_*.ttf`, `drawable/ic_*.xml` (Material Symbols Rounded: add, remove, restart_alt, check, swap_vert, drag_indicator, delete, close, lock, info, error, history, check_circle)
- Test: `core/designsystem/src/commonTest/…/OutlineProgressTest.kt`, `…/StreakCardFrameUiTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun StreakTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)
  fun outlineSegment(progress: Float, layoutDirection: LayoutDirection): OutlineSegment   // start fraction 0 = top-start corner end of radius, clockwise (counter-clockwise in RTL)
  @Composable fun StreakCardFrame(progress: Float?, onClick: () -> Unit, onClickLabel: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)
  @Composable fun PlusButton(kind: PlusKind, label: String, onClick: () -> Unit)   // PlusKind { CHECK_IN, DONE, ADD, QUIET }
  @Composable fun MinusButton(enabled: Boolean, contentDescription: String, onClick: () -> Unit)
  ```
- Card: radius xl, `surfaceContainer` fill, padding 20, outline track `outlineVariant` 3 dp + progress `primary` 3 dp, round cap; full ring when progress = 1; no outline when null. Plus: 56 dp tall pill (48 dp icon-only variants), minus 48 dp outline circle.

- [ ] **Step 1: Write failing tests:** `OutlineProgressTest` (`0.6f` LTR ends on the right/bottom per path measure; RTL mirrored; `1f` is closed; `>1f` clamps). `StreakCardFrameUiTest` with `runComposeUiTest`: whole card clickable with label "Edit Gym"; plus/minus have ≥ 48 dp touch size.
- [ ] **Step 2:** Run `./gradlew :core:designsystem:allTests` → FAIL.
- [ ] **Step 3:** Implement; colors copied from `tokens.json` (resolve `{ramp.step}` refs by hand or with a small Gradle task; builder's call).
- [ ] **Step 4:** Run → PASS. Visually compare a preview of the card against `docs/design/design.html#ds` (light + dark).
- [ ] **Step 5:** Commit `feat(design): Dusk Ember theme and streak card frame`.

---

### Task 8: Session state and list ViewModel (AppState)

**Files:**
- Create: `feature/streaks/presentation/…/session/StreakSession.kt`, `list/StreakListState.kt`, `list/StreakListAction.kt`, `list/StreakListViewModel.kt`, `di/StreaksPresentationModule.kt`
- Test: `…/list/StreakListViewModelTest.kt`, `…/session/StreakSessionTest.kt`

**Interfaces:**
- Consumes: Tasks 2, 3, 5, 6.
- Produces:
  ```kotlin
  class StreakSession(private val stateStore: StateStore) {          // private @Serializable keys: UndoKey, FailedCommandKey, SheetDraftKey, ShownCelebrationsKey, ReorderKey
      val undo: State<UndoEntry?>; val failedCommand: State<StreakCommand?>; val draft: State<StreakDraft?>
      val shownCelebrations: State<Set<String>>; val reorderMode: State<Boolean>
      fun offerUndo(entry: UndoEntry); fun clearUndo(); fun setFailed(command: StreakCommand?); fun setDraft(draft: StreakDraft?)
      fun markShown(key: String); fun setReorderMode(on: Boolean)
  }
  data class UndoEntry(val snapshot: Streak, val kind: UndoKind, val expiresAt: Instant)   // UndoKind { RESET, DELETE }
  sealed interface StreakListState {
      data object Opening : StreakListState
      data object Unreadable : StreakListState
      data class Ready(val cards: List<StreakCardUi>, val checkInsLeft: Int, val hasCheckIns: Boolean,
          val undo: UndoUi?, val saveFailed: Boolean, val celebration: CelebrationUi?, val sheet: SheetUi?,
          val reorderMode: Boolean, val dailyGoalHit: DailyGoalUi?, val savingStuck: Boolean) : StreakListState
  }
  data class UndoUi(val kind: UndoKind, val name: String?, val type: StreakType)
  data class CelebrationUi(val streakId: Uuid, val size: CelebrationSize, val value: Int, val name: String?, val type: StreakType)
  data class DailyGoalUi(val streakId: Uuid, val name: String?, val value: Int)
  data class SheetUi(val draft: StreakDraft, val validation: DraftValidation?, val isEdit: Boolean, val lockedType: StreakType?,
      val goalReachedOn: LocalDate?, val showDiscard: Boolean)
  data class StreakCardUi(val id: Uuid, val name: String?, val type: StreakType, val value: Int, val doneToday: Boolean,
      val goal: Int?, val progress: Float?, val goalReached: Boolean, val minusEnabled: Boolean, val endedNote: EndedNote?,
      val startDate: LocalDate?, val autoReset: Boolean)
  sealed interface StreakListAction { OnIncrement(id), OnDecrement(id), OnReset(id), OnDelete(id), OnUndo, OnRetry,
      OnCardClick(id), OnNewClick, OnDraftChange(draft), OnSave, OnSheetDismiss, OnKeepEditing, OnDiscard,
      OnCelebrationDismiss, OnSetNewGoal, OnReorderStart, OnMove(from: Int, to: Int), OnReorderDone,
      OnStartFreshConfirm, OnRetryOpen }
  class StreakListViewModel(repository: StreakRepository, today: Today, session: StreakSession, clock: Clock, widgetUpdater: WidgetUpdater,
      dispatcher: CoroutineDispatcher = Dispatchers.Default) : ViewModel {
      val state: State<StreakListState>   // AppState transform(initialValue = Opening, scope = viewModelScope, dispatcher) — deliberate deviation from StateFlow (owner requirement)
      fun onAction(action: StreakListAction)
  }
  ```
- Behavior: commands go through `repository.execute`; `Reset`/`Delete` success → `offerUndo` with 8 s / 10 s expiry, cleared by a coroutine `delay`; `OnUndo` → `execute(Restore(snapshot))`. Failure `WRITE_FAILED` → `setFailed(command)`; `OnRetry` replays it; 3 consecutive failures → `savingStuck = true` (T4 banner) until a write succeeds. Every successful command → `widgetUpdater.updateAll()`. `OnStartFreshConfirm` → `repository.startFresh()`; `OnRetryOpen` re-subscribes to `streaks`. Celebration = first pending across cards not in `shownCelebrations` (BIG before SMALL); dismiss → `markShown` + `execute(MarkCelebrated)`. `OnSave` validates (Task 3), creates or applies edit using `today` at save time. `OnSheetDismiss` with `draft.touched` → sheet shows discard confirm; untouched → close. `OnSetNewGoal` opens edit sheet for that streak.

- [ ] **Step 1: Write failing tests** using `transformTest { }`, `FakeStreakRepository` (with `shouldReturnError`), `FakeToday`, `TestClock`, ViewModel `dispatcher = testDispatcher`:
  - `starts_opening_then_ready`
  - `unreadable_repository_gives_Unreadable`
  - `reset_offers_undo_that_expires_after_8s` (`advanceTimeBy(8.seconds)`) and `delete_undo_10s`
  - `undo_restores_snapshot`
  - `write_failure_sets_saveFailed_and_retry_replays_same_command`; `three_failures_set_savingStuck`; `success_calls_widgetUpdater`
  - `celebration_queue_big_before_small_and_shown_once`
  - `save_invalid_draft_keeps_sheet_with_errors`; `dismiss_touched_draft_asks_discard`
  - Review Focus 4: `midnight_tick_recomputes_cards_and_save_uses_new_day` (FakeToday advances while sheet open; saved check-in edit uses the new date)
  - Review Focus 5: `undo_after_widget_change_restores_snapshot` (reset via VM, then `repository.execute(Increment)` directly as a widget would, then `OnUndo` ⇒ value equals pre-reset value and the list state matches the repository)
  - `StreakSessionTest.undo_and_draft_round_trip`
- [ ] **Step 2:** Run `./gradlew :feature:streaks:presentation:allTests` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(presentation): streak list ViewModel on AppState`.

---

### Task 9: List screen

**Files:**
- Create: `list/StreakListRoot.kt`, `list/StreakListScreen.kt`, `list/components/StreakCard.kt`, `SwipeToDelete.kt`, `ReorderList.kt`, `EmptyState.kt`, `UnreadableState.kt`, `OpeningSkeleton.kt`; `composeApp/…/App.kt` (theme + root)
- Test: `…/list/StreakListScreenUiTest.kt` (`runComposeUiTest`, common)

**Interfaces:**
- Consumes: Task 7 components, Task 8 state/actions.
- Produces: `@Composable fun StreakListRoot(viewModel: StreakListViewModel = koinViewModel())`, `@Composable fun StreakListScreen(state: StreakListState, onAction: (StreakListAction) -> Unit)`.

Must match `docs/design/design.html#streaks/list/*` states: header (date, "Your streaks", summary), cards with subtitle rules (spec 4.2: "12 of 30, 18 to go", "goal 30 reached", type-specific defaults), ended note, FAB "New streak", T3 snackbars above the FAB ("Gym reset to 0 · Undo", "Deleted ‘Gym’ · Undo", "Couldn’t save that change. · Try again"), empty state, skeleton after 300 ms, T5 "Couldn’t open your streaks" with Try again / Start fresh and the T6 "Start fresh?" dialog, swipe toward start edge past 40 %, T4 banner when `savingStuck` ("Changes aren’t being saved. Free up some storage."), reorder mode (compact rows, top bar "Drag to reorder" + Done), summary copy "One check-in left today. You’ve got this." / "All checked in today. Nice and steady.". Unnamed shows type name muted. Card accessibility: click label "Edit {name}", custom actions Check in / Undo / Reset / Delete / Move up / Move down.

- [ ] **Step 1: Write failing UI tests:** `card_click_sends_OnCardClick`; `plus_sends_OnIncrement`; `reset_shows_undo_snackbar_text`; `empty_state_shows_New_streak_button`; `unreadable_shows_start_fresh_confirm_before_action`; `skeleton_hidden_before_300ms`; `long_name_is_not_truncated` (300-char name fully in semantics, Review Focus 3); `rtl_layout_puts_steppers_on_left`.
- [ ] **Step 2:** Run `./gradlew :feature:streaks:presentation:allTests` → FAIL.
- [ ] **Step 3:** Implement (reorder: library or custom, spec 8).
- [ ] **Step 4:** Run → PASS; run the app on Android and iOS and compare with the design's Loaded, Empty, Reorder and Arabic states.
- [ ] **Step 5:** Commit `feat(ui): streak list screen`.

---

### Task 10: Create / edit sheet

**Files:**
- Create: `sheet/StreakSheet.kt`, `sheet/DiscardDialog.kt`
- Test: `…/sheet/StreakSheetUiTest.kt`

**Interfaces:**
- Consumes: `SheetUi` from Task 8.
- Produces: `@Composable fun StreakSheet(sheet: SheetUi, onAction: (StreakListAction) -> Unit)` using Calf `AdaptiveBottomSheet` (fallback M3).

Fields and labels per spec 4.4 and design `streaks/sheet/*`: per-type starting label ("Days already done" / "Started how many days ago" + helper "0 means today" / "Start at"; edit: "Current count" / "Days so far"), goal label "Goal (optional)" or "Daily goal (optional)" switching live with the Times reset switch, Days since info callout "Counts on its own", locked type row "Type: Check-in. Can’t be changed after creating.", reached hint "Goal reached on 24 Nov. Type a bigger number for a new one.", T1 messages: ENTER_NUMBER "Enter a number", WHOLE_NUMBER "Use a whole number", OVER_MAX "Use 99,999 or less", GOAL_NOT_ABOVE_COUNT "Make it more than {count}", GOAL_ZERO "Use 1 or more", GOAL_NOT_NUMBER "Enter a number, or leave it empty". Numeric keyboard. Name not auto-focused. Discard dialog "Discard this streak?" [Keep editing] [Discard].

- [ ] **Step 1: Write failing UI tests:** `days_since_hides_switch_and_shows_callout`; `times_reset_switch_relabels_goal_live`; `errors_render_under_fields_and_focus_first_invalid`; `edit_shows_locked_type_and_delete`; `close_with_changes_shows_discard`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS; check on device against design states New · Check-in, Field errors, Edit · goal reached.
- [ ] **Step 5:** Commit `feat(ui): create and edit sheet`.

---

### Task 11: Celebrations

**Files:**
- Create: `celebrate/CelebrationOverlay.kt`, `celebrate/SmallBurst.kt`, `composeResources/files/celebrate_big.json`, `celebrate_small.json`, `docs/licenses.md` (Lottie sources and licenses, Readex Pro OFL)
- Test: `…/celebrate/CelebrationOverlayUiTest.kt`

**Interfaces:**
- Consumes: `CelebrationUi` and `DailyGoalUi` from Task 8.
- Produces: `@Composable fun CelebrationOverlay(ui: CelebrationUi, reducedMotion: Boolean, onKeepGoing: () -> Unit, onSetNewGoal: () -> Unit)`, `@Composable fun SmallBurst(visible: Boolean, reducedMotion: Boolean)`.

Big: dim scrim, card with Compottie animation (~2.5 s, once, rests on last frame), big number, heading "{name} · {value} days" (unnamed: "{value} days"; Times: "{value} times"), body "The count keeps going from here.", buttons *Keep going* / *Set a new goal*. Small: ~1 s burst around the card + snackbar "{name}: {value} today. That’s your goal.". Tint animation layers with `confetti1..3`/`primary` via Compottie dynamic properties. Reduced motion → last frame, no playback.

- [ ] **Step 1: Write failing UI tests:** `big_shows_heading_and_two_actions`; `unnamed_heading_has_no_separator`; `reduced_motion_does_not_play`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement; source two Lottie files (LottieFiles free license or custom) and record them in `docs/licenses.md`.
- [ ] **Step 4:** Run → PASS; on device reach a goal from the app and from a background Days since crossing (change the device date) and confirm it plays once.
- [ ] **Step 5:** Commit `feat(ui): goal celebrations`.

---

### Task 12: Localization and number/date formatting

**Files:**
- Create: `core/presentation/…/NumberFormat.kt` (`expect fun formatCount(value: Int): String`, `expect fun formatShortDate(date: LocalDate): String`) + actuals; `composeResources/values-de/strings.xml`, `values-ar/strings.xml` (+ plurals); move every user-facing string from Tasks 9–11 into resources.
- Test: `core/presentation/src/androidHostTest/…/NumberFormatTest.kt`, `iosTest/…/NumberFormatTest.kt`

- [ ] **Step 1: Write failing tests:** `de_uses_dot_grouping` (1234 → "1.234"), `ar_uses_arabic_indic_digits` (12 → "١٢"), `en_short_date` ("25 Nov"); string resource completeness check (`allLocalesHaveSameKeys`).
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement and translate using the German and Arabic copy in `docs/design/design.html#streaks/list/german` and `…/arabic`; Android per-app language via `locales_config.xml`.
- [ ] **Step 4:** Run → PASS; switch device language to de and ar and check the list and sheet.
- [ ] **Step 5:** Commit `feat(i18n): German and Arabic`.

---

### Task 13: Android widgets

**Files:**
- Create in `androidApp/src/main/kotlin/com/streakcounter/widget/`: `SmallStreakWidget.kt` (+ receiver), `StreakListWidget.kt` (+ receiver), `StreakWidgetAction.kt` (`ActionCallback`), `WidgetSetupActivity.kt`, `MidnightRefresh.kt` (alarm scheduler + receiver + boot/time receivers), `WidgetUpdater.kt`; `res/xml/small_streak_widget_info.xml` (min 2×1, resize to 2×2, `configure` = setup activity), `res/xml/streak_list_widget_info.xml` (min 4×2); manifest entries.
- Test: `androidApp/src/test/…/SmallStreakWidgetTest.kt` (`runGlanceAppWidgetUnitTest`), `StreakWidgetActionTest.kt`

**Interfaces:**
- Consumes: `StreakRepository`, `Today`, Task 7 colors (Glance `ColorProviders` built from the same tokens), Task 12 formatting.
- Produces: `GlanceWidgetUpdater : WidgetUpdater` (Task 5 interface) calling `updateAll()` on both widgets, bound in the Android Koin platform module (iOS keeps `NoOpWidgetUpdater`). `StreakWidgetAction` parameters: `streakIdKey = ActionParameters.Key<String>("streakId")`, `opKey = ActionParameters.Key<String>("op")` (`"inc"`/`"dec"`). Small widget stores `streak_id` in its Glance preferences state.

Behavior per spec 4.5 and design `widgets/*`: Days since shows no buttons; reached → closed outline + check badge; deleted streak → "Streak removed. Tap to pick another." opening setup; unreadable → "Open StreakCounter", buttons inert; list widget empty → "No streaks yet" + "Open StreakCounter". Midnight: inexact alarm at next local midnight runs `execute(NormalizeAll)` then `updateAll()`; re-armed on boot, `TIME_SET`, `TIMEZONE_CHANGED`.

- [ ] **Step 1: Write failing tests:** `daysSince_small_widget_has_no_buttons`; `deleted_streak_shows_pick_another`; `unreadable_shows_open_app_and_no_actions`; `action_inc_increments_and_updates_widgets` (fake repository + fake updater); `action_on_unreadable_does_not_write`.
- [ ] **Step 2:** Run `./gradlew :androidApp:testDebugUnitTest` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS. On an emulator: place both widgets, tap + / −, check the app matches; kill the app and tap + again (value must continue from the real count); set the clock past midnight and confirm widgets refresh; toggle dark mode.
- [ ] **Step 5:** Commit `feat(widgets): small and list Glance widgets`.

---

### Task 14: App icon, iOS shell and release checks

**Files:**
- Create: `androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (+ `ic_launcher_round.xml`), `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `values/ic_launcher_background.xml` (plum = inverse-surface light value); `iosApp/…/Assets.xcassets/AppIcon.appiconset` (1024 px export); `composeApp/src/iosMain/…/MainViewController.kt` (Koin start, file backup exclusion check).
- Test: `androidApp/src/androidTest/…/BackupExclusionTest.kt` (parse merged manifest: `dataExtractionRules` set and excludes `streaks.enc`)

Icon per `docs/design/design.html#shell`: rounded-rect outline almost closed (78 %) with a check, ember on plum, artwork inside the 66 dp safe zone; monochrome layer = foreground only.

- [ ] **Step 1:** Write `BackupExclusionTest.datastore_file_excluded_from_backup` → FAIL if rules missing.
- [ ] **Step 2:** Run `./gradlew :androidApp:connectedDebugAndroidTest` → FAIL.
- [ ] **Step 3:** Implement icons, iOS entry, rules.
- [ ] **Step 4:** Run → PASS. Full manual pass on Android and iOS: create one streak of each type, check in, reach a goal, reset + undo, delete + undo, reorder, edit goal, Arabic and German, dark mode; `adb logcat | grep -i "smoking"` while using a streak named "No smoking" shows nothing.
- [ ] **Step 5:** Commit `feat: launcher icon, iOS shell, backup exclusion`.
