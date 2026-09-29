# QBank v2 Step 1 完成报告（含 Step 1.1 Optional 规则）

日期：2026-09-29。仓库：`quizforge_V2`。第 1–15 节的 Git 输出和 470 项测试结果记录 Step 1 完成时的提交前状态。Step 1.1 统一 Optional 规则后，经定向测试、完整测试与 diff 检查通过再 checkpoint。没有创建或改写真实 Workspace 数据。

## 1. git status --short

开始前工作区干净，HEAD 为 `4354736`。结束时 HEAD 仍为 `4354736`。以下为当前实际输出：
```text
M README.md
 M examples/step7-practice/Java集合示例.md
 M examples/step7-practice/Java集合练习.qbank
 M examples/step7-practice/README.md
 M quizforge-core/src/main/java/io/quizforge/core/asset/Asset.java
 M quizforge-core/src/main/java/io/quizforge/core/port/PracticeRuntimeProvider.java
 M quizforge-core/src/main/java/io/quizforge/core/port/QuestionBankFileCodec.java
 M quizforge-core/src/main/java/io/quizforge/core/port/QuestionBankFileStorage.java
 M quizforge-core/src/main/java/io/quizforge/core/port/QuestionBankRepository.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PersistentPracticeRuntime.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PracticeQuestionSnapshotMapper.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PracticeRuntimeMapper.java
 M quizforge-core/src/main/java/io/quizforge/core/practice/PracticeSessionService.java
 M quizforge-core/src/main/java/io/quizforge/core/question/FileQuestionBankGenerationService.java
 M quizforge-core/src/main/java/io/quizforge/core/question/Question.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionBank.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankEditorModel.java
 D quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankFile.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankFileEditService.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankPracticeSession.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankReferenceResolver.java
 D quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankV1Assembler.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankValidator.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankView.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionGenerationOutcome.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionGenerationService.java
 M quizforge-core/src/main/java/io/quizforge/core/question/QuestionSourceLinkService.java
 M quizforge-core/src/main/java/io/quizforge/core/workspace/OpenedWorkspaceFile.java
 M quizforge-core/src/main/java/io/quizforge/core/workspace/WorkspaceFileService.java
 M quizforge-core/src/test/java/io/quizforge/core/asset/AssetTest.java
 M quizforge-core/src/test/java/io/quizforge/core/practice/PracticeQuestionSnapshotMapperTest.java
 M quizforge-core/src/test/java/io/quizforge/core/question/QuestionBankEditorModelTest.java
 M quizforge-core/src/test/java/io/quizforge/core/question/QuestionBankPracticeSessionTest.java
 D quizforge-core/src/test/java/io/quizforge/core/question/QuestionBankV1AssemblerTest.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/config/DesktopConfiguration.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/DesktopView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/FileHeader.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/FilePane.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/FilePresentationLoader.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/FileQuestionBankPage.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/FileViewerRouter.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/HistorySourceNavigationAdapter.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/MainWorkspaceView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/QuestionBankEditorView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/QuestionBankPage.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/QuestionBankPracticeView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/QuestionOutlineView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/QuestionPresentationMapper.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/QuestionSourceListView.java
 M quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/QuestionSourceNavigationAdapter.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/HistorySourceNavigationAdapterTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/MainWorkspaceViewTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/QuestionPresentationMapperTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/QuestionSourceNavigationAdapterTest.java
 M quizforge-desktop-app/src/test/java/io/quizforge/desktop/ui/ShellFixture.java
 M quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/FileSystemWorkspaceAssetScanner.java
 M quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/LocalQuestionBankFileStorage.java
 M quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/LocalWorkspaceFileCatalog.java
 M quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/LocalWorkspaceFileOperations.java
 D quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/QuestionBankV1Codec.java
 M quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/SqliteQuestionBankRepository.java
 M quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/SqliteWorkspacePracticeRuntimeProvider.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/ActivePracticeSessionIntegrationTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/FileQuestionBankGenerationIntegrationTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/GenericQuestionBankSourceReferenceTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/MarkdownDocumentRegistrationIntegrationTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/PersistentPracticeRuntimeIntegrationTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/PracticePersistenceIntegrationTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/QuestionBankExampleTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/QuestionBankFileEditIntegrationTest.java
 D quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/QuestionBankV1CodecTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/QuestionGenerationIntegrationTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/WorkspaceAssetFoundationIntegrationTest.java
 M quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/WorkspaceFileExplorerIntegrationTest.java
?? docs/
?? examples/qbank-v2/
?? quizforge-core/src/main/java/io/quizforge/core/question/BlockImageNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/BlockMathNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/BlockNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/ChoiceAnswerSpec.java
?? quizforge-core/src/main/java/io/quizforge/core/question/ChoiceOption.java
?? quizforge-core/src/main/java/io/quizforge/core/question/ChoicePayload.java
?? quizforge-core/src/main/java/io/quizforge/core/question/EvaluationCriterion.java
?? quizforge-core/src/main/java/io/quizforge/core/question/EvaluationSpec.java
?? quizforge-core/src/main/java/io/quizforge/core/question/InlineImageNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/InlineMathNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/InlineNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/InlineTextNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/LineBreakNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/LinkNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/ParagraphNode.java
?? quizforge-core/src/main/java/io/quizforge/core/question/QBankResource.java
?? quizforge-core/src/main/java/io/quizforge/core/question/QuestionAnswerSpec.java
?? quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankV2Assembler.java
?? quizforge-core/src/main/java/io/quizforge/core/question/QuestionContent.java
?? quizforge-core/src/main/java/io/quizforge/core/question/QuestionPayload.java
?? quizforge-core/src/main/java/io/quizforge/core/question/QuestionSourceDocument.java
?? quizforge-core/src/main/java/io/quizforge/core/question/QuestionText.java
?? quizforge-core/src/main/java/io/quizforge/core/question/ResourceKind.java
?? quizforge-core/src/main/java/io/quizforge/core/question/RichContent.java
?? quizforge-core/src/main/java/io/quizforge/core/question/RichDocument.java
?? quizforge-core/src/main/java/io/quizforge/core/question/ScoreSpec.java
?? quizforge-core/src/main/java/io/quizforge/core/question/SourceRef.java
?? quizforge-core/src/main/java/io/quizforge/core/question/Stimulus.java
?? quizforge-core/src/main/java/io/quizforge/core/question/StoredQuestion.java
?? quizforge-core/src/main/java/io/quizforge/core/question/StoredQuestionBank.java
?? quizforge-core/src/main/java/io/quizforge/core/question/TextContent.java
?? quizforge-core/src/test/java/io/quizforge/core/question/QuestionBankV2AssemblerTest.java
?? quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/QuestionBankV2Codec.java
?? quizforge-infrastructure/src/main/resources/schema/
?? quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/QuestionBankV2CodecTest.java
```


