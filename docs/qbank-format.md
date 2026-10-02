# QBank v2 题库文件格式与内容资源

2026-10-02。本文维护当前题库文件协议、资源与兼容边界；项目结构和新增题型流程见 [新人技术指南](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/new-developer-guide.md)，具体文件职责见 [代码导读](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/code-guide.md)。

## 文件结构

```text
Example.qbank (ZIP)
  manifest.json        schemaVersion=2.0、assetId、title、资源表
  bank.json            stimuli 与 questions（含题目/选项稳定 ID）
  resources/           图片与原生富文本文档字节
```

读取器把 manifest.json 和 bank.json 合并为逻辑 QuestionBank；QuestionBank 保存稳定 assetId、标题、版本、stimuli、questions、resources。Question 通用结构包含 id/type/prompt/payload/answerSpec/scoreSpec/evaluationSpec/analysis/sourceRefs/stimulusRefs。单选和多选共用 ChoicePayload 与 ChoiceAnswerSpec；作文使用 EssayPayload 与 EssayAnswerSpec。标准答案、用户作答和作答历史分别表示。

资源不是直接散落在题干字符串中的文件路径。QBankResource 的 id 映射包内 locator、kind、mediaType 和 sha256，内容引用 resourceId；读取器通过资源表校验并打开包内字节，目录移动不改变引用。

## 物理包与逻辑模型

`manifest.json` 和 `bank.json` 都必须存在，即使没有资源也不能省略。当前只接受 ZIP 题库、`format: "quizforge-question-bank"` 和 `schemaVersion: "2.0"`。普通 JSON 改成 `.qbank` 后缀不能代替 ZIP。

一个无资源的 manifest 示例：

```json
{
  "format": "quizforge-question-bank",
  "schemaVersion": "2.0",
  "assetId": "qb_example",
  "title": "示例题库",
  "resources": []
}
```

`bank.json` 的顶层只有 `stimuli` 和 `questions`，完整题目示例见新人指南。标题、资产 ID 和资源表放在 manifest。读取器把两份 JSON 组合成逻辑 `QuestionBank`；[qbank-v2.schema.json](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/resources/schema/qbank-v2.schema.json) 是这个逻辑模型的读取约束。

资源在 manifest 中有且只有 `id`、`kind`、`mediaType`、`path`、`sha256` 五个字段，均必填；进入逻辑模型后 `path` 映射为 `locator`。资源 ID 采用 `res_` 前缀，哈希为实际资源字节的 SHA-256，以 64 个小写十六进制字符表示。

资源路径必须位于 `resources/` 下，使用 `/` 分隔，不能包含盘符、绝对路径、反斜杠、空路径段、`.` 或 `..`。重复资源 ID、重复路径、未声明的文件条目及缺失资源会被拒绝。资源字节保存在 ZIP 条目中，不塞进 QuestionBank 对象；Canvas 文档内部的内嵌图片是原生文档内容的一部分。

## 三种现有内容

| 内容 | 存储 | 当前界面能力 |
| --- | --- | --- |
| TEXT | bank.json 内的文本 | 纯文本字段与预览 |
| RICH | bank.json 内的结构化节点 | 既有内容渲染、受支持子集转 Canvas、历史读取 |
| DOCUMENT | resourceId + 派生 text 摘要 | resources/ 内 Canvas 原生 JSON，用同一编辑器编辑和预览 |

DOCUMENT 的 text 用于显示/检索/无障碍摘要，原始文档资源是排版内容的来源。Canvas 文档保留支持的布局与内嵌图片，不做另一套富文本模型往返覆盖。当前外部图片 URL 被拒绝，图片需来自题库资源或允许的内嵌数据。

Canvas 的原生文档 JSON 保存 `version`、`data`、`options`，媒体类型为 `application/vnd.quizforge.canvas+json`。当前保存路径为 `resources/res_canvas_<sha256>.canvas.json`。已有 TEXT/RICH 仅在用户明确使用 Canvas 保存时转换；打开工作区不会批量改写旧题库。保存提交暂存资源，取消不修改原内容，仍被其他内容引用的资源继续保留。

RICH 的内容模型仍保留段落、标题、图片、公式、列表、链接等节点；Canvas 转换不支持所有既有复合节点，ContentEditingSupport 会识别不能编辑的情况。当前选择题的可交互持久化文本路径仍有 QuestionText 的内容限制；混合题库可只读渲染受支持的富文本内容，不能据此宣称任意题型已支持所有内容组合。

## 逻辑数据校验

