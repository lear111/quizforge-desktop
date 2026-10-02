# 新增题型五步模板

适用当前三模块结构。现有题型为 SINGLE_CHOICE、MULTIPLE_CHOICE、ESSAY、CLOZE、READING、MATCHING、TRANSLATION。复用已有数据/响应模型时按以下步骤接入；全新作答、媒体或判分能力需先补共享基础。参考 [题库文件格式与内容资源](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/qbank-format.md)。

第一次新增题型先阅读 [新人技术指南](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/new-developer-guide.md)，其中有判断题规则示例、三个登记位置，以及编辑/练习/历史的接入说明。

## 1. 定义数据

- 稳定 typeId / 中文名称 / OBJECTIVE 或 SUBJECTIVE：
- Payload、AnswerSpec、用户作答模型（复用或新增）：
- TEXT/RICH/DOCUMENT 内容位置与所需资源：
- 自动判分 / 待评分行为及兼容版本：
- 分值是整题分还是单个小题分、总分公式、提示位置是否计分：
- 小题稳定 ID、正文标记与题库大纲编号的关系：

位置：core/question/model 仅放通用结构；专属模型放 type/objective/<type> 或 type/subjective/<type>。不要把数据库字段或 JavaFX 控件放入核心模型。

## 2. 实现规则

- QuestionTypeDefinition 的实现类：
- id/family/payloadKind/payloadClass/answerKind/answerClass：
- 默认题目 createDraft、复制 duplicate（题目/选项生成独立 ID）：
- validate 与现有 CHOICE response 的 multipleSelection/evaluate：
- 合法、非法、空答案与待评分边界：

通用内容、分值、全库身份与资源引用继续由 QuestionBankValidator 负责。

每种题型提供明确的规则类。当前单选入口是 SingleChoiceQuestionType，多选入口是 MultipleChoiceQuestionType；可分别查看默认配置、数量校验与 evaluate。两者共用 ChoicePayload/ChoiceAnswerSpec 和包内 ChoiceQuestionSupport。新增题型时优先添加自己的 QuestionTypeDefinition 实现，避免往共用类增加题型布尔开关；共用数据与复制辅助可以复用。

## 3. 组合界面

- 专有字段组件及 QuestionEditorContext 使用：
- 作答/结果/历史组件（复用或新增）：
- 共用内容渲染/编辑与资源入口：
- 保存、取消与草稿恢复：

复用现有保存服务与练习事务。选择题、完形、阅读使用选项 ID 列表；排序题使用位置到字母 ID 的映射；作文使用 EssayPracticeAnswer，翻译使用按小题 ID 保存 EssayPracticeAnswer 的映射。新响应结构需要扩展快照、命令、恢复和历史详情展示。

## 4. 登记

- core/question/type/QuestionTypes.java：数据与规则登记。
- desktop/ui/question/shared/QuestionTypeCatalog.java：名称与编辑组件登记。
- infrastructure/resources/schema/qbank-v2.schema.json：type 与 payload/answer 约束。
- 新 Payload/Response：QuestionContentData、PracticeQuestionSnapshotMapper、PracticeRuntimeMapper、PracticeSessionService、历史展示等是否需要扩展，逐项写明。
- 类型切换是否能保留旧内容/答案，或是否需要明确拒绝转换：
- 大纲是否展开小题、跳过提示、保持连续题号，移动小题是否仍移动整张父题卡：
- PracticeSummary 是否读取冻结总分和最新提交的实际得分，是否保持旧 ACTIVE 作答及历史可读：

核心类型子序列化由 QuestionBankV2Codec 从 QuestionTypes 注册。不能只改菜单而遗漏 Schema、作答或历史。

## 5. 验证与文档

- [ ] 默认题、复制题、合法/非法配置与身份不冲突。
- [ ] .qbank 写入回读，答案、资源哈希和逻辑版本符合契约。
- [ ] 编辑保存生效，取消不写文件和资源。
- [ ] 草稿、提交、重开、重做和重复提交符合约定。
- [ ] 评分/待评分符合声明，得分累计最新已评分提交，总分覆盖所有计分位置；锁定提示、草稿与待评分不得当成已得分。
- [ ] 小题编号按题库顺序连续；更改提示、重排题卡后跳转与作答身份仍正确。
- [ ] 正文或解析预览命中缓存后绑定当前资源与答案回调；内容变化使缓存失效。
- [ ] 历史冻结题目/作答/资源可恢复，不依赖后来改写的题库。
- [ ] 本次改动直接相关的规则/文件/界面回归通过；涉及协议、事务或较大跨层变化时再扩大范围，记录实际检查结果。
- [ ] 更新代码导读、内容说明及示例文件。

实际命令、用例与结果链接：

需要额外扩展的媒体/评分/历史协议及未完成边界：
