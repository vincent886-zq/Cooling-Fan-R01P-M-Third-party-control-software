# CoolerControl v1.0.0: Android app and source snapshot

[简体中文](RELEASE_NOTES_v1.0.0.md) · **English**

This release publishes the project I use on my Android phone, with its APK, corresponding source and usage notes. Power, lighting and notification features retain their existing implementation; no control logic was rewritten for this release.

## Downloads

- `CoolerControl-v1.0.0-debug.apk`: debug-signed Android package.
- `CoolerControl-v1.0.0-source.zip`: tracked source and documentation at the release commit; no local SDK, build cache, signing private key or personal configuration files.
- `SHA256SUMS.txt`: checksums for those two files.
- GitHub's automatic Source code attachments also point to the release tag.

## Features

- BLE scanning, connection and disconnection.
- Silent, Smart and Extreme power modes.
- Rainbow, Breathing, Flowing, Steady and Off lighting modes.
- Reported temperature, power and lighting status, plus operation logs.
- Notification status and shortcuts for power, lighting and light on/off.
- Companion Python CLI / PyQt tools and historical Web Bluetooth source.

## Version and scope

- Package `com.coolercontrol`, versionName `1.0.0`, versionCode `1`.
- minSdk 26; targetSdk / compileSdk 35. These settings do not imply every Android version has been tested.
- Debug signing is not app-store production signing. An existing installation with another certificate may reject an update; do not uninstall without preserving data you need.
- I use the app successfully on my own Android phone; a complete phone / firmware compatibility matrix is not available.
- The original screenshot was taken while disconnected. Python / Web do not inherit the Android setup's hardware validation.
- The historical Web version has an unresolved initialization gate issue. No code fix is included here; Android is the preferred client.

See the main README, `docs/USAGE.en.md`, `docs/PROTOCOL.en.md` and `docs/DEVELOPMENT.en.md`. This unofficial project does not modify cooler firmware or remove hardware power limits.

Bilingual descriptions and diagrams continue to improve on main. The v1.0.0 tag, published source archive, APK and checksums remain unchanged so the original version can still be reproduced.
