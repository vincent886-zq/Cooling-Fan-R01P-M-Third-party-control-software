# From a practical problem to a third-party controller

[简体中文](DEVELOPMENT.md) · **English**

[Home](../README.en.md) · [Protocol](PROTOCOL.en.md) · [Usage](USAGE.en.md)

## 1. The problem I wanted to solve

Someone can already own the cooler yet lack a convenient control interface on a Xiaomi / Redmi phone without HyperOS, or on another Android brand. I wanted people with the same R01P-M to change power and lighting and inspect status on their existing phones, without relying on a system-specific panel. I now use the Android app on my own phone.

## 2. Why did successful writes produce no response?

I started by enumerating BLE services, writable characteristics and notification characteristics, then trying candidate data. The initial difficulty was that Bluetooth could report a successful write while the device did nothing visible.

I then analyzed HCI captures of normal control: what is sent during connection, what the device returns, and which notifications follow power or lighting changes. Comparing these exchanges clarified the everyday path: AF07 for writes and AF08 for incoming data.

## 3. From captures to usable mappings

I separated authentication, initialization, property writes and unsolicited notifications, then compared them with physical-button actions. Several assumptions were corrected during that process:

| Problem | Later interpretation |
| --- | --- |
| Treating every authentication payload as session-varying | c0 and c1 differ in the records; fixed replays can only be discussed within that observed scope |
| Using UI indices as power values | Silent and Smart require a swapped property mapping |
| Naming lighting modes from an early capture alone | Use the five-mode table checked against later physical-button actions |
| Mixing byte orders | Handle outer lengths separately; lighting values use two-byte LE |
| Treating the most recent tap as device state | Update from F4 reports; a send log alone does not confirm execution |

Full captures remain local. Public notes contain the device-related frame structures and mappings; superseded conclusions were not copied directly. Unconfirmed fields retain their actual code usage and the remaining questions.

## 4. Python: separate the control core from the interface

[`cooler_ctrl.py`](../cooler_ctrl.py) provides `XiaomiCoolerController` and the CLI: scanning, saved device addresses, connection, service discovery, authentication sequences, mode control, notification listening and raw-write diagnostics.

[`xiaomi_cooler_gui.py`](../xiaomi_cooler_gui.py) uses PyQt5 and actually imports `cooler_ctrl.py`; an older version name left in its opening comment is not the current dependency.

```text
Qt main thread: buttons, device list, status cards
        | run_coroutine_threadsafe
Background thread: asyncio event loop -> Bleak -> Windows BLE
        | Qt signals
Qt main thread: temperature, power, lighting and connection updates
```

Known device names are prioritized in scan results, alongside addresses and signal strength. Signals are blocked when a received status updates a combo box, preventing a display update from generating another control write. These details connect the interface to the actual BLE operations.

## 5. Android: a directly usable phone app

The Android app uses Kotlin, Jetpack Compose and BluetoothGatt. It is not a wrapper around the PC window or browser page.

| Module | Responsibility |
| --- | --- |
| `MainActivity` | Entry point, permissions and BLE hardware checks |
| `MainScreen` | Compose control interface |
| `CoolerViewModel` | StateFlow, device list, log and UI events |
| `BleManager` | Scanning, connection, service discovery, notifications and GATT writes |
| `CoolerProtocol` | Frame constants, mode encoding and F4 parsing |
| `CoolerService` / `CoolerGlobal` | Notification entry points, shared state and shortcuts |

![Android command and feedback sequence](images/android-command-feedback.png)

[Open SVG](images/android-command-feedback.svg)

Notification buttons enter the foreground service through Service PendingIntents and invoke the existing operations. `NotificationActionReceiver.kt` also retains a BroadcastReceiver helper, but the current manifest does not register it; the active notification buttons do not use that receiver.

I iterated on scan compatibility, the light card-based UI, hiding the device list while connected, state updates and notification controls. Those changes are recorded in Git history. The current documentation work does not rewrite BLE or application control logic.

## 6. Web: preserve another implementation path

[`cooler_web.html`](../cooler_web.html) uses Web Bluetooth, the browser's device picker and JavaScript for mode buttons, status cards and logs.

It follows the same protocol findings as Android but implements its own connection and write state. The historical code has an initialization gate issue described in [Usage](USAGE.en.md#4-web-bluetooth). Android is the everyday client; the Web source preserves the development path.

## 7. What the project produced

- A native Android app I use on my phone.
- Three power modes, five lighting modes, reported status and notification controls.
- A Python core, CLI and PyQt GUI.
- An archived Web Bluetooth implementation.
- GATT channel notes, mode tables, status parsing and corrected assumptions.
- Downloadable APK, matching source and checksums.

The most valuable part for me was turning a practical problem into a tool I could use every day through communication analysis and repeated checks.

## 8. Current use and development assistance

My Android setup works; I have not covered every phone or firmware. Captures, code, screenshots and package checks answer different questions: a screenshot shows the interface, code shows implementation, and hardware operation checks the corresponding function.

I also used a USB Type-C power meter for hardware checks, rather than relying on changes to UI text alone. Power checks relate to meter observations; lighting is checked visually on the device. Specific wattage tables and raw measurement records are not published, so no numerical wattage or error figures are added here.

Temperature accuracy, the complete meaning of the second lighting byte and cross-firmware authentication compatibility still need further investigation. The project does not involve firmware modification, hardware design or universal-key recovery.

I used WorkBuddy and other AI coding assistance during analysis, implementation and documentation. Device operations, physical-button comparisons and actual use are also part of the development records. These notes focus on how the problem was progressively resolved, not on treating a tool-generated inference as a completed experiment.
