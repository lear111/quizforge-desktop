# 公共 UI 功能接口（SDK 2）

> 2026-10-05。本文列出已经接入的接口。开发者用自己的 HTML/CSS 构建 UI，再调用这些方法；宿主继续负责数据、评分和模式权限。

规则执行失败通过 `EXTENSION_TIMEOUT`、`EXTENSION_FAILED`、`EXTENSION_UNAVAILABLE` 返回，不作为零分或错误答案保存。故障后的再次调用可重新启动原版本规则进程；详见 [运行故障隔离与当前边界](RUNTIME_ISOLATION.md)。

## 1. 返回与更新

异步方法使用 `await`，返回统一结果：

```js
{ ok:true, data:... }
{ ok:false, error:{code, message, retryable:false} }
```

`QF.ui.configure/getConfiguration/mountControls`、`QF.layout`、DOM 绑定和内容挂载为同步方法。`configure/getConfiguration` 也返回上述 Reply；DOM 方法操作当前页面节点。

`QF.host.subscribe(handler)` 订阅权威作答、交互能力、白板工具/纸张/缩放等变化，返回取消函数，页面释放时也会自动清理。处理函数只读取状态并更新 UI，不要无条件调用状态修改方法，以免循环更新。页面切换后旧页面不能继续操作新题。

异步 SDK 调用的传输失败也返回 Reply，不因超时或页面关闭直接抛异常：`INVALID_ARGUMENT` 表示参数数量、类型或 JSON 内容无效；`SDK_TIMEOUT` 表示未在期限内收到回执；`INVALID_REPLY` 表示宿主回执格式无效；`PAGE_CLOSED` 表示页面已撤销。DOM 绑定、内容挂载和拓展自己写的脚本仍可抛出普通 JavaScript 异常。

`PERMISSION_DENIED` 表示拓展未声明或未获宿主授权，操作未执行。授权后仍要满足当前模式、题目状态和宿主能力要求；历史只读等限制继续有效。`QF.host.getContext()` 返回 `permissions:{declared:[...],granted:[...]}`，能力标志同时考虑授权与宿主状态。

`DATA_VALIDATION_FAILED` 表示题目、作答或规则返回值未通过宿主数据检查；错误可含 `issues:[{path,message}]`，`path` 为字段 JSON Pointer。更新被拒绝时保留上次接受的数据，修正后可再次更新。完整规则见 [宿主数据校验](DATA_VALIDATION.md)。

`getContext().data.sdk` 返回 `{apiMajor:2,apiMinor:1,packageFormatVersion:2}`。宿主权限调整通过 `QF.host.subscribe` 通知已有页面，应重新读取 context 并更新按钮和输入的禁用状态。授权更新是宿主内部接口，未开放给题型页面自行调用。

参数必须是普通 JSON 数据，不接受函数、循环引用、BigInt、非有限数值或 Date 等特殊对象，并限制嵌套深度与序列化大小。保存、提交和切题等操作会先等待此前已发起的题目/答案更新；当前页面的更新按接收顺序执行。相同方法、相同参数的重复操作在等待期间共用一次请求和回执，完成后再次调用属于新操作。超时不表示宿主已经回滚，不要自动重发写操作，应先读取权威状态。

## 2. 编辑：保存、添加、复制、删除

| 接口                                         | 功能                                                                 |
| -------------------------------------------- | -------------------------------------------------------------------- |
| `QF.bank.getState()`                       | 当前题库编辑状态，含`index/count/questions/types/editable/sources` |
| `QF.bank.save()`                           | 等待当前页面输入完成，验证并保存整个题库                             |
| `QF.bank.addQuestion(type)`                | 用已安装题型模板新增题卡；宿主分配 ID，切换到新增题卡                |
| `QF.bank.duplicateQuestion()`              | 复制当前整张题卡及其资源，生成新的题目和选项 ID                      |
| `QF.bank.deleteQuestion()`                 | 删除当前整张题卡，选择相邻题卡；可删除最后一题                       |
| `QF.editor.getData()/update(patch)/save()` | 读取、更新、刷新当前编辑草稿；`save` 不等于正式题库落盘            |