| 校验层 | 负责什么 |
| --- | --- |
| JSON 编解码和 Schema | 字段、类型、显式 kind/type 标识；拒绝重复 JSON 键、无效尾部内容和不符合约束的值 |
| QuestionBankValidator 与题型规则 | 身份唯一性、题型/答案对应关系、内容、分值、来源、共享材料和资源引用 |
| QBankPackageReader | ZIP 条目、包格式、路径、大小限制与实际资源哈希 |

当前题型为单选、多选、作文、完形填空、阅读理解、段落匹配和翻译。单选要求恰好一个正确选项；多选保留至少两个正确选项、至少一个错误选项的既有规则。题目 ID 采用 `q_` 前缀，选项 ID 采用 `opt_` 前缀且在整个题库中唯一。题目可以没有来源引用。

`scoreSpec.defaultMaxScore` 必填、为正的 BigDecimal，普通新题默认 1 分；阅读理解、段落匹配和翻译默认单题 2 分。CLOZE/READING/MATCHING/TRANSLATION 的字段表示单题分值，其余题型表示整题分值。评分细则中的权重为正；存在细则时权重之和必须精确等于 1，空细则允许。题型、标准答案、用户作答和历史快照分别表示。

单选和多选的数据 kind 均为 `CHOICE`，作文为 `ESSAY`；其余题型的 payload/answer kind 分别为 `CLOZE`、`READING`、`MATCHING`、`TRANSLATION`。标识是明确的协议字符串，不使用 Java 类名或枚举序号。新增题型须同步核心登记、桌面登记和 Schema；步骤见 [新人指南](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/new-developer-guide.md) 与 [题型模板](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/templates/new-question-type.md)。

## 可选字段与规范化

读取时，可选字段缺失和显式 `null` 都归一为“未提供”。写入时省略未提供字段，不写 `"field": null`。实际提供的空字符串仍会保留，是否允许为空由相应内容规则判断；必填字段不因此允许 null。

可选字段包括解析、评分细则、评分指导、作文占位和参考答案，以及部分富文本展示元数据。资源表各字段均必填。准确字段清单以 Schema 和模型为准；可选字符串仍需满足字符串类型，不把数值或布尔值隐式转成文本。

相关入口为 [QuestionBankV2Codec](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/QuestionBankV2Codec.java) 和 [Schema 检查脚本](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/test/python/check_qbank_v2_schema.py)。需要单独核对公开 Schema 时，可在安装 Python jsonschema 后执行：

```powershell
python quizforge-infrastructure/src/test/python/check_qbank_v2_schema.py
```

## 资产身份与内容版本

`assetId` 是资产身份，`contentId` 是内容版本，不能互相替代。文件重命名或移动保持资产身份；工作区内重复的题库资产 ID 会阻止可写练习，避免混用历史。

内容版本使用 `qfb:v2:<sha256>`。编解码器对有效逻辑模型递归排序对象键、归一化数值后计算哈希，排除 `assetId`，保留数组顺序。缺失与 null 的可选字段归一后具有相同内容版本。

标题、题目/选项顺序、答案、分值、来源和资源元数据及哈希都参与版本。ZIP 压缩方式、条目时间、物理条目顺序、文件所在工作区路径和界面状态不参与；资源自身的逻辑位置 `locator` 参与。

## 读取、写入和资源生命周期

- [QBankPackageReader](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/QBankPackageReader.java) 的 `read/open` 验证实际资源字节和哈希。`inspect` 验证条目、元数据和逻辑内容，跳过资源解压；扫描得到的元数据版本不代表已经完整校验所有字节。
- `LoadedPackage` 持有 ZIP 句柄。关闭资源流和包后再替换文件，避免 Windows 文件占用。
- [QBankPackageWriter](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/QBankPackageWriter.java) 暂存资源流、计算实际哈希、写临时 ZIP、回读核对，再原子发布；不支持原子替换时失败。
- 应用编辑保存经 [QuestionBankFileEditService](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/service/QuestionBankFileEditService.java)、存储端口和共享文件发布逻辑执行版本检查、备份、扫描回读及必要回滚。不要在 UI 中增加另一条直接覆盖原文件的保存路径。

## 当前包大小限制

默认值来自 [PackageLimits](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/PackageLimits.java)：

| 项目 | 默认上限 |
| --- | --- |
| ZIP 条目数 | 10,000 |
| manifest.json | 4 MiB |
| bank.json | 64 MiB |
| 单个资源 | 256 MiB |
| 总解压内容 | 1 GiB |
| ZIP 中央目录 | 最多 64 MiB，小条目上限时相应缩小 |

