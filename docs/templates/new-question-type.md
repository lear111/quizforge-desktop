# 新增题型五步模板

适用当前三模块结构。复用既有 CHOICE/ESSAY 模型时按以下步骤接入；全新作答、媒体或判分能力需先补共享基础。参考 [题库文件格式与内容资源](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/qbank-format.md)。

第一次新增题型先阅读 [新人技术指南](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/new-developer-guide.md)，其中有判断题规则示例、三个登记位置，以及编辑/练习/历史的接入说明。

## 1. 定义数据

- 稳定 typeId / 中文名称 / OBJECTIVE 或 SUBJECTIVE：
- Payload、AnswerSpec、用户作答模型（复用或新增）：
- TEXT/RICH/DOCUMENT 内容位置与所需资源：
- 自动判分 / 待评分行为及兼容版本：

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

复用现有保存服务与练习事务；当前通用练习/历史基础支持 CHOICE/ESSAY，新 Response 需要扩展快照、命令和详情展示。

## 4. 登记

- core/question/type/QuestionTypes.java：数据与规则登记。
- desktop/ui/question/shared/QuestionTypeCatalog.java：名称与编辑组件登记。
- infrastructure/resources/schema/qbank-v2.schema.json：type 与 payload/answer 约束。
- 新 Payload/Response：QuestionContentData、PracticeQuestionSnapshotMapper、PracticeRuntimeMapper、PracticeSessionService、历史展示等是否需要扩展，逐项写明。
- 类型切换是否能保留旧内容/答案，或是否需要明确拒绝转换：

核心类型子序列化由 QuestionBankV2Codec 从 QuestionTypes 注册。不能只改菜单而遗漏 Schema、作答或历史。

## 5. 验证与文档

- [ ] 默认题、复制题、合法/非法配置与身份不冲突。
- [ ] .qbank 写入回读，答案、资源哈希和逻辑版本符合契约。
- [ ] 编辑保存生效，取消不写文件和资源。
- [ ] 草稿、提交、重开、重做和重复提交符合约定。
- [ ] 评分/待评分符合声明，统计按已提交题数计算。
- [ ] 历史冻结题目/作答/资源可恢复，不依赖后来改写的题库。
- [ ] 本次改动直接相关的规则/文件/界面回归通过；涉及协议、事务或较大跨层变化时再扩大范围，记录实际检查结果。
- [ ] 更新代码导读、内容说明及示例文件。

实际命令、用例与结果链接：

需要额外扩展的媒体/评分/历史协议及未完成边界：
