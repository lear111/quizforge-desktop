# QuizForge V2 开发代码导读

2026-10-02 当前版本。目录与职责按当前生产源码整理；阅读路线与各手册用途见 [文档导航](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/README.md)。

## 从哪里开始

应用入口是 DesktopApplication；Spring 配置拆为适配器、核心服务与桌面装配。文件入口是 FilePane，MarkdownFileView 与 QuestionBankFileView 分别维护状态。题目数据看 core/question/model，题型规则看 type，题目编辑看 service，作答事实与历史看 practice。

| 层 | 职责 | 依赖方向 |
| --- | --- | --- |
| quizforge-core | 模型、规则、流程、外部能力端口 | 不依赖其他项目模块 |
| quizforge-infrastructure | 文件/ZIP、SQLite、DPAPI、DeepSeek HTTP | core |
| quizforge-desktop-app | JavaFX 交互、Canvas 组件、Spring 装配 | core、infrastructure |

已移除 Material、独立标准文档业务与 AI 生成业务，保留 AI 基础接入、手工编辑、命名来源锚点、练习和历史。既有旧格式读取与数据库迁移继续保留；这些兼容实现不代表旧生成业务仍存在。

## 关键业务路径

1. 编辑：FilePane → 对应文件控制器 → 编辑模型/字段 → 核心保存服务 → 分阶段文件存储 → 安全发布 → 扫描派生索引。
2. 练习：题库控制器 → PracticeRuntimeProvider（先拒绝重复身份）→ PersistentPracticeRuntime → PracticeSessionService → 一个 SQLite 事务。
3. 历史：PracticeHistoryService → 冻结题目/作答/资源 → 详情界面；当前题库修改不会反向改写归档记录。
4. 来源：注册 Markdown 身份 → 用户命名锚点 → 题目 SourceRef → 当前文件定位；缺失/孤立/版本变化显式展示。
5. AI 接入：AiSettingsService/AiConnectionService → 配置/凭据端口 → DeepSeekAiProvider；没有文档或题目生成页面。

正确率 = 正确题数 / 已提交题数；已提交待评分作文进入分母，草稿不进入分母，零提交显示“—”。历史使用相同统计入口。

## 逐文件职责

### quizforge-core / io.quizforge.core

| 文件 | 作用 |
| --- | --- |
| [ErrorCode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ErrorCode.java) | 统一业务错误码，供核心流程与适配器表达失败原因。 |
| [QuizForgeException.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/QuizForgeException.java) | 携带业务错误码、消息和原因的统一异常。 |

### quizforge-core / io.quizforge.core.ai

| 文件 | 作用 |
| --- | --- |
| [AiConnectionService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiConnectionService.java) | 调用当前配置的 AI 提供者，发送简短请求测试连接是否可用 |
| [AiFailureKind.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiFailureKind.java) | 五种提供者层失败分类：鉴权、限流、超时、不可用、非法响应；由 core/AiProviderErrors 映射业务错误 |
| [AiGenerationOptions.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiGenerationOptions.java) | 通用请求选项：有限温度、JSON 输出模式与 maxTokens；连接测试也使用。 |
| [AiMessage.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiMessage.java) | 表达消息角色与文本，两者均非 null；不保存或管理 API Key |
| [AiProviderConfig.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiProviderConfig.java) | 定义一份 AI 服务配置，记录提供者、服务地址、模型及凭据引用等信息 |
| [AiProviderErrors.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiProviderErrors.java) | 将提供者的鉴权失败、超时、限流等错误转换为项目统一的业务异常 |
| [AiProviderException.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiProviderException.java) | 携带失败分类、消息及可选 cause 的通用异常；具体提供者抛出，连接服务转换 |
| [AiRequest.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiRequest.java) | 保存消息及请求选项；复制消息列表，拒绝空列表及空选项 |
| [AiResponse.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiResponse.java) | 保存文本返回内容，拒绝 null；非空白校验当前在具体 HTTP 提供者完成 |
| [AiRole.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiRole.java) | SYSTEM/USER/ASSISTANT 通用消息角色，HTTP 适配器转为协议字符串 |
| [AiSettingsService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/ai/AiSettingsService.java) | 读取、校验和保存 AI 配置，协调 API Key 的保存、检查和删除 |

### quizforge-core / io.quizforge.core.asset

| 文件 | 作用 |
| --- | --- |
| [Asset.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/asset/Asset.java) | 描述单个资产的 ID、类型、工作区相对路径、标题、内容版本标识和格式版本，并校验这些基本信息 |
| [AssetType.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/asset/AssetType.java) | 定义 REGISTERED_MARKDOWN、QUESTION_BANK 两类资产；旧数据库文本由适配器兼容。 |
| [WorkspaceScanIssue.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/asset/WorkspaceScanIssue.java) | 描述扫描发现的问题，包括问题代码、相关文件的相对路径和说明 |
| [WorkspaceScanResult.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/asset/WorkspaceScanResult.java) | 汇总扫描结果，包含识别出的资产和发现的问题 |

### quizforge-core / io.quizforge.core.document

| 文件 | 作用 |
| --- | --- |
| [MarkdownFileEditService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/MarkdownFileEditService.java) | 保存已有 Markdown 的编辑结果；检查文件是否被外部修改，分阶段发布文件并刷新索引，失败时回滚。当前编辑界面仍使用它 |
| [package-info.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/package-info.java) | 描述此包的实际职责。 |

### quizforge-core / io.quizforge.core.document.navigation

| 文件 | 作用 |
| --- | --- |
| [MarkdownNavigationLinkCodec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/navigation/MarkdownNavigationLinkCodec.java) | 将导航地址组织成带显示文字的 Markdown 链接，并解析这类链接 |
| [QuizForgeNavigationLink.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/navigation/QuizForgeNavigationLink.java) | 描述导航目标：资产本身、标题或命名锚点；使用资产 ID 定位当前内容 |
| [QuizForgeNavigationLinkCodec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/navigation/QuizForgeNavigationLinkCodec.java) | 编码和解析 quizforge://asset 导航链接，处理标题/锚点名称及同名出现顺序 |

### quizforge-core / io.quizforge.core.document.registered