大小指解压后的内容，声明大小和实际读取大小都要满足限制。读取器不把 ZIP 解压到工作区目录。以上是包级上限，具体内容格式仍需通过自己的校验。

## 兼容与功能边界

现有 RICH、稳定身份和历史 SQL 继续保留。旧 Markdown 来源的读取兼容独立于题库协议；数据库中的历史 STANDARD_DOCUMENT 文本由适配器映射为当前 REGISTERED_MARKDOWN。当前题库读取器不提供纯 JSON、QBank v1 回退或自动批量迁移。

空题目列表可以表示编辑草稿，文件读取/编辑允许有效空草稿；正常练习校验仍要求有题目。共享材料和富文本的文件表达能力不等于所有练习界面都已支持。

资源枚举有 IMAGE、AUDIO、DOCUMENT；AUDIO 不代表已经实现音频播放和编辑。人工/AI 评分、完整音视频组件、单图片独立内容 kind、新 RESOURCE discriminator 与运行期插件发现尚未实现。主观题提交为 UNSCORED。练习与历史统一展示得分与总分，已提交待评分题不累计得分，保持未评分状态。

## 练习快照分值与旧记录

`.qbank` 的分值仍只在 `scoreSpec.defaultMaxScore` 中保存。打开练习时，`PracticeQuestionSnapshotMapper` 在 SQLite 题目快照的 `correctAnswer` 中保存顶层 `maxScore`；小题题型保留自己的逻辑分值和展示快照。普通选择题提交也保存数值 `score/maxScore`，完形、阅读和排序保留部分得分；作文和翻译的 score 为空，状态为 UNSCORED。

统计总分包含全部计分题目，而非仅已提交题目；得分累计 SUBMITTED 状态的最新已评分 attempt。草稿、重试中和修订中不重复累计旧尝试。旧 ACTIVE 缺少顶层分值时由当前题库补齐元数据，逻辑比较忽略这项新增元数据，不清除提交或草稿；已有归档不重新同步当前题库。旧归档先读取已有逻辑/展示分值或 attempt.maxScore，确实缺少分值则显示“—”，不会用题数当作分数。此变更没有新增数据库列或迁移。

## 完形填空 CLOZE

`type`、`payload.kind`、`answerSpec.kind` 均为 `CLOZE`。`prompt` 支持 TEXT、现有可编辑的 RICH 子集和 DOCUMENT；正文标记 `{{n}}` 对应 `payload.blanks[].number`。重复编号对应同一个稳定 blank ID，小题数组按编号从 1 连续递增排列。小题可以提前新增，正文可暂时没有对应标签；正文标记按编号引用，顺序不限。用 `\{{1}}` 输出字面标签。

每个 blank 包含 `id`（`blank_` 前缀）、`number`、`options`；option 复用稳定 `opt_` ID 和内容结构，限制为 TEXT、固定四个选项。`answerSpec.answers` 每项含 `blankId` 和 `correctOptionId`，每个空必须有且只有一个有效正确选项。

新增小题或标记自动生成四个内容为 `test` 的选项并预选第一个正确答案；作者可通过正文空位或下方横排选项设置标准答案。修改正文保留已有小题 ID、选项 ID 和答案，删除标签不会删除小题。复制题目会重建空位及选项身份。练习作答存为已选选项 ID 列表，每空最多一项，允许未提交的部分草稿。CLOZE 的 `scoreSpec.defaultMaxScore` 表示单题分值；得分为单题分值乘答对的小题数，总分为单题分值乘小题数量。全对为 CORRECT，其余提交为 INCORRECT；部分分保存在 attempt 的 score/maxScore。冻结快照保存 unitScore 和总 maxScore；旧快照缺少 unitScore 时仍按旧总分恢复。原始题干资源不写入用户选择或预览点击层数据。

## 阅读理解 READING

`type`、`payload.kind`、`answerSpec.kind` 均为 `READING`。`prompt` 为一篇文章，支持 TEXT、RICH、DOCUMENT；`payload.items` 是按 `number` 从 1 连续递增的小题数组，至少一道。每项含稳定 `item_` 前缀 `id`、`number`、独立 `prompt` 和四个 TEXT 选项；小题题干也支持三种内容。选项 ID 采用 `opt_` 前缀，在整个题库中唯一。`answerSpec.answers` 每项含 `itemId` 与 `correctOptionId`，每个小题必须有一个属于自己的正确答案。

