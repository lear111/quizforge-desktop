# 题型拓展开发指南

更新日期：2026-10-07。适用于 **SDK 2.3、pageApi: "simple"、包格式 2**。接口统一以 [现行接口参考](SIMPLE_PAGE_API.md) 为准。

## 1. 从哪里开始

推荐复制 `extensions/packages/true-false/`。判断题 2.3.2 是 11 文件模板，JS 已有中文语法和业务流程注释，直接修改后打包即可，不需要编译或包内客户端库。

1. 读本文，明确应用和扩展的分工。
2. 看 [模板说明](QUESTION_TEMPLATE.md)，再读判断题 `type.js → editor.js → practice.js`。
3. 修改身份、默认数据、Schema、页面和规则。
4. 用独立开发预览检查，再打包、安装、授权。
5. 查接口用 [SIMPLE_PAGE_API](SIMPLE_PAGE_API.md)，查数据限制用 [DATA_VALIDATION](DATA_VALIDATION.md)。

应用负责题型识别、页面加载、题库与答案保存、导航、尝试记录、权限、公共白板及总结页。扩展负责两个页面、数据定义与评分规则。历史是同一练习页的只读展示，草稿是在练习页外叠加默认白板，不需另外做历史 HTML 或草稿 HTML。

## 2. 文件结构

仓库内可放 `extensions/packages/my-type/`，也可放 `D:/QuizForgeExtensions/my-type/`。应用不会自动安装、授权或监听目录。

```text
my-type/
  manifest.json
  default.json
  question.schema.json
  answer.schema.json
  editor.html
  editor.js
  practice.html
  practice.js
  style.css
  type.js
  examples/basic.qbank
```

| 文件 | 作用 |
| --- | --- |
| manifest.json | 包/题型身份、版本、入口路径、SDK 要求、权限和样例 |
| default.json | 新建时的完整 Question，与 .qbank 的 questions[] 一项同构 |
| question.schema.json | 完整题目数据结构约束 |
| answer.schema.json | 用户作答对象约束；不是标准答案 answerSpec 的 Schema |
| editor.html / editor.js | 编辑结构、加载完整题目、保存编辑草稿/题库、管理操作 |
| practice.html / practice.js | 作答、提交、重试、结果、历史和预览只读展示 |
| style.css | 页面样式、控件状态；可自己决定是否使用白色题卡 |
| type.js | 独立规则运行时的校验、同步评分、可选多小题目标 |
| examples/basic.qbank | 真实展示/下载样例，与默认新建模板独立 |

HTML 是片段，不创建 WebView、iframe 或消息桥；不能嵌入 script、外链或 onclick。脚本、样式由清单引用，应用注入 QF。已有内容资源用 QF.content，页面不能直接读数据库或工作区文件。

