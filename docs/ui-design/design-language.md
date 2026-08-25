# Memmos 悬浮层 · 设计语言 v3（多源化 · 琉璃 Glass 实现规范）

> v3.3（2026-08-25）：**官方源适配**——已购并通过 MCP 获取 vision-engine-scan-console 官方
> design.md（存档 docs/vechooool/vision-engine-scan-console/），品牌色切换为官方 Accent **#10B981**；
> 主按钮=白底黑字 9999 胶囊、大标题 Light+负字距（30/300/-0.025em 移动缩放）、画画布 #000000、
> Secondary #EF4444=语义红、4px 节奏、绿系浅调成功色；紫品牌退役（历史版本 git 可回滚）。
> v3.2（2026-08-25）：**Vechooool 参考适配**（https://vechooool.com/projects/vision-engine-scan-console，
> 模板详情需登录，公开可取证：平台库 UI=近黑侧栏+亮内容网格+紧凑微圆角卡片+细边框；SaaS 场景卡=深底+
> 小型大写标签+低饱和点缀）。采用项：卡片圆角 16→12（紧凑）、分区=侧栏式分组（设置页）、媒体封面直接呈现内容
> （不做装饰性占位）；不采用：营销式页脚/口号、彩蛋装饰。

> 本文是安卓端所有前端改动的**唯一依据**：先对表，再动手。改动任何令牌需先改这里并注明原因。
> v4（2026-08-25）：**样式精修**（参考 Linear/Raycast/Notion/Apple HIG）：新增
> Type/Shapes/Space 令牌（字阶 17/15/13/11/10、圆角 28/16/12/999、4dp 栅格）、
> 基元组件（GlassCard/SectionTitle/EmptyState/PillAction）、空态引导、碎字号与
> 杂圆角归一。v3.1：**极简化多源化**——品牌色收敛为**单一紫色**（同色系深浅双调渐变），
> 全 App 颜色结构 = 1 品牌紫 + 3 语义色（成功绿 / 危险红 / 警示橙）；
> 参考位封面色统一为紫调深浅变体。

## 1. 形态（Form）

| 元素 | 规范 |
|------|------|
| 滑块 | 20×88dp 素面玻璃胶囊，**紧贴屏幕边缘零间隙**；外侧两角半径 = 宽度一半（半圆收头），贴边侧 4dp |
| 面板 | **全高侧边抽屉**：上下直角抵拢屏幕物理边缘（窗口加 FLAG_LAYOUT_NO_LIMITS，不吃状态栏留白），朝内容侧的两个角 **28dp 大圆角** |
| 设置抽屉 | 底部上滑，占屏高 84%，仅顶部两角 26dp |
| 层级 | 滑块 shadow 6dp（拖拽 14dp）· 面板右向投影 60dp · 变暗层 ≤30% |

## 2. 色彩（Color）

| 令牌 | 值 | 用途 |
|------|-----|------|
| 强调渐变 | `#8B7BFF → #9E8FFF`（**同一紫的双调**） | 品牌色唯一来源：CTA、进度条、选中态、logo |
| 品牌紫浅调 | `#B4A7FF` | 链接、高亮文字、选中图标（紫系内深浅，不引入新色相） |
| 语义红 | `#FF2E4D`（及其浅底 `#14FF2E4D`） | **只用于**：删除、失败、危险、未授权——不再是品牌色 |
| 玻璃面 | 白 `.05 → .18` 垂直渐变 | 卡片/面板基底 |
| 玻璃描边 | 白 `.16`（弱 `.09`） | 所有容器发丝线 |
| 墨底 | `#101218` | 变暗层基色、控制壳背景 |
| 成功 | `#46C882`（容器 `.09~.17`） | 完成/配对在线 |
| 文字三级 | `#F2F4F8 / #9BA1AE / #676D7A` | 主文 / 正文 / 弱提示 |

规则：一屏内强调渐变出现不超过 2 处；信息层级靠透明度不靠新颜色。

## 3. 圆角与间距

- 圆角体系：**28**（面板外轮廓）/ **16**（卡片、按钮）/ **12**（横幅等次级容器）/ **999**（chip、开关）
- 间距走 4dp 栅格：4 / 8 / 12 / 16 / 20 / 24；面板内边距统一 20dp
- 触控目标 ≥48dp：小图标用热区扩展实现，视觉尺寸不变

## 4. 字阶（Type）

| 级别 | 规格 |
|------|------|
| 面板标题 | 16sp · Bold |
| 卡片标题 | 15sp · Bold · 行高 1.4 |
| 正文 | 12–13sp |
| 辅助/标签 | 10.5–11sp；分组标签加字距 `.14em` |

中文字体走系统栈（PingFang SC / HarmonyOS Sans），不打包字体文件。

## 5. 动效（Motion）

