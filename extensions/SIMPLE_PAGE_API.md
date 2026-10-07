# 题型页面接口参考（SDK 2.3）

更新日期：2026-10-07。**本文是当前 `pageApi: "simple"` 页面接口的现行参考。** 包版本、SDK 版本、题目数据版本是三个独立概念。八个示例包均为 2.3.2；宿主 SDK 为 2.3，安装包格式为 2。

新开发先读 [开发指南](DEVELOPMENT_GUIDE.md)，复制 [判断题模板](QUESTION_TEMPLATE.md)。后续目标见 [精简接口设计草案](../docs/题型拓展精简接口方案.md)；草案不代表已实现能力。已废弃的页面接口不再单独提供开发文档。

## 1. 职责与全部公开入口

扩展负责编辑页、练习页的 HTML/CSS/JS，以及数据 Schema、校验和评分规则。历史、预览复用练习页。应用负责类型识别、加载、题库和答案保存、尝试记录、导航、权限、默认白板和最终得分卡。数据库、JSON 文件等来源均先由应用转换为页面上下文，页面不直接连接数据库。

| 接口 | 类型 | 用途 |
| --- | --- | --- |
| `QF.page.register(hooks)` | 异步 | 注册唯一加载入口及可选离开、销毁回调 |
| `QF.save({purpose,data})` | 异步 | 编辑草稿、正式题库、作答草稿、正式提交 |
| `QF.requestAction({action,params?})` | 异步 | 题库管理、题目/尝试导航、重试、来源、学习模式 |
| `QF.page.configure(options)` | 同步 | 默认白板、题卡外壳及初始布局 |
| `QF.dom` / `QF.ids` | 同步辅助 | 找元素、监听事件、生成选项等局部 ID |
| `QF.content` | 内容组件 | 公共内容渲染和编辑 |
| `QF.ui` / `QF.layout` | 同步辅助 | 公共 UI 显示、消息及布局 |

以上是页面实际暴露的全部命名空间。**页面没有 `QF.host/editor/answer/bank/practice/navigation/sources/learning/whiteboard`。** 旧页面 API 对象、旧方法分发入口及兼容分支已从运行代码移除。宿主内部仍保留保存、评分、导航等私有服务，只有结构化接口能够调用，扩展不能直接请求这些私有方法。

当前运行时只加载声明 `pageApi: "simple"` 的页面；旧扩展会显示升级提示。已有题库数据、资源和版本化安装包不会因此删除，但依赖旧页面接口的历史无法直接回放，需要对应的兼容数据方案与新版扩展。题库数据格式的兼容与页面接口的兼容是两回事。

规则文件使用另一套运行时，只提供 `QF.defineQuestionType` 等规则入口，不拥有页面 DOM 和上述保存接口。规则接口见第 8 节。

### 清单声明

在 `manifest.json` 顶层声明 SDK，在 `types[]` 对应题型内声明页面 API：

```json
{
  "packageFormatVersion": 2,
  "sdkApiMajor": 2,
  "minSdkApiMinor": 3,
  "types": [{
    "pageApi": "simple"
  }]
}
```

这是字段位置示意，不是完整可安装清单；完整清单复制模板。受保护操作必须在 `types[].permissions` 声明并由用户授权。

### 返回值与异常

保存、操作返回：

```js
{ok: true, data: {status: 'draft'}}
{ok: false, error: {code: "错误码", message: "可显示的提示"}}
```

`register` 成功直接返回初次上下文；注册失败、重复注册和非法回调会抛异常。可选 onBeforeLeave/onDispose 若提供，必须是函数（undefined 视为未提供）；注册时立即校验，并保存回调快照。`configure` 返回同步 Reply。DOM 操作、组件挂载、扩展自身脚本仍可抛普通 JS 异常，不应假定所有函数都返回 Reply。

`save` 成功表示宿主已接受该用途的数据，不代表每种用途都把题库文件写到磁盘。修改 DOM 本身不表示保存成功。

## 2. 统一加载与生命周期

```js
let context;
await QF.page.register({
  onLoad(next) {
    context = next;
    QF.dom.$('[data-prompt]').textContent = next.question.data.prompt.text;
    // 首次创建输入控件，后续恢复数据和权限；避免重复绑定事件。
  },
  onBeforeLeave() {
    // 立即通过 QF.save 保存输入的页面，不需要再次提供待保存内容。
    return {ok: true, data: {pendingSave: null}};
  },
  onDispose() {
    // 清理扩展自己创建的计时器、观察器、组件等。
  }
});
```