这些接口只用于正式题库编辑会话。新增、复制和删除先 flush 当前编辑；删除接口直接执行，自己的 UI 需要确认时由开发者展示确认框。选择默认删除按钮仍有默认确认。练习、历史、网页预览不能编辑题库；独立开发预览没有正式题库命令。

`bank.getState().data.questions` 是 `{index,id,type,label}[]`；`types` 是 `{id,label}[]`。索引均从 0 开始。

## 3. 导航和自定义大纲

| 接口                                | 功能                                                         |
| ----------------------------------- | ------------------------------------------------------------ |
| `QF.navigation.getState()`        | 当前题目位置、题目摘要列表、来源与模式                       |
| `QF.navigation.goTo(index)`       | 跳到整张题卡，先完成当前编辑或练习保存屏障                   |
| `QF.navigation.previous()/next()` | 上一张/下一张题卡；边界外返回失败                            |
| `QF.practice.getState()`          | 当前公开题目信息：`index/total/type/state/maxScore/result` |

正式编辑、练习和历史都支持导航。宿主强制保留自己的上一题/下一题，开发者可以额外制作题目列表或导航控件。当前 API 按题卡索引跳转，不提供整轮统计页、历史作答次数或复合题的小题目标定位。

练习/历史的 `navigation.getState()` 返回：

```js
{
  index:0, count:2, learningMode:'PRACTICE',
  questions:[{index:0,id:'q_...',type:'SINGLE_CHOICE',state:'DRAFT'}, ...],
  sources:[{label:'文档 · 引用',message:'',navigable:true}, ...]
}
```

列表只包含公开摘要，不带其他题目的答案或解析。编辑状态可以读取编辑题目的完整内容；练习未提交时不能通过这些接口获得标准答案。

## 4. 来源

| 接口                         | 编辑                                | 练习 / 历史                                            |
| ---------------------------- | ----------------------------------- | ------------------------------------------------------ |
| `QF.sources.list()`        | 返回`{label,message,navigable}[]` | 返回当前宿主显示的来源；练习仍沿用提交后显示来源的规则 |
| `QF.sources.open(index)`   | 通过宿主打开来源                    | 通过宿主打开当前题目的来源；历史使用冻结来源快照       |
| `QF.sources.add(link)`     | 解析 Source Anchor 链接并添加       | 拒绝                                                   |
| `QF.sources.remove(index)` | 移除当前题目来源                    | 拒绝                                                   |

索引是 `list()` 中的位置。`navigable:false` 时自己的按钮应禁用。接口不接受任意本地路径，不改变来源解析规则。

## 5. 作答、分值和结果

| 接口                                       | 功能                                               |
| ------------------------------------------ | -------------------------------------------------- |
| `QF.answer.get()/update(answer)/flush()` | 读取、保存当前作答、等待保存完成                   |
| `QF.practice.getQuestion()`              | 题干、选项等当前模式允许读取的数据                 |
| `QF.practice.getResult()`                | 已提交的权威分值、判定及 reference；提交前为 null  |
| `QF.practice.submit()`                   | 完成作答刷新、草稿保存、评分和提交事务             |
| `QF.practice.retry()`                    | 重试当前已提交题目，保留已提交作答历史并开始新草稿 |
| `QF.content.render(node,content)`        | 公共普通文本/富文本/文档渲染，可用于自己的解析区   |

自己的提交按钮默认仍弹出宿主确认框；`QF.ui.configure({confirmation:false})` 后直接提交，并等待事务完成。自己的分值 UI 应读取 `getResult()`，不要自行写入权威分值。历史和网页预览返回 `READ_ONLY`，不能提交、重试或改答案。

`submit()` 成功返回 `{confirmationRequired, result}`：默认确认框已打开时为 `{confirmationRequired:true,result:null}`，此时尚未提交；直接提交完成时为 `{confirmationRequired:false,result:已保存的公开结果}`。`retry()` 完成后返回 `null`，最新状态通过 `getState()/getResult()` 读取。同一道题提交、重试保留页面实例并发送权威状态更新，拓展通过订阅更新显示内容，不必等待页面重新加载。

## 6. 切换草稿与练习

