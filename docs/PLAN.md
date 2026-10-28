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

---

## 执行状态（2026-10-24）

| 阶段 | 状态 |
|---|---|
| M0 骨架+图标 | ✅ 真机验证（四Tab/深色/图标） |
| M1 数据层+互导 | ✅ 17 单测 + 网页版双向互导全链路验收 |
| M2 知识库 | ✅ 四分页/搜索/详情弹层/状态标记/笔记/日期目标/隐藏恢复 |
| M3 知识网 | ✅ 单Canvas自绘/缩放平移/路径高亮流动/长按快标/搜索定位/前置聚焦 |
| M4 进度 | ✅ 今日卡片/加权环形/分类进度/岗位掌握度/岗位笔记 |
| M5 动效性能 | ✅ 呼吸光晕/连线流动/环形生长/按压回弹；系统「移除动画」+应用内开关双重尊重。Baseline Profile 待装机生成（需真机/模拟器跑 macrobenchmark，v0.3） |
| M6 发布 | ✅ release 2.3MB（R8+签名）；版本 0.2.0 |

**与方案的偏差（记录）**：
1. 技能/岗位/路径/资源的「新增/编辑表单」未做——重定制场景在网页版操作后导入即可（互导已打通），App v0.3 再补
2. 删除语义简化为纯墓碑（不做路径手术）：合并层自动过滤，行为等价、完全可逆，网页版吃回亦兼容
3. baseline profile 未生成（需设备），当前冷启动性能依赖 R8 优化

## v0.3（2026-10-24 追加）：增删改表单

- 新增技能/岗位/路径/资源表单（SkillFormSheet/JobFormSheet/PathFormSheet/ResourceFormSheet，EditForms.kt）
- 详情弹层加「编辑」入口（内置编辑=完整覆盖记录 user:true，与网页版编辑对话框同语义）；路线详情加「隐藏路线」
- 路径编辑器：目标岗位选择 + 技能有序选择（搜索点选加入、↑↓排序、✕移除）
- 岗位表单：层级/难度/需求星级、三层薪资、配色板（网页版 PALETTE）、企业/项目多行输入
- 技能表单：分类/难度、四段描述、资源多行、前置可搜索多选
- 列表页顶部「＋新增」入口；资源支持新增（删除已有）
- 未做：自定义资源的「编辑」（仅新增+删除）；Baseline Profile（待真机）

## v1.0（2026-10-28）：知识网双视图 + 动效/性能

**版本**：versionName 1.0.0 / versionCode 7（上一版 0.5.0 / 6）

### 路线视图重写（NetScreen.kt）
| 问题 | 处理 |
|---|---|
| 内容坐标用 px、字号用 sp，默认 scale 2.19 再放大一次（14sp→约30dp） | 坐标系改 dp@scale1，真机 14sp 即为 14sp |
| 泳道宽 240 而标签实测 300+，跨泳道重叠 | 单泳道布局，文字块宽 214dp，两行省略 |
| 路径标题在画布内互相压字 | 标题移出画布改为顶部 HUD |
| 无 LOD，缩小后糊成一团 | 三级 LOD |
| 两个无限动画使画布永不静止 | 静态层/动效层双 Canvas 分离 |

- 相机：110ms 防抖 settle + 速度预测 + 泳道吸附 + 缩放档位 0.5/1.0/1.7（双击循环）+ 越界橡皮筋 30%
- 动效层：轨道流光、学习节点呼吸、点击涟漪、入场阶梯、长按触感
- 底部 16 路径覆盖率总览条；视口 + 节点级剪裁；RenderModel 预计算

### 关系网视图（新增 GraphView.kt）
- 节点：81 技能 + 16 岗位；边：前置（紫）/ 同路径相邻 / 路径→岗位
- 布局：确定性 Fruchterman-Reingold（黄金角螺旋初值，180 迭代，约 20ms）
- 名称在屏幕空间绘制 + 恒定字号 + 贪心避让 → 任意缩放可读
- 点岗位进入对应路线；点技能开详情；分类筛选；命中半径按屏幕 24dp 折算

### 修复
- 二级弹层滚到内容尽头抽搐：LazyColumn 的剩余位移/惯性被传给 ModalBottomSheet 拖拽 → 用 NestedScrollConnection 在越界处吃掉
- 构建：debug 也使用正式 keystore 签名（否则与已装 release 包签名冲突无法覆盖安装）
- 发布包启用 v2 + v3 签名

### 实测（真机 1080×2412 / 120Hz）
| 指标 | 改前 | 改后 |
|---|---|---|
| 路线拖动 janky | 24.90% | 2.66%（release 0.00%） |
| 拖动 50th/90th | 26ms / 32ms | 11ms / 20ms |
| 关系网拖动 janky | — | 1.65% |
| 冷启动 | — | 约 330ms |
| APK | 2.3MB | 2.34MB |
