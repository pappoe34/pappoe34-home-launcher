# Home Launcher

A minimal custom Android home screen built with Kotlin and Jetpack Compose.

## Features

- Registers for the `HOME` intent so it can be chosen as the device's default launcher.
- Clock and date header over the system wallpaper.
- Alphabetical app grid with instant search; press Go on the keyboard to open the top match.
- Long-press an app for **App info** or **Uninstall**.
- Reloads automatically when apps are installed, removed or updated.
- Prompts to set itself as default via `RoleManager` (Android 10+) or Home settings.
- Back is swallowed, as a launcher should.

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
  MainActivity.kt          HOME activity, swallows Back
  LauncherApplication.kt   App-scoped repository
  data/AppRepository.kt    PackageManager queries, launch, uninstall, package-change flow
  model/AppInfo.kt         Launchable activity model
  ui/LauncherViewModel.kt  UI state, search filter, refresh
  ui/HomeScreen.kt         Compose UI: clock, search, grid, long-press menu
  ui/theme/Theme.kt        Material 3 theme with dynamic colour
```