| 文件 | 作用 |
| --- | --- |
| [AddressableMarkdownBlock.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/registered/AddressableMarkdownBlock.java) | 描述旧节点 ID 对应的正文块、类型、显示文本与源码范围；用于读取旧引用。 |
| [MarkdownBlockType.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/registered/MarkdownBlockType.java) | 定义标题、段落、列表、引用、代码块等 Markdown 内容块类型 |
| [MarkdownSourceRange.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/registered/MarkdownSourceRange.java) | 描述内容块在 Markdown 正文中的行列范围，供定位和插入锚点使用 |
| [NamedMarkdownAnchor.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/registered/NamedMarkdownAnchor.java) | 描述命名锚点、同名出现顺序和绑定的内容块位置，并表示未绑定内容块的孤立锚点 |
| [QuizForgeReference.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/registered/QuizForgeReference.java) | 表示整篇文档或命名锚点的来源引用；锚点引用携带引用时的文档内容版本 |
| [QuizForgeReferenceCodec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/registered/QuizForgeReferenceCodec.java) | 将来源引用编码成 quizforge://document 链接，以及解析复制/粘贴得到的引用 |
| [RegisteredMarkdownDocument.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/document/registered/RegisteredMarkdownDocument.java) | 已注册 Markdown 快照：稳定身份、版本、路径、正文块和命名锚点。 |

### quizforge-core / io.quizforge.core.port

| 文件 | 作用 |
| --- | --- |
| [AiProvider.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/AiProvider.java) | 供应商无关的 AI 调用端口：提供者 ID、请求与响应；连接测试使用该契约。 |
| [AiProviderConfigRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/AiProviderConfigRepository.java) | 读取和保存默认 AI 配置；具体持久化由基础设施实现 |
| [AiProviderResolver.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/AiProviderResolver.java) | 根据当前配置和凭据取得可调用的 AiProvider；连接测试仍使用它 |
| [AssetIndexRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/AssetIndexRepository.java) | 按工作区查找/列出资产，并用一次完整扫描原子替换派生索引；存储的是资产定位信息 |
| [CredentialStore.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/CredentialStore.java) | 按凭据引用保存、读取、检查和删除密钥；供 AI 设置和连接使用 |
| [DocumentNodeLookup.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/DocumentNodeLookup.java) | 从当前磁盘文档查找命名锚点与旧节点，返回存在、孤立和版本状态。 |
| [FileDocumentStorage.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/FileDocumentStorage.java) | Markdown 读取与分阶段替换契约；版本冲突时保留暂存编辑，发布后可回滚或完成。 |
| [MarkdownDocumentRegistration.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/MarkdownDocumentRegistration.java) | 检查注册状态、写入文档身份、按用户请求创建命名锚点；注册本身不自动添加节点 ID 或来源锚点 |
| [package-info.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/package-info.java) | 描述此包的实际职责。 |
| [PracticeRuntimeProvider.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/PracticeRuntimeProvider.java) | 为指定工作区和题库取得持久化练习运行对象及历史服务；隐藏具体数据库装配细节 |
| [PracticeSessionQuestionRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/PracticeSessionQuestionRepository.java) | 保存会话内题目快照、草稿、状态和顺序；供练习服务同步当前题库及恢复练习使用 |
| [PracticeSessionRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/PracticeSessionRepository.java) | 保存练习会话、查找当前/归档会话、更新位置与题库版本、归档和删除历史会话 |
| [PracticeTransaction.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/PracticeTransaction.java) | 在一次事务回调中提供上述三个练习仓储，失败时回滚相关写入；避免向核心层暴露 JDBC |
| [QuestionAttemptRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/QuestionAttemptRepository.java) | 追加和查询已提交的作答记录；不提供单条作答事实的修改或删除接口 |
| [QuestionBankFileCodec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/QuestionBankFileCodec.java) | 对题库的逻辑 JSON 进行编码、解析、校验和内容版本计算；接口本身不读取物理 .qbank 文件 |
| [QuestionBankFileStorage.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/QuestionBankFileStorage.java) | 题库读取与分阶段替换契约；允许传入新增资源和预期版本，冲突保留恢复副本。 |
| [QuestionResourceInput.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/QuestionResourceInput.java) | 打开题库中图片等资源的输入流；资源不存在返回 null，NONE 表示无资源来源。题库保存和练习快照仍使用它 |
| [WorkspaceAssetScanner.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/WorkspaceAssetScanner.java) | 扫描工作区并返回资产和扫描问题；资产扫描与索引刷新继续保留 |
| [WorkspaceDirectoryStorage.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/WorkspaceDirectoryStorage.java) | 检查既有工作区、定位根目录、创建或补齐工作区结构，以及失败时清理空目录 |
| [WorkspaceFileCatalog.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/WorkspaceFileCatalog.java) | 列出和检查文件、读取文本或题库、打开题库资源流 |
| [WorkspaceFileOperations.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/WorkspaceFileOperations.java) | 创建文件夹/文件、重命名、删除和解析绝对路径，供工作区文件操作使用 |
| [WorkspaceRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/port/WorkspaceRepository.java) | 保存、列出和查找工作区记录 |

### quizforge-core / io.quizforge.core.practice

| 文件 | 作用 |
| --- | --- |
| [ActivePracticeSnapshot.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/ActivePracticeSnapshot.java) | 恢复活动练习所需的会话、单题记录和作答历史集合；供运行对象和统计使用 |
| [EssayPracticeAnswer.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/EssayPracticeAnswer.java) | 表示作文答案的文本与可选 Canvas 原生文档，校验文档长度并在 TEXT/CANVAS_DOCUMENT 负载间转换；只处理答案保存和读取 |
| [EssayQuestionSnapshot.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/EssayQuestionSnapshot.java) | 保存作文题干、参考答案、解析、满分和评分细则，以及所用资源信息和 Base64 内容；捕获资源时核对 SHA-256，供归档历史在资源已改变后展示原内容 |
| [PersistentPracticeRuntime.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PersistentPracticeRuntime.java) | 供界面调用的持久化练习运行对象；每次操作先调用服务保存，再用返回快照恢复同一个 QuestionBankPracticeSession，供题目和大纲共用；还提供作文答案和统计读取 |
| [PracticeHistoryDetail.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticeHistoryDetail.java) | 历史详情结果：题库标题/版本、时间、统计、题目快照、最终状态、草稿和全部作答记录；还携带作文展示所需的结构化快照 |
| [PracticeHistoryEntry.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticeHistoryEntry.java) | 历史列表中单轮练习的摘要：会话 ID、开始/归档时间和统计 |
| [PracticeHistoryService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticeHistoryService.java) | 按题库身份查询归档轮次、读取历史详情、删除指定归档轮次；详情来自保存的题目/作答快照，并校验轮次确实归档且属于该题库 |
| [PracticePayload.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticePayload.java) | 深度冻结历史/作答的基础结构；枚举以稳定名称保存，数值规范化为 BigDecimal。 |
| [PracticeQuestionSnapshotMapper.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticeQuestionSnapshotMapper.java) | 将当前题目映射为持久化快照；选择题保存文本与正确选项，作文保存结构化内容及展示资源，来源保存命名锚点。logical 方法剥离作文展示附加数据用于语义比较 |
| [PracticeRuntimeMapper.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticeRuntimeMapper.java) | 核对持久化快照与当前运行题库是否匹配，恢复当前题目、草稿、提交状态、结果和总结页；供 PersistentPracticeRuntime 使用 |
| [PracticeSession.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticeSession.java) | 一轮练习的会话记录：题库身份/版本、标题快照、活动或归档状态、当前题目/总结页和时间 |
| [PracticeSessionQuestion.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticeSessionQuestion.java) | 一轮练习内的单题记录：题目顺序、题干/选项/答案/解析/来源快照、当前状态、草稿和时间；状态包含未作答、草稿、已提交、重做中和修改中 |
| [PracticeSessionService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticeSessionService.java) | 组织打开/恢复练习、同步题库修订、保存草稿、提交、重做、导航和重新开始；通过 PracticeTransaction 将相关写入放在同一事务内，并校验活动状态、内容版本和当前题目 |
| [PracticeSummary.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/PracticeSummary.java) | 按题目当前状态计算统计；正确率为正确题数/已提交题数，包含已提交未评分作文；零提交时为空。 |
| [QuestionAttempt.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/QuestionAttempt.java) | 一次已提交作答的不可变记录：作答序号、首次/修改/重做模式、答案、对错或未评分结果、可选分数和提交时间 |
| [QuestionBankPracticeSession.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/practice/QuestionBankPracticeSession.java) | 管理当前题库的内存选择、位置、提交与恢复状态；题型规则由 QuestionTypes 分派。 |

