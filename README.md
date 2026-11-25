# Home Launcher

A minimal custom Android home screen built with Kotlin and Jetpack Compose.

## Features

- Registers for the `HOME` intent so it can be chosen as the device's default launcher. Runs on top of One UI or any other skin; status bar, quick settings and gestures stay the system's.
- **Three concentric scroll wheels** (6 / 10 / 14 slots) filling the screen width. Each ring is a named **group** (default Daily, Social, Tools) with its own **selection window** on the ring. Drag or fling a ring; it snaps so one app sits in the window. Three chips under the dial show each group's selected app and launch it.
- Tap an app to launch it. Long-press any slot (or tap an empty one) to pick which app lives there. Assignments persist with DataStore; wheels auto-fill from installed apps on first run.
- **3D depth** toggle: tilted perspective, tube-shaded rings, bevelled rims and shadows. Off gives a flat look.
- **Use it inside One UI**: add the **Wheel** widget to any Samsung home page. It shows the dial as an image; tap an app to rotate it into its window, tap the window to open it, tap the centre for the full spinning wheel as a pop-up over One UI. Selection is shared between widget, pop-up and the launcher.
- **Pages and widgets**: swipe left for widget pages, like One UI. Add pages, add any home-screen widget (bind and configure flows included), resize Small / Medium / Large, remove.
- **Haptic tick** on every detent and an optional synthesised **click sound**.
- **Swipe-up app drawer** with search; long-press for Add to wheel, App info, Uninstall.
- **Themes**: Metallic, Neon, Glass, Dynamic (Material You). **Wallpaper blur** via window blur-behind on Android 12+, no permissions needed.
- Reloads automatically when apps are installed, removed or updated. Back collapses the drawer, then returns to the dial, and is otherwise swallowed.

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
  data/LauncherWidgetHost.kt   AppWidgetHost wrapper and provider listing
  model/                       AppInfo, Ring, SlotId, WheelTheme, LauncherSettings
  ui/LauncherViewModel.kt      UI state, wheel assignment, settings
  ui/WheelHomeScreen.kt        Clock, dial, swipe-up drawer, sheets
  ui/AppDrawer.kt              Searchable grid + long-press menu
  ui/pages/WidgetPageScreen.kt Widget page: hosted AppWidgetHostViews, resize, remove
  ui/widgets/WidgetBinder.kt   Contract the activity implements for bind/configure
  ui/WheelOverlayActivity.kt   Translucent pop-up wheel opened from the widget
  widget/WheelRenderer.kt      android.graphics renderer for the widget image
  widget/WheelWidgetProvider.kt AppWidgetProvider: tap grid, select/launch/open actions
  ui/wheel/Dial.kt             Canvas rings, angular drag/fling gestures, slot icons
  ui/wheel/RingState.kt        Per-ring rotation, fling-to-detent, focus
  ui/wheel/WheelPalette.kt     Theme palettes
  ui/sheets/                   App picker, widget picker and settings bottom sheets
  ui/theme/Theme.kt            Material 3 theme with dynamic colour
```
