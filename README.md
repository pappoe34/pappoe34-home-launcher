# Home Launcher

A minimal custom Android home screen built with Kotlin and Jetpack Compose.

## Features

- Registers for the `HOME` intent so it can be chosen as the device's default launcher. Runs on top of One UI or any other skin; status bar, quick settings and gestures stay the system's.
- **Three concentric scroll wheels** (6 / 10 / 14 slots). Drag a ring to spin it, fling it, and it snaps to the next app with a soft overshoot. Icons stay upright and swell as they pass 12 o'clock.
- Tap an app to launch it. Long-press any slot (or tap an empty one) to pick which app lives there. Assignments persist with DataStore; wheels auto-fill from installed apps on first run.
- **Haptic tick** on every detent and an optional synthesised **click sound**.
- **Swipe-up app drawer** with search; long-press for Add to wheel, App info, Uninstall.
- **Themes**: Metallic, Neon, Glass, Dynamic (Material You). **Wallpaper blur** via window blur-behind on Android 12+, no permissions needed.
- Clock and date header, name of the app at 12 o'clock under the dial, settings in the hub.
- Reloads automatically when apps are installed, removed or updated. Back collapses the drawer and is otherwise swallowed.

## Requirements

- Android 8.0 (API 26) or newer.
- JDK 17 and the Android SDK (compileSdk 35) to build locally.

## Build locally

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then press Home and pick **Home Launcher**, or tap **Set as default** inside the app.

## CI builds

`.github/workflows/android.yml` runs on every push, pull request and tag:

1. Lint and `assembleDebug`.
2. `assembleRelease` (signed when the secrets below are set, otherwise unsigned).
3. Uploads `home-launcher-debug` and `home-launcher-release` as workflow artifacts.
4. On a `v*` tag it also publishes a GitHub Release with both APKs attached.

### Optional release signing

Add these repository secrets to get a signed release APK:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |

## Project layout

```
app/src/main/kotlin/com/pappoe34/launcher/
  MainActivity.kt              HOME activity, applies wallpaper blur
  LauncherApplication.kt       App-scoped repository, preferences, tick player
  data/AppRepository.kt        PackageManager queries, launch, uninstall, package-change flow
  data/LauncherPreferences.kt  DataStore: slot assignments, theme, toggles
  data/TickPlayer.kt           Synthesised click sound via SoundPool
  model/                       AppInfo, Ring, SlotId, WheelTheme, LauncherSettings
  ui/LauncherViewModel.kt      UI state, wheel assignment, settings
  ui/WheelHomeScreen.kt        Clock, dial, swipe-up drawer, sheets
  ui/AppDrawer.kt              Searchable grid + long-press menu
  ui/wheel/Dial.kt             Canvas rings, angular drag/fling gestures, slot icons
  ui/wheel/RingState.kt        Per-ring rotation, fling-to-detent, focus
  ui/wheel/WheelPalette.kt     Theme palettes
  ui/sheets/                   App picker and settings bottom sheets
  ui/theme/Theme.kt            Material 3 theme with dynamic colour
```
