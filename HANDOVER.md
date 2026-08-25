# Memmos 安卓配套软件 · 交接文档

> 面向：接手开发的 AI / 开发者。本文档是**唯一权威现状**（截至 2026-08-25 晚，近期高频迭代后）——
> 与 Obsidian 端插件交接（`/Users/tylor/Code/Studyboard/obsidian-plugin/HANDOVER.md`）配套使用。
> 本文档对应的插件端增量（同步服务）见本文 §7 与插件仓 `src/sync/`。

---

## 1. 项目定位与两端路径

| 端 | 路径 | 说明 |
|----|------|------|
| **安卓端（本文档主角）** | `/Users/tylor/Code/Android-Memmos` | 包名 `com.tylor.memmos`，Kotlin 2.0 + Compose（BOM 2024.12.01），非 git 仓库 |
| **Obsidian 插件端** | `/Users/tylor/Code/Studyboard/obsidian-plugin` | memos-graph（id 不可改），本会话为其新增了**设备同步服务**（src/sync/），已构建部署 |
| **部署 Vault** | `/Users/tylor/Note/OBISIDIAN` | 插件目录 `.obsidian/plugins/memos-graph/`；同步范围文件夹 `Memmos graph/` |

用户：Tylor。中文交流；注释写「为什么」（本仓知识载体）；标「用户要求」的常数慎改。

## 2. 产品定位

手机端「贴边捕捉工具」：屏幕边缘滑块（悬浮窗）→ 在小红书刷到帖子 → **一键抓取完整内容（正文/图集/视频/评论/头像）**→ 本地剪藏库管理（搜索/多选/删除/批量同步）→ **局域网配对 Obsidian 双向同步**（md + 媒体文件 + 相对引用）→ 多类型文件阅读。后续规划：手机端简化图谱、AI 对话（apiKey/model=qwen-plus 在插件端预留）。

## 3. 安卓端架构（文件清单）

```
app/src/main/java/com/tylor/memmos/
├── MainActivity.kt                  # 三页导航：捕捉 / 剪藏库 / 设置（Scaffold+NavigationBar）
│                                    #   剪藏状态（clips/query/多选/删除/批量同步）在 MainTabs 统一管理
├── data/ClipStore.kt                # 剪藏本地库 filesDir/clippings.json（org.json 手写序列化）
│                                    #   模型：ClipNote / ClipComment（id=noteId 或 vault:sha16）
├── net/XhsFetcher.kt                # HTTP 免登录解析：INITIAL_STATE + HTML 兜底（无 token 时降级）
│                                    #   图片链：urlDefault → infoList[WB_DFT 优先] → video.image（视频笔记
│                                    #   imageList[].url 为空，封面在这两处）；短链两段式请求防跨域 Cookie 风控
├── net/MediaDownloader.kt           # 视频（流式落盘）+ 图片下载（带 Referer 防盗链）
├── overlay/FloatingService.kt       # ★核心：前台服务 + 三窗口管理 + 滑块手势状态机
├── overlay/PanelHost.kt             # 面板 Compose：dim(86%) + 面板(86%) + 透明区点击关闭 + 设置抽屉
├── overlay/OverlayModel.kt          # 悬浮层共享状态（edge/frac/scale/opacity）
├── overlay/OverlayCompose.kt        # ComposeView 跑在服务窗口的 LifecycleOwner 桩
├── ui/EdgeTab.kt                    # 滑块视觉（20×88 贴边胶囊 + systemGestureExclusion）
├── ui/Icons.kt / theme/Theme.kt     # 自绘图标 + 琉璃 Glass 令牌
├── ui/clips/ClipDetailActivity.kt   # 详情页：媒体上移/图集Pager/全屏查看器/评论/**内嵌视频播放器**
│                                    #   （就地播放不放大；全屏按钮→Dialog 全屏层；手势：左半竖滑=亮度、
│                                    #   右半竖滑=音量、横滑=进度、单击=控制条；下载完自动转内嵌播放）
├── ui/fetch/ClipFetchActivity.kt    # WebView 渲染抓取页（分享直进）：轮询就绪→评论滚动→DOM 提取→
│                                    #   双路线合并（XhsFetcher 补视频/封面）→保存+视频后台服务
├── ui/fetch/XhsCaptureService.kt    # ★后台抓取前台服务（面板按钮入口，用户要求不跳转）：
│                                    #   隐藏 WebView 跑同源管线（JS 与抓取页共享 XhsDomCapture），
│                                    #   state 流驱动悬浮面板+通知进度条；视频转 VideoSaverService
├── ui/fetch/XhsDomCapture.kt        # 共享 JS 常量（READY/SCROLL/EXTRACT）+ 视频/封面拦截正则
├── ui/fetch/ClipboardBridgeActivity.kt # 透明桥接页：前台合法读剪贴板（Android 10+ 后台禁读），
│                                    #   拿到链接启动 XhsCaptureService 即退出（无跳转感）
├── ui/login/XhsLoginActivity.kt     # 小红书登录 WebView：桌面 UA 模拟电脑（手机版无网页登录入口）
│                                    #   只有 PC 版才有扫码/手机号浮层；Cookie WebView 全局持久化
├── ui/viewer/FileViewerActivity.kt  # vault 文件查看器：md 渲染/PDF(PdfRenderer)/图片/视频/外部打开
├── ui/md/MarkdownView.kt            # Obsidian 兼容 md 渲染器（手写：标题/粗斜体/删除线/==高亮==/
│                                    #   内联代码/代码围栏/任务清单/嵌套列表/引用/提示框[!note]/
│                                    #   GFM 表格/水平线/图片与![[嵌入]]/[[双链]]/[]()链接/自动URL/#标签；
│                                    #   链接点击跳浏览器、[[双链]]打开本地 vault 文件；渲染入口统一走这里）
├── sync/SyncClient.kt               # 同步 HTTP 客户端 + SyncPrefs（配对信息持久化）
├── sync/SyncEngine.kt               # 双向同步引擎（指纹去重/媒体上传/md 引用改写/下载媒体拉取）
├── sync/DeviceDiscovery.kt          # UDP 局域网发现（广播 28423 + MulticastLock）
└── util/AppPrefs.kt / MediaSaver.kt # 偏好（自动下载视频/评论开关/服务自恢复） + 相册保存（MediaStore 去重）
```