## 2. git diff --stat

以下统计包含已跟踪文件的修改与删除；Git 默认不把新增且尚未跟踪的文件计入 diff stat。
```text
README.md                                          |   7 +-
 examples/step7-practice/Java集合示例.md            |   2 +
 examples/step7-practice/Java集合练习.qbank         | 251 ++++++++++++++++-----
 examples/step7-practice/README.md                  |   2 +-
 .../main/java/io/quizforge/core/asset/Asset.java   |   2 +-
 .../core/port/PracticeRuntimeProvider.java         |   5 +-
 .../quizforge/core/port/QuestionBankFileCodec.java |  12 +-
 .../core/port/QuestionBankRepository.java          |   6 +-
 .../core/practice/PersistentPracticeRuntime.java   |   5 +-
 .../practice/PracticeQuestionSnapshotMapper.java   |  31 ++-
 .../core/practice/PracticeRuntimeMapper.java       |   2 +-
 .../core/practice/PracticeSessionService.java      |  24 +-
 .../FileQuestionBankGenerationService.java         |  16 +-
 .../java/io/quizforge/core/question/Question.java  |  25 +-
 .../io/quizforge/core/question/QuestionBank.java   |  27 ++-
 .../core/question/QuestionBankEditorModel.java     | 122 +++++-----
 .../quizforge/core/question/QuestionBankFile.java  |  43 ----
 .../core/question/QuestionBankFileEditService.java |  71 ++----
 .../core/question/QuestionBankPracticeSession.java |  14 +-
 .../question/QuestionBankReferenceResolver.java    |  14 +-
 .../core/question/QuestionBankV1Assembler.java     |  77 -------
 .../core/question/QuestionBankValidator.java       | 177 ++++++++++-----
 .../quizforge/core/question/QuestionBankView.java  |   2 +-
 .../core/question/QuestionGenerationOutcome.java   |   2 +-
 .../core/question/QuestionGenerationService.java   |   6 +-
 .../core/question/QuestionSourceLinkService.java   |   6 +-
 .../core/workspace/OpenedWorkspaceFile.java        |   4 +-
 .../java/io/quizforge/core/asset/AssetTest.java    |   4 +-
 .../PracticeQuestionSnapshotMapperTest.java        |  43 ++--
 .../core/question/QuestionBankEditorModelTest.java |  59 +++--
 .../question/QuestionBankPracticeSessionTest.java  |  26 +--
 .../core/question/QuestionBankV1AssemblerTest.java |  83 -------
 .../desktop/config/DesktopConfiguration.java       |  14 +-
 .../java/io/quizforge/desktop/ui/FileHeader.java   |   3 +
 .../java/io/quizforge/desktop/ui/FilePane.java     |   6 +-
 .../desktop/ui/FilePresentationLoader.java         |  20 +-
 .../quizforge/desktop/ui/FileQuestionBankPage.java |  27 +--
 .../io/quizforge/desktop/ui/FileViewerRouter.java  |  11 +-
 .../desktop/ui/HistorySourceNavigationAdapter.java |  10 +-
 .../desktop/ui/QuestionBankEditorView.java         |  31 ++-
 .../io/quizforge/desktop/ui/QuestionBankPage.java  |  10 +-
 .../desktop/ui/QuestionBankPracticeView.java       |  10 +-
 .../quizforge/desktop/ui/QuestionOutlineView.java  |  10 +-
 .../desktop/ui/QuestionPresentationMapper.java     |   8 +-
 .../desktop/ui/QuestionSourceListView.java         |   7 +-
 .../ui/QuestionSourceNavigationAdapter.java        |  11 +-
 .../ui/HistorySourceNavigationAdapterTest.java     |  31 +--
 .../desktop/ui/MainWorkspaceViewTest.java          | 157 +++++++------
 .../desktop/ui/QuestionPresentationMapperTest.java |  16 +-
 .../ui/QuestionSourceNavigationAdapterTest.java    |  13 +-
 .../java/io/quizforge/desktop/ui/ShellFixture.java |  39 ++--
 .../FileSystemWorkspaceAssetScanner.java           |  10 +-
 .../filesystem/LocalWorkspaceFileCatalog.java      |   4 +-
 .../filesystem/LocalWorkspaceFileOperations.java   |  10 +-
 .../filesystem/QuestionBankV1Codec.java            | 187 ---------------
 .../persistence/SqliteQuestionBankRepository.java  |  24 +-
 .../SqliteWorkspacePracticeRuntimeProvider.java    |   5 +-
 .../ActivePracticeSessionIntegrationTest.java      |  88 +++-----
 .../FileQuestionBankGenerationIntegrationTest.java |  54 ++---
 .../GenericQuestionBankSourceReferenceTest.java    | 100 ++++----
 ...arkdownDocumentRegistrationIntegrationTest.java |   4 +-
 .../PersistentPracticeRuntimeIntegrationTest.java  | 108 ++++-----
 .../PracticePersistenceIntegrationTest.java        |   2 +-
 .../infrastructure/QuestionBankExampleTest.java    |  15 +-
 .../QuestionBankFileEditIntegrationTest.java       |  59 ++---
 .../infrastructure/QuestionBankV1CodecTest.java    | 139 ------------
 .../QuestionGenerationIntegrationTest.java         |  14 +-
 .../WorkspaceAssetFoundationIntegrationTest.java   |  10 +-
 .../WorkspaceFileExplorerIntegrationTest.java      |  23 +-
 69 files changed, 1047 insertions(+), 1413 deletions(-)
```

