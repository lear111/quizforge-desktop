# Draft Canvas Core v1 POC

独立白板技术验证。无需题库、Workspace、Spring、Vite 服务或运行时网络；不接入正式 Practice。

真实单选/多选题与持久化入口和架构说明见 [SHARED_PRACTICE.md](SHARED_PRACTICE.md)，静态 Renderer Contract 见 [SHARED_RENDERER_CONTRACT.md](SHARED_RENDERER_CONTRACT.md)。本页仍说明原独立白板入口。
当前几何、布局、操作顺序及未来生命周期合同见 [DRAFT_CANVAS_CONTRACT.md](DRAFT_CANVAS_CONTRACT.md)。

正式 Practice 单选/多选 TEXT 题现已支持右上角“草稿 / 退出草稿”原地切换，见 [PRACTICE_DRAFT_MODE.md](PRACTICE_DRAFT_MODE.md)。本页描述的独立白板仍是开发工具。

正式 History Detail 已支持按 Attempt 冻结草稿的只读回放，见 [HISTORY_DRAFT_REPLAY.md](HISTORY_DRAFT_REPLAY.md)。复用本页的 Canvas Core；不提供历史编辑或保存。

## 构建与启动

在本目录首次运行 `npm ci`，然后运行 `npm run build` 和 `npm test`。
构建产物提交在 `src/main/resources/editor/draft-canvas/`，JavaFX 运行时只加载这些本地资源。
从仓库根目录启动独立窗口：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\tools\Start-DraftCanvas.ps1
```

启动脚本构建本地前端、Java 模块，并使用 `target/draft-poc` 输出目录启动
`io.quizforge.desktop.poc.draftcanvas.DraftCanvasLauncher`。关闭窗口即释放 WebView。

## 目录与职责

```text
editor-web/draft-canvas/
  index.html                 真实 DOM 测试题卡与工具栏
  README.md / ACCEPTANCE.md   设计、启动与验收记录
  package.json / package-lock.json
  scripts/build.mjs          独立 esbuild 打包
  src/model.js               JSON 校验、坐标、整笔擦除、笔迹历史
  src/app.js                 原独立演示题卡与白板组合入口
  src/canvas/core.js         Pointer Events、SVG、live object 容器、白板 JS API
  src/style.css
  test/model.test.js         无浏览器模型测试
src/main/resources/editor/draft-canvas/
  draft-canvas.html / draft-canvas.js / draft-canvas.css
src/main/java/io/quizforge/desktop/poc/draftcanvas/
  DraftCanvasWebView.java / DraftCanvasLauncher.java
src/test/java/io/quizforge/desktop/poc/draftcanvas/
  DraftCanvasWebViewTest.java
```

## 渲染与坐标

`viewport` 裁剪可视区域；`world` 使用共同的 `translate(x*zoom,y*zoom) scale(zoom)`。
真实 HTML 题卡和透明 SVG 笔迹层都是 `world` 的子元素。没有将题卡转成截图或 Canvas 图像。
SVG 笔迹在题卡之上，但不接收事件；白板使用 Pointer Events 和 pointer capture。
INTERACT 允许题卡原生选择和输入；PEN/ERASER/PAN 禁止题卡接收指针，避免误选。

```text
screen = (world + viewport.translation) * viewport.zoom
world  = screen / viewport.zoom - viewport.translation
screen = event.client - viewport.getBoundingClientRect().origin
```

card x/y/width、笔迹点和笔宽均为 World 单位。viewport x/y 也为 World 单位的平移偏移，
不是任何一次鼠标事件的 client 坐标。Pan 把 screen 拖动增量除以 zoom 后加到该偏移，
笔迹和题卡几何保持不变。
题卡默认逻辑宽度为 720，导入时保留 snapshot width；窗口缩放不改变题卡宽度。
底部缩放及“适应窗口”仅调整视口。载入的正数 zoom 参与渲染、坐标换算和橡皮半径换算。

橡皮使用相邻指针点之间的扫过线段检测，整笔删除，包含笔宽。
同一橡皮拖动是一条撤销记录。Undo/Redo 覆盖笔迹新增、擦除、清空；不撤销平移。
Clear 保留题卡与视口。导入成功后清空撤销历史；校验失败保留原有模型与历史。

## Draft JSON v1

```json
{
  "schemaVersion": "1.0",
  "layoutVersion": "1",
  "viewport": { "x": -40, "y": 25, "zoom": 1 },
  "questionCard": { "x": 120, "y": 70, "width": 720 },
  "strokes": [{
    "id": "stroke-example-1",
    "tool": "PEN",
    "color": "#7054a5",
    "width": 2.4,
    "points": [
      { "x": 180, "y": 220, "pressure": 0.5 },
      { "x": 240, "y": 230, "pressure": 0.5 }
    ]
  }]
}
```

schemaVersion 必须是 `1.0`，layoutVersion 必须是 `1`，两者分别验证；stroke ID 必须非空且唯一；坐标须为有限数；zoom、width 须为正数；
pressure 范围为 0–1。canonical v1 的 width/color/pressure 必填；仅旧 POC 升级入口补齐缺省 720、`#7660ab`、0.5。
color 只接受十六进制 CSS 颜色。模型规范化后导出，忽略未定义字段。
当前不序列化测试题卡的选项选择、输入文本、提交状态、工具模式或撤销历史；同一页面导入保留
题卡内部输入，页面重新加载则恢复测试题卡默认内容。几何与笔迹参与完整往返恢复。

