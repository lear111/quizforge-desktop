# QuizForge V2 文档目录

更新日期：2026-10-07。当前宿主 SDK 2.3，八种题型以外部包提供；应用不自动安装或授权。**接口统一以 SIMPLE_PAGE_API 为准；目标设计不等于可调用能力。**

## 运行与架构

| 文档 | 用途 |
| --- | --- |
| [新人技术指南](new-developer-guide.md) | 模块、启动、安装扩展与验证入口 |
| [代码地图](code-guide.md) | 模块职责和打开、编辑、作答、草稿历史、扩展安装链路 |
| [题库文件格式](qbank-format.md) | ZIP/JSON、内容资源、版本和兼容 |
| [开发热更新](development-live-update.md) | Java、CSS、前端和扩展源码各自的更新边界 |
| [实施状态](题型扩展实施方案.md) | 当前完成的工作和待实现功能 |

## 开发题型：当前文档

| 文档 | 用途 |
| --- | --- |
| [新增题型五步](templates/new-question-type.md) | 从复制模板到安装的简要路线 |
| [完整开发指南](../extensions/DEVELOPMENT_GUIDE.md) | 数据、两个页面、规则、样例、打包与热更新 |
| [接口参考 SDK 2.3](../extensions/SIMPLE_PAGE_API.md) | 加载、保存、操作、公共内容、布局、权限和错误 |
| [SDK 文档入口](../extensions/SDK_README.md) | 当前接口、教程与设计资料索引 |
| [判断题模板](../extensions/QUESTION_TEMPLATE.md) | 11 文件结构与中文注释代码 |
| [八种题型源码](../extensions/packages/README.md) | 包版本和手写/编译式入口 |
| [数据校验](../extensions/DATA_VALIDATION.md) | Schema、计算预算、答案与规则返回约束 |
| [安装与测试](../extensions/README.md) | 空配置和真实导入、授权、开发目录流程 |
| [WebView2 后端](../extensions/WEBVIEW2_BACKEND.md) | 原生浏览器与沙箱边界 |
| [规则隔离](../extensions/RUNTIME_ISOLATION.md) | 规则进程、故障与恢复 |
| [题型设计要求](题型设计说明.md) | 各题型的内容、交互和富文本要求 |

## 迁移资料与目标设计

| 文档 | 状态 |
| --- | --- |
| [剩余题型迁移记录](../extensions/READING_TYPES_MIGRATION.md) | 旧题库迁移、历史阶段验证，当前版本见文首 |
| [精简接口方案](题型拓展精简接口方案.md) | 职责与目标；未开放项不作为现有 API |

## 建议阅读顺序

1. 新人指南：运行应用并手动导入扩展。
2. 代码地图：选一条业务链路理解宿主。
3. 开发新题型：五步 → 开发指南 → 判断题源码 → 现行接口。
4. 协议/安全问题：题库格式 → 数据校验 → 后端与规则隔离。
5. 需要未开放能力时再查设计文档，先确认实际接口支持。

## 维护原则

- 接口参数、返回值和可用范围统一维护在 SIMPLE_PAGE_API；教程展示用法，不另定义契约。
- 包版本和源码入口维护在 packages/README；设计文档明确区分目标与实现。
- 示例和默认模板独立维护，使用真实 .qbank，不自动写入工作区。
- 修改源码后按模板决定是否构建，不直接修改生成资源或已发布包。
- 定向检查覆盖本次改动，历史阶段的通过数量不作为本次完整回归证据。

早期白板/学习面阶段文档位于 editor-web/draft-canvas，按文首迁移说明理解旧路径和 DTO；当前入口以代码地图为准。