- 入场：面板 320ms `FastOutSlowIn` 滑入 + 200ms 淡入；抽屉 300ms 上滑
- 循环动画**只允许**出现在录音波形（用户主动触发的场景）
- 拖拽吸附回弹 220ms；其余反馈 ≤240ms

## 6. 图标

自绘线性风格：24 视口 · stroke 1.8–2f · 圆头圆角（`ui/Icons.kt`），不引入图标库。

## 7. Vision Engine 全量还原（v4.1 · 2026-08-25）

> 依据：docs/vechooool/vision-engine-scan-console/（用户购买后提供的官方包：design.md /
> index.html / design.html / preview.jpg / assets）。本次按 **index.html 实际落地配方**而非只按
> design.md 概括，逐项照搬/折算：

| 模板配方（index.html class） | App 落点 | 实现 |
|------|------|------|
| Ambient Background：`img opacity-60 mix-blend-screen blur(4px)` + `from-black/50 via-black/10 to-black/80` | 主界面/悬浮面板/设置抽屉底 | `AmbientBackdrop()`：官方资产图 `res/drawable-nodpi/ambient_glow.jpg`（alpha .42）+ 三段纵向渐变罩 |
| gs-card 渐变发丝壳：外层 1px padding 露出 `from-white/40 via-white/5 to-white/10`（opacity .7），内层 `bg-black/10 backdrop-blur-2xl rounded-23` | 全部卡片/行卡 | `VisionCard()`（23px 大卡）/ `VisionRowCard()`（16px 行卡）：壳=ShellGradient，内层=VisionSurface 黑 .10；模糊由环境底承担 |
| Floating Island Navigation：发丝壳 + `bg-black/55 blur-2xl p-1.5`；激活项=白胶囊图标+标签展开，未激活=圆形玻璃图标 white/50 | 底栏 | `IslandBar()`/`IslandItem()`：IconScan/IconGrid/IconGear 矢量图标，白胶囊 `animateDpAsState` 展开标签 |
| Segmented（Mesh/Depth）：容器 `bg-black/30 border-white/10 p-1 rounded-full`，选中白底黑字 | 浮条设置「左缘/右缘」 | `EdgeChip` 选中=白胶囊黑字，未选中=white/60 |
| Header 圆钮 `w-9 h-9 bg-white/10 border-white/20` | 面板设置/关闭钮 | `GlassCircleButton()` |
| 主按钮：宽幅 CTA 白色 rounded-2xl(16) `shadow-black/30`；小动作白胶囊（Execute） | CtaButton=白底黑字 16dp；抓取/配对=白胶囊 | `BtnPrimaryBg/BtnPrimaryText` |
| 列表行：缩略图 44dp rounded-xl `ring-1 white/10` · 标题 white/95 正文 · 副行 white/50 · 尾部状态icon | 剪藏行/同步行/最近行 | ClipRow/RecentRow/SyncFileRow 统一 VisionRowCard + RingWhite 描边环 + 视频角标 |
| eyebrow：`text-xs uppercase tracking-widest white/50` | 分区标签 | SectionLabel 11sp / 2.4sp 字距 / TextSoft |
| headline-lg：Roboto 30/300/-0.025em | 页面标题 | PageTitle 24sp Light -0.6sp + 品牌绿短下划线（保留 App 标识） |
| 圆角家族 12/16/23/24/48/9999 · 4px 节奏 | Shapes：Panel 28 / Tile 23 / Card 16 / Sub 12 / Pill 999 | — |
| Canvas #000000 · 文字 white/95 / 50 / 30 · Secondary #EF4444=语义红 · Accent #10B981 | Ink=#000000；TextHi / TextSoft(50) / TextGhost(30) | — |

**不照搬**（手机工具 App 无对应物）：88px 分节节奏、桌面网格、GSAP 滚动编舞、Solar 图标库、
设备白框容器（sm:shadow 白圈）、首页 indicator 横条（系统手势条已有）。

> v4.1.1（2026-08-25）修正：①面板/抽屉顶部钮叠在亮环境光斑上「白 10% 玻璃底」不可见 →
> GlassCircleButton 默认改深玻璃底（黑 40% + 白描边），关闭钮 42dp/22dp 白全亮，设置钮 40dp/20dp；
> ②悬浮面板/设置抽屉是悬浮层、底下是宿主 App——环境背景若全透明会把宿主文字叠进来 →
> 面板与抽屉先垫 95% 不透明深底（0xF20B0D12）+ 环境光晕，仅外侧 14% 捕获层保持透明露宿。

> v4.1.2（2026-08-25）悬浮层可读性：面板/抽屉叠在任意宿主内容上，文字对比优先——
> 深底加到 0xF5（96%）、环境光 alpha 降到 0.30/0.32，并叠加「上 60% / 中带 35%（压亮光斑）/ 下 60%」
> 纵向可读性罩；面板说明性弱提示由 TextFaint 升到 TextSoft（white/50）。
> 主界面（App 内）维持 0.42 不变——它没有宿主文字穿透问题，保留环境光氛围。
