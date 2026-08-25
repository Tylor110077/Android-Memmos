# Memmos 安卓配套软件 · 设计简报与决策记录

> 阶段：Phase 1 —— 仅 UI（无联网、无抓取逻辑，全部用模拟数据撑起完整样式）
> 设计方法：ui-designer skill 流程（3 个独立方向 → 画廊 → 用户挑选 → 精修 → 落地 Compose）
> 日期：2026-08-24

## 1. 产品定位（一句话）

贴在 Android 屏幕边缘的「捕捉滑块」：在小红书刷到值得留存的帖子时，一指触发，
把帖子（原文 / 图片 / 视频 / 评论）整理进 Obsidian Vault，并顺手记下自己的感想（语音或打字）。

## 2. 需求拆解（来自 Tylor，2026-08-24 口述整理）

| # | 需求 | Phase |
|---|------|-------|
| 1 | 贴边悬浮滑块触发，默认屏幕**左缘靠近手指的位置**，带吸附效果，位置可自定义 | **P1（本期）** |
| 2 | 触发后识别当前小红书帖子，抓取原文/评论/视频/图片，整理为笔记 | P2（参考已有爬取工具后设计） |
| 3 | 抓取过程中可写感想：语音按住说话 或 打字 | **P1 出模拟样式** |
| 4 | 手机端知识图谱预览（更简化的图谱信息：记录了什么、看了什么） | P3+（明确暂缓） |
| 5 | 设备配对 + 同局域网手动/自动传输，**不部署服务器** | 架构方向已定，P1 只出配对状态 UI |

## 3. 信息架构（三方向共享）

六个关键场景（每个方向的原型都完整覆盖，模拟数据一致，便于横向对比）：

1. **待机** —— 小红书浏览中，滑块以低存在感贴在左缘 ~62% 高度
2. **拖拽吸附** —— 长按滑块沿边缘移动，其他边缘出现吸附轨道预览
3. **识别帖子**（功能框主视图）—— 配对状态条 / 帖子卡片（封面、标题、作者、赞藏评）/ 抓取内容 chips / 感想输入区 / 同步主按钮 / 目标文件夹提示 `Memmos graph/小红书/`
4. **语音感想** —— 按住说话变波形条、计时、实时转写预览、上滑取消
5. **抓取进度 → 完成** —— 原文✓ 图片 9/9 视频 78% 评论抓取中的清单式进度；完成态显示落盘路径 + 最近剪藏列表
6. **浮条设置抽屉** —— 四边位置选择器（手机轮廓可视化）/ 高度·大小·透明度滑杆 / 自动隐藏 / 仅小红书显示 / **设备配对区块**（Mac 已连接 · 局域网 IP · 重新配对）

交互约定（初稿，随精修更新；已吸收 §7 调研结论）：
- 单击滑块 = 展开面板；位移在系统 touch slop（约 8dp）内不算拖拽；长按（约 500ms）= 进入拖拽换边模式，松手吸附最近边缘并震动反馈
- 滑块视觉小（约 28–36dp 宽）、热区大（≥48dp）；闲置自动降低不透明度至 ~30%（AssistiveTouch 式），全屏视频/横屏时自动收起防误触
- 面板从滑块所在侧滑入，宽约 86%；点击面板外区域收回
- 所有触控目标 ≥ 48dp（Material 官方标准）；深浅色两套（三方向各演示其一）

## 4. 三个设计方向

| 方向 | 文件 | 一句话理由 |
|------|------|-----------|
| A 琉璃 Glass | `direction-a-glass.html` | 深色玻璃拟态 + 小红书红橙渐变点缀，消费级质感，与宿主 App 氛围融合不突兀 |
| B 原生 Material You | `direction-b-material.html` | 浅色 M3 表达性设计，最像"安卓原生的一部分"，系统级信任感 |
| C 墨石 Ink | `direction-c-ink.html` | 暖黑纸墨 + Obsidian 紫点缀 + 衬线标题，知识工具的安静高级感，与桌面端气质同源 |

画廊入口：`index.html`（浏览器打开即可横向浏览全部 18 个手机帧）。

## 5. 决策记录

- [x] 方向选择：**A 琉璃 Glass**（Tylor 拍板于 2026-08-24，画廊三选一）
  - 保留：深色玻璃拟态体系、红橙渐变强调色、呼吸微光滑块、面板从左滑入
  - B/C 的可回收资产：B 的「跟随系统深浅色」思路留到设置项里做；C 的宋体标题可用于笔记落盘路径等「书斋感」局部点缀（待定）
- [x] 色彩令牌锁定：强调渐变 `#FF2E4D → #FF7A45`；玻璃面 `rgba(255,255,255,.05~.14)` + 描边 `.16`；底色 `#101218`；成功 `#46C882`
- [x] 字体方案锁定：拉丁用 Sora，中文走系统 PingFang SC / HarmonyOS Sans 回退（P1 不打包字体文件）
- [x] 动效签名时刻锁定：滑块呼吸微光（breathe 3.2s）+ 面板玻璃滑入；语音波形为次级动效
- [x] **Phase 1 实现状态（2026-08-24）**：方向 A 已落地 Kotlin + Compose（包名 `com.tylor.memmos`）。
  **架构已从应用内演示升级为真正的系统悬浮窗**：`FloatingService` 前台服务（specialUse 类型）+
  两个 `TYPE_APPLICATION_OVERLAY` 窗口（滑块小窗口 / 面板 86% 宽窗口，窗口外触摸穿透到下层 App），
  Compose 内容通过 LifecycleOwner 桩（`overlay/OverlayCompose.kt`）跑进服务窗口。
  MainActivity 仅是控制壳：权限引导 + 服务启停。
  悬浮窗形态已在模拟器桌面之上实测：滑块停靠左缘 62%、单击展开面板、右侧窄条穿透可见。
  截图见 `app-screens/`（09/10 为悬浮窗实拍）。
  已知待打磨：拖拽松手吸附手感需真机连续事件流验证；设置抽屉开关项尚为纯 UI 状态；
  面板感想输入暂无 TextField（悬浮窗 NOT_FOCUSABLE，接输入框时需动态切 flags 唤起键盘）。
