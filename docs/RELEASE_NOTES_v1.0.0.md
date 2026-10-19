# CoolerControl v1.0.0 · Android 自用版与源码同步

**简体中文** · [English](RELEASE_NOTES_v1.0.0.en.md)

这个版本同步了我正在 Android 手机上使用的工程，并提供 APK、对应源码和使用说明。功率、灯效与通知栏等功能沿用现有实现，没有为这次发布额外改写控制逻辑。

## 下载

- `CoolerControl-v1.0.0-debug.apk`：Android debug 签名安装包。
- `CoolerControl-v1.0.0-source.zip`：同发布提交的跟踪源码与文档，不包含本地 SDK、构建缓存、签名私钥和个人配置。
- `SHA256SUMS.txt`：以上两个文件的 SHA-256。
- GitHub 自动提供的 Source code 附件同样指向本发布标签。

## 当前功能

- BLE 扫描、连接与断开。
- 静音 / 智能 / 极寒三档功率。
- 彩虹 / 呼吸 / 流动 / 常亮 / 关闭五种灯效。
- 设备上报的温度、功率、灯效及操作日志。
- 通知栏状态显示与功率、灯效、灯开关快捷操作。
- 附 Python CLI / PyQt 与 Web Bluetooth 历史源码。

## 版本与适用范围

- 应用包名 `com.coolercontrol`，versionName `1.0.0`，versionCode `1`。
- 源码 minSdk 26，targetSdk / compileSdk 35；不等于全部 Android 版本兼容测试。
- APK 为 debug 签名，不是应用商店生产签名。手机已有包的签名不同时可能无法直接覆盖；不要未经备份卸载。
- 我已在自己的 Android 手机上正常使用；目前没有覆盖所有手机与固件的兼容性矩阵。
- 截图为真实原图，截屏时处于未连接状态；Python / Web 不与 Android 自用版共用验证结论。
- 当前 Web 历史实现有初始化门控问题，本次未改代码，优先使用 Android 版。

详见仓库主页及 `docs/USAGE.md`、`docs/PROTOCOL.md`、`docs/DEVELOPMENT.md`。项目非官方，设备档位 / 灯效控制不涉及固件修改或硬件功率限制解除。

中英文介绍与原理图随后在 main 分支持续完善；v1.0.0 标签、已发布源码包、APK 和校验值保持不变，便于复现当时的版本。
