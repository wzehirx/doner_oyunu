# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Döner Dükkanı: a native Android doner-shop simulation that must be fully usable by blind players (TalkBack). Java 17, minSdk 24, target/compile 34, no AndroidX and no external libraries (tests use JUnit and Robolectric only). The UI is Turkish. Detailed design lives in `docs/spec.md` (spec), `docs/research.md` (products, economy, customers), `docs/engine_api.md` (engine API).

## Commands

```bash
export ANDROID_HOME=/opt/android-sdk
/opt/gradle/bin/gradle testDebugUnitTest                      # all unit tests (engine + Robolectric UI)
/opt/gradle/bin/gradle testDebugUnitTest --tests '*EngineTest' # single test class
/opt/gradle/bin/gradle assembleDebug
scripts/release.sh v0.3     # builds debug + release, copies to releases/doner-<tag>-{debug,release}.apk, runs apksigner verify
python3 tools/gen_audio.py  # synth fallback audio generator (numpy)
```

- Release APKs are signed with `keystore/doner-release.jks` (alias `doner`, password `donerdukkani`, committed on purpose). Debug uses the standard debug key. Both APKs of each version are committed under `releases/`.
- Release lint is disabled in `app/build.gradle` because `lintVital` fails offline with Maven 429s. Bump `versionCode`/`versionName` there for each release.
- No emulator or device is available in the container (no KVM), so TalkBack feel, haptics and audio balance are unverified on real hardware.

## Architecture

Single Activity (`MainActivity`) that swaps programmatic View screens (no XML layouts). Three packages under `com.doner.oyunu`:

- `engine/`: pure Java, **no `android.*` imports**, deterministic (seeded), advanced by `GameEngine.tick(dt)` and reporting through `GameListener`/`GameListenerAdapter`. It holds catalogs (products, ingredients, stations, upgrades, customer types), `DayConfig`, orders/customers, stock, money, stars and patience. `SaveData` serializes to plain `key=value` text (stored in SharedPreferences by the UI). `Narrator` produces the Turkish announcement text and emits it through `Output.say(text, urgent)`, so accessibility wording is testable without Android.
- `audio/`: `SoundManager` (SoundPool for SFX with stereo pan by order slot, MediaPlayer loop for music; looks up `res/raw` by name and silently skips missing files), `Speaker` (uses `announceForAccessibility` when TalkBack is on, its own tr-TR TextToSpeech when off, to avoid double speech), `Haptics`.
- `ui/`: `*Screen` classes (Menu, Briefing, Shop, Game, Summary, Settings, Help) over a `Screen` base, with `Theme`/`Ui` helpers and Canvas-drawn `Icons`/`IconView`/`StarsView`/`BarView`. `GameScreen` is the largest and wires engine events to sound, speech and views.

Game flow: Menu, Briefing, Shop (market), Day, Summary, then Shop again. The menu is cumulative: day N includes all products unlocked earlier. A failed day (stars below 1.0) restores money and stock and lets the player retry.

## Rules to keep

- Accessibility is mandatory: Turkish `contentDescription` on every interactive view, targets of at least 56dp, single-tap interactions only (no drag, pinch or long press), live-region status bar, a "read status" action on screens, pause on background, focus moved to the title on screen change. Dynamic events must go through the Speaker/Narrator path.
- Keep the engine free of Android classes and deterministic so `TestBot`/`Campaign` simulations (days 1–40) keep working. A do-nothing bot must fail within 5 days, and a competent bot must pass all days.
- Audio files are `app/src/main/res/raw/{sfx_*,music_*}.ogg`. Most come from ElevenLabs (originals in `assets_src/elevenlabs/`); the rest are synth output from `tools/gen_audio.py`. Convert with ffmpeg and loudnorm, keeping peaks at or below -3 dB (Vorbis overshoots).
- `docs/ses_onizleme/tum_sesler.zip` and the README line linking it are temporary and must be deleted when the work is finished.
- Every version is committed and pushed to the designated branch, and the stop hook requires a clean, pushed tree.