另外有 38 个未跟踪文件：
```text
docs/qbank-v2-foundation.md
docs/qbank-v2-step1-report.md
examples/qbank-v2/rich-foundation.qbank
quizforge-core/src/main/java/io/quizforge/core/question/BlockImageNode.java
quizforge-core/src/main/java/io/quizforge/core/question/BlockMathNode.java
quizforge-core/src/main/java/io/quizforge/core/question/BlockNode.java
quizforge-core/src/main/java/io/quizforge/core/question/ChoiceAnswerSpec.java
quizforge-core/src/main/java/io/quizforge/core/question/ChoiceOption.java
quizforge-core/src/main/java/io/quizforge/core/question/ChoicePayload.java
quizforge-core/src/main/java/io/quizforge/core/question/EvaluationCriterion.java
quizforge-core/src/main/java/io/quizforge/core/question/EvaluationSpec.java
quizforge-core/src/main/java/io/quizforge/core/question/InlineImageNode.java
quizforge-core/src/main/java/io/quizforge/core/question/InlineMathNode.java
quizforge-core/src/main/java/io/quizforge/core/question/InlineNode.java
quizforge-core/src/main/java/io/quizforge/core/question/InlineTextNode.java
quizforge-core/src/main/java/io/quizforge/core/question/LineBreakNode.java
quizforge-core/src/main/java/io/quizforge/core/question/LinkNode.java
quizforge-core/src/main/java/io/quizforge/core/question/ParagraphNode.java
quizforge-core/src/main/java/io/quizforge/core/question/QBankResource.java
quizforge-core/src/main/java/io/quizforge/core/question/QuestionAnswerSpec.java
quizforge-core/src/main/java/io/quizforge/core/question/QuestionBankV2Assembler.java
quizforge-core/src/main/java/io/quizforge/core/question/QuestionContent.java
quizforge-core/src/main/java/io/quizforge/core/question/QuestionPayload.java
quizforge-core/src/main/java/io/quizforge/core/question/QuestionSourceDocument.java
quizforge-core/src/main/java/io/quizforge/core/question/QuestionText.java
quizforge-core/src/main/java/io/quizforge/core/question/ResourceKind.java
quizforge-core/src/main/java/io/quizforge/core/question/RichContent.java
quizforge-core/src/main/java/io/quizforge/core/question/RichDocument.java
quizforge-core/src/main/java/io/quizforge/core/question/ScoreSpec.java
quizforge-core/src/main/java/io/quizforge/core/question/SourceRef.java
quizforge-core/src/main/java/io/quizforge/core/question/Stimulus.java
quizforge-core/src/main/java/io/quizforge/core/question/StoredQuestion.java
quizforge-core/src/main/java/io/quizforge/core/question/StoredQuestionBank.java
quizforge-core/src/main/java/io/quizforge/core/question/TextContent.java
quizforge-core/src/test/java/io/quizforge/core/question/QuestionBankV2AssemblerTest.java
quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/QuestionBankV2Codec.java
quizforge-infrastructure/src/main/resources/schema/qbank-v2.schema.json
quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/QuestionBankV2CodecTest.java
```


