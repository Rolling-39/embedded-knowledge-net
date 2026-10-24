# 嵌入式知识网（Android）

「嵌入式工程师·知识网工作台」的手机端 App，与网页版 [Learning_Guider](../Learning_Guider) 数据互通。

- 打开：Android Studio → Open → 本目录（首次 Sync 会自动下载依赖）
- 命令行构建：`gradlew.bat assembleDebug`（需 JAVA_HOME 指向 JDK 17+，Android Studio 自带 jbr 可用）
- 开发方案与里程碑：[docs/PLAN.md](docs/PLAN.md)

内置内容（技能/岗位/路径/资源）以网页版为单一事实源，由其 `tools/export-builtin.js` 导出到 `app/src/main/assets/builtin.json`。
