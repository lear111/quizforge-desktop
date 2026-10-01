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

当前题型为单选、多选、作文。单选要求恰好一个正确选项；多选保留至少两个正确选项、至少一个错误选项的既有规则。题目 ID 采用 `q_` 前缀，选项 ID 采用 `opt_` 前缀且在整个题库中唯一。题目可以没有来源引用。

`scoreSpec.defaultMaxScore` 必填、为正的 BigDecimal，新题默认 1 分。评分细则中的权重为正；存在细则时权重之和必须精确等于 1，空细则允许。题型、标准答案、用户作答和历史快照分别表示。

单选和多选的数据 kind 均为 `CHOICE`，作文为 `ESSAY`。标识是明确的协议字符串，不使用 Java 类名或枚举序号。新增题型须同步核心登记、桌面登记和 Schema；步骤见 [新人指南](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/new-developer-guide.md) 与 [题型模板](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/templates/new-question-type.md)。

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

资源枚举有 IMAGE、AUDIO、DOCUMENT；AUDIO 不代表已经实现音频播放和编辑。人工/AI 评分、完整音视频组件、单图片独立内容 kind、新 RESOURCE discriminator 与运行期插件发现尚未实现。主观题提交为 UNSCORED，正确率为正确题数除以已提交题数，包含已提交待评分题。
