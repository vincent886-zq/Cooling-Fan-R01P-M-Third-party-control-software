# Installation, execution and source builds

[简体中文](USAGE.md) · **English**

[Home](../README.en.md) · [Protocol](PROTOCOL.en.md)

## 1. Android: download the APK first

Download `CoolerControl-v1.0.0-debug.apk` from the [release page](https://github.com/vincent886-zq/Cooling-Fan-R01P-M-Third-party-control-software/releases/latest). The same page provides source and `SHA256SUMS.txt`; you do not need to download the whole repository to your phone.

This is the **debug-signed APK** built from my existing Android project, matching the application source at the v1.0.0 tag. Later bilingual documentation and diagram updates do not change the APK or move that published tag.

### Basic operation

1. Power the cooler and enable Bluetooth on your phone.
2. Install CoolerControl and grant the Bluetooth / scanning permissions requested by Android. Older systems may also require location permission and the location service to be enabled.
3. Tap Scan and select your own cooler. A similar device name alone does not establish compatibility.
4. Wait for initialization. Try Silent, Smart and Extreme power modes and the five lighting modes, checking the device's actual response.
5. While connected, inspect temperature, power and lighting status. The notification toggle enables status display and shortcut controls.
6. Disconnect when finished. The app does not replace the device's own power control or protection mechanisms.

### Installation and connection notes

| Situation | What to check |
| --- | --- |
| An existing installation cannot be updated | Check applicationId, version compatibility and signing certificate. A debug certificate may differ from the old package; do not uninstall without preserving data you need |
| Scanning finds nothing | Check power, Bluetooth and permissions; on older Android versions, check location settings. Make sure another client is not occupying the connection |
| A device name appears but control fails | Confirm the required service, characteristics and compatible firmware. Check the device and its reports, not just a Sent log |
| No notification appears | Check notification permission, foreground-service support and background restrictions; ROM behavior varies |
| The temperature differs from the phone temperature | This is the cooler's reported sensor value, not the phone CPU or battery temperature |

The application configuration uses Android 8.0 / API 26 as its minimum and SDK 35 for compilation and targeting. These are build settings, not a tested compatibility matrix.

## 2. Android: inspect and build the source

```bash
git clone https://github.com/vincent886-zq/Cooling-Fan-R01P-M-Third-party-control-software.git
cd Cooling-Fan-R01P-M-Third-party-control-software
```

Current configuration: JVM target 17, Android SDK 35, Android Gradle Plugin 8.7.0, Kotlin 2.0.21 and Gradle 8.9.

You do not need `push_fix.bat` to install or build the app. It is an old development helper containing a force-push command, not a user setup step.

### Android Studio

1. Open the repository root in an Android Studio version compatible with the specified AGP.
2. Configure a compatible JDK, install Android SDK 35 and allow dependencies to synchronize.
3. Set your own SDK path in `local.properties`; do not copy someone else's absolute path.
4. Build the app's debug APK from the Build menu.

### Command line with Gradle 8.9 already installed

```bash
gradle assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

**The repository tracks `gradle-wrapper.properties`, but not a complete set of `gradlew`, `gradlew.bat` and the wrapper JAR.** A fresh clone therefore cannot unconditionally run `./gradlew`. With Gradle 8.9 installed, generate a local wrapper first if needed:

```bash
gradle wrapper --gradle-version 8.9
```

Then use `gradlew.bat assembleDebug` on Windows or `./gradlew assembleDebug` on macOS / Linux. These commands generate local build helper files. This documentation update does not modify the repository's build scripts.

### GitHub Actions

The existing [Build APK workflow](../.github/workflows/build.yml) sets up Java, generates a wrapper, builds the debug APK and uploads an artifact. Successful runs provide a `CoolerControl-APK` artifact in Actions.

Workflow artifacts can expire and are not Release attachments. Prefer the release's fixed downloads for this version. A historical workflow configuration is not evidence that a new CI run has succeeded.

## 3. Python: desktop GUI and CLI

Entry points:

- GUI: [`xiaomi_cooler_gui.py`](../xiaomi_cooler_gui.py).
- Controller / CLI: [`cooler_ctrl.py`](../cooler_ctrl.py).

Use an isolated virtual environment. Windows example:

```powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install bleak PyQt5
.\.venv\Scripts\python.exe xiaomi_cooler_gui.py
```

CLI:

```powershell
.\.venv\Scripts\python.exe cooler_ctrl.py scan
.\.venv\Scripts\python.exe cooler_ctrl.py
```

These dependencies follow the source imports; a fully pinned environment is not provided. Windows was the main Python development environment. Successful dependency installation alone does not establish Bluetooth compatibility on every operating system.

The Qt main thread handles the interface; a background thread runs asyncio / Bleak and returns device lists and status through Qt signals. The CLI retains raw hexadecimal writes and notification listening for development diagnostics. **Everyday use does not require brute / raw-write modes. Do not batch-write experimental data to unknown devices.**

## 4. Web Bluetooth

Page: [`cooler_web.html`](../cooler_web.html).

This is a separate browser implementation using `navigator.bluetooth`, not an embedded page inside the Android app. It requires Web Bluetooth support, typically a secure context, and a user-driven device selection. Do not assume iPhone / Safari support.

For a local PC preview, you can try:

```bash
python -m http.server 8000
```

Open `http://localhost:8000/cooler_web.html` in a supporting browser. A phone accessing an ordinary HTTP address on the LAN is not the same as localhost on that phone; a trusted HTTPS service is normally needed for mobile use.

**The archived Web version has an unresolved `connected` state-gate issue during initialization writes.** Prefer the Android app I currently use. The browser source is retained to document an independent implementation and its development history.

## 5. Verify downloaded files

Windows PowerShell:

```powershell
Get-FileHash .\CoolerControl-v1.0.0-debug.apk -Algorithm SHA256
```

Linux / macOS:

```bash
shasum -a 256 CoolerControl-v1.0.0-debug.apk
```

Compare the result with `SHA256SUMS.txt` on the release page. A checksum checks file consistency; it does not replace Android signature validation or device compatibility testing.