### quizforge-core / io.quizforge.core.question

| 文件 | 作用 |
| --- | --- |
| [package-info.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/package-info.java) | 描述此包的实际职责。 |

### quizforge-core / io.quizforge.core.question.content

| 文件 | 作用 |
| --- | --- |
| [BlockImageNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/BlockImageNode.java) | 块级图片引用及说明、尺寸比例和对齐配置；尺寸比例限定为 25/50/75/100 |
| [BlockMathNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/BlockMathNode.java) | 块级公式的 TeX 内容 |
| [BlockNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/BlockNode.java) | 块级内容节点接口，限定允许的段落、标题、列表、引用、图片和公式 |
| [BlockQuoteNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/BlockQuoteNode.java) | 块引用，包含内部块级内容 |
| [BulletListNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/BulletListNode.java) | 无序列表，包含列表项集合 |
| [DocumentContent.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/DocumentContent.java) | 引用 Canvas 原生文档资源，并携带派生的纯文本摘要；对应资源类型为 DOCUMENT |
| [HeadingNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/HeadingNode.java) | 带行内内容及可选对齐方式的标题，目前级别限定为 1–3 |
| [InlineImageNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/InlineImageNode.java) | 行内图片资源引用及替代文本 |
| [InlineMathNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/InlineMathNode.java) | 行内公式的 TeX 内容 |
| [InlineNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/InlineNode.java) | 行内内容节点接口，限定文字、图片、公式、换行和链接 |
| [InlineTextNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/InlineTextNode.java) | 行内文字及粗体、斜体等格式标记 |
| [LineBreakNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/LineBreakNode.java) | 行内换行节点 |
| [LinkNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/LinkNode.java) | 链接地址与显示内容 |
| [ListItemNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/ListItemNode.java) | 单个列表项，其内部可包含多个块级节点 |
| [OrderedListNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/OrderedListNode.java) | 有序列表，包含起始序号和列表项 |
| [ParagraphNode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/ParagraphNode.java) | 带行内内容及可选对齐方式的段落 |
| [QuestionContent.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/QuestionContent.java) | 题干、选项、解析和参考答案共用的内容接口，允许 TEXT、RICH 和 DOCUMENT 三种表示 |
| [QuestionContentData.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/QuestionContentData.java) | 内容与基础结构互转、提取文本和资源引用；样式与对齐进入结构化快照。 |
| [QuestionContentNormalizer.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/QuestionContentNormalizer.java) | 将没有格式和对齐的普通富文本段落归一为 TEXT；保留真正的结构化内容和文档资源 |
| [RichContent.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/RichContent.java) | 包含 RichDocument 的结构化富文本内容；当前格式解析、内容转换和历史仍使用它 |
| [RichDocument.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/RichDocument.java) | 由块级节点组成的富文本文档 |
| [TextAlignment.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/TextAlignment.java) | 左、中、右对齐方式 |
| [TextContent.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/TextContent.java) | 纯文本内容 |
| [TextMark.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/content/TextMark.java) | 粗体、斜体、下划线和删除线格式 |

### quizforge-core / io.quizforge.core.question.model

| 文件 | 作用 |
| --- | --- |
| [EvaluationCriterion.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/model/EvaluationCriterion.java) | 一个评分维度的 ID、说明和权重；格式校验要求已配置维度的权重之和为 1 |
| [EvaluationSpec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/model/EvaluationSpec.java) | 保存评分维度列表及评分细则文字；题库和作文界面仍使用这些数据 |
| [Question.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/model/Question.java) | 当前单题模型：题型、共享材料引用、题干、题型内容、答案配置、分值、评分细则、解析和来源引用 |
| [QuestionAnswerSpec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/model/QuestionAnswerSpec.java) | 答案配置的统一接口，目前允许选择题答案和作文参考答案 |
| [QuestionBank.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/model/QuestionBank.java) | 当前可携带题库的逻辑模型：资产 ID、标题、格式版本、共享材料、题目和资源；来源文档摘要由题目引用推导 |
| [QuestionPayload.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/model/QuestionPayload.java) | 题型内容的统一接口，目前允许选择题和作文两种实现 |
| [ScoreSpec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/model/ScoreSpec.java) | 题目满分配置，默认值为 1；不代表作文已经具备自动评分 |
| [Stimulus.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/model/Stimulus.java) | 定义供多道题引用的共享材料及其内容；与旧 core/material 的独立材料导入模型不同 |

### quizforge-core / io.quizforge.core.question.resource

| 文件 | 作用 |
| --- | --- |
| [QBankResource.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/resource/QBankResource.java) | 保存资源 ID、类型、媒体类型、包内定位地址及 SHA-256；资源二进制由存储和资源输入接口处理 |
| [ResourceKind.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/resource/ResourceKind.java) | 定义 IMAGE、AUDIO、DOCUMENT 资源类型；现有类型声明不等于所有界面都已支持对应内容 |

### quizforge-core / io.quizforge.core.question.service