## Java ↔ JS 桥接

JS 的 `window.draftCanvas` 提供：

| API | 作用 |
| --- | --- |
| `getDraft()` | 返回规范化 JSON 字符串，先结束当前操作 |
| `loadDraft(json)` | 校验并载入 JSON 字符串或对象 |
| `setMode(mode)` | INTERACT / PEN / ERASER / PAN |
| `setZoom(zoom, screenAnchor)` | 改变视口缩放，保持锚点的 World 坐标 |
| `fitCard()` | 通过视口缩放和平移适应窗口，保留 logical width |
| `diagnostics()` | Pointer Events 支持情况、计数与最近 128 条事件 |
| `destroy()` | 移除事件监听器，停用界面；重复调用安全 |

Java 对应 `DraftCanvasWebView` 的 getDraft/loadDraft/setMode/destroy 和 diagnostics(JSON 字符串)。
`ready()` 返回 CompletionStage；所有操作应在 JavaFX 线程且页面 ready 后执行。
destroy 同时卸载页面。所有白板逻辑位于 JS。

## 验证与人工验收

```powershell
# 仓库根目录；先执行前端 build/test
mvn -B -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/draft-poc' '-Dtest=DraftCanvasWebViewTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -B '-Dquizforge.build.directory=target/draft-poc' test
```

模型测试覆盖 JSON 往返、坐标转换、pan、增笔、擦除、扫过命中、撤销重做、清空、载入、
校验失败与状态隔离。JavaFX 测试加载本地资源、桥接往返、销毁，并使用原生 JavaFX Robot
操作可见窗口；`DRAFT_CANVAS_NATIVE_POINTER_EVIDENCE` 输出实际可信事件、pointerType、pressure。
这不是触控笔测试，也不是人工验收。运行 Robot 测试时不要同时移动鼠标或输入。

人工验收清单（由使用者打开独立窗口确认）：

- A：交互模式点击 A–D，输入文字，提交测试答案。
- B：在空白及题卡上画笔；题卡选项不应变化。
- C：拖动平移；笔迹与题卡一起移动，相对位置保持不变。
- D：用橡皮删除笔迹。
- E：撤销/重做新增、擦除、清空；清空保留题卡。
- F：绘制、平移、输入文字，导出 JSON；重开窗口并载入，恢复几何与笔迹。

实际本次测试结果和未确认的人工验收项目见 [验收记录](ACCEPTANCE.md)。

## 当前限制

仅一个固定测试 DOM 题卡；无正式题型绑定或题卡拖动。常量笔宽，保存 pressure 但没有真实压感
笔宽变化或笔锋。没有触控笔、触屏、掌触、多指、平板兼容性声明。无虚拟化或大型
草稿性能评估；撤销使用笔迹快照。JSON 通过文本面板导出/载入，不自动保存。

Shared Practice 的 Active/Frozen SQLite 数据、500ms autosave、Submit ACK barrier 与 Retry 清空见 [DRAFT_PERSISTENCE.md](DRAFT_PERSISTENCE.md)。独立白板入口仍不保存数据库。
