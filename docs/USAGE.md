# 安装、运行与源码构建

[返回主页](../README.md) · [协议说明](PROTOCOL.md)

## 1. Android：先下载 APK

从 [正式发布页](https://github.com/vincent886-zq/Cooling-Fan-R01P-M-Third-party-control-software/releases/latest) 获取 `CoolerControl-v1.0.0-debug.apk`。同页附源码包和 `SHA256SUMS.txt`，不必把整个仓库下载到手机。

这是当前原有源码构建的 **debug 签名安装包**。本次整理没有修改应用逻辑、协议、应用版本或界面；发布的是已有功能更完整的本机源码快照。

### 基本操作

1. 给散热背夹正常供电；开启手机蓝牙。
2. 安装并打开 CoolerControl，根据系统提示授权蓝牙 / 扫描等权限。旧系统可能还要求定位权限与定位服务开启。
3. 点击“扫描”，在列表中选择自己的设备。不要把名字相似的其他 BLE 设备当成兼容散热器。
4. 等待初始化，分别切换静音 / 智能 / 极寒、五种灯效，并观察设备实际反应。
5. 连接状态下查看温度、功率和灯效；通知栏开关可启用状态显示及快捷操作。
6. 不再使用时点击“断开”。应用不会替代设备自身的电源与保护功能。

### 安装与连接提示

| 情况 | 建议 |
| --- | --- |
| APK 不能覆盖安装 | 检查是否为相同 applicationId、兼容版本和相同签名；debug 签名不一定与手机旧包一致，不要未经备份直接卸载 |
| 扫描不到 | 检查供电、手机蓝牙和权限；旧系统检查定位；确认没有被另一个控制端占用连接 |
| 有名称但不能控制 | 名称只是提示，必须有对应服务、特征与兼容固件；检查设备反应与状态，不只看“已发送”日志 |
| 通知栏不显示 | 检查系统通知权限、前台服务和后台限制；不同 ROM 的行为不同 |
| 温度与手机温度不同 | 页面显示散热背夹通过协议上报的温度，不等于手机 CPU / 电池温度 |

应用最低源码配置是 Android 8.0 / API 26，编译与目标 SDK 为 35；这些是构建配置，不是逐版本兼容测试结果。

## 2. Android：查看和构建源码

```bash
git clone https://github.com/vincent886-zq/Cooling-Fan-R01P-M-Third-party-control-software.git
cd Cooling-Fan-R01P-M-Third-party-control-software
```

当前构建配置：JDK / JVM 17 目标、Android SDK 35、Android Gradle Plugin 8.7.0、Kotlin 2.0.21、Gradle 8.9。

普通安装和构建不需要运行 `push_fix.bat`。它是作者历史开发辅助脚本，包含强制推送命令，不属于应用使用步骤。

### Android Studio

1. 使用支持该 AGP 版本的 Android Studio 打开仓库根目录。
2. 配置兼容 JDK、安装 Android SDK 35，并等待依赖同步。
3. `local.properties` 中的 SDK 路径按自己的电脑配置，不要照抄他人的绝对路径。
4. 使用 Build 菜单构建 `app` 的 debug APK。

### 已安装 Gradle 8.9 的命令行环境

```bash
gradle assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。

**当前仓库只跟踪 `gradle-wrapper.properties`，没有完整跟踪 `gradlew`、`gradlew.bat` 和 wrapper JAR。** 因此首次克隆后不能无条件运行 `./gradlew`。若已经安装 Gradle 8.9，可先生成本地 wrapper：

```bash
gradle wrapper --gradle-version 8.9
```

然后在 Windows 使用 `gradlew.bat assembleDebug`，在 macOS / Linux 使用 `./gradlew assembleDebug`。这些命令会生成本地构建辅助文件；本次文档整理没有改动仓库的构建脚本。

### GitHub Actions

仓库已有 [Build APK 工作流](../.github/workflows/build.yml)，流程包含 JDK 设置、wrapper 生成、debug APK 构建及 Artifact 上传。可在 Actions 中查看成功运行后下载 `CoolerControl-APK`。

工作流 Artifact 可能过期，也不等于 Release 附件。本发布版优先使用 Releases 的固定附件；历史工作流不能代替本次实际运行结果。

## 3. Python：桌面 GUI 和 CLI

源码入口：

- GUI：[`xiaomi_cooler_gui.py`](../xiaomi_cooler_gui.py)。
- 控制核心 / CLI：[`cooler_ctrl.py`](../cooler_ctrl.py)。

建议使用独立虚拟环境。Windows 示例：

```powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install bleak PyQt5
.\.venv\Scripts\python.exe xiaomi_cooler_gui.py
```

CLI：

```powershell
.\.venv\Scripts\python.exe cooler_ctrl.py scan
.\.venv\Scripts\python.exe cooler_ctrl.py
```

这些是源码导入对应的安装命令，依赖尚未提供完整锁定版本。Python 版主要开发环境为 Windows；不把安装依赖成功描述成所有系统的蓝牙兼容验证。

GUI 的 Qt 主线程负责界面，后台线程运行 asyncio / Bleak，通过 Qt signal 传回设备列表与状态。CLI 还保留原始十六进制写入、通知监听等开发诊断入口。**日常使用无需进入 brute / 原始写入模式，不要向未知设备批量试写。**

## 4. Web Bluetooth

页面：[`cooler_web.html`](../cooler_web.html)。

这是独立的浏览器实现，使用 `navigator.bluetooth`，并不是 Android App 内嵌网页。浏览器必须支持 Web Bluetooth，通常需要安全上下文和用户手动选择设备；不能假定 iPhone / Safari 可用。

PC 本机预览可尝试：

```bash
python -m http.server 8000
```

然后使用支持 Web Bluetooth 的浏览器访问 `http://localhost:8000/cooler_web.html`。手机访问局域网普通 HTTP 地址与本机 localhost 不同，不能据此认为满足安全上下文要求；手机通常需要可信 HTTPS 服务。

**当前 Web 源码保留历史实现，初始化写入存在 `connected` 状态门控时序问题。** 本次仅发布原有源码，没有修复该逻辑，不将此页面描述为已和作者 Android 自用版同等验证的替代安装方式。优先使用作者确认正常使用的 Android 版。

## 5. 校验下载文件

Windows PowerShell：

```powershell
Get-FileHash .\CoolerControl-v1.0.0-debug.apk -Algorithm SHA256
```

Linux / macOS：

```bash
shasum -a 256 CoolerControl-v1.0.0-debug.apk
```

将输出与发布页的 `SHA256SUMS.txt` 比对。校验值用于检查文件一致性，不替代 Android 签名检查，也不等于设备兼容性测试。