| 文件 | 作用 |
| --- | --- |
| [QuestionBankEditorModel.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/service/QuestionBankEditorModel.java) | 维护尚未保存的编辑状态：改标题、题干、题型、选项、参考答案、评分细则和来源，增删/复制题目及清理不再引用的资源；新题目和选项 ID 在本地生成 |
| [QuestionBankFileEditService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/service/QuestionBankFileEditService.java) | 检查预期内容版本、校验题目与来源、发布题库、刷新索引；失败保留文件与编辑。 |
| [QuestionBankValidator.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/service/QuestionBankValidator.java) | 校验当前 QBank v2 的身份、题型、内容、答案、资源引用、命名来源锚点及评分配置；包含空题库草稿的单独校验入口，继续保留 |

### quizforge-core / io.quizforge.core.question.source

| 文件 | 作用 |
| --- | --- |
| [QuestionBankReferenceResolver.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/source/QuestionBankReferenceResolver.java) | 按稳定文档身份解析来源，报告匹配、版本变化、缺失与孤立；兼容旧来源地址。 |
| [QuestionSourceAddress.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/source/QuestionSourceAddress.java) | 定义命名锚点与旧小节/节点地址；当前写入命名锚点，保留旧引用读取。 |
| [QuestionSourceDocument.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/source/QuestionSourceDocument.java) | 从题目来源推导出的文档摘要，供运行时展示；不作为另一套题库文件字段 |
| [QuestionSourceLinkService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/source/QuestionSourceLinkService.java) | 将用户粘贴的 Source 链接转换为带当前内容版本的题目来源，验证文档与命名锚点可用，并提供显示名称 |
| [SourceRef.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/source/SourceRef.java) | 记录来源文档身份、引用时的内容版本、目标地址和显示文字；当前题库使用命名锚点，历史兼容仍包含旧地址 |

### quizforge-core / io.quizforge.core.question.type

| 文件 | 作用 |
| --- | --- |
| [QuestionTypeDefinition.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/QuestionTypeDefinition.java) | 单一题型的数据与规则契约：类别、JSON 类型名、默认题目、复制、校验与选择题判分。 |
| [QuestionTypes.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/QuestionTypes.java) | 核心题型登记表，提供数据/规则查找及选择题、作文与单选行为判断。 |
| [QuestionValidationContext.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/QuestionValidationContext.java) | 把通用内容校验和全题库选项 ID 集合交给题型规则，避免重复通用校验。 |

### quizforge-core / io.quizforge.core.question.type.objective.choice

| 文件 | 作用 |
| --- | --- |
| [ChoiceAnswerSpec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/objective/choice/ChoiceAnswerSpec.java) | 保存正确选项 ID 列表，供校验和对错判断使用 |
| [ChoiceOption.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/objective/choice/ChoiceOption.java) | 单个选项的稳定 ID 与内容；不同于旧数据库模型 QuestionOption |
| [ChoicePayload.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/objective/choice/ChoicePayload.java) | 保存选择题的选项集合 |
| [SingleChoiceQuestionType.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/objective/choice/SingleChoiceQuestionType.java) | 单选独立规则入口：默认两个选项、一个正确答案，校验恰好一个正确选项，使用单选交互与集合匹配判分。 |
| [MultipleChoiceQuestionType.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/objective/choice/MultipleChoiceQuestionType.java) | 多选独立规则入口：默认三个选项、两个正确答案，保留至少两个正确选项及一个错误选项的既有校验，完整集合匹配判分。 |
| [ChoiceQuestionSupport.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/objective/choice/ChoiceQuestionSupport.java) | 包内共用实现：构建选项、复制时重建题目/选项 ID、校验选项内容与答案引用；不决定单选/多选的数量或评分政策。 |
| [QuestionText.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/objective/choice/QuestionText.java) | 现有纯文本选择题编辑/练习适配器；明确检查是否支持题目，不能用它读取任意 RICH 或 DOCUMENT 内容 |

### quizforge-core / io.quizforge.core.question.type.subjective.essay

| 文件 | 作用 |
| --- | --- |
| [EssayAnswerSpec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/subjective/essay/EssayAnswerSpec.java) | 保存可选的作文参考答案内容 |
| [EssayPayload.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/subjective/essay/EssayPayload.java) | 作文作答区的提示文字配置；用户实际答案由 practice 包保存 |
| [EssayQuestionType.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/question/type/subjective/essay/EssayQuestionType.java) | 作文默认题目、独立复制与专有配置校验；提交后维持待评分流程。 |

### quizforge-core / io.quizforge.core.workspace

| 文件 | 作用 |
| --- | --- |
| [package-info.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/package-info.java) | 描述此包的实际职责。 |

### quizforge-core / io.quizforge.core.workspace.model

| 文件 | 作用 |
| --- | --- |
| [OpenedWorkspaceFile.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/model/OpenedWorkspaceFile.java) | 打开结果：文件条目、Markdown 源文本或题库模型，以及题库逻辑版本 |
| [Workspace.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/model/Workspace.java) | 工作区模型：UUID、名称、创建/更新时间、根目录；rootPath 为空时使用默认目录定位路径，四参数构造用于兼容这种表示 |
| [WorkspaceFileEntry.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/model/WorkspaceFileEntry.java) | 文件树条目：工作区相对路径、名称、分类及可选身份、版本、标题和问题；校验相对路径基本形状并提供 parentPath。实际访问边界由文件系统实现再次检查 |
| [WorkspaceFileKind.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/model/WorkspaceFileKind.java) | 区分目录、普通/已注册/无效 Markdown、有效/无效题库和其他文件。 |
| [WorkspaceFileTree.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/model/WorkspaceFileTree.java) | 文件树数据快照及索引刷新失败提示；集合防御复制，childrenOf 按目录优先、名称和路径排序。它与 desktop/ui 的同名 JavaFX 控件分别承担数据和交互职责 |
| [WorkspaceFileType.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/model/WorkspaceFileType.java) | 可新建文件的类型及扩展名：Markdown .md、题库 .qbank；与打开时的有效/无效分类不同 |
| [WorkspaceFolder.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/model/WorkspaceFolder.java) | 从既有文件夹的 .quizforge/workspace.json 读取出的身份、名称和规范化位置，供注册已有工作区使用 |
| [WorkspaceId.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/model/WorkspaceId.java) | 工作区稳定 UUID 的创建、解析与非空检查；不同于题库 assetId 和单题 ID |

### quizforge-core / io.quizforge.core.workspace.service

| 文件 | 作用 |
| --- | --- |
| [WorkspaceFileService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/service/WorkspaceFileService.java) | 创建文件/目录、重命名、删除、取得绝对路径、刷新文件树及打开文件的业务入口；操作前核对目标工作区，实际磁盘操作委托端口实现 |
| [WorkspaceService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-core/src/main/java/io/quizforge/core/workspace/service/WorkspaceService.java) | 创建/登记/打开工作区并协调失败清理；列出登记记录不创建目录，打开时检查真实路径和身份。 |

