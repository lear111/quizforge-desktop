# QuizForge V2 文档目录

这里仅维护与当前实现对应的开发手册。第一次接触项目先读新人指南，按具体任务查代码导读、协议或热更新说明；新增题型时使用五步模板。

| 文档 | 用途 | 什么时候看 |
| --- | --- | --- |
| [新人技术指南](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/new-developer-guide.md) | 模块地图、数据关系、编辑/练习调用流程、判断题接入实例 | 第一次阅读项目或新增题型 |
| [逐文件代码导读](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/code-guide.md) | 当前生产代码的文件职责及入口 | 寻找实现、定位修改位置 |
| [题库文件格式与内容资源](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/qbank-format.md) | ZIP 与逻辑模型、资源、校验、内容版本和兼容边界 | 修改文件、内容或资源协议 |
| [开发热更新说明](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/development-live-update.md) | 启动参数、Java/CSS/Canvas 更新、重启边界和日志 | 开发调试或排查启动问题 |
| [新增题型五步模板](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/templates/new-question-type.md) | 新题型的数据、规则、界面、登记和验证清单 | 开始实现、检查接入是否完整 |

## 阅读路线

1. 新人指南第 1～3 节：先运行并认识模块和代码位置。
2. 新人指南第 4～5 节：理解题目、资源、作答、保存与历史。
3. 新人指南第 6 节及题型模板：按固定五步接入；需要新媒体或新评分时再检查公共基础。
4. 遇到具体文件或协议问题，查代码导读或题库格式文档。

## 维护原则

- 各手册维护自己的主题；详细题型教程放新人指南，待填写清单放模板，文件协议放题库格式文档。
- 文档区分已经实现的能力和待开发能力，以当前代码与实际验证为准。
- 改动同步相关说明和链接；小改动只做必要的编译或定向检查，较大协议/事务变化再扩大验证范围。

已结束的审查、阶段报告、重复架构说明和优化前快照不再作为现行手册保留。2026-10-02 整理前的副本在项目 `target/docs-cleanup-2026-10-02/before`，用于本机恢复。
