# CoolerControl ❄️

**简体中文** · [English](README.en.md)

**散热背夹买到了，手机却没有方便可用的控制入口？**

CoolerControl 面向持有小米冰封散热背夹 R01P-M、但使用**非澎湃系统的小米 / Redmi 手机，或其他品牌 Android 手机**的用户，提供一个独立的 BLE 控制入口，不依赖澎湃系统的原生控制面板。

我通过蓝牙 HCI 抓包分析和设备按键对照，实现了这个工具，现在已在自己的 Android 手机上正常使用：切换散热档位、调整灯效、查看设备状态，也能从通知栏直接操作。

[下载 Android APK](https://github.com/vincent886-zq/Cooling-Fan-R01P-M-Third-party-control-software/releases/latest) · [下载源码](https://github.com/vincent886-zq/Cooling-Fan-R01P-M-Third-party-control-software/archive/refs/heads/main.zip) · [安装与构建](docs/USAGE.md) · [协议说明](docs/PROTOCOL.md) · [开发过程](docs/DEVELOPMENT.md)

## 解决的是“有设备，缺少合适的控制入口”

散热背夹能够供电和制冷，不代表每部 Android 手机都能方便地使用它的全部控制入口。对不在相应澎湃系统环境中的用户，功率调节、灯效设置和状态查看可能没有合适的入口，或原有适配体验不够顺畅。

这不是手机“没有蓝牙”，而是**设备控制协议与系统入口的适配问题**。我想做的是让已经买到背夹的人，在现有手机上也有一个直接、清楚的操作界面，而不是为了调档位和灯效去更换手机或依赖某个系统专属面板。

适用人群以“持有 R01P-M、需要独立控制入口的 Android 用户”为中心；具体手机和固件仍需确认兼容性，不代表所有非澎湃设备都必然无法使用原有工具。

## 先确认是不是这款背夹

![小米冰封散热背夹外观参考](docs/images/cooler-product-reference.png)

[小米官网产品页](https://www.mi.com/shop/buy/detail?product_id=19492&cfrom=search) · [正面参考图](docs/images/cooler-product-front.png)

产品宣传图仅用于型号辨识，来源参考小米官网，相关产品外观与商标权利归各自权利人；它不是软件运行截图，也不是我拍摄的硬件实物图。

这个项目从 BLE 服务枚举和通信尝试开始，随后通过正常操作的 HCI 抓包分析、物理按键核对和多轮界面调整，逐步形成了现在的 Android App。仓库也保留了 Python 桌面工具与 Web Bluetooth 的实现过程。

## Android 实际界面

<p align="center">
  <img src="docs/images/android-app-original.jpg" width="360" alt="我的 Android 手机截图：功率切换、灯效切换、日志与通知栏开关">
</p>

我的 Android 手机原始截图。截屏时处于未连接状态，画面中可以看到功率、灯效、日志和通知栏开关。

## 日常能做什么？

| 功能 | 说明 |
| --- | --- |
| 扫描与连接 | 显示设备名称、地址和 RSSI，选择自己的散热背夹连接或断开 |
| 三档功率 | 静音、智能、极寒 |
| 五种灯效 | 彩虹、呼吸、流动、常亮、关闭 |
| 设备状态 | 接收设备上报的温度、当前功率和灯效 |
| 按键状态同步 | 通过设备回报更新显示，而不只是记住上次点过哪个按钮 |
| 操作日志 | 查看连接过程与功率、灯效操作信息 |
| 通知栏快捷操作 | 显示状态，循环切换功率、切换灯效和开关灯 |

手机与背夹通过本地 BLE 通信，不需要我另外搭建云服务、账号后台或订阅系统。通知栏显示受手机的通知权限、前台服务和后台限制影响。

## 控制原理

蓝色箭头是手机发出的请求，绿色箭头是设备回传的状态；Python 与 Web 是另外两套独立实现，不是 Android App 的内部模块。

![本地 BLE 控制与状态回传，中英文原理图](docs/images/communication-overview.png)

[打开高清矢量图](docs/images/communication-overview.svg)

Android 的主要交互路径如下。设备状态通过通知回到界面，不只依靠按钮点击后的显示变化。

![Android 控制请求、设备通知与界面更新，中英文时序图](docs/images/android-command-feedback.png)

[打开高清矢量图](docs/images/android-command-feedback.svg)

## 安装包与源码

### 直接安装

在 [v1.0.0 发布页](https://github.com/vincent886-zq/Cooling-Fan-R01P-M-Third-party-control-software/releases/tag/v1.0.0) 下载：

- **`CoolerControl-v1.0.0-debug.apk`**：Android 安装包，约 9.5 MB。
- **`CoolerControl-v1.0.0-source.zip`**：与该发布标签对应的源码快照。
- **`SHA256SUMS.txt`**：文件校验值。

给背夹供电，开启手机蓝牙并授予权限，扫描并选择自己的设备，连接后即可操作。具体步骤见 [使用指南](docs/USAGE.md)。

APK 使用 **Debug 签名**，不是应用商店生产签名。若手机旧包的签名不同，可能无法直接覆盖安装；不要未经备份就卸载。源码最低配置为 Android 8.0 / API 26，目标 SDK 为 35，实际兼容性仍取决于手机和设备固件。

### 查看实现

| 入口 | 技术与源码 |
| --- | --- |
| Android App | Kotlin、Jetpack Compose、Android BluetoothGatt；[`app/src/main`](app/src/main) |
| PC 图形界面 | Python、PyQt5、asyncio、Bleak；[`xiaomi_cooler_gui.py`](xiaomi_cooler_gui.py) |
| PC 控制核心 / CLI | 扫描、连接、控制、状态与诊断；[`cooler_ctrl.py`](cooler_ctrl.py) |
| 浏览器历史实现 | Web Bluetooth、JavaScript；[`cooler_web.html`](cooler_web.html) |

Android 是我目前的日常使用入口。Python 与 Web 有独立的运行环境和实现细节；Web 历史版本的初始化门控问题见 [使用说明](docs/USAGE.md#4-web-bluetooth)。

**源码包是发布时的固定快照，main 分支则保留后续文档更新。** APK 和应用逻辑不会因为介绍或配图更新而改变。

## 我做了哪些工作？

- **找通信入口。** 枚举 GATT 服务和特征，明确 AF07 写入、AF08 接收的主要路径。
- **读懂操作过程。** 将 HCI 抓包里的认证、初始化、属性写入和主动通知分开分析。
- **核对协议映射。** 对照物理按键和设备反应，修正功率档位关系，整理五种灯效的两字节小端值及温度解析。
- **做成可操作的软件。** 整理 Python 控制核心与桌面工具，再实现独立的 Android App 和通知栏入口。
- **持续调整使用体验。** 迭代设备列表、状态卡片、连接后的界面布局、日志与快捷操作。

详细的开发过程、模块关系和纠错记录放在 [DEVELOPMENT.md](docs/DEVELOPMENT.md)；UUID、帧结构和值表放在 [PROTOCOL.md](docs/PROTOCOL.md)。

## 使用状态与兼容性

我已在自己的 Android 手机上正常使用，并实现日常所需的功率和灯效控制。目前没有覆盖所有手机、系统与固件的兼容性矩阵；如果反馈问题，请附手机系统、设备型号、操作步骤和脱敏日志。

在我当前这台设备上，官网手机端提供的蓝牙控制项已经逐项核对：功率档位和灯效都能切换，界面与官方入口不同，但可以作为这些已核对控制功能的独立替代入口。我也使用 Type-C 功率测试仪做过实机核对；功率测试用于核对功率控制，灯效则按设备实际显示检查。这里不补写尚未整理公开的具体瓦数、仪表精度或完整测试曲线。

温度来自背夹自身的状态上报，不是手机芯片温度。本项目不修改设备固件、不解除硬件功率限制。抓包中的固定初始化序列也不等于通用的小米认证算法。

## 更多

- [安装、运行与构建](docs/USAGE.md)
- [BLE 协议与值表](docs/PROTOCOL.md)
- [开发过程与源码地图](docs/DEVELOPMENT.md)
- [v1.0.0 发布说明](docs/RELEASE_NOTES_v1.0.0.md)
- [我的机械设计与机电自动化作品](https://github.com/vincent886-zq/Mechanical-Engineering-Portfolio)
- [我的科研仪器采集与控制软件](https://github.com/vincent886-zq/Research-Instrumentation-Control)

这是我维护的非官方个人项目，与小米无隶属或背书关系。许可沿用仓库原有的 MIT 声明，第三方依赖遵循各自许可。

[vincent886-zq](https://github.com/vincent886-zq)