### quizforge-desktop-app / io.quizforge.desktop.bootstrap

| 文件 | 作用 |
| --- | --- |
| [DesktopApplication.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/bootstrap/DesktopApplication.java) | JavaFX 启动入口，建立 Spring 上下文并创建桌面窗口，结束时关闭上下文。 |

### quizforge-desktop-app / io.quizforge.desktop.config

| 文件 | 作用 |
| --- | --- |
| [DesktopConfiguration.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/config/DesktopConfiguration.java) | 桌面组合入口，导入适配器/服务配置并组装 DesktopView。 |
| [InfrastructureConfiguration.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/config/InfrastructureConfiguration.java) | 集中装配文件、SQLite、DPAPI、题库编解码和 DeepSeek 等具体适配器。 |
| [ServiceConfiguration.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/config/ServiceConfiguration.java) | 集中装配核心业务协调服务、来源服务和时钟。 |

### quizforge-desktop-app / io.quizforge.desktop.dev

| 文件 | 作用 |
| --- | --- |
| [DevelopmentRefreshable.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/dev/DevelopmentRefreshable.java) | 组件显式声明开发刷新能力与当前是否允许刷新，取代私有方法反射。 |
| [DevelopmentUiReloader.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/dev/DevelopmentUiReloader.java) | 在 JavaFX 线程按刷新接口重绘组件，保存输入/焦点/滚动等状态并避开模态编辑器。 |
| [LiveCssReloader.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/dev/LiveCssReloader.java) | 开发模式监听样式变化并更新 Scene；返回关闭监听器的清理动作。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.ai

| 文件 | 作用 |
| --- | --- |
| [AiSettingsDialog.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/ai/AiSettingsDialog.java) | 提供 AI 服务地址、模型与凭据配置和连接测试交互。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.content

| 文件 | 作用 |
| --- | --- |
| [ContentEditResult.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/ContentEditResult.java) | 内容编辑结果及本次新增资源，供题目编辑或作答保存。 |
| [QuestionContentLayout.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/QuestionContentLayout.java) | 共享内容宽度和正文/标题/图片展示参数。 |
| [QuestionContentRenderer.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/QuestionContentRenderer.java) | 组合 TEXT、RICH、DOCUMENT 内容显示，读取资源并展示缺失资源提示。 |
| [StagedContentResource.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/StagedContentResource.java) | 尚未发布的资源条目与字节，用于编辑确认后写入题库或历史。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.content.document.canvas

| 文件 | 作用 |
| --- | --- |
| [CanvasClipboardImage.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/CanvasClipboardImage.java) | 读取系统剪贴板图片并交给资源导入流程。 |
| [CanvasDocumentView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/CanvasDocumentView.java) | 用 Canvas 渲染原生文档的只读组件；移出 Scene 释放桥接，重新附着后重建。 |
| [CanvasEditorAdapter.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/CanvasEditorAdapter.java) | 将受支持的旧 TEXT/RICH 节点转换为 Canvas 元素，并保留转换回归所需的子集解码。 |
| [CanvasEditorBridge.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/CanvasEditorBridge.java) | 内部 Java/JavaScript 桥接：加载、读取内容、资源与编辑事件，限制页面导航。 |
| [CanvasEditorImageHost.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/CanvasEditorImageHost.java) | 提供给 JavaScript 的窄桥接入口，转发选图、初始化、内容和高度事件。 |
| [CanvasEditorPageLocator.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/CanvasEditorPageLocator.java) | 选择包内 Canvas 页面或允许的本地开发地址，核对页面 URL。 |
| [CanvasEditorWindow.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/CanvasEditorWindow.java) | 公开共用内容编辑窗口入口；窗口、会话与桥接内部实现不向调用者暴露。 |
| [CanvasNativeDocument.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/CanvasNativeDocument.java) | 读取/校验 Canvas 原生 JSON；允许内嵌图片，拒绝外部图片地址和尾随 JSON。 |
| [ContentEditingSupport.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/ContentEditingSupport.java) | 检查当前 TEXT/RICH 内容是否能进入保留的 Canvas 编辑路径。 |
| [ContentEditSession.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/ContentEditSession.java) | 暂存内容编辑资源，导入图片/原生文档，读取已有资源并在取消时放弃暂存结果。 |
| [ContentJson.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/content/document/canvas/ContentJson.java) | Canvas 内部共享 JSON 序列化辅助，集中错误处理。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.file

| 文件 | 作用 |
| --- | --- |
| [FileHeader.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileHeader.java) | 文件页面共用标题与模式/历史等操作区域。 |
| [FileMode.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileMode.java) | 文件页面的浏览/编辑模式。 |
| [FilePageHost.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FilePageHost.java) | 内部页面访问 FilePane 槽位、Scene 和重新打开动作的窄接口。 |
| [FilePane.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FilePane.java) | 每个标签页的稳定入口；选择 Markdown/题库控制器并委托编辑、保存、导航和刷新。 |
| [FilePresentation.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FilePresentation.java) | 读取结果与页面所需的草稿/注册等展示信息。 |
| [FilePresentationLoader.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FilePresentationLoader.java) | 加载文件并生成页面投影，识别可编辑空草稿，提供题库资源读取入口。 |
| [FileView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileView.java) | 内部 Markdown/题库页面共有的模式、脏状态、保存、导航与刷新契约。 |
| [FileViewerRouter.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/FileViewerRouter.java) | 把文件投影转换为对应浏览节点，组合练习区域和大纲。 |
| [MarkdownFileView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/MarkdownFileView.java) | 独立拥有 Markdown 编辑状态、保存、来源锚点、导航和滚动恢复。 |
| [QuestionBankFileView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/file/QuestionBankFileView.java) | 独立拥有题库编辑/练习/历史切换、保存和练习区域状态。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.markdown

| 文件 | 作用 |
| --- | --- |
| [MarkdownDocumentNavigator.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/markdown/MarkdownDocumentNavigator.java) | 按标题或命名锚点定位 Markdown 正文并滚动到对应位置。 |
| [MarkdownOutline.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/markdown/MarkdownOutline.java) | 从 Markdown 提取标题与命名锚点的大纲数据。 |
| [MarkdownOutlineView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/markdown/MarkdownOutlineView.java) | 展示并选择 Markdown 大纲，通知页面跳转。 |
| [MarkdownSourceEditorView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/markdown/MarkdownSourceEditorView.java) | 普通 Markdown 源文本编辑、变化通知与选区操作。 |
| [SafeMarkdownPreview.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/markdown/SafeMarkdownPreview.java) | 受限 Markdown HTML 预览，处理来源/导航动作并阻止未经允许的外部请求。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.question.editor

