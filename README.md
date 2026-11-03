# Orbit Launcher

Orbit is an Android launcher under construction. Its planned interaction model is a set of concentric, physics-driven app orbits around a central search hub. This repository currently contains **step 1 only**: the build and module foundation, a single-activity Compose entry point, and the default-home onboarding flow. It does **not** yet list or launch apps. Do not rely on this early build as your only launcher; you can change the default home app in Android settings.

## Build

Prerequisites: JDK 17, Android SDK Platform 35, and Android SDK Build Tools 35.0.0 (install these using Android Studio's SDK Manager). Open the project in a current Android Studio release, or configure `ANDROID_HOME` / `local.properties` and run:

```sh
./gradlew :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 8.0+ device or emulator. The app's first screen explains the current limitations before offering the default-home role. On Android 10+ this uses `RoleManager`; on older devices it opens system home settings. Skipping the prompt does not change the default. Pressing Home sends a new HOME intent to the existing activity and resets the onboarding view; later steps will extend this to reset navigation and transient UI state.

## Modules

- `:app` — application, Hilt root, activity, launcher intent filters and system role integration.
- `:core:domain` — platform-independent domain models and use cases (added in subsequent steps).
- `:core:data` — Android data implementations, Room, DataStore and Hilt dependencies.
- `:core:ui` — shared Compose theme and UI primitives.
- `:feature:home`, `:feature:drawer`, `:feature:search`, `:feature:settings` — feature boundaries. The home feature currently supplies the setup/foundation screen; the others are empty modules until their respective steps.

The version catalog centralizes dependency and plugin versions; feature modules depend on domain/UI, while the app composes features and data. Room and DataStore are wired as dependencies, not yet implemented. The manifest declares no network or runtime permissions. Android system backup is disabled until the explicit, user-controlled backup/restore implementation is added. The release build enables R8 and resource shrinking; signing is intentionally left to the developer's local configuration.

The visual identity in the initial screen is a static Canvas motif, not the interactive launcher. Motion, shaders, app discovery, gestures, widgets, search, persistence, permissions, accessibility refinements, tests, and performance targets will be implemented and measured in later steps. No cold-start or frame-rate target is asserted at this stage.
