# 嵌入式知识网（Android）

「嵌入式工程师·知识网工作台」的手机端 App，与网页版 [Learning_Guider](../Learning_Guider) 数据互通。

- 打开：Android Studio → Open → 本目录（首次 Sync 会自动下载依赖）
- 命令行构建：`gradlew.bat assembleDebug`（需 JAVA_HOME 指向 JDK 17+，Android Studio 自带 jbr 可用）
- 开发方案与里程碑：[docs/PLAN.md](docs/PLAN.md)

内置内容（技能/岗位/路径/资源）以网页版为单一事实源，由其 `tools/export-builtin.js` 导出到 `app/src/main/assets/builtin.json`。

## 安装

- `app/build/outputs/apk/release/app-release.apk`（v0.2.0，签名包 2.3MB）—— 传到手机直接安装
- 或 `app/build/outputs/apk/debug/app-debug.apk`（调试包）
- 数据互通：网页版「导出 JSON」→ App「我的 · 导入恢复/合并导入」；App「导出备份」→ 网页版「导入恢复」

## 开发快查

```bash
# 构建（JAVA_HOME 用 Android Studio 自带 jbr）
JAVA_HOME="E:/Android Studio/jbr" ./gradlew assembleDebug
# 测试（17 个：合并语义/内容完整性/互导回归）
JAVA_HOME="E:/Android Studio/jbr" ./gradlew testDebugUnitTest
# 网页版互导回归（需先跑测试生成 app-export.json）
node tools/gen-web-fixture.js && node tools/check-web-import.js
```

## v0.3

新增/编辑/隐藏全链路补齐：四类内容的「＋新增」、详情内「编辑」、路线隐藏恢复；路径编辑支持技能排序。