| 接口                                        | 功能                                                 |
| ------------------------------------------- | ---------------------------------------------------- |
| `QF.learning.getMode()`                   | 返回`PRACTICE`、`DRAFT`；编辑会话返回 `EDITOR` |
| `QF.learning.setMode('DRAFT'/'PRACTICE')` | 经宿主切换，保留草稿内容                             |
| `QF.learning.toggleMode()`                | 在练习与草稿模式之间切换                             |

只支持正式桌面练习及历史题卡。练习切换先完成保存；历史只改变查看模式。编辑或没有白板的网页预览不能切换。

## 7. 白板工具与缩放

| 接口                                    | 参数与含义                                                                                                  |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `QF.whiteboard.getState()`            | `learningMode/tool/readOnly/canEdit/canNavigate/canUndo/canRedo/zoom/paper`                               |
| `QF.whiteboard.setTool(tool)`         | `INTERACT` 选择、`PAN` 拖动、`PEN` 画笔、`ERASER` 橡皮、`LINE` 直线、`RECT` 矩形、`TEXT` 文本 |
| `QF.whiteboard.undo()/redo()/clear()` | 撤销、重做、清空笔迹与白板文本；不修改答案                                                                  |
| `QF.whiteboard.setAppearance(patch)`  | 部分更新`{color:'#fff7dc',pattern:'LINES'}`；样式支持 `PLAIN/DOTS/LINES/GRID`                           |
| `QF.whiteboard.setZoom(zoom)`         | 0.1–4 的倍率，1 为 100%；以视口中心缩放                                                                    |
| `QF.whiteboard.zoomBy(factor)`        | 正数倍率因子；例如 1.2 放大、1/1.2 缩小，结果限制在 0.1–4                                                  |

正常练习模式固定视口并禁止修改笔迹。草稿模式按当前作答状态允许修改；已提交题目不能修改笔迹，仍可拖动查看。历史草稿只允许 `PAN` 和缩放，不能更改纸张、文本和笔迹。所有修改复用原白板模型和自动保存，提交仍冻结快照。

`canEdit/canNavigate/canUndo/canRedo` 用于自己的控件禁用状态。无白板宿主返回 `CAPABILITY_UNAVAILABLE`；只读历史修改返回 `READ_ONLY`；固定模式或保存期间返回 `CAPABILITY_DENIED`。非法参数返回失败，不修改文档。

### 自己的工具栏

`practice.html`：

```html
<nav data-tools>
  <button type="button" data-switch>切换草稿</button>
  <button type="button" data-pan>拖动</button>
  <button type="button" data-pen>画笔</button>
  <button type="button" data-select>选择</button>
  <button type="button" data-undo>撤销</button>
  <button type="button" data-zoom>放大</button>
  <output data-scale></output>
</nav>
```

`practice.js`：

```js
QF.ui.configure({draftToggle:false,draftToolbar:false,draftZoom:false});
QF.ui.mountControls(QF.dom.$('[data-tools]'));

function bind(selector,action){
  QF.dom.on(QF.dom.$(selector),'click',async()=>{
    const reply=await action();
    if(!reply.ok)QF.ui.notify(reply.error.message);
  });
}
bind('[data-switch]',()=>QF.learning.toggleMode());
bind('[data-pan]',()=>QF.whiteboard.setTool('PAN'));
bind('[data-pen]',()=>QF.whiteboard.setTool('PEN'));
bind('[data-select]',()=>QF.whiteboard.setTool('INTERACT'));
bind('[data-undo]',()=>QF.whiteboard.undo());
bind('[data-zoom]',()=>QF.whiteboard.zoomBy(1.2));

async function refreshTools(){
  const reply=await QF.whiteboard.getState();
  const s=reply.ok?reply.data:null;
  QF.dom.$('[data-pen]').disabled=!s?.canEdit;
  QF.dom.$('[data-select]').disabled=!s||s.readOnly;
  QF.dom.$('[data-pan]').disabled=!s?.canNavigate;
  QF.dom.$('[data-undo]').disabled=!s?.canUndo;
  QF.dom.$('[data-zoom]').disabled=!s?.canNavigate;
  QF.dom.$('[data-scale]').textContent=s?Math.round(s.zoom*100)+'%':'';
}
QF.host.subscribe(refreshTools);
await refreshTools();
```