| 文件 | 作用 |
| --- | --- |
| [QuestionBankEditorView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/editor/QuestionBankEditorView.java) | 题库编辑会话的共用区域、题目列表、保存/取消与资源管理；具体字段交给题型组件。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.question.history

| 文件 | 作用 |
| --- | --- |
| [HistoryQuestionOutlineView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/HistoryQuestionOutlineView.java) | 按题型展示归档题目大纲，保留当轮状态与当前选中题。 |
| [PracticeHistoryDetailView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/PracticeHistoryDetailView.java) | 展示冻结题目、用户作答、结果和历史资源，来源状态按当前磁盘解析。 |
| [PracticeHistoryView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/history/PracticeHistoryView.java) | 列出归档轮次、打开详情和确认删除历史；活动轮次不在列表中。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.question.objective.choice

| 文件 | 作用 |
| --- | --- |
| [ChoiceCardView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/objective/choice/ChoiceCardView.java) | 选择题题卡、单选/多选控件、反馈和标准答案展示。 |
| [ChoiceEditorFields.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/objective/choice/ChoiceEditorFields.java) | 选择题题干、选项、正确答案与解析编辑字段。 |
| [ChoicePresentation.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/objective/choice/ChoicePresentation.java) | 选择题的界面投影，包括题干、选项、参考答案、来源和题号。 |
| [ChoicePresentationMapper.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/objective/choice/ChoicePresentationMapper.java) | 把当前题目或冻结历史快照转换为选择题界面投影。 |
| [ChoiceResultPresentation.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/objective/choice/ChoiceResultPresentation.java) | 用户选择、正确/错误结果和重做/修改动作的只读界面投影。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.question.practice

| 文件 | 作用 |
| --- | --- |
| [MixedQuestionPracticeView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/MixedQuestionPracticeView.java) | 组合选择题与作文预览/作答，复用同一持久化轮次和共享大纲。 |
| [QuestionBankPracticeView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/practice/QuestionBankPracticeView.java) | 选择题交互、结果/重做、题目切换与总结卡；命令先持久化再更新界面。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.question.shared

| 文件 | 作用 |
| --- | --- |
| [QuestionCardLayout.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/shared/QuestionCardLayout.java) | 题卡标题、正文、元信息与详情区域的共用布局。 |
| [QuestionEditorContext.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/shared/QuestionEditorContext.java) | 向题型字段组件传入模型、资源、来源动作、窗口拥有者与刷新能力。 |
| [QuestionOutlineView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/shared/QuestionOutlineView.java) | 题型分组的大纲组件，表达未作答、草稿、正确、错误、待评分与当前题。 |
| [QuestionPracticeLayout.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/shared/QuestionPracticeLayout.java) | 练习内容和可调宽度大纲的共用 SplitPane。 |
| [QuestionTypeCatalog.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/shared/QuestionTypeCatalog.java) | 桌面题型名称/编辑组件登记；检查重复登记和核心题型遗漏。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.question.source

| 文件 | 作用 |
| --- | --- |
| [HistorySourceListView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/source/HistorySourceListView.java) | 归档来源的只读列表，显示引用版本及当前定位状态。 |
| [HistorySourceNavigationAdapter.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/source/HistorySourceNavigationAdapter.java) | 将历史引用转换为当前可执行的导航或缺失说明。 |
| [QuestionSourceListView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/source/QuestionSourceListView.java) | 当前题目来源列表、状态与点击跳转动作。 |
| [QuestionSourceNavigationAdapter.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/source/QuestionSourceNavigationAdapter.java) | 校验当前题目的命名来源地址并解析导航结果。 |
| [QuestionSourceVisuals.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/source/QuestionSourceVisuals.java) | 当前/历史来源行共用图标、文本、状态和操作样式。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.question.subjective.essay

| 文件 | 作用 |
| --- | --- |
| [EssayAnswerPane.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/subjective/essay/EssayAnswerPane.java) | 作文作答的编辑入口、自动草稿保存、提交与重做/修改交互。 |
| [EssayEditorFields.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/subjective/essay/EssayEditorFields.java) | 作文题干、参考答案、评分指导与分值编辑字段，复用共用内容编辑入口。 |
| [EssayQuestionCardView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/question/subjective/essay/EssayQuestionCardView.java) | 作文题卡正文和分值等元信息展示。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.shared

| 文件 | 作用 |
| --- | --- |
| [EditorUi.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/shared/EditorUi.java) | 编辑表单共用字段、按钮、提示与布局辅助。 |
| [TextClipboard.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/shared/TextClipboard.java) | 可替换的文本剪贴板契约，生产使用系统剪贴板，测试使用内存实现。 |
| [UiTheme.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/shared/UiTheme.java) | 加载集中样式与 SVG 图标，提供共用控件样式和开发 CSS 路径。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.shell

| 文件 | 作用 |
| --- | --- |
| [DesktopView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/shell/DesktopView.java) | 创建桌面 Scene 与主窗口，安装关闭保护和开发监听器，隐藏时清理标签页。 |
| [MainWorkspaceView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/shell/MainWorkspaceView.java) | 主窗口业务协调：工作区切换、标签/来源导航、刷新及未保存编辑退出检查。 |
| [WindowChrome.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/shell/WindowChrome.java) | 自绘窗口标题栏、窗口操作与分隔线；关闭按钮发送统一关闭请求。 |
| [WorkspaceLayout.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/shell/WorkspaceLayout.java) | 主窗口布局与拖动分隔线几何；通过包内父类复用已有布局行为。 |

### quizforge-desktop-app / io.quizforge.desktop.ui.workspace

| 文件 | 作用 |
| --- | --- |
| [WorkspaceFileCommands.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceFileCommands.java) | 协调新建、改名、删除及复制路径，处理确认、标签刷新和文件树选择。 |
| [WorkspaceFileTreeView.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceFileTreeView.java) | 工作区文件树控件、图标、单/双击、右键菜单与行内名称编辑。 |
| [WorkspaceHistory.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceHistory.java) | 保存最近使用的工作区顺序，供切换器展示。 |
| [WorkspaceMenus.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceMenus.java) | 工作区菜单项、图标和样式共用辅助。 |
| [WorkspaceNavigationService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceNavigationService.java) | 检查导航地址并找到当前工作区对应资产，返回实际文件与目标位置。 |
| [WorkspaceSidebar.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceSidebar.java) | 组合工作区切换、文件操作入口和文件树。 |
| [WorkspaceSwitcher.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceSwitcher.java) | 最近工作区选择菜单与新建/打开入口，维护滚动区域与箭头状态。 |
| [WorkspaceTab.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceTab.java) | 一个文件标签的身份、路径、固定/预览状态与页面实例。 |
| [WorkspaceTabManager.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/java/io/quizforge/desktop/ui/workspace/WorkspaceTabManager.java) | 管理预览/固定标签、激活、关闭、重命名和页面缓存。 |

