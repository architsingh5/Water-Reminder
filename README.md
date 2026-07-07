# Water Reminder

Native Android water reminder app for configurable awake hours, snooze, skip, and confirmation-based scheduling.

## Behavior

- Configure day start, day end, reminder interval, snooze duration, and a daily glass goal.
- Enable reminders from the home screen.
- Home screen shows an animated goal progress ring, a live countdown to the next reminder, and a tappable last-7-days chart.
- Home screen widget shows today's progress and next reminder, with a one-tap log button.
- `Drank` records water intake and schedules the next reminder from the confirmation time.
- `Snooze` delays the current reminder without recording intake.
- `Skip` dismisses the current reminder and schedules the next reminder from the skip time.
- Manual water logging also recalculates the next reminder from the manual log time.
- Reminders outside awake hours are moved to the next valid day start.
- Date-wise analytics show daily drank, reminder, and skipped counts.
- `Reset today` clears only the current day's visible metrics.
- Reminder alerts open a full-screen alarm-style screen, play an alarm sound (auto-silenced after 60 seconds), and treat swipe-dismiss as skip so the next reminder advances.
- Optional respect silent mode: skip the alarm sound while the phone is on vibrate or silent (off by default; the alarm otherwise rings like a real alarm clock).
- Light and dark themes follow the system setting, including the widget.

Example: with a 60-minute interval, if a 10:00 reminder is snoozed for 15 minutes and `Drank` is tapped at 10:15, the next reminder is scheduled for 11:15.

## Stack

- Kotlin
- Jetpack Compose + Material 3
- DataStore Preferences
- AlarmManager
- Full-screen notification intent for alarm-style reminders
- Notification actions through BroadcastReceiver
- Plain Kotlin unit tests for scheduling rules

## Build Without Android Studio

You can build this with Gradle from the command line. You do not need Android Studio, but you still need:

- JDK 17
- Gradle, at least once, to generate the Gradle wrapper
- Android SDK command-line tools
- Android SDK platform/build tools for API 36
- USB debugging enabled on your phone if you want to install directly

If you are setting this project up in an online Linux workspace, see [.agents/linux-onboarding.md](.agents/linux-onboarding.md) for the Linux-first setup path.

### 1. Install JDK 17 and Gradle

On Windows:

```powershell
winget install -e --id EclipseAdoptium.Temurin.17.JDK
winget install -e --id Gradle.Gradle
```

After installing JDK 17, make sure `JAVA_HOME` points to JDK 17 before building. Android Gradle builds should not run on Java 25.

### 2. Install Android SDK Command-Line Tools

Download the Windows command-line tools from:

https://developer.android.com/studio#command-line-tools-only

Extract them so the final layout is:

```text
%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
```

Then set SDK environment variables:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $sdk, "User")
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
```

Install the required SDK packages:

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --install "platform-tools" "platforms;android-36" "build-tools;36.0.0"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

### 3. Generate the Gradle Wrapper

From this project folder:

```powershell
gradle wrapper --gradle-version 8.10.2
```

After this, use `gradlew.bat` instead of the system Gradle install:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

### 4. Install On Your Phone

Enable Developer options and USB debugging on your Samsung phone, connect it by USB, then run:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

On Android 13 and newer, allow notification permission. For reliable exact timing, allow the app's alarm permission if the Reliability card reports that exact alarms need permission. If reminders only appear as notifications, open the full-screen alert setting from the Reliability card and allow full-screen alerts for this app. On Samsung One UI, set the app battery mode to unrestricted if reminders are delayed.
