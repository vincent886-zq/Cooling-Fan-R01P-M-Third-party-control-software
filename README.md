# CoolerControl ❄️

Xiaomi Cooling Fan — Third-party Android BLE Control App
小米冰封散热背夹 — 第三方 Android 蓝牙控制 App

> Can't control your Xiaomi Cooling Fan (R01P-M) because you're on a non-Xiaomi Android phone, or your Xiaomi phone hasn't updated to HyperOS yet? This app is here to help.
>
> 你是否购买了小米冰封散热背夹（R01P-M），但手机是非小米的安卓手机/小米手机还没升级澎湃系统无法对买的小米冰封散热背夹进行控制？那我可以帮到你。

## Features / 功能

- 🔍 BLE scan / BLE 扫描
- 🔗 One-tap connect / 一键连接
- 🌡️ Real-time temperature display / 温度实时显示
- 🔋 3 power modes: Silent / Smart / Extreme / 3 档功率切换
- 💡 5 light modes / 5 种灯效切换
- ⏺️ Communication log / 日志面板

## Download APK / 下载 APK

Get the latest APK from [Releases](https://github.com/vincent886-zq/Cooling-Fan-R01P-M-Third-party-control-software/releases).

## Build from Source / 从源码编译

### GitHub Actions (recommended)

1. Fork this repo
2. Go to Actions tab → latest build → download **CoolerControl-APK**
3. Install on your phone

### Local Build

Requires Android Studio Koala+ or JDK 17 + Android SDK 35.

```bash
git clone https://github.com/vincent886-zq/Cooling-Fan-R01P-M-Third-party-control-software.git
./gradlew assembleDebug
```

## Cross-platform Versions / 跨平台版本

- [cooler_ctrl.py](cooler_ctrl.py) — Python version (PyQt5 GUI + CLI)
- [cooler_web.html](cooler_web.html) — Web Bluetooth version

## License / 开源许可

MIT License

## Credits / 致谢

- [vincent886-zq](https://github.com/vincent886-zq)