## 3. 已删除的 QBank v1 类型和兼容路径

- `QuestionBankFile`，以及嵌套的 `SourceDocument`、`SourceRef`、`Entry`、`Option`、`Data`。
- `QuestionBankV1Codec`；v1 / v1.1 / v1.2 解析、字段 fallback 和旧多版本写入逻辑。
- `QuestionBankV1Assembler`，生成流程改用 v2 assembler。
- 保存时将旧节点引用转换为锚点的 `anchorEntries` 路径。
- 当前 Practice snapshot writer 的 `sectionId` / `nodeId` 写入分支；当前题库快照仅允许命名锚点。
- 旧 schema 的正向兼容测试已重写为 v2 测试，旧格式拒绝测试保留。

对 `src` 和仓库示例搜索 `QuestionBankV1Codec`、`QuestionBankV1Assembler`、`QuestionBankFile`、`qfb:v1`、`quizforge-question-bank` 均无残留。`QuestionBankFileCodec` / `QuestionBankFileEditService` 等名称表示文件端口/服务，并非旧 DTO。

原有 SQLite 生成流程使用的题库/题目记录改名为 `StoredQuestionBank` / `StoredQuestion`，其数据库职责保留。历史快照读取器仍读取已归档的旧来源地址；它不读取 v1 `.qbank`，不为当前题库提供兼容入口。Markdown 的 `qfd:v1` 也是独立文档格式，未删除。

## 4. 新增/重构的核心 Domain 类型

正式根模型：`QuestionBank`、`Question`。

内容：`QuestionContent`、`TextContent`、`RichContent`、`RichDocument`。

块节点：`BlockNode`、`ParagraphNode`、`BlockImageNode`、`BlockMathNode`。

行内节点：`InlineNode`、`InlineTextNode`、`InlineImageNode`、`InlineMathNode`、`LineBreakNode`、`LinkNode`。

题型：`QuestionPayload`、`ChoicePayload`、`ChoiceOption`、`QuestionAnswerSpec`、`ChoiceAnswerSpec`。

元数据：`Stimulus`、`QBankResource`、`ResourceKind`、`ScoreSpec`、`EvaluationSpec`、`EvaluationCriterion`、`SourceRef`。

`QuestionText` 提供统一 TEXT 读取；`QuestionSourceDocument` 是由引用推导的运行时摘要，不是另一个 JSON 字段。集合均做不可变复制。

## 5. 最终 Domain tree
```text
QuestionBank(assetId, title, schemaVersion, stimuli, questions, resources)
├── Stimulus(id, QuestionContent)
├── QBankResource(id, ResourceKind, mediaType, locator, sha256)
└── Question(id, type, stimulusRefs, prompt, payload, answerSpec,
             scoreSpec, evaluationSpec?, analysis?, sourceRefs)
    ├── QuestionContent
    │   ├── TextContent(text)
    │   └── RichContent(RichDocument)
    │       ├── BlockNode: ParagraphNode, BlockImageNode, BlockMathNode
    │       └── InlineNode: InlineTextNode, InlineImageNode, InlineMathNode,
    │                       LineBreakNode, LinkNode
    ├── QuestionPayload → ChoicePayload(options: ChoiceOption[])
    ├── QuestionAnswerSpec → ChoiceAnswerSpec(correctOptionIds)
    ├── ScoreSpec(defaultMaxScore: BigDecimal)
    ├── EvaluationSpec(criteria, evaluatorGuidance)
    └── SourceRef(documentAssetId, documentContentId, anchorName, occurrence,
                  documentTitle?, sectionTitle?)
```


## 6. 当前选择题实际 JSON

以下两段直接摘自 `examples/step7-practice/Java集合练习.qbank`。实际文件包含 4 题、空 stimuli/resources、每题默认 1 分。顶层已使用 `schemaVersion: "2.0"` 和 `assetId`，没有旧 format/id/sourceDocuments/data/stem 字段。

