# Design brief: StreakCounter

Status: iterating (full design published)
Artifacts: options (round 1 page deleted from claude.ai; local copy docs/design/options/round-1/options.html) · design https://claude.ai/artifact/X6Kg7d3db5zy5qbibDr1MB

## Product
- What it is: a personal streak tracker. You track as many streaks as you like, each one either a check-in day streak (gym), a days-since counter (no smoking) or a daily/total count (glasses of water).
- Users and context of use: one user (the owner), personal use. Quick glances and one-thumb taps, from the app and from Android home-screen widgets. Sessions last seconds.
- Platforms / viewports: Android + iOS phones via Compose Multiplatform. Android home-screen widgets (Glance). iOS widgets out of scope.
- Themes: light + dark, default follows system (user, 2026-11-27)
- Accessibility target: WCAG 2.2 AA
- Implementation target (for platform conventions): Compose Multiplatform (Material 3 base), Calf adaptive components where useful, Glance for widgets.

## Mood and brand
- Feels like: calm, encouraging, quietly proud (user picked "Calm & encouraging")
- Must not feel like: gamified/loud, guilt-tripping when a streak breaks (designer call, follows from mood)
- Fixed brand assets: none. Designer picks palette, typography and launcher icon.
- References and what's liked about each: none; user said "surprise me"
- Density: airy (designer call, follows from mood)

## Content
- Language / locale: English, Arabic (RTL), German. Language follows the phone (per-app language in system settings), no in-app picker.
- Voice and tone: informal "you", short, kind; never scolds (designer call)
- Sample data domain: No sugar, Gym, No smoking, Glasses of water, Reading pages, Meditation.

## Streak behavior (settled before design)
| Type | Number shown | + | − | Reset | Auto-reset toggle |
|---|---|---|---|---|---|
| Check-in days | days in a row | "done today", max once per day | undoes today's check-in | 0 | on: a missed day drops it to 0 |
| Days since | days since start, grows by itself | start date 1 day earlier | start date 1 day later | "I slipped": start = today | hidden, doesn't apply |
| Times | count | +1 | −1, floor 0 | 0 | on: back to 0 at local midnight |

- Initial value: Check-in = days already done (today not yet checked); Days since = days ago it started; Times = starting count. Default 0.
- Name optional.
- Reset and delete show an Undo snackbar.

## Screen inventory
| Feature | Screen | Primary action / question | Notes (data, destructive actions, offline) |
|---|---|---|---|
| Streaks | Main list | Bump a streak (+) at a glance | Long-press + drag to reorder (user, 2026-11-27). Row: name (optional, may be long), number, unit, +/−/reset. Swipe to delete with Undo. Empty state. No network, no loading beyond first disk read. |
| Streaks | Create / edit sheet | Save a streak | Name (optional), type (3), initial value (default 0), auto-reset toggle (hidden for Days since). Edit opened by tapping the card (outside its buttons). |
| Widgets | Small widget (Android) | Bump one chosen streak | Name (if any), number, +/−. Days since: number + name only. Tap name opens app. |
| Widgets | Widget configuration (Android) | Pick which streak the small widget shows | List of streaks; empty case. |
| Widgets | List widget (Android) | Bump any streak | Scrollable rows with +/−. |
| Brand | Launcher icon | Recognisable at 48dp | Adaptive + monochrome (themed) icon. |

## Key flows to make clickable
- Create streak via sheet → appears in list
- Tap + / − / reset → number changes; reset shows Undo snackbar
- Swipe to delete → Undo snackbar

