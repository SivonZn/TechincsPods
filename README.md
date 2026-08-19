# TechnicsPods

[English](#english) | [中文](#中文)

---

## English

Xposed module that brings system-level Technics earphone control to Xiaomi HyperOS devices.

Based on [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen.

### Features

- **ANC Control** — Switch between Off / Noise Cancellation / Adaptive / Transparency
- **Game Mode** — Low-latency audio toggle with optional auto-enable on connect
- **Battery Display** — Real-time battery level for left ear, right ear, and charging case
- **Quick Popup** — Tap the persistent notification to open a compact floating dialog with battery, ANC, and game mode controls; tap "More" to enter the full app
- **HyperOS Integration** — Focus Island battery popup on connection, optional Super Island-style persistent notification, status bar headset icon
- **Dark Mode** — Full dark theme support including popup dialog and battery icons
- **Standalone Mode** — Direct RFCOMM connection when Xposed hooks are unavailable
- **Optional Launcher Icon** — Hide the desktop icon while keeping LSPosed and notification entry points available

### Requirements

- Xiaomi device running **HyperOS** (Android 15+)
- **LSPosed** or compatible Xposed framework
- Module scope: `com.android.bluetooth`, `com.milink.service`, `com.xiaomi.bluetooth`, `com.android.settings`

### How It Works

TechnicsPods hooks into four packages:

| Process | Purpose |
|---------|---------|
| `com.android.bluetooth` | Detect Technics earphone via A2DP, establish RFCOMM via the selected UUID or channel mode, send/receive protocol packets |
| `com.milink.service` | Mirror headset ANC and battery state into HyperOS headset runtime |
| `com.xiaomi.bluetooth` | Show Focus Island battery popup, create persistent notification |
| `com.android.settings` | Sync headset settings page state and ANC commands |

### Protocol

Communication uses Bluetooth Classic **RFCOMM**. The connection method can be selected in settings: `UUID` tries the Technics/Airoha SPP UUIDs `00001107-D102-11E1-9B23-00025B00A5A5` and `0000079A-D102-11E1-9B23-00025B00A5A5`; `Channel 15` uses the fixed RFCOMM channel directly. Packet format:

```
AA [TotalLen] 00 00 [Cmd 2B LE] [Seq] [PayLen 2B LE] [Payload...]
```

| Function | Cmd | Payload |
|----------|-----|---------|
| ANC Control | `0x0404` | `01 01 <mode>` — `01`=Off, `02`=NC, `04`=Transparency, `00 08`=Adaptive |
| Game Mode Set | `0x0403` | `28 01`=On, `28 00`=Off |
| Battery Query | `0x0106` | (empty) |
| Battery Response | `0x8106` | Pairs of `[Index, RawValue]` — battery=`val & 0x7F`, charging=`(val & 0x80) != 0` |
| Active Battery Report | `0x0204` | `01 <count> [Index, StatusValue]...` — unsolicited, same value encoding as above |
| Batch Status Query | `0x010D` | Fixed blob (see below), wakes earbuds, no prerequisite |
| Batch Status Response | `0x810D` | Key-value stream; find byte `0x28`, next byte = game mode (`01`=On, `00`=Off) |

**Batch Status Query (fixed hex):**
```
AA 13 00 00 0D 01 00 0C 00 0B 05 04 0B 11 13 18 06 1B 1C 27 28
```

### Build

```bash
./gradlew assembleDebug
```

### Install

1. Install the APK
2. Enable the module in LSPosed with scope: `com.android.bluetooth`, `com.milink.service`, `com.xiaomi.bluetooth`, `com.android.settings`
3. Reboot
4. Connect your Technics earphones via Bluetooth

### Credits

- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — original project
- [libxposed](https://github.com/libxposed/api) — Xposed module API
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS-style Compose UI components

### License

GPL-3.0

---

## 中文

为小米 HyperOS 设备提供系统级 Technics 耳机控制的 Xposed 模块。

基于 Art_Chen 的 [HyperPods](https://github.com/Art-Chen/HyperPods)。

### 功能

- **降噪控制** — 在关闭 / 降噪 / 自适应 / 通透模式之间切换
- **游戏模式** — 低延迟音频开关，支持连接时自动开启
- **电量显示** — 实时显示左耳、右耳、充电盒电量
- **快捷弹窗** — 点击常驻通知，弹出浮窗显示电量、降噪、游戏模式控制；点击「更多」进入完整页面
- **HyperOS 集成** — 连接时焦点岛电量弹窗、可选超级岛样式常驻通知、状态栏耳机图标
- **深色模式** — 完整深色主题支持，包括弹窗对话框与电池图标
- **独立模式** — 在 Xposed 钩子不可用时通过 RFCOMM 直连耳机
- **可隐藏桌面图标** — 隐藏启动器图标后，仍可从 LSPosed 或连接通知进入

### 系统要求

- 小米设备，运行 **HyperOS**（Android 15+）
- **LSPosed** 或兼容的 Xposed 框架
- 模块作用域：`com.android.bluetooth`、`com.milink.service`、`com.xiaomi.bluetooth`、`com.android.settings`

### 工作原理

TechnicsPods 挂钩四个包：

| 进程 | 用途 |
|------|------|
| `com.android.bluetooth` | 通过 A2DP 检测 Technics 耳机，按设置选择 UUID 或通道模式建立 RFCOMM，收发协议包 |
| `com.milink.service` | 将耳机电量和降噪状态同步到 HyperOS 耳机运行时 |
| `com.xiaomi.bluetooth` | 焦点岛电量弹窗、创建常驻通知 |
| `com.android.settings` | 同步系统耳机设置页状态和降噪命令 |

### 协议

通信使用经典蓝牙 **RFCOMM**。连接方式可在设置中选择：`UUID` 会尝试 Technics/Airoha SPP UUID `00000000-0000-0000-0099-AABBCCDDEEFF` 和标准 SPP UUID `00001101-0000-1000-8000-00805F9B34FB`；`通道 15` 会直接使用固定 RFCOMM 通道。数据包使用 Airoha RACE 格式：

```
05 [类型] [长度 2字节小端] [Race ID 2字节小端] [载荷...]
```

| 功能 | 命令 | 载荷 |
|------|------|------|
| 左/右耳电量查询 | `0x0CD6` | `00`=agent, `01`=client |
| 充电仓电量查询 | `0x0040` | （空） |
| 电量响应 | `0x0CD6` / `0x0040` | `status=00` 成功；左右耳从响应偏移 `7/8` 读取角色和电量，充电仓从偏移 `7` 读取电量 |

当前版本只接入 Technics 电量读取与 HyperOS 灵动岛/通知显示。降噪、游戏模式等控制项暂不向耳机发送旧项目的 OPPO 协议包。

### 构建

```bash
./gradlew assembleDebug
```

### 安装

1. 安装 APK
2. 在 LSPosed 中启用模块，作用域选择：`com.android.bluetooth`、`com.milink.service`、`com.xiaomi.bluetooth`、`com.android.settings`
3. 重启设备
4. 通过蓝牙连接你的 Technics 耳机

### 致谢

- [HyperPods](https://github.com/Art-Chen/HyperPods) by Art_Chen — 原始项目
- [libxposed](https://github.com/libxposed/api) — Xposed 模块 API
- [Miuix](https://github.com/YuKongA/miuix) — HyperOS 风格 Compose UI 组件

### 许可证

GPL-3.0
