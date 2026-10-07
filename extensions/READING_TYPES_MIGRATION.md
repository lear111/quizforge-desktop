# 剩余题型迁移与宿主改动边界

> 2026-10-07 更新：八个最新包均使用 SDK 2.3；八个示例包均为 2.3.2。均已迁移到 [精简页面接口](SIMPLE_PAGE_API.md)，并各带独立样例题库。判断题直接维护页面 JS，无需编译；其余样例构建用 `node extensions/tools/build-page-extensions.mjs`。下文保留此前迁移记录与旧版本验证，不代表当前包版本或最新公开接口。

2026-10-06 · SDK 2.2 · 完形、阅读为 2.2.1；其余三份为 2.2.0。

## 题型实现

| 拓展目录 | 题型 ID | 编辑与练习 |
| --- | --- | --- |
| `packages/cloze/` | `CLOZE` | 富文本文章、`{{1}}` 空位；正文直接选择与下方普通文本选项同步；逐空计分，解析为普通文本 |
| `packages/reading/` | `READING` | 文章和小题题干为富文本，选项为普通文本；自动选择 4、2、1 列；逐题计分 |
| `packages/matching/` | `MATCHING` | 富文本材料，八个排序位置、三个锁定提示；只为五个待答位置编号；允许重复选未锁定字母，逐位置显示正误 |
| `packages/translation/` | `TRANSLATION` | `{{句子}}` 自动编号并画线；每句独立富文本回答和参考译文；重排保留句子身份，新增句子使用新参考答案 |
| `packages/essay/` | `ESSAY` | 小作文和大作文共用；富文本题干、正式答案、参考答案及解析；默认直接输入，开启格式编辑后显示工具栏 |

练习、草稿和历史仍复用同一份 `practice.html`。提交后保留选择，历史禁用输入并保留富文本答案。翻译和作文提交为待评分，不伪造自动分数。输入工具栏当前包含文字、字体、字号、颜色、段落对齐等操作；已有答案的内嵌图片和表格可显示、保存，尚未提供新图片上传和表格插入按钮。

四种复合题的 `scoreSpec.defaultMaxScore` 延续旧数据语义，表示每个待答小题分值；规则 `maxScore` 将其转换成整张卡片总分。排序提示不计分。作文为单题总分。

## 为什么仍修改宿主

之前的 SDK 足够运行基础选择题，但未完整支持复合题。本轮补充的通用能力如下；宿主没有按五个题型 ID 增加分支。

| 宿主源文件 | 修改及原因 |
| --- | --- |
| `frame-client.js` | 增加 `QF.content.renderAsync`，让拓展等富文本渲染完成后操作其标记；原 `render` 保持返回 DOM 节点 |
| `rules-runtime.js` | 增加可选 `allocateQuestion`、`maxScore`、`publicPayload` 规则钩子。题型自身负责复制数据引用、总分和可公开提示 |
| `ExternalQuestionTypeDefinition.java` | 私有数据的创建、复制调用规则；公开快照接收 `publicPayload`；旧存储数据交给包内 Schema 校验，编辑后采用私有数据封装 |
| `sdk.js`、`preview-projection.js`、`workbench-state.js` | 网页预览和开发预览使用相同规则快照，总分、提示与正式练习一致 |
| `data-validation-core.js` | 校验公开 payload 必须为 JSON 对象；预览调用同样检查规则返回值 |
| `compatibility.js`、`ExtensionCompatibility.java`、`pack.mjs` | 统一声明 SDK 2.2；旧 SDK 2.0/2.1 包继续兼容，新包要求至少 2.2 |

`src/main/resources/editor/draft-canvas/` 中的 JS 是上述源码构建产物。它们出现在 diff 中，不代表每份页面各写了一套题型逻辑。新增测试和开发文档也不属于题型宿主实现。

题干格式、选项、锁定提示、标记解析、答案结构、逐项评分、输入工具栏、布局和 CSS 均在拓展源码中。`extensions/shared/` 是这些包的源码复用目录，构建时复制到每份包的脚本中；运行时不依赖共享目录，也不注入主程序资源。

以后新增题型应只添加自己的包。若需要未提供的系统能力，单独扩展 SDK 并声明最低版本；不增加题型专属加载器、评分器或数据字段分支。

## 开发、打包与导入

这五份包使用 `*-source.js` 作为可读入口，公共实现位于 `extensions/shared/`。不要手工改生成的 `editor.js`、`practice.js`、`type.js` 后再运行源码构建，否则会被覆盖。

在仓库根目录运行：

```powershell
node extensions/tools/build-reading-types.mjs
node quizforge-desktop-app/editor-web/draft-canvas/scripts/build-extensions.mjs
```

第一步需要现有前端开发依赖，生成五个独立包；第二步生成 `.bundle.json` 和网页预览。修改宿主源码另需前端构建与 Java 编译；应用重启后使用 SDK 2.2。

安装包在 `extensions/dist/quizforge.types.<题型>-2.2.0.qfext`，通过应用的拓展导入界面安装并确认权限。桌面主程序不会自动安装、覆盖已安装拓展或改写用户题库。

新建题目使用 `EXTENSION` 数据封装。包内 Schema 同时接受本题型旧存储封装，因此可加载已有题库；修改对应题目后再保存为新版数据。不能用缺少拓展提示代替保留原数据，也不应打开文件即批量重写。

## 验证范围

### 2.2.1 布局修复

完形、阅读的选项布局只观察宽度变化，将列数调整推迟到下一动画帧，测量文字的临时节点放在观察网格之外。宿主仅将无异常对象的两种 ResizeObserver 浏览器通知视为非致命；真实脚本异常仍终止题卡并提供重载入口。

使用现有题库的真实完形和阅读文档，在隔离 WebView2 中分别连续调整宽度 30 次，未产生布局循环通知；另核对浏览器通知保留页面、真实异常显示失败。正式配置已安装两个 2.2.1 包，旧包与题库文件保留。重启应用后加载新版。

仅重建这两份源码可以执行 `node extensions/tools/build-reading-types.mjs cloze reading`。最新安装包为 `quizforge.types.cloze-2.2.1.qfext` 和 `quizforge.types.reading-2.2.1.qfext`；旧版本安装包保持不变。

- 定向检查独立包、Schema、复制身份及引用、部分得分、提示保密、未评分提交、网页预览与旧包兼容。
- 宿主定向检查私有模板委托规则分配身份及公开快照、安装版本校验。
- 对 `EnglishEssayAcceptance.qbank` 的九题进行只读校验；不修改文件、不写入正式作答或历史数据库。
- 使用隔离配置的真实 WebView2 检查编辑、练习输入、历史只读、正文跨格式标记及富文本答案回放。

未执行全量回归；AI 评分、上传新答案图片与新建表格不在本轮实现范围。