| 回调 | 调用与返回 |
| --- | --- |
| `onLoad(context)` | 必需；注册时及后续状态同步调用；可以异步；返回 `{ok:false,error}` 会报加载错误 |
| `onBeforeLeave({reason})` | 可选；当前 reason 为 `"navigation"`，提交/保存屏障也复用；可阻止操作或提供待保存草稿 |
| `onDispose()` | 可选；页面释放时调用；自行清理资源 |

`onLoad` 不是“每次一定创建一个全新网页”的承诺。同一回调处理状态、权限、作答恢复等变化；切题和切换尝试由宿主准备对应页面。不要依赖 iframe 的复用与否，也不要自行导航 WebView。

### 上下文字段

| 字段 | 实际含义与边界 |
| --- | --- |
| `contextId / revision` | 页面作用域和数据版本，SDK 自动附在写请求上 |
| `reason` | 当前实现固定为 `"stateChanged"`；尚未细分初次加载、切题等原因 |
| `mode` | 小写 `editor / practice / history / preview` |
| `learningMode` | 正常学习页为 `practice / draft`；编辑页没有白板，不据此判断是否编辑 |
| `question` | `{id,type,dataVersion,maxScore,data}` |
| `question.data` | 编辑为完整 Question；练习为公开投影，提交前不含标准答案和解析 |
| `attempt` | 编辑时为 null；其他场景为 `{id,status,answer,result}`；ID 不可用于任意记录跳转 |
| `reference` | 编辑或已提交时提供答案/解析；提交前为空 |
| `permissions` | 当前可执行能力，字段见下表 |
| `grantedPermissions` | 当前包身份、版本和哈希绑定的实际授权名称数组 |
| `navigation` | `index / total`、宿主提供的题目/尝试状态及全局 `targets` |
| `sources` | 当前作用域来源数组；打开/移除使用其当前位置 index |
| `types` | 宿主允许新建的 `{id,label}` 题型列表 |
| `resources` | 当前为预留空数组；已有文档资源通过 `QF.content` 解析 |

`question.data` 不必在每种模式都满足编辑用完整题目 Schema：公开投影有意去掉受限字段。页面应按模式读取展示字段；完整数据和用户答案的强制校验由宿主执行。

| `permissions` 字段 | 用途 |
| --- | --- |
| `editQuestion` | 编辑题目及调用内容编辑器 |
| `writeAnswer` / `editAnswer` | 可修改当前用户作答；精简页优先用 writeAnswer |
| `submit / retry` | 当前是否允许正式提交/重试 |
| `saveBank / manageQuestions` | 整库保存及正式编辑会话可用性；具体增删还须检查授权 |
| `navigate` | 当前导航能力 |
| `manageSources / viewSources` | 管理/查看来源 |
| `changeLearningMode / whiteboard` | 学习模式切换和宿主白板能力 |
| `layout` | 布局能力 |
| `ai / submitScore` | 当前均为 false |

历史、网页预览、浏览过去尝试、已提交状态和白板画笔/手型操作可能暂时关闭写入。按钮展示检查模式与授权，按钮可用性检查当前能力；**暂时不可操作只禁用，避免隐藏按钮造成题卡抖动**。宿主仍独立执行权限检查。

## 3. 保存接口

```js
const reply = await QF.save({
  purpose: 'draft',
  data: {answer: {selectedOptionIds: [option.id]}}
});
if (!reply.ok) QF.ui.notify(reply.error.message);
```

| purpose | data | 效果与权限 |
| --- | --- | --- |
| `editDraft` | `{questionData:完整Question}` | 更新编辑会话，未正式写入题库文件；question.edit |
| `edit` | `{questionData:完整Question}` | 先更新编辑会话，再保存整库；question.edit + bank.save |
| `draft` | `{answer:题型作答对象}` | 保存当前未提交作答；answer.write |
| `submit` | `{answer:题型作答对象}` | 先保存答案，再调用规则评分和尝试事务；answer.write + practice.submit |

