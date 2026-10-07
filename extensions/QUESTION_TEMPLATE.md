# 通用题型模板 v1：判断题

> 当前模板版本 2.3.2 使用 [精简页面接口](SIMPLE_PAGE_API.md)，共 11 个文件。直接修改 `editor.js` / `practice.js`，通过 `QF.page.register` 加载、`QF.save` 保存、`QF.requestAction` 请求操作；没有重复源码、编译脚本或包内客户端库。`examples/basic.qbank` 是独立样例题库。

判断题源码：`packages/true-false/`，题型 ID：`TRUE_FALSE`，扩展 ID：`quizforge.types.true-false`。这是完整的 HTML SDK 2 扩展，需要手动导入、确认权限并重启，之后才能在新增题目菜单中选择“判断题”。

此版作为后续题型的视觉起点，先在判断题上调整并确认效果，再沿用到其他题型。白板继续使用宿主默认样式。

从头开发请先读 [拓展开发指南](DEVELOPMENT_GUIDE.md)。本模板的 JS、HTML、CSS 已补充开发注释；清单、默认 JSON 和 Schema 保持标准 JSON，不插入注释。注释不会改变源码行为，但重新打包会改变包哈希；发布仍遵循新版本安装规则。

## 带注释源码的阅读路线

1. `manifest.json`：确认身份、入口和申请权限，再看 `default.json`。
2. 两份 Schema：对照下表区分完整题目、标准答案和用户作答。
3. `editor.html` → `editor.js`：先看控件，再看加载、保存、题库操作和富文本。
4. `practice.html` → `practice.js`：看如何写入选项 ID、接收上下文、提交、重试及恢复只读记录。
5. `type.js`：独立规则运行时如何校验固定选项并上报分数。
6. `style.css`：视觉变量、选中与反馈、禁用圆点、窄屏和减少动态效果。

## JSON 字段导读

| 文件/字段 | 含义 | 复制为新题型时注意 |
| --- | --- | --- |
| 清单顶层 `id` / `version` | 拓展包身份与版本 | 使用发布者命名空间，修改内容后发布需新版本 |
| 清单 `types[].id` | 该包提供的题型 ID | 同步默认 JSON、Schema 与 `type.js` |
| 清单 `dataVersion` | 题型数据契约版本 | 不会自动迁移数据 |
| 清单 `capabilities` / `permissions` | 支持场景 / 申请操作权限 | 场景声明不等于授权；复制后按实际调用精简 |
| 默认数据 `id` | 模板题目 ID | 新建和复制时由应用替换 |
| `prompt` | 普通文本题干 | 页面用 `textContent` / `value`，不拼接 HTML |
| `payload.options[].id` | 每个固定选项的身份 | 用当前题目中的 ID，不写死模板 ID |
| `payload.options[].content` | “正确／错误”的文本 | 本模板不允许添加、改名或颠倒顺序 |
| `answerSpec.correctOptionIds` | 标准答案，恰好一个选项 ID | 与用户作答分开保存，提交前不向练习页公开 |
| `scoreSpec.defaultMaxScore` | 满分 | 正数；评分使用应用传入的冻结满分 |
| `analysis` | 答案与解析内容 | 默认 TEXT；编辑与展示支持公共富文本 |
| `sourceRefs` / `stimulusRefs` | 引用来源与材料关联 | 由宿主对应功能管理，不自行修改来源身份 |
| 作答 `selectedOptionIds` | 用户选择，最多一个选项 ID | `answer.schema.json` 约束作答；`{}` 表示未作答 |

本模板复用 `CHOICE`，新增私有数据格式时改用 `EXTENSION` 的 `data` 外壳，同时替换两份 Schema 和页面/规则绑定。不能只改 HTML 而沿用判断题的数据校验。

## 页面与内容

| 区域 | 设计要求 |
| --- | --- |
| 题干 | 普通文本，保留换行，长句自动换行；默认 18px、1.8 倍行高 |
| 编辑区 | 题干、正确答案、分值、答案与解析分区排列；全页面滚动 |
| 正确答案设置 | “正确／错误”两项固定，不能增加、删除或改名 |
| 作答区 | 两个同宽选项并排；窄屏小于 380px 时改为两行；原生单选控件支持键盘操作 |
| 选中状态 | 淡紫底色、紫色边框和圆点；提交后继续保留用户选中标记 |
| 提交与重试 | 紫色主按钮提交，行内确认；提交后冻结，使用次级按钮重新作答 |
| 评分反馈 | 正确项浅绿、错误已选项浅红；文字标明“正确答案／你的答案”，不只依赖颜色 |
| 结果区 | 单独浅色面板：判定、得分、正确答案、答案与解析 |
| 答案与解析 | 使用 `QF.content.mountEditor` 富文本编辑，使用 `QF.content.render` 渲染 |
| 历史／预览 | 复用练习页；历史保留所选答案、反馈及解析，隐藏提交与重试，禁止输入 |
| 草稿 | 复用练习页；右上角保留宿主草稿开关，工具栏和缩放沿用默认白板 |

## 视觉基线

默认题卡宽 720px，最大宽度为可用浏览区的 100%。练习卡居中，编辑卡从上方开始。题卡外部、白板及强制保留的导航由宿主负责，扩展通过 `QF.layout.configure` 设置尺寸与位置。