### SINGLE_CHOICE
```json
{
  "id": "q_demo_arraylist_structure",
  "type": "SINGLE_CHOICE",
  "stimulusRefs": [],
  "prompt": {
    "kind": "TEXT",
    "text": "ArrayList 的底层结构是什么？"
  },
  "payload": {
    "kind": "CHOICE",
    "options": [
      {
        "id": "opt_demo_01_a",
        "content": {
          "kind": "TEXT",
          "text": "可扩容数组"
        }
      },
      {
        "id": "opt_demo_01_b",
        "content": {
          "kind": "TEXT",
          "text": "双向链表"
        }
      },
      {
        "id": "opt_demo_01_c",
        "content": {
          "kind": "TEXT",
          "text": "红黑树"
        }
      }
    ]
  },
  "answerSpec": {
    "kind": "CHOICE",
    "correctOptionIds": [
      "opt_demo_01_a"
    ]
  },
  "scoreSpec": {
    "defaultMaxScore": 1
  },
  "sourceRefs": [
    {
      "documentAssetId": "doc_java_collections_demo",
      "documentContentId": "qfd:v1:49628d5843e7089c6bb68d968ad5198cdfd11de2cfa48de0fe32ab2c8db757c1",
      "anchorName": "section_arraylist_demo",
      "occurrence": 1,
      "documentTitle": "Java 集合示例",
      "sectionTitle": "ArrayList"
    }
  ],
  "analysis": {
    "kind": "TEXT",
    "text": "ArrayList 基于可扩容数组，因此支持按索引快速访问。"
  }
}
```

### MULTIPLE_CHOICE
```json
{
  "id": "q_demo_arraylist_properties",
  "type": "MULTIPLE_CHOICE",
  "stimulusRefs": [],
  "prompt": {
    "kind": "TEXT",
    "text": "下列哪些描述适用于 ArrayList？"
  },
  "payload": {
    "kind": "CHOICE",
    "options": [
      {
        "id": "opt_demo_02_a",
        "content": {
          "kind": "TEXT",
          "text": "保持插入顺序"
        }
      },
      {
        "id": "opt_demo_02_b",
        "content": {
          "kind": "TEXT",
          "text": "允许重复元素"
        }
      },
      {
        "id": "opt_demo_02_c",
        "content": {
          "kind": "TEXT",
          "text": "自动按元素大小排序"
        }
      }
    ]
  },
  "answerSpec": {
    "kind": "CHOICE",
    "correctOptionIds": [
      "opt_demo_02_a",
      "opt_demo_02_b"
    ]
  },
  "scoreSpec": {
    "defaultMaxScore": 1
  },
  "sourceRefs": [
    {
      "documentAssetId": "doc_java_collections_demo",
      "documentContentId": "qfd:v1:49628d5843e7089c6bb68d968ad5198cdfd11de2cfa48de0fe32ab2c8db757c1",
      "anchorName": "section_arraylist_demo",
      "occurrence": 1,
      "documentTitle": "Java 集合示例",
      "sectionTitle": "ArrayList"
    }
  ],
  "analysis": {
    "kind": "TEXT",
    "text": "ArrayList 保持插入顺序、允许重复元素；它不会自动按元素大小排序。"
  }
}
```


## 7. RichContent 实际 JSON 和往返

以下直接摘自 `examples/qbank-v2/rich-foundation.qbank` 的 prompt。段落可表示“文字 + inline formula + 文字 + inline image”，后面也有独立 Block Image / Block Math。
```json
{
  "kind": "RICH",
  "document": {
    "blocks": [
      {
        "type": "PARAGRAPH",
        "children": [
          {
            "type": "TEXT",
            "text": "已知函数 "
          },
          {
            "type": "MATH",
            "tex": "f(x)=x^2"
          },
          {
            "type": "TEXT",
            "text": "，观察 "
          },
          {
            "type": "IMAGE",
            "resourceId": "res_img_01",
            "alt": "函数图像"
          },
          {
            "type": "TEXT",
            "text": "，求 "
          },
          {
            "type": "MATH",
            "tex": "f'(x)"
          },
          {
            "type": "TEXT",
            "text": "。"
          }
        ]
      },
      {
        "type": "IMAGE",
        "resourceId": "res_img_01",
        "alt": "函数图像",
        "caption": "Figure 1"
      },
      {
        "type": "MATH",
        "tex": "\\int_0^1 x^2 dx"
      }
    ]
  }
}
```

`QuestionBankV2CodecTest.richInlineBlockResourceAndStimulusRoundTrip` 覆盖完整节点往返（含 LINK 和 LINE_BREAK）；`QuestionBankExampleTest.richFoundationExampleRoundTripsWithoutResourceFiles` 对实际示例做 parse → write → parse 的模型相等断言。均通过。

## 8. ScoreSpec 和 EvaluationSpec

`ScoreSpec.defaultMaxScore` 的 Java 类型为 `BigDecimal`，validator 要求必填且严格大于 0。新建题目默认 `BigDecimal.ONE`；复制/编辑保留原值。测试覆盖 0.5、1、1.5、2.5、10、1.50 以及长精度小数；往返保留精度和小数表示。0、负数、缺失分值会被拒绝。