### 悬浮窗手势最终状态（用户多轮反馈调优，勿轻易改）

- **点击滑块** → 开/关面板
- **滑块上向屏幕内滑**（左缘向右 / 右缘向左，|dx|>12dp 横向主导）→ **跟手拖出面板**，
  松手判定：拉过 ~14% 屏宽（≥90dp）或轻甩（>350px/s）→ 打开；否则回弹收起
  （触发 80dp→48dp→16dp→12dp≈slop 下限勿再调低；打开判定=触发即必开——用户要求
  「只要触发震动就一定打开」；打开门槛 50%→35%→22%→14%→废弃，仅保留朝外甩回可收回）
- **长按 380ms**（震动确认）→ 拖拽换边模式，松手吸附最近边缘
- 滑块可视区 `systemGestureExclusion()` 排除系统返回手势（API 29+，手势导航不冲突）
- **面板展开**：右侧透明区点击关闭 / 面板上左滑关闭 / **系统返回键关闭**（全屏透明可聚焦「返回拦截窗」，FLAG_NOT_TOUCHABLE 触摸全穿透）
- **收起易用性**（用户反馈手小，勿再收紧）：面板与右缘透明层都可滑关；松手时**轻甩
  （朝隐藏方向 >250px/s）或拖回 ~10% 屏宽（至少 48dp）即收回**（旧判定需拖过半屏）。
  速度来自 draggable 的 onDragStopped（px/s ÷ 1000 上报）；右缘透明层曾有 3 个重复 Box
  导致滑动被最上层点击拦截 + 钳制只有 +60dp（右缘面板物理拖不回），均已修复
- 状态区分：面板展开时滑块被盖住（点击滑块不可用），关闭后再点
- 服务自恢复：`AppPrefs.serviceWanted`——启动过一次后 App 回前台自动拉起（`MainTabs` ON_RESUME）

## 4. 同步协议（两端对照）