## State plan (phase 3)
**Main list**
- Goal progress on the card outline (with goal / no goal / reached)
- Goal reached just now → big Lottie celebration overlay (day streaks) or small one (Times daily goal), then outline stays closed
- Goal reached while app was closed (Days since, widget tap) → celebration plays on next open
- Loaded (mixed types) · All checked in today (small positive moment) · Empty, first use
- Opening (skeleton; only if decrypt + read takes over 300 ms)
- Streak ended while away: row shows "Ended 25 Nov, 12 days. New streak from today." (T4 inline on that row, dismissed by the next tap)
- Reset done → T3 "Gym reset to 0 · Undo" · Deleted by swipe → T3 "Deleted Gym · Undo"
- Reorder mode (long-press, drag handles)
- Couldn't save → T3 "Couldn't save that change · Try again"
- Can't open saved data (file unreadable, e.g. restored from another phone) → T5 "Couldn't open your streaks" with Try again; "Start fresh" behind T6 confirm
- Stress: 2-line names, unnamed streak (type name shown muted), 5-digit numbers (Days since 1,234), 1 streak, 12 streaks (scroll clears the FAB), Arabic RTL, German long words
**Create / edit sheet**
- Goal field (optional): "Goal" in days, or "Daily goal" for Times with midnight reset. T1 errors: not a whole number, not above the current count, over 99,999. Edit sheet shows "Goal reached on 24 Nov" hint when reached, so a new goal is set by just changing the number
- New · per-type labels (Check-in "Days already done" + "Break the streak if I miss a day"; Days since "Started how many days ago", no toggle; Times "Start at" + "Reset at midnight")
- Initial value error T1: empty, not a whole number, over 99,999
- Edit: prefilled, type locked (changing type would reinterpret the number), "Delete streak" text button → T3 Undo
- Swipe down with changes → T6 "Discard this streak?" [Keep editing] [Discard]
**Small widget (2x1 minimum, 2x2)**
- Outline progress on the widget; reached = closed outline + check mark
- Check-in not done / done · Times · Days since (number + name, no buttons) · unnamed · streak was deleted ("Tap to pick a streak")
**List widget (4x2+)**
- Loaded · many (scrolls) · empty ("Add a streak in the app")
**Widget setup**
- Pick a streak list · no streaks yet → "Create a streak first"
**Brand**
- Launcher icon: adaptive (ember on plum) + monochrome themed icon
**Shell page**
- Single screen, no nav. Top bar at rest and scrolled. RTL mirror specimen.

Note: no "locked after restart" widget state. The app isn't direct-boot aware, so before first unlock the launcher shows the last drawn widget and taps wait for unlock.

## Decisions log
- 2026-11-27 · Three streak types (Check-in days, Days since, Times) · user chose option C so gym-like and no-smoking-like streaks both work
- 2026-11-27 · Language follows the phone, no in-app picker · keeps settings out of a one-screen app

- 2026-11-27 · Light + dark, follow system · widgets sit on any wallpaper
- 2026-11-27 · Mood: calm & encouraging · user choice
- 2026-11-27 · Manual drag-to-reorder · user wants most-used on top

- 2026-11-27 · Direction C Dusk Ember · user pick; Readex Pro covers Latin + Arabic in one family

- 2026-11-27 · Optional goal per streak; reaching it plays a Lottie celebration (Compottie, shared UI) · user request
- 2026-11-27 · After goal: keep counting, show "Goal reached" + "Set next goal" · user choice
- 2026-11-27 · Times + midnight reset: goal is a daily target with a small daily celebration; big one reserved for day streaks · user choice
- 2026-11-27 · Widgets can't animate: they show a "Goal reached" badge, celebration plays on next app open · platform limit

- 2026-11-27 · Goal progress = D, the card outline is the progress bar (top-left, clockwise; mirrored in RTL); count to go lives in the subtitle · user choice, keeps rows uncramped
- 2026-11-27 · No "Goal reached" chip or "Set next goal" button on the row. Reached = closed outline + subtitle "goal 30 reached" · user disliked the extra row UI
- 2026-11-27 · Tapping anywhere on the card (outside its buttons) opens the edit sheet; goal is changed there · user request (replaces "tap name to edit")

- 2026-11-27 · Edit sheet: type locked after creation · switching would silently reinterpret the number
- 2026-11-27 · Arabic digits follow the locale (Arabic-Indic for ar) · designer call, flagged to user

## Options history
- Round 1: A Sage Journal (warm paper + sage, Fraunces/Figtree, soft cards), B Still Water (cool fog + tide blue, Plex Mono/Plex Sans, ledger rules), C Dusk Ember (plum + ember, Readex Pro, big numbers, pill buttons) → chose C Dusk Ember (user, 2026-11-27)

## Open questions

