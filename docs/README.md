# QuizForge V2 文档目录

这里仅维护与当前实现对应的开发手册。第一次接触项目先读新人指南，按具体任务查代码导读、协议或热更新说明；新增题型时使用五步模板。

| 文档 | 用途 | 什么时候看 |
| --- | --- | --- |
| [新人技术指南](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/new-developer-guide.md) | 模块地图、七种题型、编辑/练习/大纲调用流程、判断题接入实例 | 第一次阅读项目或新增题型 |
| [逐文件代码导读](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/code-guide.md) | 当前生产代码的文件职责及入口 | 寻找实现、定位修改位置 |
| [题库文件格式与内容资源](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/qbank-format.md) | ZIP 与逻辑模型、资源、校验、内容版本和兼容边界 | 修改文件、内容或资源协议 |
| [开发热更新说明](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/development-live-update.md) | 启动参数、Java/CSS/Canvas 更新、预览缓存、内存排查、重启边界和日志 | 开发调试或排查启动问题 |
| [新增题型五步模板](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/templates/new-question-type.md) | 新题型的数据、规则、界面、登记和验证清单 | 开始实现、检查接入是否完整 |

## 阅读路线

1. 新人指南第 1～3 节：先运行并认识模块和代码位置。
2. 新人指南第 4～5 节：理解题目、资源、作答、保存与历史。
3. 新人指南第 6 节及题型模板：按固定五步接入；需要新媒体或新评分时再检查公共基础。
4. 遇到具体文件或协议问题，查代码导读或题库格式文档。

## 维护原则

2026-10-02 本阶段更新：四种新增题型、连续小题编号与整卡排序、按分值汇总及旧记录兼容、Canvas 段落排版和最多六个脱离场景的只读预览缓存。协议细节以题库格式文档为准，具体实现位置以代码导读为准。

- 各手册维护自己的主题；详细题型教程放新人指南，待填写清单放模板，文件协议放题库格式文档。
- 文档区分已经实现的能力和待开发能力，以当前代码与实际验证为准。
- 改动同步相关说明和链接；小改动只做必要的编译或定向检查，较大协议/事务变化再扩大验证范围。

已结束的审查、阶段报告、重复架构说明和优化前快照不再作为现行手册保留。2026-10-02 整理前的副本在项目 `target/docs-cleanup-2026-10-02/before`，用于本机恢复。

## 验证入口

在仓库根目录执行；构建输出使用启动器的隔离目录，避免混用 IDE 的编译产物：

```powershell
mvn -Dquizforge.build.directory=target/launcher test
python quizforge-infrastructure/src/test/python/check_qbank_v2_schema.py
npm --prefix quizforge-desktop-app/editor-web/canvas run build
```

2026-10-02 本阶段回归覆盖 core 70、infrastructure 303、desktop-app 247，共 620 项；首轮三条旧界面规则断言更新后专项复测全部通过，其余 617 项通过。公开 Schema 验证六个题库包与 107 项协议检查，前端构建通过；七份手册的 367 个本地链接和两个 JSON 示例通过检查，代码导读覆盖 282 个生产 Java 文件。没有进行相同题库与操作序列的受控内存性能对比。
