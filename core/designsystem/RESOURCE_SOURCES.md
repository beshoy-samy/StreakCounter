# Resource sources

## Fonts (`font/`)
Readex Pro, SIL OFL 1.1 (see `font/OFL.txt`, copied from the same repo).
Source: https://github.com/google/fonts/tree/main/ofl/readexpro (`ReadexPro[HEXP,wght].ttf`, the only
font file google/fonts ships; no static TTFs exist there). Static instances generated with
`fonttools varLib.instancer RP.ttf wght=<400|500|600|700> HEXP=0`. Covers Latin + Arabic.

## Icons (`drawable/ic_*.xml`)
Material Symbols Rounded, weight 400, fill 0, 24dp, Apache License 2.0.
Source: https://github.com/google/material-design-icons/tree/master/symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml
Only changes: removed `android:tint="?attr/colorControlNormal"` and replaced `@android:color/white` with `#FFFFFFFF` (Compose resources support neither theme attrs nor Android color refs; icons are tinted at the call site).