### quizforge-infrastructure / io.quizforge.infrastructure.ai

| 文件 | 作用 |
| --- | --- |
| [DeepSeekAiProvider.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/ai/DeepSeekAiProvider.java) | 将通用请求转换为 HTTP 调用，校验完整 JSON 响应并映射鉴权、超时、限流和协议错误。 |

### quizforge-infrastructure / io.quizforge.infrastructure.filesystem

| 文件 | 作用 |
| --- | --- |
| [package-info.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/package-info.java) | 描述此包的实际职责。 |
| [QuizForgeDataDirectory.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/QuizForgeDataDirectory.java) | 确定全局数据目录，默认 user.home/.quizforge；支持 quizforge.dataDir，定位全局数据库与工作区父目录。 |
| [SafeFilePublication.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/SafeFilePublication.java) | Markdown/题库共用发布器：版本检查、原文/编辑备份、条件回滚、冲突恢复副本。 |

### quizforge-infrastructure / io.quizforge.infrastructure.filesystem.markdown

| 文件 | 作用 |
| --- | --- |
| [FileDocumentNodeLookup.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/markdown/FileDocumentNodeLookup.java) | 读取注册/旧格式文档，核对身份、版本、命名锚点及旧节点位置。 |
| [LegacyMarkdownCodec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/markdown/LegacyMarkdownCodec.java) | 只读解析既有 study-document v1，计算旧格式版本；不提供标准文档生成业务。 |
| [LocalMarkdownFileStorage.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/markdown/LocalMarkdownFileStorage.java) | 读取并暂存 Markdown 替换，通过共用安全发布器发布和回滚。 |
| [MarkdownDocumentRegistrationService.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/markdown/MarkdownDocumentRegistrationService.java) | 按预期正文登记身份或创建命名锚点；分阶段发布、刷新索引并校验结果，失败保护外部修改。 |
| [RegisteredMarkdownCodec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/markdown/RegisteredMarkdownCodec.java) | 保留原 Markdown 格式，读取注册元数据、内容块与命名锚点；注册/插入锚点入口跳过嵌套代码中的示例标记。 |

### quizforge-infrastructure / io.quizforge.infrastructure.filesystem.qbank

| 文件 | 作用 |
| --- | --- |
| [LocalQuestionBankFileStorage.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/LocalQuestionBankFileStorage.java) | 构建替换 ZIP，新增资源优先用调用者提供的字节，其余复用旧包；由 SafeFilePublication 发布。 |
| [PackageJson.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/PackageJson.java) | 借助 QuestionBankV2Codec 将逻辑模型拆成 manifest/bank JSON，转换资源 locator/path 名称，提供字段与包内路径检查。它是协议编码辅助，不是题型模型定义 |
| [PackageLimits.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/PackageLimits.java) | 配置 ZIP 条目数、JSON、单资源、总解压大小和中央目录的上限 |
| [QBankImageImporter.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/QBankImageImporter.java) | 读取本地 PNG/JPEG，核对文件大小、像素与解码，生成资源 ID、类型、路径、哈希和字节；只负责导入准备，不直接完成界面插入或题库保存 |
| [QBankPackageReader.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/QBankPackageReader.java) | 检查 ZIP、必需 JSON、资源目录和数据格式并恢复 QuestionBank；inspect 不打开资源流验证字节，read/open 会核对资源字节的大小和 SHA-256，LoadedPackage 提供包内资源流与关闭入口 |
| [QBankPackageWriter.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/QBankPackageWriter.java) | 将题库与供应的资源字节写为临时 ZIP，按实际字节计算资源哈希，重新读包核对后使用原子替换发布；不支持原子替换时报告失败，没有普通覆盖回退 |
| [QuestionBankV2Codec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/QuestionBankV2Codec.java) | 在 QuestionBank 与逻辑 JSON 之间转换，校验正式题库/空草稿，映射稳定的内容、块、行内、payload 和 answer kind，转换命名 sourceRefs；计算 qfb:v2 内容版本。它不打开 ZIP、不读取资源字节，也不实现编辑或评分。应保留，新增类型需同步此处编码契约 |
| [ResourceContentProvider.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/qbank/ResourceContentProvider.java) | 按资源元数据提供实际 InputStream，供写包等操作使用，使二进制字节不需要塞入核心题目模型；调用者负责关闭获取的流 |

### quizforge-infrastructure / io.quizforge.infrastructure.filesystem.workspace

| 文件 | 作用 |
| --- | --- |
| [FileSystemWorkspaceAssetScanner.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/workspace/FileSystemWorkspaceAssetScanner.java) | 识别资产并诊断损坏或重复身份；普通扫描刷新派生索引，scanReadOnly 不更新索引。 |
| [LocalWorkspaceFileCatalog.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/workspace/LocalWorkspaceFileCatalog.java) | 读取真实文件树、Markdown、题库和包内资源；检查路径边界，区分无效资产与普通文件。 |
| [LocalWorkspaceFileOperations.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/workspace/LocalWorkspaceFileOperations.java) | 新建目录/空文档/空题库、改名和删除；阻止覆盖与路径越界，删除前检查整棵目录。 |
| [WorkspaceManifestStore.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/workspace/WorkspaceManifestStore.java) | 读写工作区内的 .quizforge/workspace.json；当前 format=quizforge-workspace、schemaVersion=1.0，保存稳定 workspaceId 和名称。首次写入使用 CREATE_NEW，读取核对格式、版本、名称和 UUID。Java 包迁移不应改写已有身份或文件协议 |
| [WorkspacePathGuard.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/workspace/WorkspacePathGuard.java) | 检查归一化路径与真实路径均位于拥有的根目录，阻止符号链接和目录联接越界。 |
| [WorkspacePathResolver.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/filesystem/workspace/WorkspacePathResolver.java) | 定位、创建和打开工作区，校验路径与 manifest，补齐三个默认目录；保留已有文件。 |

### quizforge-infrastructure / io.quizforge.infrastructure.persistence

| 文件 | 作用 |
| --- | --- |
| [package-info.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/package-info.java) | 描述此包的实际职责。 |
| [SqliteAiProviderConfigRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/SqliteAiProviderConfigRepository.java) | 全局 ai_provider_config 的默认项读取和配置 upsert，保存提供者、地址、模型、credential_ref 和更新时间；不发送网络请求、不保存实际 API Key。credential_ref 由凭据实现解析。属于已确认保留的基础 AI 接入，应保留 |
| [SqliteAssetIndexRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/SqliteAssetIndexRepository.java) | 事务替换资产索引；将 REGISTERED_MARKDOWN 映射为历史数据库文本 STANDARD_DOCUMENT。 |
| [SqliteDatabase.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/SqliteDatabase.java) | 执行既有全局/练习 Flyway 迁移，创建启用外键的连接与 IMMEDIATE 练习事务连接。 |
| [SqliteWorkspaceRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/SqliteWorkspaceRepository.java) | 全局 workspace 表的新增、列表及按 UUID 查询，保存名称、UTC 时间和可选 root_path |
| [WorkspaceAssetDatabase.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/WorkspaceAssetDatabase.java) | 初始化工作区派生索引数据库与独立 workspace-migration；不保存作答事实。 |

