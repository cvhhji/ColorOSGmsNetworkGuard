---
version: alpha
name: GMS Guard
description: Native Android controls sharing TriKey and USB Smart Control settings surfaces.
colors:
  primary: "#51625C"
  primary-container: "#D4E7DF"
  on-primary-container: "#0F1E1A"
  background: "#EFEDEC"
  surface: "#FAF9F7"
  on-primary: "#FFFFFF"
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

参考用户指定的 TriKey 和 USB 智能控制原生 Android 设置界面，不引入 WebView、营销页、渐变或仪表盘。面向国行 ColorOS 模块使用者，主任务是查看加载状态和逐项控制既有功能。界面为简体中文，使用系统字体和机型自身 CJK 回退；不包含日本市场或账号、付费、远端业务数据。

灰绿状态卡、圆形状态图标和组内标题沿用两个参考项目。保留原模块身份和图标，不展示参考项目身份。设置页一屏流程、自然滚动，不需要单独的多页面 UX-CONTRACT。

2026-10-24 按用户要求重新对照源码和实机：TriKey main ba3d80f0fbecc4c273e59e51af587fa0db4ffd35；usb-smart-control main 565d39c6182fcd75dc40a920d82a48583508ade1。采用其 52dp 标记、28sp 标题、52dp 圆形状态图标和 18sp 组内标题，不复制保存按钮。用户明确删除重新检测按钮、“功能设置”副标题及所有底部介绍；随后强调精简不等于省略范围、豁免和生效条件，说明可占多行。

## Colors

Model B：Android 资源 `app/src/main/res/values/colors.xml` 是运行时颜色源，`values-night/colors.xml` 提供夜间同名语义映射；本文件记录接受的浅色值。`MainActivity` 只消费资源，不另设一套颜色常量。正文使用 colors.text；需重启卡片使用 colors.warning-container 与 colors.on-warning-container。圆形图标使用独立前景/背景语义色；开关开与关同时改变滑块位置和颜色。状态用文字与矢量图标表达，不仅靠颜色。

未选中开关沿用参考项目：浅色滑块 #777776、轨道 #E3E2E1；夜间滑块 #8D938D、轨道 #424943。它们是非文本图形，不映射为文字颜色；仍由 Android 资源的 switch_thumb/switch_track 管理。

## Typography

应用标题 28sp、状态 22sp、分组标题 18sp；开关标题 16sp 常规字重，说明和版本 13sp。系统字体随用户设置变化，不加载字体文件。开关标题和说明允许换行，不固定文字区高度。技术包名留在文档而非开关标题中。

## Layout

一个原生 ScrollView，系统栏和挖孔 insets 加到滚动容器，页面内距 20dp。标题与版本、至少 96dp 的状态卡、“联网”和“系统”两张分组卡依次排列。组标题在卡片内；卡片水平内距 20dp、顶部 18dp，卡间距 14dp。开关至少 64dp 高，点击整条原生 Switch 可操作。支持窄屏、横屏、深色和系统大字体；没有检测按钮、底部说明或悬浮栏。

## Elevation & Depth

用页面与卡片明度区分层级，不用阴影。标题下的版本区域兼作保存/错误提示，固定占两行的字体缩放空间，状态变化不挤动开关。错误保留文字及 polite live region，Toast 仅作补充提醒。

## Shapes

普通卡片 20dp、状态卡 24dp；数值由 dimens.xml 映射到 MainActivity。状态图标 52dp 圆形，内部矢量图标 26dp；检查中显示同尺寸原生进度环。Frontmatter 的 px 是设计单位，原生使用同数值 dp。组内靠间距分隔，不加分隔线。

## Components