Evaluation 是可选题库语义。criterion 的 weight 也是 `BigDecimal`，要求正数、ID 唯一；criteria 非空时总和必须精确等于 1。理由是提供可移植的相对权重，不依赖隐式归一化或浮点容差。空 criteria 合法。没有 AI runtime、实际 awardedScore 或新的编辑控件。

## 9. Resource / Stimulus

共享材料以顶层 `Stimulus(id, content)` 存储，题目通过 `stimulusRefs` 引用，必须能找到对应材料且不可重复。

图片块和行内图片都引用同一个顶层 Resource 表；引用必须存在且 kind 必须为 IMAGE。AUDIO 当前仅提供资源元数据。ID 必须唯一，mediaType 与资源类型匹配，sha256 为 64 位小写十六进制，locator 必须是相对逻辑路径；拒绝 Windows 绝对路径、URI/Base64 和路径穿越。

没有导入/复制资源，也不要求真实文件存在。示例资源元数据：
```json
[
  {
    "id": "res_img_01",
    "kind": "IMAGE",
    "mediaType": "image/png",
    "locator": "resources/res_img_01.png",
    "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  },
  {
    "id": "res_audio_01",
    "kind": "AUDIO",
    "mediaType": "audio/mpeg",
    "locator": "resources/res_audio_01.mp3",
    "sha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
  }
]
```

JSON Schema 文件：`quizforge-infrastructure/src/main/resources/schema/qbank-v2.schema.json`。Java validator 负责结构之外的唯一性、引用关系、答案基数、内容语义及权重和。Schema 与两个实际示例还通过 Python jsonschema 的 Draft 2020-12 校验。

## 10. contentId 升级

`QuestionBankV2Codec.contentId` 对经过验证的逻辑模型计算 SHA-256，前缀为 `qfb:v2:`。

- 排除根 `assetId`，保留身份/修订分离。
- 对象键递归排序；数组顺序保留。
- 哈希中的数字单独规范化 scale，1 与 1.00 得到相同修订；常规 JSON 往返仍保留精度和 scale。
- title、stimuli、questions/顺序、prompt、payload/options、answerSpec、scoreSpec、evaluationSpec、analysis、sourceRefs/来源快照、resource 元数据及 hash 都参与。
- 不读取 filesystem path、mtime、UI state 或 ZIP 容器布局。

测试验证逻辑内容稳定、资产身份不影响哈希、题干/分值/资源 hash/材料/评分标准/题目顺序变化会改变哈希。

## 11. 迁移的 fixture / regression

- `examples/step7-practice/Java集合练习.qbank`：4 道已有单选/多选迁移为 TEXT + ChoicePayload + ChoiceAnswerSpec + ScoreSpec(1)。
- 示例来源 Markdown 增加两处明确的命名锚点，题库记录同步新文档 contentId；示例以外的真实 Workspace 未改写。
- 核心 assembler、editor、practice session、snapshot mapper 的测试数据迁移到 v2。
- 基础设施中的 generation、file edit、asset scan/explorer、来源解析、Active/Persistent Practice fixtures 迁移到 v2；旧版本恢复测试改为 v2 恢复回归。
- Desktop ShellFixture / MainWorkspaceView / presentation fixtures 迁移，来源导航使用实际命名锚点。
- 历史旧来源地址的测试使用固定 archived payload，验证历史读取，不再通过当前题库写入器生成旧格式。
- 新增 Rich 示例、精确分值/评分标准/资源/材料/哈希、编辑元数据保留、RICH 占位与编辑禁用等测试。

MULTIPLE_CHOICE 保留原有“至少两个正确、至少一个错误”的校验；所有 correctOptionIds 必须引用真实且唯一的 option，option ID 继续在题库全局唯一。未删除失败测试来获得 green build，没有加入 sleep/retry workaround。

## 12. Targeted tests

14 个测试类，166 项测试；Failures 0 / Errors 0 / Skipped 0，`BUILD SUCCESS`。

实际命令：
```powershell
mvn -pl quizforge-extension-api,quizforge-core,quizforge-default-extensions,quizforge-infrastructure,quizforge-desktop-app test '-Dtest=PracticeQuestionSnapshotMapperTest,QuestionBankEditorModelTest,QuestionBankPracticeSessionTest,QuestionBankV2AssemblerTest,ActivePracticeSessionIntegrationTest,FileQuestionBankGenerationIntegrationTest,GenericQuestionBankSourceReferenceTest,PersistentPracticeRuntimeIntegrationTest,QuestionBankExampleTest,QuestionBankFileEditIntegrationTest,QuestionBankV2CodecTest,HistorySourceNavigationAdapterTest,QuestionPresentationMapperTest,QuestionSourceNavigationAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dstyle.color=never'
```