### quizforge-infrastructure / io.quizforge.infrastructure.persistence.practice

| 文件 | 作用 |
| --- | --- |
| [PracticeConnectionScope.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/PracticeConnectionScope.java) | 区分单独调用仓储时自己打开/关闭的连接，与事务回调内借用的连接；借用者不关闭共享连接，已关闭事务不能继续使用。包内实现细节，不是另一个业务服务；随三个仓储放在同一边界内保留 |
| [PracticePayloadJsonCodec.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/PracticePayloadJsonCodec.java) | 将已冻结的 PracticePayload 编码为 SQLite JSON 字段或解码回基础结构，拒绝重复 JSON 字段和尾随内容，使用 BigInteger/BigDecimal 保留数值；null 对应未保存草稿等状态。单字符串读取上限按作文 64 MiB 字符预算对应的 Base64 长度配置。它不读取 .qbank，不能用它代替文件协议 codec，也不负责将题目枚举变成基础数据 |
| [SqlitePracticeSessionQuestionRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqlitePracticeSessionQuestionRepository.java) | 保存会话内的冻结题目、顺序、当前状态和草稿。createAll 独立调用时自建事务，事务内调用时借用外层事务；更新快照/顺序与更新草稿分别处理。删除指定题目要求所属会话 ACTIVE，其作答记录由外键级联删除。业务层决定什么时候允许更新和什么改动需要重置，仓储不自行判断题库语义 |
| [SqlitePracticeSessionRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqlitePracticeSessionRepository.java) | 保存/恢复 practice_session：题库身份/版本、标题快照、位置、活动时间、ACTIVE/ARCHIVED；按资产 ID 找活动会话，按归档时间列出历史。当前写入时间采用固定九位 UTC 小数，支持 SQLite TEXT 的纳秒排序；位置/版本/归档更新只作用于 ACTIVE，deleteArchived 只删除 ARCHIVED。题型和判分规则不在此实现 |
| [SqlitePracticeTransaction.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqlitePracticeTransaction.java) | 为一次业务操作创建一条启用外键、IMMEDIATE 模式的连接，将三个仓储绑定到同一事务；成功提交，SQLException/RuntimeException/Error 失败都尝试回滚，恢复失败作为 suppressed exception 保留。创建/恢复会话、提交、重做、归档、历史查询/删除均由核心服务通过此边界协调，继续保留 |
| [SqliteQuestionAttemptRepository.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqliteQuestionAttemptRepository.java) | 追加并读取 question_attempt：每次提交的答案、序号、INITIAL/RETRY/REVISION、结果、可空分数和提交时间；按 attempt_no 排序，下一序号为 max+1。没有覆盖已有答案的更新方法，重复序号由数据库唯一约束拒绝。序号查询和追加需要同一业务事务协调，不能由多个独立连接的读写代替 |
| [SqliteWorkspacePracticeRuntimeProvider.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/persistence/practice/SqliteWorkspacePracticeRuntimeProvider.java) | 打开练习前只读扫描并拒绝重复身份；装配工作区内持久化练习及历史服务。 |

### quizforge-infrastructure / io.quizforge.infrastructure.security

| 文件 | 作用 |
| --- | --- |
| [package-info.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/security/package-info.java) | 描述此包的实际职责。 |
| [WindowsDpapiCredentialStore.java](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/java/io/quizforge/infrastructure/security/WindowsDpapiCredentialStore.java) | 用 Windows DPAPI 加密凭据并按引用保存密文；每次访问验证 secrets 目录边界。 |

## 资源、测试与开发工具

| 位置 | 作用 |
| --- | --- |
| [Start-QuizForge.cmd](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/Start-QuizForge.cmd) | 普通启动的双击/命令行入口，转发参数到共用 PowerShell 实现。 |
| [Start-QuizForge-LiveUi.cmd](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/Start-QuizForge-LiveUi.cmd) | 开发启动快捷入口，向普通入口传入 LiveUi，统一退出码和错误显示。 |
| [tools/Start-QuizForge.ps1](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/tools/Start-QuizForge.ps1) | 共用启动实现：独立 Maven 编译输出、JavaFX 启动、开发服务和进程树清理。 |
| [quizforge-desktop-app/editor-web/canvas](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/editor-web/canvas) | 当前 Canvas HTML/JS 源码、package-lock 与构建脚本；node_modules 不入版本管理。 |
| [quizforge-desktop-app/src/main/resources/editor/canvas](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/resources/editor/canvas) | 可离线加载的 HTML/JS/CSS 构建结果，普通启动不需要 Node。 |
| [quizforge-desktop-app/src/main/resources/styles](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-desktop-app/src/main/resources/styles) | JavaFX workspace.css 与 Markdown WebView 的 markdown-preview.css。 |
| [quizforge-infrastructure/src/main/resources/schema/qbank-v2.schema.json](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/resources/schema/qbank-v2.schema.json) | 当前逻辑题库 JSON Schema；题型登记时同步约束。 |
| [quizforge-infrastructure/src/main/resources/db](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/quizforge-infrastructure/src/main/resources/db) | 既有全局/练习与派生索引迁移；历史 SQL 校验和不变。 |
| [tools](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/tools) | 启动检查、进程管理、LiveJava 编译代理及隔离探针。 |

桌面集成测试按 Markdown、题库编辑、作文编辑、练习、历史、来源、窗口退出、工作区和开发刷新拆分；WorkspaceUiTestSupport 共享临时工作区与窗口清理，FxTestRuntime 共享 JVM 的 JavaFX 初始化。Canvas 私有实现由同包测试覆盖，跨组件测试只使用测试专用驱动。

当前盘点：250 个生产 Java 文件（包括包说明）。2026-10-02 将单选、多选规则分为两个独立类，共用选项数据、包内辅助与界面组件，保持题型 ID 和文件格式。项目地图、业务流程与当前能力边界见 [新人指南](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/new-developer-guide.md)，文件协议见 [题库格式](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/qbank-format.md)，新增题型清单见 [模板](C:/Users/wangg/OneDrive/Desktop/QuizForge/quizforge_V2/docs/templates/new-question-type.md)。
