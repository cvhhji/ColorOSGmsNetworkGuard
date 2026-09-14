# GMS 守护

给国行 ColorOS 用的 LSPosed 模块，目前做三件事：

- 防止系统在重启或网络变化后断掉 Google Play 服务、Play 商店和 Google 服务框架的网络。
- 关闭手机管家里的 AI 通话反诈组件。
- 关闭“电话 → 拦截规则”里的国家反诈中心拦截服务。

不会强制显示系统设置里的 Google 入口，也不会改动普通来电拦截、信息拦截、黑名单和白名单。

## 使用方法

1. 从 [Releases](https://github.com/cvhhji/ColorOSGmsNetworkGuard/releases) 下载 APK 并安装。
2. 在 LSPosed 中启用模块，使用模块推荐的作用域。
3. 重启手机。

模块更新后如果新增了作用域，进 LSPosed 确认新项目已经勾选，再重启一次。

## 适配情况

目前按下面这套环境开发和测试：

- OnePlus 15 / PLK110
- Android 16
- ColorOS 16.0.9.400
- 手机管家 17.1.6

系统或手机管家更新后，类名和组件名可能变化。如果功能失效，请带上机型、系统版本和 LSPosed 日志提 Issue。

## 大致原理

GMS 联网部分会拦截 ColorOS 的网络策略调用，并在开机、网络变化或 GMS 包更新后清理已有的限制。

手机管家的 AI 反诈通过停用已经确认的 Activity、Receiver、Service 和 Provider 来处理。电话里的国家反诈中心拦截服务则只改它自己的支持判断和开关读写，不碰其他电话功能。

模块没有定时轮询。

## 恢复手机管家 AI 反诈

卸载模块前，可以在 root shell 中执行：

```sh
for c in $(pm dump com.coloros.phonemanager | sed -n '/disabledComponents:/,/enabledComponents:/p' | grep -E 'aivoicecalldetect|FraudDetectRuleFilePipeProvider'); do pm enable "com.coloros.phonemanager/$c"; done
```

然后卸载模块并重启。电话里的国家反诈中心拦截服务不需要单独恢复，模块停用后会按系统原本的状态工作。

## 构建

项目使用 JDK 17、Gradle 8.10.2 和 Android SDK 35。推送到 `main` 后，GitHub Actions 会编译 APK、创建新版本并发布到 Releases。

LSPosed API 版本为 102。
