# Orbit Launcher

Orbit is an **early-stage**, single-activity Android launcher experiment. It is not yet a production launcher or a replacement for a fully featured home app. The implemented interface uses a rotating pair of app rings around a search hub. Drag horizontally to rotate; fling to continue moving; swipe up or tap the hub to search; long-press an app for actions. A physical keyboard starts an app search from home.

## Build and verification

Prerequisites: JDK 17 and Android SDK Platform / Build Tools 35. In Android Studio, open this directory, or configure `ANDROID_HOME` / `local.properties` and run `./gradlew :app:assembleDebug :core:domain:test`. The GitHub Actions workflow performs the same build and unit tests on each push and uploads a debug APK on success. Release builds have shrinking enabled; configure your own signing credentials outside the repository.

The first-run screen offers the Android home role (Android 10+) or system home settings (Android 8–9). Skipping the prompt does not change the default. Keep another launcher installed and know how to restore it in system settings before trying this build. HOME intents return to the home screen.

## Implemented so far

- Profile-aware discovery and launching through `LauncherApps`, package callbacks, and platform-badged icons with an in-memory LRU bitmap cache. Long-press offers available app shortcuts, app info/uninstall for the current profile, rename, pin and hide. Hidden apps can be restored in settings.
- Room stores per-component/profile pinning, visibility, label and launcher-recorded launch counts; DataStore stores icon size, label visibility and reduced-motion preference. Ranking is currently pinned-first, followed by launch count and recency. These counts are Orbit's own, not Android usage statistics.
- Fuzzy app search and a scrollable app list. A notification listener, enabled explicitly in system settings, displays current-profile notification counts without storing notification contents. Work-profile notification counts are intentionally not attributed.
- A JSON export/import via the Storage Access Framework for these app preferences and settings. Restore replaces all existing Orbit app preferences. The file includes launcher-recorded usage history and profile serials; serials may differ on another device, so per-app settings may not match after migration. JSON is size-limited and validated before writes, but Room and DataStore writes are separate and are not atomic together.
- Settings include a live icon preview, label and icon-size controls, reduced motion, notification access and hidden-app management. No network permission, analytics or ads are included.

## Not yet implemented

This is a partial implementation of the requested launcher. It does not yet have widgets, folders, biometric locking, search-only apps, disk icon caching, icon packs, themed icons or configurable masks. Search covers apps only; contacts, files, calculator, conversions and web are not implemented. Contextual suggestions, system usage-stat permission, wallpaper/parallax, dynamic/manual theming, AGSL effects, full gesture configuration, two-finger gestures and the complete requested animation system are also absent. Accessibility, localization/RTL, large-screen layouts, predictive back, automated UI coverage, macrobenchmarks, baseline profiles, lint/ktlint/detekt, and performance targets still require dedicated work and device testing. The current reduced-motion setting disables fling, not every animation. Do not assume production readiness or any specific performance threshold.

## Modules

`:app` owns the activity, Hilt root, notification service and integration; `:core:domain` contains models and search ranking; `:core:data` contains LauncherApps, Room, DataStore, and backup; `:core:ui` holds the base theme. `:feature:home`, `:feature:search`, and `:feature:settings` hold Compose UI. `:feature:drawer` remains an empty boundary because app access is currently provided by search rather than a grid drawer.