`questionData` 不可改变当前题目 ID 或 type；新增/复制通过操作请求让宿主分配身份。作答对象必须符合当前包的答案 Schema。标准答案属于 Question 的 answerSpec，与用户作答不同。

`submit` 使用宿主提交流程；保留默认确认 UI 时，成功 Reply 可能包含 `confirmationRequired:true`，不能直接认定已有成绩。自定义提交确认应配置 `confirmation:false`，确认后再调用 submit。最终结果以 `onLoad` 返回的 `attempt.result` 为准。

编辑草稿不是练习草稿，编辑草稿与整库落盘也不是同一操作。未提交作答的 draft 不生成正式已提交尝试；正式记录在提交事务成功时创建。重试保留旧尝试，后续再次提交才生成新记录。无需单独的 saveAttempt 接口。

### 写入顺序与离开检查

输入变化时立即调用 `save`，不要用延迟定时器发送必要写入。SDK 串行处理临时写入，正式保存、提交、导航等先等待已有写入和离开回调。

需要在离开时保存的页面可以返回：

```js
return {
  ok: true,
  data: {pendingSave: {purpose: 'draft', data: {answer: currentAnswer}}}
};
```

这里只允许 `draft / editDraft`；不要在回调里再次提交、正式保存整库或导航，以免递归进入同一屏障。可返回 `{ok:false,error:{code,message}}` 阻止离开。

失败时宿主保留上次接受的数据。页面决定保留输入让用户修正或回退，但不得显示虚假的已保存状态。草稿写入失败会阻止后续导航，之后成功写入可恢复。文本编辑还应协调待保存输入和状态刷新，避免旧上下文覆盖新输入。

### 请求身份与去重边界

SDK 自动添加 `requestId/contextId/revision`，普通调用不手填。重新发送同一请求必须保留完整原请求；相同 ID 改变参数会被拒绝。

当前去重回执是**页面上下文内的内存缓存，最多 1024 项**，不是跨关闭、跨重启的永久查询接口。成功写入推进 revision；已清理的旧回执再次写入通常会被版本检查拒绝。超时不代表回滚，不要盲目生成新 ID 重发提交。

## 4. 操作请求白名单

```js
await QF.requestAction({action: 'addQuestion', params: {type: 'TRUE_FALSE'}});
await QF.requestAction({action: 'goToQuestion', params: {direction: 'next'}});
await QF.requestAction({action: 'retry'});
```

| action | params | 使用场景/权限 |
| --- | --- | --- |
| `saveBank` | 无 | 编辑：bank.save，保存整个编辑会话 |
| `addQuestion` | `{type,position?:"afterCurrent"}` | 编辑：bank.add，在当前题后新增 |
| `duplicateQuestion` | 无 | 编辑：bank.duplicate，复制整题和资源 |
| `deleteQuestion` | 无 | 编辑：bank.delete；页面自行做删除确认 |
| `moveQuestion` | `{beforeQuestionId:string\|null}` | 编辑：bank.move，整题移动，null 为末尾 |
| `goToQuestion` | `{direction:"previous"\|"next"}` 或 `{questionId}` | navigation；不能同时指定两种定位；练习末尾 next 进入总结 |
| `goToAttempt` | `{direction:"previous"\|"next"\|"current"}` | navigation；练习/历史，历史无 current |
| `returnToCurrentAttempt` | 无 | navigation；练习中回到当前作答 |
| `setLearningMode` | `{mode:"practice"\|"draft"}` | learning.mode；练习/历史，不改变答案和笔迹 |
| `retry` | 无 | practice.retry；当前已提交练习，旧记录保留 |
| `addSource` | `{link}` | 编辑：sources.manage，宿主解析链接 |
| `removeSource` | `{index}` | 编辑：sources.manage |
| `openSource` | `{index}` | sources.open，当前上下文的来源列表位置 |

操作参数只接受规定字段，不接收 SQL、表名、任意工作区路径。导航目标仍受宿主当前范围和状态约束。

尚未开放：任意 attemptId 跳转、`goToQuestion.targetId` 小题聚焦、`setPageMode` 请求切到编辑页、练习中的扩展整题移动、稳定 sourceId。宿主自己的模式切换与大纲跳转可用。

## 5. 页面配置、辅助接口与默认白板

### 页面配置