### Canonical UI Map

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
| --- | --- | --- | --- | --- |
| Form | MainActivity.addFeature + 原生 Switch | Feature + RemoteSettings | 每项即时保存；同项保存期间禁用；其他项顺序执行；失败回滚 | SettingsTest + MainActivityTest |
| Scrollbar | 原生 ScrollView 和 Android 主题 | MainActivity.buildPage | 平台滚动条可见，系统栏不遮挡控件 | MainActivityTest 窄屏、大字体与夜间渲染 |
| Status feedback | MainActivity.showMessage | RemoteSettings 写入确认 | 标题下固定两行区域、polite live region；未知结果不称已保存 | MainActivityTest 写入/回滚故障与大字体反馈测试 |
| Activation | ActivationStatus + libxposed running targets | system 目标加载版本 | 比较实际版本；仅连接服务不算激活 | SettingsTest 状态模型；实机另验 |

无表格选择、下拉、日期、搜索或 CRUD 导航，不创建这些控件的替代实现。

### Behavior

只保留 4 个功能开关，默认全开，与原有无配置行为一致：Google 联网保护、反诈服务屏蔽、反诈入口隐藏、状态隐藏。反诈组件、号码规则合并控制；电话和权限管理的入口、手机管家的页面启动由入口开关控制。入口开关不负责停用服务，服务支持标志本身仍可能影响系统显示。状态隐藏统一控制无障碍、开发者选项与 ADB。反诈标题使用“屏蔽”，避免误读为开启系统反诈；保留无障碍服务提供方和系统组件的原有豁免。

配置走 libxposed 私有 RemotePreferences，不另建设置 ContentProvider，不执行 root Shell，不写系统开关。依赖自身会合并公开的 XposedProvider，仅作框架 Binder 连接桥接，query/insert/update/delete 不提供配置读写。远程写入按应用级单线程有界队列串行执行，保存期间只禁用正在操作的一项。成功后才更新确认值，失败最多尝试一次原值回滚；回滚未确认时暂停编辑，提示重新打开页面恢复上次确认配置。没有集中保存按钮、重新检测按钮、自动重启或后台轮询。

初次连接时禁用编辑，读取确认后开放。连接死亡、请求超时和迟到结果有明确状态与生命周期校验；重进页面刷新状态。系统缓存、已绑定/停止服务可能仍需重启统一恢复，界面不把“已保存”称为“所有服务已重载”。

状态隐藏的 Settings API 缓存在系统端处理：监听配置位变化，只递增相关设置已导出缓存的 generation，不写真实设置、不改返回元数据、不向检测应用注入、不增加轮询。验收必须在同一探针进程内完成开/关/开，不能依靠杀进程避开缓存。应用自行保存的业务状态、反诈服务已有绑定仍另行说明，不混同于 Settings API 缓存。

框架最低 API 标为 102，与原有 setId Hook 和新增 running targets 查询一致；Android 最低版本仍为 API 30。Hook 类按设计在宿主系统进程访问非 SDK 接口，因此仅在 Hook 类抑制反射相关 lint，netpolicy 服务名及调用方包查询各自按方法抑制；这些不作为普通 Activity 访问私有接口的许可。系统适配仍需实机回归。应用本地数据不参与备份或迁移，框架侧配置不在应用备份内。

### Accessibility, motion and content

使用原生 Switch 键盘和读屏语义；开关标题与说明属于同一可聚焦控件。分组标题标为 heading，纯装饰标记不重复朗读；保存中的开关有状态描述。保留平台按压/焦点反馈、滚动条和原生过渡，不增加布局动画。错误用短句说明；按用户选择通过重新打开页面恢复，不添加重试按钮，不靠短暂 Toast 作为唯一反馈。

## Do's and Don'ts

- 保留原模块功能和默认配置，新增 UI 不扩大作用域，不重新注入 Google 进程。
- 保存、加载、未激活、需重启、状态未知分别表达，不用服务连接伪装运行成功。
- 不添加集中保存按钮，不把合并的状态隐藏拆成三项，不通过写真实系统设置实现隐藏。
- 每个开关的说明说清范围和不影响的功能；不强求一行，不把详细解释重新堆到底部。
- 用户给定的说明原文优先；四项功能说明均不保留末尾句号。不因删除豁免说明而删掉既有豁免行为。
- 不把本地检查或界面预览当作手机与跨进程功能验收。
