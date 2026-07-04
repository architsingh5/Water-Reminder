# Linux Onboarding

This workspace is set up to work in an online Linux dev environment.

## What this project needs

- JDK 17
- Gradle wrapper from the repository
- Android SDK command-line tools
- Android platform tools
- Android build tools for the target API level

## Recommended setup on Linux

Set these environment variables in the Linux workspace:

- `JAVA_HOME` to the JDK 17 install
- `ANDROID_HOME` to the Android SDK root
- `ANDROID_SDK_ROOT` to the same SDK root

Typical SDK location on Linux:

- `~/Android/Sdk`

Typical build commands:

```bash
./gradlew test
./gradlew assembleDebug
```

To install the APK on a connected device:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## App behavior to keep in mind

- `Drank` should schedule the next reminder from the confirmation time.
- `Snooze` should delay only the current reminder.
- Dismissing a reminder should behave like `Skip` so the schedule advances.
- Full-screen alarm behavior may still depend on Android alarm and notification permissions on the phone.

## Workspace goal

Keep the repository self-contained so the Linux workspace can build the app without relying on Windows-local Android tooling.

## Agent runbooks

- For private GitHub repository auth issues in Codespaces, see `./codespaces-private-repo-auth.md`.