新建阅读题默认五道小题、四个内容为 `test` 的选项、正确答案 A，单题分值 2。`scoreSpec.defaultMaxScore` 表示单题分值；总分为单题分值乘小题数，得分为单题分值乘答对数。文章编辑不会生成或删除小题；新增、删除小题是独立操作，至少保留一题。删除后只重排显示编号，保留剩余身份与答案；复制会重建题目、小题和选项 ID。`analysis` 是统一的可选答案与解析内容。

练习使用文章在上、小题在下的布局；作答选项变化进入 active 草稿，整道阅读统一确认提交、锁定和重试。每个小题最多选择一个选项，允许保存部分草稿；冻结历史保留文章、小题题干、选项、答案与资源。大纲展示每道小题并进入所属大题，定位对应小题。示例包为 `examples/qbank-v2/reading-first-version.qbank`。

## 段落匹配 MATCHING

`type`、`payload.kind`、`answerSpec.kind` 均为 `MATCHING`。`prompt` 是普通 TEXT/RICH/DOCUMENT 题干，统一存放完整文章和 A–H 选项；不解析 `{{n}}`，也不从正文生成答案槽。`payload.blanks` 恰好八项，每项包含稳定 `blank_` 前缀 `id`、从 1 连续递增的 `number` 和必填布尔 `locked`。`payload.options` 恰好八项，仅保存稳定 `opt_` 前缀 `id` 和唯一 A–H `label`，不存单独选项正文或 fixed 状态。默认锁定第 1、4、6 槽作为三个提示、正确字母顺序 A–H、每个可作答槽 2 分。

`answerSpec.answers` 用 `blankId`、`correctOptionId` 为八个槽设置完整的正确排列，包括已锁定的提示槽；同一字母只能出现一次。编辑时给某个未锁定槽选择其他字母，会与原先持有该字母的未锁定槽交换；已锁定提示不能修改或被交换。每槽可独立锁定、解锁，字母保持不变，保存时必须恰好锁定三个提示槽。锁定三个槽即成为五个待作答位置。

用户作答保存未锁定槽 ID 到选项 ID 的映射，允许空、部分草稿和多个位置选择同一字母；重复选择也按每个位置独立判分。锁定提示不写入草稿，其字母也不能再使用。练习槽位仅显示题号、字母和下拉箭头，没有锁图标或清空按钮，提交前可直接改选。题目大纲只为未锁定位置分配连续题号，跳过三个提示位置；点击仍定位原始槽位。历史大纲使用冻结的锁定状态执行同样规则。得分为正确位置数量乘单槽分，总分为未锁定槽数量乘单槽分；提示不计分。题干和 `analysis` 继续使用共享富文本编辑与资源入口，历史冻结正文、解析、槽位、锁定状态、标准排列与作答映射。复制重新生成题目、槽位和选项身份，保留锁定状态。示例包为 `examples/qbank-v2/matching-first-version.qbank`。


## 翻译 TRANSLATION

`TRANSLATION` 位于 `core/question/type/subjective/translation`，界面位于 `desktop/ui/question/subjective/translation`。在共享富文本题干中用 `{{需要翻译的句子}}` 标记，按正文出现顺序生成小题，无需输入序号；预览隐藏标记、给句子加下划线并显示自动编号。`\{{literal}}` 为字面文本；空、嵌套、缺失结束符的标记会被拒绝。默认五句，每句 2 分；标记数量可以变化，保存至少保留一句。

`TranslationPayload.items` 保存 `TranslationItem(id, number, text)`，`TranslationAnswerSpec.answers` 为每个 `itemId` 保存可空的 `referenceAnswer`（共享 TEXT/RICH/DOCUMENT）。正文编辑保留相同句子出现次数对应的 ID 与参考译文；新增或改写的句子生成新 ID、清空其参考译文，避免译文挂到其他句子。`QuestionBankEditorModel.setTranslationReference` 编辑单句参考译文，复制重新生成小题 ID。

练习按句独立保存 `TranslationPracticeAnswer` 中的 `EssayPracticeAnswer`，可直接输入文本或打开富文本编辑器。整道大题二次确认后提交；缺少译文时提示未完成小题数。提交结果为 `UNSCORED`，分数为空，总分为单句分值乘句数；参考译文与解析在提交后显示。重试清空当前译文并保留已提交记录。

`TranslationQuestionSnapshot` 冻结文章、每句参考译文、解析及资源字节；`translationPresentation` 优先用于历史，`translation` 提供逻辑回退。ACTIVE 草稿与历史均使用原始小题 ID；大纲按句展开、连续编号、按句区分未作答/草稿/待评分并跳转所属大题。没有新增数据库表或迁移。示例包：`examples/qbank-v2/translation-first-version.qbank`。