`style.css` 的根作用域定义可调整的视觉变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `--qf-accent` | `#7962ad` | 主按钮、选中圆点、强调文字 |
| `--qf-soft` | `#f5f1fb` | 选中及确认背景 |
| `--qf-line` | `#e8e3ed` | 分隔线和面板边框 |
| `--qf-muted` | `#8a8295` | 次级说明和状态 |

正文使用 15px，辅助信息 12px；区块通常间隔 24px，控件间距 10–12px。按钮圆角 8px，选项圆角 10px，结果面板圆角 12px。只将提交作为主操作，避免所有按钮都使用实色。禁用状态仍保留选中圆点。减少动态效果偏好下关闭选项过渡动画。

通用结构类：`.qf-type-heading`、`.qf-type-meta`、`.qf-editor-section`、`.qf-type-actions`、`.qf-result-panel`、`.qf-type-sources`。后续题型可以复制这些结构与样式，再定制自己的作答区。样式随每份扩展打包；此版没有新增共享远程 CSS 依赖。

## 文件职责

| 文件 | 作用 |
| --- | --- |
| `manifest.json` | 声明题型、页面文件、数据版本和接口权限 |
| `default.json` | 完整默认题目，结构与 `.qbank` 中该题目的 JSON 一致 |
| `editor.html` / `practice.html` | 静态页面结构与控件；题干等实际数据从接口填入 |
| `editor.js` | 调用编辑、保存、复制、删除、来源等 SDK 接口 |
| `practice.js` | 读取题目、写入作答、提交、重试并刷新反馈 |
| `style.css` | 页面外观、响应式布局、交互状态 |
| `question.schema.json` / `answer.schema.json` | 数据结构校验 |
| `type.js` | 固定选项与正确答案校验、同步评分 |
| `examples/basic.qbank` | 真实样例题库，用于预览、下载和开发验证 |

两份页面 JS 均为直接维护的源码，打包不会生成或覆盖它们。无需使用 esbuild，也无需先编译页面。界面文件可以在连接开发目录后热更新；发布修改仍需增加版本号。

判断题复用 `CHOICE` 数据结构，两项按“正确、错误”的顺序保存；正确答案和作答均引用选项 ID。不要在页面或规则中写死默认 JSON 的 ID，新建和复制时宿主会重新分配。空作答允许暂存，提交前由宿主检查。

题目、作答、评分与历史保存均由宿主管理。页面仅通过 SDK 更新数据，评分逻辑使用 `reportResult` 上报得分。结果区应渲染接口返回的结果和参考解析，不能在未提交阶段直接展示正确答案。历史可写能力由宿主拒绝，页面同时按 `context.permissions` 与 `context.mode` 调整控件。

操作按钮是否显示由模式、`context.grantedPermissions` 和提交结果决定。`context.permissions` 控制是否可操作：切题、保存或使用画笔时，能力可能暂时关闭，应只禁用按钮，保留其布局位置，避免按钮消失造成题卡抖动。

## 开发与打包

先用独立打包工具生成 `.qfext`，在应用中导入、确认权限并重启。再通过“加载开发目录…”显式连接源码；修改 HTML、CSS 或页面 JS 后，当前浏览区会更新。规则、Schema、默认 JSON 的变化走新版本安装。完整空白配置验收流程见 [README.md](README.md)。

在仓库根目录打包：

```powershell
node extensions/tools/pack.mjs extensions/packages/true-false extensions/dist/quizforge.types.true-false-2.3.2.qfext
```

开发其他题型时复制整个源码目录，修改扩展 ID、题型 ID、名称、默认 JSON、Schema 和规则。保留公共页面结构和接口流程，替换题干及作答区域。安装版本不能用同一 ID、同一版本覆盖不同内容；发布修改时增加版本号。

完整现行接口和权限约定见 [SDK 2.3 接口参考](SIMPLE_PAGE_API.md)。新页面不使用旧业务命名空间；文档入口见 [SDK_README.md](SDK_README.md)。

## 原模板阶段验证记录（2026-10-06）

以下是原模板阶段的定向验收，不是当前 2.3.2 全量回归结论，也不代表本次文档更新重新运行了这些检查。2026-10-07 已为三个 JS 文件补充中文语法和数据流注释，规范化编译后确认执行逻辑未变。

- 前端定向测试 22 项通过，覆盖模板新建／复制／判分、数据约束及现有 SDK 生命周期。
- Core 定向测试 1 项通过，确认不同扩展题型名能复用 `CHOICE` 数据契约，完成新建、复制和编码往返。
- 实际 WebView2 窗口检查 10 项通过：练习 6 项、编辑 4 项；确认固定选项、保存、错误反馈、重试、历史只读，以及首次加载提示清理。
- 前端构建、Java 模块构建通过。未运行全量回归。测试使用独立题库和数据库。

实际窗口验证产物位于仓库 `target/`：

```text
webview2-product-acceptance/81a00ff9-7762-47a8-b0a2-7d0642f20501/
  verification.json
  practice.png
  incorrect.png
webview2-editor-acceptance/04aad9ad-de61-4c9f-8832-32be0be28a18/
  verification.json
  editor.png
  saved-bank.json
```