`mountControls` 标记当前题型页面中的宿主工具区，使它在画笔/拖动模式下仍可点击，而且点击不会被当成白板绘画。不要把答题区整体标为工具区；答题控件仍受当前交互能力限制。该工具区随题卡布局，API 不创建额外悬浮窗口。

## 8. 题卡与浏览区布局

```js
QF.layout.configure({
  cardWidth:720,
  maxCardWidth:'90%',
  horizontalAlign:'center',
  verticalAlign:'center',
  padding:24
});
const settings=QF.layout.getConfiguration();
const actual=QF.layout.getState();
```

三个方法均同步返回 Reply。`configure` 支持局部更新；未知字段或非法值返回 `INVALID_LAYOUT`，不部分应用配置。页面释放后返回 `PAGE_CLOSED`。

| 字段                | 支持值                                             | 默认值                             |
| ------------------- | -------------------------------------------------- | ---------------------------------- |
| `cardWidth`       | 64–8192 的数值，逻辑像素                          | 720                                |
| `maxCardWidth`    | 64–8192 的数值，或大于 0、不超过`100%` 的百分比 | `100%`                           |
| `horizontalAlign` | `left` / `center` / `right`                  | `center`                         |
| `verticalAlign`   | `top` / `center` / `bottom`                  | 编辑页`top`，其他页面 `center` |
| `padding`         | 0–256 的数值，浏览区四周留白                      | 20                                 |

编辑、练习、草稿、历史、独立开发预览和 Hub 预览均可声明布局。编辑和普通网页使用自然排版及响应式宽度；桌面练习使用固定 World 题卡及计算出的阅读视口。题卡高度随内容增长；长题卡始终从可读的顶部开始，并通过浏览区滚动查看。

**草稿保护规则：** `cardWidth` 是首次创建草稿时的逻辑宽度；数值 `maxCardWidth` 同时限制初始逻辑宽度。百分比上限限制练习和首次草稿视口的可见宽度，通过等比缩放适配窄窗口，不随窗口尺寸反复改写 World。已保存的题卡宽度、坐标、笔迹和草稿视口优先，布局声明及源码热更新不会重新排放它们。阅读模式仍可按新的对齐和留白配置计算临时视口；切回草稿恢复保存的视口。历史布局调整只影响查看，不改写快照。

`getConfiguration().data` 是本页声明的完整配置；`getState().data` 包含 `configuration`、当前实际逻辑 `cardWidth` 和 `hasSavedGeometry`。声明宽度与已保存宽度可能不同，界面应以实际状态为准。自然排版页的 `cardWidth` 为当前显示宽度，`hasSavedGeometry` 为 false。

配置随当前页面生命周期存在，不写入 `.qbank`。新建和切题使用该题型的声明；重试保留当前页面的声明；缺省配置由宿主提供。白板底色/样式继续使用白板接口，左右导航、工作区和标签栏继续由宿主管理。

## 9. 扩展权限

每个 `manifest.types[]` 声明 `permissions` 数组；有效授权是声明与宿主批准项的交集。未声明、未批准均返回 `PERMISSION_DENIED`。权限不会解除历史、预览、已提交状态及正式编辑会话的限制。

| 权限                      | 受保护接口                                                               |
| ------------------------- | ------------------------------------------------------------------------ |
| `question.edit`         | `editor.update/save`、富文本组件内部的格式编辑请求（`content.edit`） |
| `bank.save`             | `bank.save`                                                            |
| `bank.add`              | `bank.addQuestion`                                                     |
| `bank.duplicate`        | `bank.duplicateQuestion`                                               |
| `bank.delete`           | `bank.deleteQuestion`                                                  |
| `answer.write`          | `answer.update/flush`                                                  |
| `practice.submit`       | `practice.submit`                                                      |
| `practice.retry`        | `practice.retry`                                                       |
| `navigation`            | `navigation.goTo/previous/next`                                        |
| `sources.open`          | `sources.open`                                                         |
| `sources.manage`        | `sources.add/remove`                                                   |
| `learning.mode`         | `learning.setMode/toggleMode`                                          |
| `whiteboard.tools`      | `whiteboard.setTool`                                                   |
| `whiteboard.history`    | `whiteboard.undo/redo`                                                 |
| `whiteboard.clear`      | `whiteboard.clear`                                                     |
| `whiteboard.appearance` | `whiteboard.setAppearance`                                             |
| `whiteboard.zoom`       | `whiteboard.setZoom/zoomBy`                                            |