- [x] **滑块简洁化改版（2026-08-24，Tylor 反馈）**：36×112dp 带 logo 点/把手点/呼吸辉光 →
  **20×88dp 素面玻璃细条**：紧贴屏幕边缘（触控余量全部留在内侧，热区仍 ≥48dp）、
  外侧两角半圆收头（半径=宽一半）、发丝描边、无内部装饰、无常驻动画，仅拖拽时提亮+投影加深。
  截图 `app-screens/11、12`。

## 6. 后续路线（备忘）

1. 方向确定后按 Template C 分层精修（tokens → 布局 → 组件状态 → 一个动效高光时刻）
2. 落地实现默认 **Kotlin + Jetpack Compose**（本机已具备构建与模拟器自动化链路）；滑块本体后续迁移系统悬浮窗（SYSTEM_ALERT_WINDOW），P1 先做应用内演示
3. P2 抓取逻辑：先调研成熟小红书采集工具的能力边界再定方案（子代理调研进行中，结论会补进本文档 §7）

## 7. 竞品调研结论（子代理产出，2026-08-24）

**边缘浮条范式**：Android 官方触控目标最小 48×48dp、间距 ≥8dp，小图标可用 `TouchDelegate` 扩大热区；悬浮窗用 `TYPE_APPLICATION_OVERLAY` + SYSTEM_ALERT_WINDOW 特殊权限，配 `FLAG_NOT_FOCUSABLE` 不抢焦点（矩形外触摸自动穿透）。AssistiveTouch / Edge Panel / MIUI 侧边条的共性：贴边停靠、可调大小与透明度、闲置降不透明度、点击展开 + 拖拽改位。

**剪藏工具信息架构**：Obsidian Web Clipper 保存即 Markdown 并写 front-matter 属性；Cubox 与简悦共同哲学是「先零摩擦收集、后从容整理」——收藏与整理拆成两步；XHS-Downloader 的字段集即小红书元数据全集：标题/描述/作者昵称与 ID/发布时间/类型/话题标签/赞·藏·评·分享计数，图片按序号选取、视频按清晰度下载。默认命名「发布时间 作者昵称 标题」可借鉴为我们的笔记命名规则。

**语音速记惯例**：微信/Telegram/iMessage 共性 = 按住录音（变色 + 波形 + 计时）、松手默认提交、上滑取消/转文字是可选增强、转文字结果可编辑后再发。

### 对本设计的五条落地启示

1. **滑块做小、热区做大**：视觉 ~28dp 宽，触控区 48dp；闲置降至 ~30% 不透明度
2. **手势分工克制**：单击=开面板；touch slop 内不算拖拽；全屏视频自动收起
3. **一步入库、两步整理**（Cubox 式）：考虑把「点滑块 → 默认模板直接落盘收件箱」作为快捷路径，当前面板里的精细选项留给第二步整理——精修阶段决定是否加这个「闪电模式」
4. **字段对齐 XHS 元数据集**：赞/藏评计数存为 front-matter 数字属性便于检索排序（Phase 2 抓取实现时的数据契约）
5. **语音闭环**：按住说、松手存、上滑取消；转写文本作为批注追加进刚剪藏的笔记

## 8. 安卓开发环境速查（本机，2026-08-24 配置）

```bash
# JDK：AGP 8.7 不支持本机默认的 Java 25，必须用 17
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home
# Gradle/JVM 不读 shell 的代理环境变量，构建需显式传 JVM 代理参数（127.0.0.1:7890）
export GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
./gradlew :app:assembleDebug          # 产物 app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- SDK 在 `~/Library/Android/sdk`（cmdline-tools 手动安装，非 Android Studio）；已装 platform-35 / build-tools 35 / emulator / arm64 系统镜像
- 模拟器 AVD：**MemmosAPI35**（Pixel 7 · API 35 · arm64-v8a），`sdk/emulator -avd MemmosAPI35` 启动；本机还插着一台实体设备（adb 序列号 f0bc3c88）
- `sdkmanager` 走代理用 `JDK_JAVA_OPTIONS` 环境变量传参（zsh 不会对未引号变量分词，别学我用 `$PROXY` 展开）

### 调试踩坑记录（勿重复交学费）

1. **uiautomator 语义 bounds ≠ Compose 真实命中区**：最小触控目标会把语义框膨胀偏移，按 dump 坐标点齿轮点不中、实测命中在 x≈805。自动化调试以回调日志为准（临时 `Log.d` + `logcat -s MemmosDbg`）
2. **冷启动后立刻 `input tap` 会丢事件**：等 `dumpsys window` 显示 mCurrentFocus 后再操作，关键步骤间用 UI dump 断言 + 重试
3. **adb 分段注入拖拽手势会被系统取消事件流**（tab 弹回原位 = 走了 onDragCancel）：验证拖拽吸附逻辑用设置抽屉的四边选择钮；手感调优留给真机
4. **悬浮窗边到边必须 `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS` 双加**：只加 NO_LIMITS 时 Compose 内容仍会被状态栏 inset 整体下推 136px（面板顶部留空即此原因）；窗口外穿透区（面板右侧窄条）依赖 NOT_TOUCH_MODAL