单选、多选和五个复合/主观题型暂保留 *-source.js 构建链；不要把它们的生成脚本当手写入口。具体区别见 [源码表](SIMPLE_PAGE_API.md#9-示例源码与构建)。

## 3. 复制模板后修改身份

示例：包 ID `example.short-answer`，题型 ID `example.short-answer`，包版本 1.0.0，dataVersion 1。一个包可声明多个题型，包 ID 和题型 ID 不要求相同。

| 修改位置 | 要改什么 |
| --- | --- |
| manifest.json | id/name/version，types[].id/label/family/dataVersion，权限和入口 |
| manifest.json | sdkApiMajor:2、minSdkApiMinor:3，types[].pageApi:"simple" |
| default.json | type、默认题干、私有数据、标准答案与满分 |
| question.schema.json | type.const，以及实际题目字段结构 |
| answer.schema.json | 新题型作答字段 |
| type.js | defineQuestionType 的 type 及对应校验/评分 |
| HTML/JS | 标签、输入控件、字段读写、按钮和结果 |
| examples/basic.qbank | 更新为所属题型的真实题库，不留下模板题型的数据 |

新题型使用自己的发布者命名空间，不占用现有稳定题型 ID。普通题型只修改自身目录，无需增加 Java 题型分支；缺少宿主能力时先扩展并审查 SDK。

应用负责题目身份及复制时的重分配。页面可用 QF.ids.create('opt_') 创建选项 ID，但不能更换当前题目 id/type。自定义嵌套身份结构需核对默认分配行为，必要时使用规则 allocateQuestion。

## 4. 先设计数据，再写界面

题目有公共字段 id、type、prompt、payload、answerSpec、scoreSpec、analysis、sourceRefs 等。用实际模板核对完整结构，不在 HTML 中写死题目正文。

自定义题型可以使用：

```json
{
  "payload": {"kind": "EXTENSION", "data": {"placeholder": "请输入回答"}},
  "answerSpec": {"kind": "EXTENSION", "data": {"referenceAnswer": "示例答案"}}
}
```

用户作答是独立对象，例如 `{"text":"学生的回答"}`。题库保存完整 Question，练习和历史保存用户作答及结果；页面不自行决定数据库表结构。

Schema 使用宿主支持的 Draft-07 安全子集。远程引用、复杂正则和无限嵌套等不允许，预算见数据校验文档。规则可加业务限制，不能取消宿主强制校验。

每包声明 1～16 份真实 .qbank 样例。default.json 用于新建，样例用于展示；样例不自动导入用户工作区。更改默认数据后也要核对样例，打包成功不代表已通过全部原生安装校验。

## 5. 编辑页：统一加载和保存

```js
const $ = QF.dom.$;
let question;

await QF.page.register({
  onLoad(context) {
    question = context.question.data;
    $('[data-prompt]').value = question.prompt.text;
    $('[data-prompt]').disabled = !context.permissions.editQuestion;
  },
  onBeforeLeave: () => ({ok: true, data: {pendingSave: null}})
});

QF.dom.on($('[data-prompt]'), 'input', () => {
  question = {...question, prompt: {kind: 'TEXT', text: $('[data-prompt]').value}};
  return QF.save({purpose: 'editDraft', data: {questionData: question}});
});
```

这是基本调用示意。正式实现还要处理失败提示、快速输入和异步状态刷新；判断题 editor.js 使用待写入计数和最近接受的数据，避免旧上下文覆盖新编辑。

| 意图 | 调用 |
| --- | --- |
| 暂存题目修改 | save editDraft，data.questionData 为完整 Question |
| 修改并正式保存整库 | save edit，data.questionData 为完整 Question |
| 保存已更新的编辑会话 | requestAction saveBank |
| 新增/复制/删除 | requestAction addQuestion / duplicateQuestion / deleteQuestion |
| 整题排序 | requestAction moveQuestion |
| 添加/移除/查看来源 | requestAction addSource / removeSource / openSource |

输入变化立即调用保存；SDK 等待已发写入后才导航/提交。不要依赖延迟定时器保存必要内容。删除确认由扩展绘制，应用收到删除请求即执行。

解析编辑可用 `QF.content.mountEditor(node,{value,onChange,formatting:true})`。onChange 将新解析交给 editDraft；提前移除组件时 destroy。此公共编辑器只支持编辑模式，练习作答区的编辑交互由扩展实现。

## 6. 练习页：作答、提交和重试

```text
page.register → onLoad 提供公开题目、当前答案、状态和权限
save draft    → 保存未提交用户作答
save submit   → 保存答案，应用校验、规则评分、保存正式尝试
onLoad        → 提供 attempt.result 和提交时的 reference
requestAction retry → 保留旧尝试，开始空白作答
```

```js
const reply = await QF.save({
  purpose: 'draft', data: {answer: {text: input.value}}
});
if (!reply.ok) QF.ui.notify(reply.error.message);
```

无需单独 saveAttempt。draft 不生成正式记录，重试不直接评分，重试后的提交才产生新记录。未提交题目的公开投影不含标准答案/解析，结果从 context.attempt.result 读取，不在页面里计算“可信分数”。

自己制作提交确认时，设置 `QF.ui.configure({submit:false,retry:false,confirmation:false})`，确认后再调用 submit。若保留默认确认，成功回复可能仅说明确认框已打开，最终结果仍以 onLoad 为准。

| 场景 | 要求 |
| --- | --- |
| 当前可作答 | 按 permissions.writeAnswer 启用输入 |
| 已提交 | 保留用户答案和选中点；显示结果，按 permissions.retry 启用重试 |
| 重试 | 恢复空答案，不改旧记录 |
| 历史/旧尝试 | 复用练习页，答案、笔迹和结果只读 |
| 网页预览 | 只读，不依赖正式题库/桌面命令 |
| 画笔/拖动、导航准备中 | 暂时禁用控件，不因短暂能力下降隐藏提交按钮 |

onLoad 可重复调用；不要重复绑定事件或重建整个输入框，否则光标、中文输入和富文本装饰会丢失。接口失败时不要把本地 UI 更新当保存成功。

## 7. 规则与多小题

```js
QF.defineQuestionType({
  type: 'example.short-answer',
  validate(question) {
    return question.answerSpec.data.referenceAnswer.trim() ? [] : ['请填写参考答案'];
  },
  validateAnswer(question, answer) {
    const valid = typeof answer.text === 'string';
    return {errors: valid ? [] : ['回答必须是文本'], empty: valid && !answer.text.trim()};
  },
  grade({question, answer, maxScore, reportResult}) {
    const right = answer.text.trim() === question.answerSpec.data.referenceAnswer.trim();
    return reportResult({score: right ? maxScore : 0});
  }
});
```

这是去掉首尾空白后完全匹配的教学示例，不是语义评分。reportResult 同步调用一次，分数处于 0～满分；null 表示未评分。不能返回 Promise，不能调用页面保存或网络/AI。附加 feedback 当前不作为桌面历史持久化评价承诺。

默认新建、复制、快照和单题目标由适配层完成。复合题使用 targets(question) 声明稳定目标 ID、局部 number、label、locked、gradable；应用统一生成导航编号，排除固定提示。不要用展示题号当答案身份。

## 8. 布局、白板与资源交互

```js
QF.page.configure({
  initialLayout: {
    cardWidth: 720, maxCardWidth: '100%',
    horizontalAlign: 'center', verticalAlign: 'center', padding: 20
  }
});
```

扩展可决定页面结构和样式，不强制使用模板题卡。宿主根据布局定位并恢复用户移动的位置，已保存几何优先。左右上一题/下一题保持宿主控件，其他公共 UI 可配置显示。

白板默认启用，右上角入口、工具和纸张设置由应用提供。关闭使用 `QF.page.configure({useDraft:false})`；清单 pageOptions 禁用后运行时不能越权打开。精简页未开放自行控制白板工具的命令。

富文本正文中复杂交互可以在 `await QF.content.renderAsync(node,content)` 后扫描标记、插入选择控件，再通过 save draft/submit 写答案。完形参考 [扩展实现](packages/cloze/practice-source.js) 与 [共享交互](shared/reading-type-practice.js)。已有富文本资源解析由公共组件负责；独立图片/视频接口暂未开放。

## 9. 预览、打包、热更新与发布

### 开发验证

“题型扩展 → 独立开发预览…”选择目录。测试使用临时上下文，不写正式题库/作答，正式题库管理和白板能力可能不可用；使用窗口提供的测试操作。规则、Schema、默认数据在这里更新检查。

### 独立打包

```powershell
node extensions/tools/pack.mjs D:/QuizForgeExtensions/my-type D:/QuizForgeExtensions/example.short-answer-1.0.0.qfext
```

输出在源码目录外。独立 pack.mjs 只需 Node 标准库，可以一起复制到仓库外。手写判断题无需编译。

仓库内其余七个题型改 *-source.js 或 shared 后先运行：

```powershell
node extensions/tools/build-page-extensions.mjs
node quizforge-desktop-app/editor-web/draft-canvas/scripts/build-extensions.mjs
```

### 安装和热更新

1. 安装扩展，核对权限、确认授权，重启。
2. 显式“加载开发目录…”连接与已安装 ID、版本和清单一致的源码。
3. 修改 HTML、运行 JS、CSS，主浏览区实时更新，保留编辑/作答/白板。
4. 编译式模板先更新运行 JS；只改 *-source.js 不会自动编译。
5. 规则、Schema、default.json 继续使用安装版本；要检查它们用独立开发预览。
6. 发布修改必须升级包 version，重新打包、导入与授权；保留历史所依赖的旧版本。

应用不会自动连接源码，不会把网页预览或测试答案写进真实工作区。完整空配置流程见 [安装与测试](README.md)。

## 10. 发布前最小检查

- 身份在清单、默认数据、Schema、规则和样例中一致。
- 样例是有效 .qbank，只有所属题型，资源完整。
- 编辑、恢复作答、提交、重试、只读展示和所需权限可用。
- 保存失败能提示；切换前等待输入写入；选中点在提交后保留。
- 不暴露未提交标准答案，不自建数据库、消息桥或评分保存旁路。
- 验证对应改动即可；不要把历史测试数量写成当前完整回归结果。

## 11. 新对话开发提示词

```text
在 QuizForge V2 开发一个独立题型扩展：[名称和要求]。
先读 extensions/DEVELOPMENT_GUIDE.md 和 SIMPLE_PAGE_API.md，
复制 packages/true-false 的 11 文件手写模板。
使用 SDK 2.3 / pageApi simple，通过 page.register 加载、save 保存、
requestAction 管理操作；不要使用旧业务命名空间。
题目与作答由 Schema 校验，规则同步 reportResult，
附带真实 examples/basic.qbank。
仅修改新扩展目录；缺少宿主能力先说明。使用独立测试数据，
不改真实工作区，不提交或推送。完成后给出打包路径和验证边界。
```
