# QuickWallet

**双击电源键，快速打开 Google 钱包付款界面。**

适用于 HyperOS 4 的 LSPosed 模块。

## 简介

QuickWallet 是一个适用于 HyperOS 4 的 LSPosed 模块，用于将双击电源键的操作替换为打开 Google 钱包。

HyperOS 默认只允许将双击电源键设置为启动相机或小米钱包。安装本模块后，可以直接通过双击电源键打开 Google 钱包的付款卡片页面，无需先进入应用首页。

支持亮屏、息屏和锁屏状态。

## 功能

- **快速打开付款页面**：直接启动 Google 钱包的付款卡片界面，无需手动选择。
- **支持锁屏启动**：允许付款页面显示在锁屏上方，实际支付仍遵循 Google 钱包的身份验证要求。
- **避免重复启动**：设置 800ms 触发间隔，防止多个 Hook 同时执行导致钱包重复打开。
- **轻量化设计**：无需额外权限，没有独立界面和后台服务，APK 大小约 20 KB。

## 兼容性

- HyperOS 4
- LSPosed


## 技术实现

模块主要 Hook `system_server` 中与电源键快捷操作相关的方法，并修改其原有行为。

| Hook 位置 | 功能 |
| --- | --- |
| `MiuiSingleKeyRule` / `PowerKeyRule` | 拦截 `getFunction("double_click_power_key")`，修改双击电源键对应的快捷操作 |
| `ShortCutActionsUtils.launchDefaultWallet` | 拦截系统默认钱包启动方法，改为启动 Google 钱包 |
| `ActivityRecord.canShowWhenLocked` | 允许 Google 钱包的付款页面显示在锁屏上方 |
| `PhoneWindowManager.powerPress` | 兼容 AOSP 电源键处理逻辑，在检测到双击操作时启动钱包 |

模块会优先尝试启动 Google 钱包的 `PresentationModeActivity`，以直接显示付款卡片页面。

如果当前 Google 钱包版本不存在该 Activity，则回退到应用默认启动入口。

各 Hook 独立处理，即使部分方法在当前系统版本中不存在，也不会影响其他 Hook 的执行。

## 日志

如果模块没有正常工作，可以在 LSPosed 日志中搜索 `QuickWallet`。

正常加载时，日志中会包含类似内容：

```text
QuickWallet: getFunction hooked x1
QuickWallet: launchDefaultWallet hooked x1
QuickWallet: canShowWhenLocked hooked
QuickWallet: com.android.server.policy.PhoneWindowManager powerPress hooked x1
```

常见日志说明：

| 日志内容 | 说明 |
| --- | --- |
| `hooked x0` / `not found` | 未找到对应方法，可能是系统版本差异 |
| `Google Wallet is not installed` | 设备未安装 Google 钱包 |
| `launch failed` | Google 钱包启动失败，需要查看详细异常日志 |



## 免责声明

本项目仅用于学习和技术研究，与 Google、小米及 LSPosed 项目无官方关联。

模块涉及系统框架 Hook，不同设备和系统版本可能存在兼容性问题。使用前请自行评估风险。

## 开源许可

本项目基于 [GNU General Public License v3.0 (GPL-3.0)](https://www.gnu.org/licenses/gpl-3.0.html) 开源。

## 致谢

本项目的开发由 Claude 和 OpenAI Codex 提供 AI 辅助支持。