```
Obsidian 端（memos-graph 插件，TCP 默认 28422 + UDP 28423）：
  GET  /pair?code=6位配对码            → {token, folder}（配对码在插件设置页显示）
  GET  /api/inventory                  → [{path, sha256, mtime}]（全类型白名单：md/pdf/docx/pptx/xlsx/
                                        csv/图片/视频/音频/txt；md 文本指纹，二进制字节指纹）
  GET  /api/file?path=                 → md:{content} / 二进制:{base64}
  POST /api/file  {path, content}      → 写 md（范围校验+自动建目录）
  GET  /api/binary?path=               → {base64}
  POST /api/binary {path, base64}      → 写二进制（上限 200MB）
  UDP 28423：收到 "MEMMOS_DISCOVER_V1" → 单播回报 {service,name,host,port,code}
  API 鉴权：X-Memmos-Token 头（/pair 除外）

Android 端：
  发现：DeviceDiscovery（255.255.255.255 + DHCP 网段广播 + 模拟器宿主 10.0.2.2）
  上传（双向补全·手机→电脑）：剪藏图片/视频字节 → /api/binary 到 {同步目录}/小红书/media/
        （图片视频同一文件夹）；md 内图片 ![](media/xxx)、视频 ![[media/xxx.mp4]]（Obsidian 原生
        嵌入播放）相对引用，上传失败回退远程直链；视频仅当本地已下载才传。
        md 全内容格式 toMarkdown：frontmatter（title/url/author/avatar/tags/type/memmos-id）+
        正文 + 「## 评论」列表（含楼中楼「- **昵称**：内容（♥N）」）。
  下载/更新（电脑→手机）：inventory 对位 originPath，指纹不同即拉取→新条目或覆盖更新
        （Obsidian 侧修改后回填手机）；md 引用 media 一并拉（![](..) / ![[..]] / [x](..) 三种
        语法都匹配）；非 md 文件也拉取（剪藏库「同步的文件」分组）
  去重：上传侧远端同 sha256 跳过；下载侧本地存在/已下载按 localPaths+指纹跳过
```

**同步范围**：Obsidian 端独立设置「同步文件夹」（默认 `Memmos graph`，与图谱 scanFolder 解耦——用户明确要求只同步该文件夹，勿改回全库）。

## 5. 已完成功能时间线（近期）

| 功能 | 状态 |
|------|------|
| 悬浮窗全套（滑块/面板/换边/穿透/返回关闭） | ✅ 模拟器全回归通过 |
| 小红书分享直达抓取（短链 xhslink.cn / .com 兼容） | ✅ 真实链接验证 |
| 视频自动下载+相册（Movies/Memmos）开关 | ✅ |
| 头像（user.avatar）/标签清洗/标签底部 FlowRow | ✅ |
| 评论解析（页面内嵌→实际页面**不含**评论，见 §6） | ⚠️ 路线受阻 |
| WebView 渲染抓取管线（方案A 登录态，双路线合并 + 评论完整抓取） | ✅ 真机验证：主 29 + 回复 21 = 50 = App 计数 |
| 详情页（媒体上移/图集 Pager/全屏查看器/相册去重保存） | ✅ |
| 剪藏库三页导航 + 多选（长按/勾选/全选/操作菜单） | ✅ |
| 局域网设备发现 + 一键配对（手动输入折叠备用） | ⚠️ 待 Obsidian 重载后端到端验证 |
| 双向同步（md+媒体+引用） | ⚠️ 检查未端到端（Obsidian 侧仍是旧代码进程） |
| 多类型文件查看器（md/PDF/图片/视频/外部） | ✅ 代码完成（少量预览链路逻辑） |
| md 渲染 Obsidian 语法对齐（原仅标题/列表/图片，已补表格/callout/任务清单/双链等） | ✅ 实现完成，⚠️ 待真机与 Obsidian 对照目测 |
| 服务自恢复 / 返回拦截 / 手势返回排除 | ✅ |

## 6. 已知问题与待办（按优先级）

1. **Obsidian 端重载验证（最高优先，阻塞端到端）**：插件新代码（binary 端点/发现服务/syncFolder）已部署，
   但检测显示 TCP 28422 仍是**旧代码进程**、UDP 28423 未监听——需用户在 Obsidian「禁用→启用」插件。
   完成后跑：模拟器发现 → 配对 → 上传「Vibe Coding」剪藏（验证 md+media 落盘 Obsidian）→
   放 PDF 进 Memmos graph → 拉回手机。
2. **剪藏库闪退/卡顿（已定位修复）**：根因是**全选删除逐篇触发全量写盘**——`remove()` 每删一篇都
   做一次全量 filter + 主线程 `store.save(整库 JSON)`（含 rawMd/评论），N 篇 = N 遍全量序列化 +
   N 次磁盘写，主线程被秒级阻塞 → ANR 闪退；若 kill 在 writeText 中途，截断 JSON 会让 load()
   静默返回空 = 整库丢失。修复：`removeMany` 一次过滤 + IO 线程一次持久化；`ClipStore.save`
   改原子写（tmp+rename 覆盖）；`reload()` 挪后台。若真机仍有闪退，`logcat -b crash` 取证。