日志保留在 `target/qbank-v2-targeted-passed.log`。

完整测试发现文件浏览 fixture 未迁移的旧地址后，修正为正式命名锚点并额外定向运行 `WorkspaceFileExplorerIntegrationTest`：9 项测试通过，零失败/错误/跳过。
```powershell
mvn -pl quizforge-extension-api,quizforge-core,quizforge-default-extensions,quizforge-infrastructure -am test '-Dtest=WorkspaceFileExplorerIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dstyle.color=never'
```

日志 `target/qbank-v2-fixture-targeted.log`。两次定向共 175 项、15 个独立测试类。

最后一次定向验证重新运行严格多态/枚举校验的 `QuestionBankV2CodecTest` 和 6 个受影响的桌面回归：22 项通过，零失败/错误/跳过。日志 `target/qbank-v2-final-targeted.log`。这次与前面的 codec 测试存在重复，共计 197 次定向测试执行。

两条编辑模式的大纲断言同步为此前已提交的 UI 行为（保留大纲）；没有修改布局以迎合测试。来源测试迁移为命名锚点，旧归档测试直接使用冻结的历史 payload。
```powershell
mvn -pl quizforge-desktop-app -am test '-Dtest=QuestionBankV2CodecTest,MainWorkspaceViewTest#questionBankDefaultsToPracticeAndReusesOneModeToggle+questionOutlineDividerTracksSidebarAndRemainsInEditMode+formalMarkdownNamedAnchorCanBecomeSourceButLegacyIdCannot+archivedLegacySourceStillShowsItsStatusWithoutInventingAnAnchorButton+submittingAnswersRevealsFeedbackAndPracticeIsSingleQuestion+richQuestionBankShowsExplicitUnsupportedContentAndCannotEnterTextEditor' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dstyle.color=never'
```


## 13. Full test

为排除重命名后的旧 class，先清理五个 module 的 target，然后运行项目完整命令：
```powershell
mvn test '-Dstyle.color=never'
```

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| quizforge-extension-api | 0 | 0 | 0 | 0 |
| quizforge-core | 42 | 0 | 0 | 0 |
| quizforge-default-extensions | 29 | 0 | 0 | 0 |
| quizforge-infrastructure | 240 | 0 | 0 | 0 |
| quizforge-desktop-app | 159 | 0 | 0 | 0 |
| **总计** | 470 | 0 | 0 | 0 |

`BUILD SUCCESS`。日志 `target/qbank-v2-full.log` 与 Surefire XML 总数一致。

## 14. Practice / History 必要改动

Practice 的题库参数改用 v2；bank ID 读取改为 assetId，题目仍用自身 id。统一通过 `QuestionText` 把现有 TEXT prompt/options/analysis 映射到原有字符串快照。选择、提交、重试、恢复、同步、归档的业务流程不重写。

当前快照写入只允许正式 named anchors，避免当前 writer 继续生产旧 QBank 来源字段。已有 archived 来源 payload 的解码保留；历史展示继续使用保存的旧字符串快照，与当前题库内容无关。

RICH 或共享材料引用不能进入 TEXT-only 练习/编辑页面；显示明确 unsupported 占位。底层快照 mapper 同样拒绝静默丢失共享材料。没有 Rich Renderer / Stimulus UI。

`V4__practice_persistence.sql` 未修改，Practice / History 表结构未改动；真实数据库和归档行未重写。actual awardedScore/runtime 仍是现有规则，本阶段仅存储 Question 的默认满分语义。

## 15. ZIP package 前的边界和待决事项

- Step 1 只验证逻辑资源引用和元数据。Step 2 要增加真实 bytes/entry 存在性、大小、媒体类型与 SHA-256 校验。
- 需要确定 ZIP manifest/entry 布局、重复 entry、逻辑 locator 与 entry 的一一对应、解包路径安全和资源总量限制。
- 确定容器格式化/entry 顺序/压缩方式与 logical contentId 的分离规则；当前 qfb:v2 已按逻辑模型计算，未加入 ZIP 字节布局。
- SourceRef 是 asset identity + revision + anchor，不携带路径；包导入不得悄悄重绑来源或丢失来源标题快照。
- RICH/共享材料 UI 和持久化富内容快照、新的题型、评分 runtime 都是后续独立工作；不会在 ZIP 实施时顺带重构。
- 真实 v1 `.qbank` 不被当前 reader 接受；本轮按要求没有实现迁移，也没有触碰用户真实题库。

Step 1 完成时没有 commit 或 push；Step 1.1 的 checkpoint 按用户授权在验证通过后进行。

Step 1.1 已统一最终规则：缺失字段与显式 null 均读为 absent；canonical writer 必须省略 absent 字段，不输出 null。公开 Schema 是容错 reader schema，可选字段不 required，接受 null 或对应合法类型；writer 输出是其省略 null 的 canonical 子集。domain、重新写出的 JSON 和 qfb:v2 contentId 均不会因 missing/null 两种写法而不同。非 null 值（包括空字符串）保持原值，必填字段不享受该容错规则。

