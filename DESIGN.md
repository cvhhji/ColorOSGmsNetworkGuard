---
version: alpha
name: GMS Guard
description: Native Android module controls following TriKey's sage and warm-gray settings surfaces.
colors:
  primary: "#51625C"
  primary-container: "#D4E7DF"
  on-primary-container: "#0F1E1A"
  background: "#EFEDEC"
  surface: "#FAF9F7"
  outline: "#C7C6C5"
  text: "#1B1C1B"
  secondary: "#464746"
  tertiary: "#686A68"
  warning-container: "#F5E7D2"
  on-warning-container: "#4E361A"
  error: "#BA1A1A"
  error-container: "#FFDAD6"
  on-error-container: "#410002"
typography:
  sans:
    fontFamily: "Android system default with OEM CJK fallback"
rounded:
  card: "20px"
  status: "24px"
  control: "14px"
spacing:
  page-inset: "20px"
  card-inset: "20px"
components:
  settings-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
  active-card:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    rounded: "{rounded.status}"
  restart-card:
    backgroundColor: "{colors.warning-container}"
    textColor: "{colors.on-warning-container}"
    rounded: "{rounded.status}"
  inactive-card:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    rounded: "{rounded.status}"
---

# GMS 守护界面约定

## Overview

参考用户指定的 TriKey 原生 Android 设置界面，不引入 WebView、营销页、渐变或仪表盘。面向国行 ColorOS 模块使用者，主任务是查看加载状态和逐项控制既有功能。界面为简体中文，使用系统字体和机型自身 CJK 回退；不包含日本市场或账号、付费、远端业务数据。

视觉签名是灰绿色状态卡片与安静的暖灰分组卡片。保留原模块身份和图标，不展示参考项目身份。设置页一屏流程、自然滚动，不需要单独的多页面 UX-CONTRACT。

## Colors

Model B：Android 资源 `app/src/main/res/values/colors.xml` 是运行时颜色源，`values-night/colors.xml` 提供夜间同名语义映射；本文件记录接受的浅色值。`MainActivity` 只消费资源，不另设一套颜色常量。正文使用 colors.text；需重启卡片使用 colors.warning-container 与 colors.on-warning-container。状态同时使用文字和符号，不仅靠颜色。

## Typography

应用标题 28sp、状态 22sp、开关标题 16sp、说明 13sp、分组标题 14sp。系统字体随用户设置变化，不加载字体文件。所有长标题和说明允许换行，不固定文字区高度。技术包名留在文档而非开关标题中。

## Layout

一个原生 ScrollView，系统栏和挖孔 insets 加到滚动容器，页面内距 20dp。标题、至少 96dp 的状态卡、重新检查按钮、联网保护/反诈控制/状态隐藏三组依次排列。卡片 20dp 内距；开关至少 80dp 高，点击整条原生 Switch 可操作。支持窄屏、横屏、深色和系统大字体，不加底部悬浮栏。

## Elevation & Depth

用页面与卡片明度区分层级，不用阴影。错误反馈位于设置下方的固定最小行数区域，不插入到正在操作的卡片前方。

## Shapes

普通卡片 20dp、状态卡 24dp、辅助按钮 14dp；数值由 dimens.xml 映射到 MainActivity。Frontmatter 的 px 是设计单位，原生使用同数值 dp。分隔线只区分同一卡片里的相邻开关。

## Components

### Canonical UI Map

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
| --- | --- | --- | --- | --- |
| Form | MainActivity.addFeature + 原生 Switch | Feature + RemoteSettings | 每项即时保存；同项保存期间禁用；其他项顺序执行；失败回滚 | SettingsTest + MainActivityTest |
| Scrollbar | 原生 ScrollView 和 Android 主题 | MainActivity.buildPage | 平台滚动条可见，系统栏不遮挡控件 | MainActivityTest 窄屏、大字体与夜间渲染 |
| Status feedback | MainActivity.showMessage | RemoteSettings 写入确认 | 屏幕下方文字、polite live region；未知结果不称已保存 | MainActivityTest 写入/回滚故障测试 |
| Activation | ActivationStatus + libxposed running targets | system 目标加载版本 | 比较实际版本；仅连接服务不算激活 | SettingsTest 状态模型；实机另验 |

无表格选择、下拉、日期、搜索或 CRUD 导航，不创建这些控件的替代实现。

### Behavior

只保留 4 个功能开关，默认全开，与原有无配置行为一致：Google 联网保护、反诈服务屏蔽、反诈入口隐藏、状态隐藏。反诈组件、号码规则合并控制；电话和权限管理的入口、手机管家的页面启动由入口开关控制。入口开关不负责停用服务，服务支持标志本身仍可能影响系统显示。状态隐藏统一控制无障碍、开发者选项与 ADB。反诈标题使用“屏蔽”，避免误读为开启系统反诈；保留无障碍服务提供方和系统组件的原有豁免。

配置走 libxposed 私有 RemotePreferences，不另建设置 ContentProvider，不执行 root Shell，不写系统开关。依赖自身会合并公开的 XposedProvider，仅作框架 Binder 连接桥接，query/insert/update/delete 不提供配置读写。远程写入按应用级单线程有界队列串行执行，保存期间只禁用正在操作的一项。成功后才更新确认值，失败最多尝试一次原值回滚；回滚未确认时暂停编辑，允许重新检查并恢复上次确认配置。没有集中保存按钮、自动重启或后台轮询。

初次连接时禁用编辑，读取确认后开放。连接死亡、请求超时和迟到结果有明确状态与生命周期校验；重进页面刷新状态。系统缓存、已绑定/停止服务可能仍需重启统一恢复，界面不把“已保存”称为“所有服务已重载”。

框架最低 API 标为 102，与原有 setId Hook 和新增 running targets 查询一致；Android 最低版本仍为 API 30。Hook 类按设计在宿主系统进程访问非 SDK 接口，因此仅在 Hook 类抑制反射相关 lint，netpolicy 服务名及调用方包查询各自按方法抑制；这些不作为普通 Activity 访问私有接口的许可。系统适配仍需实机回归。应用本地数据不参与备份或迁移，框架侧配置不在应用备份内。

### Accessibility, motion and content

使用原生 Switch/Button 键盘和读屏语义；开关标题与说明属于同一可聚焦控件。分组标题标为 heading，纯装饰标记不重复朗读；保存中的开关有状态描述。保留平台按压/焦点反馈、滚动条和原生过渡，不增加布局动画。错误使用明确中文并提供“重新检查”，不靠弹窗或短暂 Toast 作为唯一反馈。

## Do's and Don'ts

- 保留原模块功能和默认配置，新增 UI 不扩大作用域，不重新注入 Google 进程。
- 保存、加载、未激活、需重启、状态未知分别表达，不用服务连接伪装运行成功。
- 不添加集中保存按钮，不把合并的状态隐藏拆成三项，不通过写真实系统设置实现隐藏。
- 不把本地检查或界面预览当作手机与跨进程功能验收。
