# Orbit Launcher

Orbit is an Android launcher in active development, built with Kotlin, Compose, Hilt, Room and DataStore. The home surface is a pair of independently rotating rings around a search hub. Drag to spin the selected ring, pinch to switch rings, tap the hub to search, and long-press an app for actions. This is **not yet a production-ready launcher**; keep another launcher installed and know how to restore it in Android system settings before trying Orbit.

## Build and test

Use JDK 17 and Android SDK Platform / Build Tools 35. Open the project in Android Studio or set `ANDROID_HOME` / `local.properties`. For example:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :baselineprofile:assembleBenchmarkRelease :app:lintDebug :core:domain:test
```

GitHub Actions performs builds and domain tests on pushes, uploads a debug APK on success, and provides a manually triggered API 35 emulator job for Compose instrumentation tests. `:baselineprofile` contains a baseline profile generator and a cold-start Macrobenchmark. Generate profiles on a compatible connected device with `./gradlew :app:generateBaselineProfile`; benchmark results are device-specific. No cold-start or frame-rate target has been verified. Ktlint and detekt are configured as Gradle tasks, but a clean result is not yet claimed.

The app offers the Android home role on Android 10+ or opens system home settings on Android 8–9. Skipping the prompt does not change the default. HOME intents return to the home surface. Release builds use R8/resource shrinking and are unsigned unless signing is configured by the developer outside this repository.

## Capabilities

- **Apps and icons:** `LauncherApps` discovers and launches activities in accessible profiles and listens for package changes. System-badged work icons, long-press app shortcuts, pinning, hiding, custom labels, search-only placement, named app groups, and smart groups based on Android app categories are supported. Orbit uses bounded memory and private disk icon caches, four masks, Android 13+ monochrome icons where provided, basic ADW/Nova `appfilter.xml` icon packs, and per-app images selected with the system document picker. Missing or inaccessible resources fall back to system icons.
- **Search:** Apps and launcher-host shortcuts, optional contacts, direct files in an explicitly selected folder, calculator, unit conversion, and an optional external web-search intent. Contacts permission and file access are requested only after user action. Shortcut availability depends on the home role. File search is limited to the selected folder's direct files and its first 1,000 entries; it does not scan the device.
- **Widgets:** A separate widget surface supports picking current-profile providers, system bind permission, provider configuration, host lifecycle, removal and vertical resizing when supported. Backups record provider and size; restored widgets may need to be rebound and configured individually.
- **Personalization:** Light, dark, system and AMOLED modes; optional Material You colors on Android 12+; manual palettes, fonts, corner and icon sizes, labels, masks, wallpaper visibility, supported wallpaper offset parallax, optional Android 12+ window blur, spring screen transitions, an Android 13+ shader glow with an older-API fallback, and reduced motion. Actual wallpaper blur depends on device and OS support.
- **Gestures:** Swipe up/down/left/right, double tap, long press on empty orbit space and a two-finger tap can be assigned to launcher surfaces, an installed app, torch, notifications, quick settings or screen lock. Pinch selects the active ring. System panel and lock actions require the optional accessibility service; it does not retrieve window content. Torch permission is requested only when used. Device behavior can vary.
- **Local suggestions:** Opt in to rank apps using time, weekday, charging and headphone state recorded when Orbit launches an app. Optionally grant system usage access to include recent foreground events. Processing is on-device. Disabling context suggestions clears Orbit's context-event history. No location signal is collected.
- **Privacy and backup:** Notification access is optional and only counts current-profile notifications; content is not retained. Optional biometric verification gates management of hidden apps and backups. This is a launcher UI restriction, not encryption or an OS-level app lock; other launchers or system settings can still expose apps. JSON backup/restore uses the Storage Access Framework and validates input. It contains preferences, Orbit's launcher usage counts and widget provider/layout metadata, but not widget provider configuration, selected image bytes or document permissions. URI references and profile serials may not work on another device. Restore replaces Orbit app preferences; Room and DataStore writes are not atomic together. No analytics, ads, or network permission are included; external web search sends the query to the chosen search app.

## Important remaining work

This project still needs full device validation, localization and RTL coverage, large-font/foldable layout and TalkBack auditing, predictive-back polish, resilient widget restoration across all providers, fully portable icon/permission backups, more nuanced automatic categorization, location-category suggestions, more complete icon-pack compatibility, and a comprehensive gesture/animation and test/performance pass. Automated UI and migration coverage is limited; baseline profiles and performance targets must be generated and measured on representative devices. Do not rely on hidden-app protection as a security boundary.

## Modules

`:app` owns the activity, system integrations and composition root; `:core:domain` holds domain models, ranking and quick answers; `:core:data` contains platform repositories, Room, DataStore, icon caches and backup; `:core:ui` provides the shared theme. Compose screens are in `:feature:home`, `:feature:search` and `:feature:settings`. `:feature:drawer` remains an empty boundary because Orbit uses search and groups rather than a grid drawer. `:baselineprofile` contains profile generation and Macrobenchmark instrumentation.
