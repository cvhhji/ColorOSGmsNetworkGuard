# GMS 守护
LSPosed 模块，阻止 ColorOS/OPlus 在重启或网络状态变化后禁止 Google Play services、Play Store 和 Google Services Framework 联网。

## 原理
Hook `OplusNetworkingControlManager.setUidPolicy(uid, policy)`，将目标 UID 的非零策略改为 `POLICY_NONE (0)`；并在 `system_server` 启动、网络变化以及目标包安装/更新后清理已有策略。模块完全由事件驱动，不进行定时轮询。

## 使用
安装 Actions 生成的 APK，在 LSPosed 启用，作用域勾选“系统框架”，重启。针对 OnePlus 15 / PLK110 / Android 16 / ColorOS 16 开发；已按 ColorOS 16.1（16.0.9.400）与手机管家 17.1.6 的组件清单复核。


## 中国版 ColorOS AI 反诈停用

模块仅停用手机管家中已确认属于 `aivoicecalldetect` / `FraudDetectRuleFilePipeProvider` 的活动、广播、服务和 Provider，包括通话录音反诈检测、跨场景检测设置、风险详情与弹窗、反诈记录、误报反馈和规则管道。保留手机管家的清理、病毒扫描、权限管理，以及电话的普通来电和骚扰拦截功能。

停用在 `system_server` 启动后执行，并会在手机管家更新后重新应用，不使用轮询。卸载模块前如需恢复，可执行：

```sh
for c in $(pm dump com.coloros.phonemanager | sed -n '/disabledComponents:/,/enabledComponents:/p' | grep -E 'aivoicecalldetect|FraudDetectRuleFilePipeProvider'); do pm enable "com.coloros.phonemanager/$c"; done
```


## Compatibility and releases

- Declares LSPosed API target **102**.
- Every successful `main` build automatically increments the patch version, creates a GitHub Release, and uploads an installable APK.
- Releases are signed with the repository's reproducible debug signing configuration; upgrading requires the same signing identity.