```js
QF.page.configure({
  useDraft: true,
  initialLayout: {
    cardWidth: 720, maxCardWidth: '100%',
    horizontalAlign: 'center', verticalAlign: 'center', padding: 20
  }
});
QF.ui.configure({submit: false, retry: false, confirmation: false});
```

| 配置 | 规则 |
| --- | --- |
| `useDraft` | 不声明默认启用；false 隐藏公共草稿入口、工具栏和缩放 |
| `card` | 是否使用宿主可选题卡外壳；精简练习页默认无宿主外壳 |
| `initialLayout` | 只接受下表布局字段 |

题型清单可写 `pageOptions:{useDraft:false}`，运行时不能越过此禁用设置。隐藏入口不删除保存的白板。默认左右导航由应用保留，不能通过配置关闭。

白板是公共组件，位置、底色、纸张样式、工具和持久化均由应用管理。精简接口没有自定义白板工具命令；使用应用默认工具。练习保留纸张背景和已有笔迹，关闭白板编辑；历史答案和白板只读。首次默认纯白、无纹理，之后恢复保存设置。

样例中的白色圆角题卡由包内 CSS 提供，可修改或移除。已保存的草稿位置/尺寸优先于初始布局。

### 布局与 DOM

| 方法/字段 | 返回或范围 |
| --- | --- |
| `QF.layout.configure(patch)` | 同步 Reply，部分更新布局 |
| `QF.layout.getConfiguration()` | 同步 Reply，读取声明配置 |
| `QF.layout.getState()` | 同步 Reply，读取实际布局与恢复信息 |
| `cardWidth` | 数字 64～8192 |
| `maxCardWidth` | 数字 64～8192，或大于 0 且不超过 100% 的百分比字符串 |
| `horizontalAlign` | left / center / right |
| `verticalAlign` | top / center / bottom |
| `padding` | 数字 0～256 |
| `QF.dom.root / $(selector)` | 页面根节点/匹配元素，不返回 Reply |
| `QF.dom.on(node,event,handler)` | 绑定事件，返回取消监听函数 |
| `QF.ids.create(prefix?)` | 返回局部身份字符串，默认前缀 opt_；不用于自行替换题目 ID |
| `QF.ui.notify(message)` | 显示消息 |
| `QF.ui.configure(patch) / getConfiguration()` | 同步 Reply，显示配置 |
| `QF.ui.mountActions(node)` | 标记公共操作区 |
| `QF.ui.mountControls(node)` | 标记画笔/手型模式仍可操作的工具区；普通答题区不要这样标记 |

编辑 UI 可配置：title、save、position、typeLabel、add、duplicate、delete、sources、outline、errors。练习/历史/预览 UI 可配置：card、typeLabel、position、score、state、submit、retry、confirmation、note、sources、outline、draftToggle、draftToolbar、draftZoom、errors。值只能为布尔值；这些只控制宿主 UI，不会自动隐藏扩展自己创建的节点或提升权限。

## 6. 公共内容与富文本内交互

| 方法 | 实际行为 |
| --- | --- |
| `QF.content.render(node,content)` | 发起渲染并立即返回 node；不能据此认定异步文档已完成 |
| `QF.content.renderAsync(node,content)` | Promise，内容完成后返回 node；装饰文档时应 await |
| `QF.content.mountEditor(node,{value,onChange,formatting?})` | 仅编辑模式；返回 `{getValue(),destroy()}`，formatting 默认 true |

已有内容为 TEXT、RICH、DOCUMENT，资源引用和解码由宿主处理。`mountEditor` 自动响应编辑权限；onChange 收到完整的新内容对象，扩展须调用 editDraft 保存它。提前移除组件时调用 destroy，页面释放也会自动清理。

**当前公共 mountEditor 仅用于题目编辑模式，不能直接挂到练习答案区。** 作文的作答编辑交互由作文页面自身实现，答案通过 draft/submit 保存。独立图片、视频公共接口仍未提供。

富文本内的题型交互可由拓展装饰渲染后的 DOM：

```js
const body = QF.dom.$('[data-article]');
await QF.content.renderAsync(body, context.question.data.prompt);
// 后续由扩展扫描正文标记、插入 select 等控件、绑定事件。
// 选择变化后调用 QF.save({purpose:'draft', data:{answer:...}})。
// 解析资源、渲染内容与选择交互是三件不同的事。
```

