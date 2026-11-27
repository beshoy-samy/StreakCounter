# Third-party assets and licenses

Libraries (Compottie, Calf, Koin and so on) carry their own licenses through Gradle; only bundled
assets are listed here.

## Celebration animations (Lottie)

| File | Source | License |
| --- | --- | --- |
| `feature/streaks/presentation/src/commonMain/composeResources/files/celebrate_big.json` | Authored for this project (generated, not downloaded): 20 rectangle and circle shape layers that burst from the middle of the card and settle (150 frames at 60 fps, 2.5 s, rests on the last frame). Layers are named `c1`, `c2`, `c3`, `p` and are tinted at runtime with `confetti1..3` and `primary` through Compottie dynamic properties. | Same license as this project (no third-party content) |
| `feature/streaks/presentation/src/commonMain/composeResources/files/celebrate_small.json` | Authored for this project: 16 dots on a ring that pop out past the card edge (60 frames at 60 fps, 1 s). Same layer names and tinting. | Same license as this project (no third-party content) |

No LottieFiles (or other third-party) animation is bundled, so there is no attribution to carry.
If one is added later, record its URL, author and license here.

## Fonts

- Readex Pro, SIL Open Font License 1.1. Full text: `core/designsystem/src/commonMain/composeResources/font/OFL.txt`.
  Source and how the static weights were made: `core/designsystem/RESOURCE_SOURCES.md`.

## Icons

- Material Symbols Rounded, Apache License 2.0. Source and the two edits made: `core/designsystem/RESOURCE_SOURCES.md`.