读取当前题目、当前公开答案/结果、当前来源与会话状态，页面自身 DOM、布局和公共 UI 可见性配置不请求额外操作权限；其原有模式检查仍生效。隐藏或显示公共按钮不会给拓展代码新增授权，用户点击宿主按钮仍走宿主自己的操作流程。

安装时展示请求列表并允许取消各项授权。宿主把授权保存在扩展存储根目录的 `.permissions.json`，按 `id@version#sha256` 绑定包；包内同名文件或 bundle 元数据不会获得正式授权。确认后包内容变化会拒绝安装，需重新确认。直接程序安装而未批准权限时，受保护接口默认拒绝。新安装版本重启后启用；已有版本的授权调整立即生效。

主浏览区热更新保留当前安装授权，不允许通过修改清单或 bundle 扩大权限。独立开发预览仅在临时测试上下文采用声明权限；网页预览继续只读。已安装版本提供“权限…”界面，可撤销/恢复部分或全部声明权限；不会把旧版本授权传给新版本。权限变更不重新挂载题卡，保留题目、作答和白板内容；尚未执行的调用使用新权限，已经开始的操作不保证回滚。

## 9.1 SDK 版本约束

当前 SDK 为 2.1。清单顶层 `sdkApiMajor` 必须为 2，可选 `minSdkApiMinor` 为非负整数，缺省为 0。次版本只能增加兼容接口；新扩展若依赖 2.1 的能力，应声明 `minSdkApiMinor:1`。`QF.host.getContext()` 可用于界面展示和能力判断，但不代替安装时的兼容检查。

未来的主版本、包格式或高于宿主的最低次版本会拒绝加载，提示更新应用；旧主版本或旧包格式提示更新扩展。拒绝发生在扩展脚本执行前。打包工具、桌面安装/开发目录和网页 SDK 使用相同规则；Hub 预览也会拒绝不兼容的包，不默默降级运行。

## 10. 范围

题型页面在隔离 iframe 中运行。`QF.dom.root`、`document` 和 `window` 均属于当前拓展页面；不能直接访问宿主导航、白板 DOM 或原生桥。需要应用能力时调用 `QF.*`。SDK 校验消息窗口来源及页面会话，仅分派已登记的方法，销毁页面后拒绝旧请求。

桌面题型 iframe 位于独立可终止进程，主界面显示其画面并转发输入；同步死循环不会占用主应用的 JavaFX 线程。页面终止后按原权限与宿主保存的数据重新加载，接口语义不变。纯网页宿主的 iframe 未获得此进程保障。限制和恢复规则见 [运行故障隔离](RUNTIME_ISOLATION.md)。

`QF.layout` 与 `QF.ui` 的配置方法保留同步返回值：先校验本页配置，再异步通知宿主。`QF.layout.getState()` 为最近一次宿主返回的实际状态；如需在布局变化后读取实际尺寸，应在 `QF.host.subscribe` 回调中读取。切题、复制、新增、删除或热更新可以替换页面。旧页面立即失去调用能力，只暂时保留已受理操作的回执通道，收到回执确认后移除；排队但尚未执行的旧写请求返回 `PAGE_CLOSED`。操作回调可检查回执，但不要继续依赖旧 DOM。交接超过期限会关闭通道，不能保证已离开的页面继续执行脚本。

以上接口没有新增文件、数据库或全局 AI 权限。AI 和异步评分仍按独立接口设计后续接入。工作区、标签栏、历史列表、作答次数导航和整轮统计属于应用界面，本轮没有开放重新实现它们的接口。

默认 UI 可见性配置见 [SDK 说明](SDK_README.md#32-扩展选择公共-ui)。框架更新后需要重新打开应用；之后编辑已连接的题型 HTML/JS/CSS 仍支持主浏览区热更新。