3. **评论路线已打通（本会话实测校准）**：小红书 PC 页 `__INITIAL_STATE__` 不含评论（评论走签名接口），
   唯一可行路线=登录态 WebView DOM 提取。实测 DOM 结构（2026-08-25）：`.parent-comment` 内
   `.comment-item`（主评论，depth2）+ `.comment-item.comment-item-sub`（楼中楼回复，depth4）——
   **没有 `.sub-comment` 类**；曾踩坑：`:scope >` 在部分 WebView 抛异常导致整段提取作废降级 HTTP，
   已改为类名过滤 + try/catch 兜底（选择器问题只丢评论不丢整条笔记）。评论区为懒加载：
   滚动+点「展开回复」直到 主+回复 总数连续两轮稳定再提取。实测「建议大家尽早搭建」笔记
   主 29 + 回复 21 = 50 = App 计数，去重后无重复。调试产物：`filesDir/comment_dump.html`（评论区 HTML，
   校准选择器用）。
4. **方案A 未验证面**：登录 WebView 已按「模拟电脑」配置（桌面 UA + 宽视口 + 双指缩放，用户要求——
   手机版网页无登录入口，只有 PC 版才有扫码/手机号浮层）；OkHttp 降级路线现在会带上 WebView 的
   Cookie 头。CLIPFetchActivity 的 JS 提取选择器（`.parent-comment` 等）未对真实登录态页面校准；
   登录检测（cookie 含 web_session）逻辑待实测。
5. **图谱入场慢（插件端）**：全库 855 md → 620+ 节点，yield 600 流畅线；解法=扫描范围限制或
   WebGL 专项（插件 HANDOVER 待办）。同步服务不影响图谱性能（异步启动）。
6. **蓝牙配对**：Obsidian Electron 插件无法访问经典蓝牙（硬限制），未实现；未来需原生辅助进程。
7. **md 渲染已知取舍（MarkdownView.kt）**：数学公式 Obsidian 用 KaTeX，本端无 LaTeX 引擎，
   按等宽代码样式呈现（`$..$`/`$$..$$`）；HTML 片段不解析（原样显示）；表格单元格内不做
   点击展开保持紧凑；嵌套列表仅按缩进推层级，>2 级兜底渲染。
8. **剪藏库防覆盖保护（重要，曾数据丢失）**：`ClipStore.load()` 曾「整体解析失败→返回空列表」，
   任一条数据损坏+一次写库 = 整库被覆盖（实测真机 200+ 条被清到 5 条）。已改：
   逐条解析（单条坏只丢该条）、损坏文件备份成 `.corrupt-时间戳` 后返回空（绝不静默覆盖）、
   `saveAndFinish` 包 runCatching（保存失败提示不闪退）；重复抓取提示「已更新剪藏」。
9. **环境/构建坑**（读者必读）：
   - Gradle 增量缓存曾损坏导致「构建成功但产物是旧的」——**改完代码若行为未变，先 `./gradlew clean`**
   - JDK：AGP 8.7 不支持 Java 25，必须 `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/...`
   - 代理：构建需 `GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"`；
     curl 直连局域网需 `--noproxy '*'`
   - zsh 不做未引用变量分词；python heredoc 单引号防 shell 展开；写文件用临时脚本（heredoc 转义坑多次踩）
   - Compose：`Brush`/`Color` 三元混用必须 `SolidColor` 包裹（摔过三次）；PaddingValues 参数不可混用
   - 悬浮窗边到边 = `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`；拖动中移除触摸源窗口会截断触摸流
   - 冷启动后 `input tap` 会丢事件：先 uiautomator dump 一次「预热」；长按拖拽测试用设备端单进程
     `input motionevent` 序列
   - 真机（PJA110/序列号 f0bc3c88）频繁掉线：失败时先 `adb devices`；真机不支持 `appops set`（ColorOS）

## 7. Obsidian 插件端增量（本会话新增）

- `src/sync/SyncServer.ts`：HTTP（/pair /api/inventory /api/file /api/binary）+ UDP 发现（dgram）
- 设置页「设备同步」区块：开关/本机IP/配对码（重新生成）/端口/**同步文件夹**（独立于图谱 scanFolder）
- 命令「开启/关闭设备同步服务」；`applySyncService()` 异步启动不阻塞图谱
- esbuild external 增 `dgram`、`os`；⚠️ **复用坑**：Node Buffer 与 Obsidian 的 ArrayBuffer 类型标签不兼容，
  需 `Buffer.from(ab)` / `.buffer.slice() as ArrayBuffer`；模板字符串注入用 python 非转义写法
- 部署：`npm run typecheck && npm run build && npm run deploy`（deploy 合并 main.css+styles.css 到 Vault）

## 8. 操作提醒（用户侧）

- Obsidian 重载：设置 → 第三方插件 → memos-graph 禁用→启用
- 真机重连后：`adb install -r app/build/outputs/apk/debug/app-debug.apk`
- 配对流程：设置页「扫描局域网设备」→ 点设备卡片「配对」（自动带配对码，无需手输）