已核对全部 8 个可选字段：analysis、evaluationSpec、evaluatorGuidance、InlineImage.alt、BlockImage.alt/caption、SourceRef.documentTitle/sectionTitle。LINK 和 Resource 没有可选元数据。本轮没有修改 Rich domain、ScoreSpec、Stimulus、Resource、SourceRef 或 Practice/History，也没有改变哈希的逻辑内容范围。

对非 null 字符串字段，Reader 显式拒绝数字、布尔值和数组，避免 Jackson 将 `42` 等错误类型隐式转为字符串而与 Schema 不一致。这仅统一 JSON 类型约束，不修改合法题库值的业务语义。

## 16. Step 1.1 验证与 Checkpoint

本轮只统一 optional/null 规则并完成 Step 1 checkpoint；没有开始 ZIP Step 2。

Step 1.1 修改的 6 个文件：

- `quizforge-infrastructure/src/main/resources/schema/qbank-v2.schema.json`：8 个 optional property 接受 null 或合法类型，不 required；说明 reader schema 与 canonical writer 的关系。
- `quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/QuestionBankV2Codec.java`：记录 missing/null 合同，并禁止 Jackson 将数字/布尔值隐式转换为字符串。原有 NON_NULL 输出和哈希范围不变。
- `quizforge-infrastructure/src/test/java/io/quizforge/infrastructure/QuestionBankV2CodecTest.java`：新增 5 个 JUnit regression，原 16 项增至 21 项。
- `quizforge-infrastructure/src/test/python/check_qbank_v2_schema.py`：公开 Schema 的 Draft 2020-12 校验，含两个实际示例、全部 8 个 optional field 的 61 个正反用例。
- `docs/qbank-v2-foundation.md`：正式 Optional JSON 合同与校验命令。
- `docs/qbank-v2-step1-report.md`：将此前 null 差异改为最终结论，并记录本轮结果。

新增的 JUnit 测试：

1. `optionalQuestionFieldsNormalizeNullAndMissing`：单选/多选的 analysis/evaluationSpec 缺失、独立 null、同时 null，归一化后 domain、writer JSON 和 contentId 相同。
2. `optionalNestedMetadataNormalizeNullAndMissing`：RICH 题干/选项/解析/材料、嵌套 LINK 中的图片 alt、块图片 alt/caption、evaluatorGuidance 和来源标题快照。
3. `optionalValuesIncludingBlankMetadataArePreserved`：有值及空字符串按原值往返，不与 absent 混同。
4. `optionalNonNullValuesStillRequireTheirDeclaredTypes`：拒绝数组、整数、小数和布尔值等错误类型。
5. `nullDoesNotMakeRequiredPropertiesOptional`：required 内容、分值、资源、链接、材料和来源地址不因 optional 容错而接受 null。

### 定向结果

QuestionBankV2CodecTest 21 项 + QuestionBankExampleTest 3 项 + QuestionBankFileEditIntegrationTest 6 项：**30 项全部通过**，零失败、零错误、零跳过。

```powershell
mvn -pl quizforge-infrastructure -am test '-Dtest=QuestionBankV2CodecTest,QuestionBankExampleTest,QuestionBankFileEditIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dstyle.color=never'
python quizforge-infrastructure/src/test/python/check_qbank_v2_schema.py
```

Schema：**61 个用例 + 2 个实际示例通过**。日志为 `target/qbank-v2-step1.1-targeted.log`、`target/qbank-v2-step1.1-schema.log`。

### 最终完整测试

```powershell
mvn test '-Dstyle.color=never'
```

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| quizforge-extension-api | 0 | 0 | 0 | 0 |
| quizforge-core | 42 | 0 | 0 | 0 |
| quizforge-default-extensions | 29 | 0 | 0 | 0 |
| quizforge-infrastructure | 245 | 0 | 0 | 0 |
| quizforge-desktop-app | 159 | 0 | 0 | 0 |
| **总计** | 475 | 0 | 0 | 0 |

**BUILD SUCCESS**。日志 `target/qbank-v2-step1.1-full.log` 与五个模块的 Surefire XML 总数一致。

提交前检查：`git diff --check` 通过；已复查 diff/stat/status。未发现密钥/API Key、真实 Workspace 数据、V4 migration 或无关 UI/CSS 修改。Step 1 必要的 JavaFX 模型适配与 RICH 占位处理保留，没有 UI 重构。当前源码和示例没有 QBank v1 reader/DTO/fallback；独立的已归档 Practice source payload 读取不构成 v1 `.qbank` 兼容入口。

Checkpoint 使用用户指定消息 `feat: establish qbank v2 core format`，在完整验证通过后提交并推送当前 main。实际 commit hash、push 结果和最终 status 在本轮回复中报告。
