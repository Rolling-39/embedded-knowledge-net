# 嵌入式知识网 · Android App 开发方案（已确认版）

> 2026-10-24 与用户确认的四项决策：**仅 Android 原生 / 本地 + JSON 互通 / 移动端重设计 / 自用分享 APK**

## 一、技术栈

| 层 | 选型 |
|---|---|
| 语言/UI | Kotlin + Jetpack Compose + Material 3（AGP 9 内建 Kotlin） |
| 架构 | 单 Activity + Navigation Compose，MVVM + StateFlow 单向数据流 |
| 存储 | Room（数据）+ DataStore（偏好）|
| JSON | kotlinx-serialization（与网页版导出格式逐字段互通） |
| 构建 | Gradle 9.8.2 + AGP 9.4.1 + R8 + Baseline Profile（M5 接入） |
| 基线 | minSdk 26 / targetSdk 36 / 自用签名 APK / 目标体积 <10MB |

版本清单见 `gradle/libs.versions.toml`。

## 二、数据层（与网页版互通）

- 单一事实源仍是网页版 `Learning_Guider`：内容按其 SKILL.md 流程维护
- `tools/export-builtin.js`（网页版仓库）提取 DEF_* → 本 App `assets/builtin.json`
- 首次启动 seed 进 Room；用户定制存覆盖表（override + 墓碑 deleted），合并语义 = 网页版 buildEffective
- 导入：吃网页版导出 JSON（覆盖/合并两模式）；导出：同格式，网页版可直接导入 → **双向互导是 M1 验收标准**
- 网页版 tools/verify.js 的内容校验（断链/孤立/薪资/demand）移植为本 App 单元测试

## 三、UI（移动端重设计）

底部 4 Tab：知识网（全屏画布：双指缩放/拖拽/长按快捷标记）/ 知识库（技能·岗位·路径·资源四分页 + 搜索）/ 进度（环形+分类+岗位掌握度）/ 我的（主题/导入导出/清空/关于）。详情用 BottomSheet 两档。视觉语言沿用网页版（primary #2563EB、分类色系、深色模式）；底部导航图标 path 直接复用网页版 ICONS。

## 四、动效映射

弹窗→ModalBottomSheet（spring）；切页→Navigation transition；按压→scale 反馈；节点呼吸→graphicsLayer alpha（不重组）；连线流动→PathEffect dashPhase；环形图→animateFloatAsState；卡片→详情 SharedTransitionLayout；氛围光斑→单层 Canvas（可关）；动画降级→系统「移除动画」+ 应用内档位。

## 五、性能红线

1. 知识网 = 单 Canvas Composable 自绘（drawWithCache），手势只触发重绘不重组
2. 无限动画只走 graphicsLayer
3. LazyColumn 稳定 key + contentType
4. Room Flow + stateIn 精准刷新
5. Baseline Profile + R8；里程碑跑 Macrobenchmark
6. 指标：冷启动 <500ms / 手势 60fps / APK <10MB

## 六、里程碑

| 阶段 | 内容 | 验收 |
|---|---|---|
| M0 | 骨架 + 主题 + 底部导航 + 图标 | assembleDebug 通过，APK 可安装 |
| M1 | 数据层 + 双向 JSON 互导 | 网页版导出能导入，导出能被网页版导入 |
| M2 | 知识库四分页 | 列表/搜索/详情/状态标记可用 |
| M3 | 知识网画布 | 布局/手势/路径高亮/弹层 |
| M4 | 进度页 | 环形/分类/岗位/今日卡片 |
| M5 | 动效 + 性能 | 60fps + Baseline Profile |
| M6 | 导入导出 UI + 设置 + 签名 APK | 端到端完整闭环 |

## 七、图标

用户提供的设计稿（深灰圆底 #5B5F66 + 白色电路树+三芯片）→ 自适应图标：
background = #5B5F66 纯色；foreground = 原图 90% 缩放（圆底超出裁切区与背景融合）；monochrome = 亮度阈值 215 提取的白色图形（主题图标用）。