完形示例实现位置：[practice-source.js](packages/cloze/practice-source.js) 及 [共享页面交互](shared/reading-type-practice.js)。只编辑扩展代码即可调整标记与选择交互；无需把题型规则加入宿主。

## 7. 权限、校验与常见错误

### 权限声明

| 权限 | 对应能力 |
| --- | --- |
| question.edit | editDraft、edit 更新题目及公共内容编辑 |
| bank.save / add / duplicate / delete / move | 同名题库操作 |
| answer.write | draft、submit 的答案更新 |
| practice.submit / retry | 正式提交/重试 |
| navigation | 题目和尝试导航 |
| sources.open / sources.manage | 打开/编辑来源 |
| learning.mode | 练习/草稿切换 |
| whiteboard.tools / history / clear / appearance / zoom | 宿主白板工具的既有权限名称；不是精简页新增方法 |

权限省略默认拒绝受保护操作，未知权限拒绝打包/安装。授权绑定包 ID、版本、哈希；声明不能自行授予权限。HTML 位于隔离 iframe，规则位于受限进程；页面不能直接访问原生桥、文件、数据库或绕过网络边界。完整沙箱和计算预算见 [后端](WEBVIEW2_BACKEND.md)、[规则隔离](RUNTIME_ISOLATION.md)、[数据校验](DATA_VALIDATION.md)。

| 错误码 | 含义/处理 |
| --- | --- |
| INVALID_REQUEST / INVALID_ARGUMENT | 请求或参数不合法，修正后重试 |
| ACTION_UNAVAILABLE / CAPABILITY_UNAVAILABLE | 操作或当前宿主能力不可用 |
| PERMISSION_DENIED / CAPABILITY_DENIED / READ_ONLY | 授权、模式或状态不允许 |
| CONTEXT_EXPIRED / PAGE_CLOSED | 页面已切换/关闭，不继续写旧上下文 |
| REVISION_CONFLICT | 请求版本过期，等待新上下文，不强行覆盖 |
| DATA_VALIDATION_FAILED | Schema 或规则返回值不合法；可能附带 issues 的 path/message |
| BUSY | 正在执行命令或请求过多，避免重复点击 |
| BEFORE_LEAVE_FAILED | 离开前校验/保存失败，修正输入 |
| SDK_TIMEOUT / INVALID_REPLY | 通信超时/回执异常，不假定写入已回滚 |
| EXTENSION_TIMEOUT / EXTENSION_FAILED / EXTENSION_UNAVAILABLE | 规则超时、失败或不可用，不把故障当 0 分保存 |

错误集不是固定封闭枚举，底层保存等流程还可能返回业务错误码。必须检查 ok，显示 message，不依赖只有表内错误才会发生。

## 8. 规则接口、评分与多小题

```js
QF.defineQuestionType({
  type: 'TRUE_FALSE',
  validate(question) { return []; },
  validateAnswer(question, answer) {
    return {errors: [], empty: !answer.selectedOptionIds?.length};
  },
  grade({question, answer, maxScore, reportResult}) {
    const right = answer.selectedOptionIds[0] === question.answerSpec.correctOptionIds[0];
    return reportResult({score: right ? maxScore : 0});
  }
});
```

这是入口示意，完整校验参照 [判断题规则](packages/true-false/type.js)。评分只读取应用提供的冻结题目和答案，不从 DOM 读选中状态。

| 规则字段/方法 | 必需性/用途 |
| --- | --- |
| `type` | 必需，与清单/默认数据一致 |
| `grade(ctx)` | 必需，同步调用 reportResult 一次 |
| `validate(question)` | 可选，返回错误字符串数组；Schema 校验仍强制执行 |
| `validateAnswer(question,answer)` | 可选，返回 `{errors,empty}`；不提供时默认按空对象判断 |
| `targets(question)` | 可选，返回子题目标数组；默认一题一个可评分目标 |
| `maxScore(question)` | 可选，覆盖默认满分计算 |
| `publicPayload(question)` | 可选，自定义公开展示载荷；宿主仍执行公开投影检查 |
| `allocateQuestion(question,ids)` | 可选，自定义新建/复制的身份重分配；通常使用默认处理 |

