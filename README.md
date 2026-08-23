# ColorOS GMS Network Guard
LSPosed 模块，阻止 ColorOS/OPlus 在重启或网络状态变化后禁止 Google Play services、Play Store 和 Google Services Framework 联网。

## 原理
Hook `OplusNetworkingControlManager.setUidPolicy(uid, policy)`，将目标 UID 的非零策略改为 `POLICY_NONE (0)`；并在 `system_server` 中定期及网络变化后清理已有策略。

## 使用
安装 Actions 生成的 APK，在 LSPosed 启用，作用域勾选“系统框架”，重启。针对 OnePlus 15 / PLK110 / Android 16 / ColorOS 16 开发。