`reportResult({score,feedback?})` 只暂存评分，应用完成校验和事务后持久化分数。score 为有限数字且处于 0～满分，或为 null 表示 UNSCORED。满分生成 CORRECT，其余数字分生成 INCORRECT。可选 feedback 当前不保存到桌面历史，不应当作持久化评价接口。规则不可返回 Promise；页面直传分数、submitScore、AI 评分均未开放。

复合题在 targets 中声明稳定 ID、局部 number、label、locked、gradable。应用按题库顺序计算全局编号，固定提示不编号、不计分。页面从 `navigation.targets` 获取当前宿主提供的目标编号；global number 不作为保存身份，不强制假定每个宿主都提供 localNumber。字段约束见 [数据校验](DATA_VALIDATION.md)。

## 9. 示例、源码与构建

所有精简包必须在题型清单声明 1～16 份真实 `.qbank` 样例。每份样例只包含所属题型，ZIP/资源格式与正式题库一致。`default.json` 用于新建；`examples/basic.qbank` 用于展示，不自动导入工作区。

| 包 | 当前包版本 | 维护入口 |
| --- | --- | --- |
| true-false | 2.3.2 | 11 个文件；直接维护 editor.js、practice.js、type.js，无编译和包内客户端库 |
| single-choice / multiple-choice | 2.3.2 | 页面维护 *-source.js，构建到运行脚本 |
| cloze / reading / matching / translation / essay | 2.3.2 | *-source.js 与 extensions/shared，构建到独立脚本 |

```powershell
# 手写判断题或仓库外独立包：直接打包，只需 Node 标准库。
node extensions/tools/pack.mjs ./my-type ./publisher.my-type-1.0.0.qfext
# 仓库内需要编译的样例：已有 esbuild 依赖后构建。
node extensions/tools/build-page-extensions.mjs
# 更新网页预览及分发 bundle。
node quizforge-desktop-app/editor-web/draft-canvas/scripts/build-extensions.mjs
```

手写页没有 *-source.js 时，页面构建工具直接打包，不覆盖其 JS。其余七型仍需构建输出。打包器检查路径、ZIP、大小、资源哈希和所属题型；完整样例 Schema 校验由原生安装和宿主执行。

安装、授权、独立开发预览、开发目录热更新的操作见 [开发指南](DEVELOPMENT_GUIDE.md)。发布修改必须增加包版本，同 ID/版本不能覆盖不同内容；历史依赖的旧版本包继续保留。

## 10. 实现定位与维护

| 源码 | 职责 |
| --- | --- |
| [simple-client.js](../quizforge-desktop-app/editor-web/draft-canvas/src/extensions/simple-client.js) | register、保存队列、离开屏障、页面配置 |
| [simple-api.js](../quizforge-desktop-app/editor-web/draft-canvas/src/extensions/simple-api.js) | 保存用途、操作白名单、版本与回执校验 |
| [frame-client.js](../quizforge-desktop-app/editor-web/draft-canvas/src/extensions/frame-client.js) | 隔离页实际公开的 QF 对象、内容组件 |
| [html-ui.js](../quizforge-desktop-app/editor-web/draft-canvas/src/extensions/html-ui.js) | 上下文构造及宿主能力适配 |
| [permissions.js](../quizforge-desktop-app/editor-web/draft-canvas/src/extensions/permissions.js) | 权限名称与方法映射 |
| [rules-runtime.js](../quizforge-desktop-app/editor-web/draft-canvas/src/extensions/rules-runtime.js) | 规则方法、身份分配、同步报告分数 |

新增接口时同步修改宿主白名单、页面公开对象、权限及数据校验、定向测试和本文。文档更新不表示对应设计项自动实现。

### 2026-10-07 审查修复

- 八个示例包统一发布 2.3.2；源码改变必须升级包版本，旧版本哈希不可覆盖。
- 作文/翻译的富文本答案恢复先在隐藏容器渲染，完成并确认版本后一次性提交；期间禁用输入和读取，过期渲染不能覆盖新答案。
- 安装样例只使用候选包自己的 Schema，通用 ZIP/资源校验照常执行；不调用已安装旧版规则。
- 已删除宿主旧 Canvas 完形/翻译交互链和 question/compat；公共富文本保留，复杂交互由扩展实现。旧专项 kind 文件需手动迁移，用户原文件不会被清除。
