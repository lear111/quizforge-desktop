# Draft Canvas Core v1 验收记录

日期：2026-10-03；环境：Windows、Android Studio JBR Java 21、JavaFX WebView 23.0.2、Node 24.13.0。

## 1. 仓库状态

开始时 `git status --short` 无输出，HEAD 为
`9650171f7e6d6bd3b1676be195456861e72b38e9`。

现有 stash：

```text
stash@{0}: On main: wip-before-generic-rich-content-editor
stash@{1}: On main: wip-essay-before-richcontent-foundation
```

本次修改 `.gitignore`，新增独立前端、其本地资源、独立 JavaFX POC 类与测试、启动脚本。
完整文件清单和目录职责见 [README](README.md)。没有 commit/push；没有编辑用户题库或真实 Workspace 数据。
正式 Practice 与现有 Canvas Editor 的源码和业务职责保持原样。

结束时 `git status --short`：

```text
 M .gitignore
?? quizforge-desktop-app/editor-web/draft-canvas/
?? quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/
?? quizforge-desktop-app/src/main/resources/editor/draft-canvas/
?? quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/
?? tools/Start-DraftCanvas.ps1
```

修改 1 个文件 `.gitignore`（忽略新模块 node_modules），新增 17 个文件：

| 位置（相对仓库根） | 新增文件 |
| --- | --- |
| quizforge-desktop-app/editor-web/draft-canvas | README.md、ACCEPTANCE.md、index.html、package.json、package-lock.json |
| 同上 scripts / src / test | build.mjs；app.js、model.js、style.css；model.test.js |
| quizforge-desktop-app/src/main/resources/editor/draft-canvas | draft-canvas.html、draft-canvas.js、draft-canvas.css |
| quizforge-desktop-app/src/main/java/io/quizforge/desktop/poc/draftcanvas | DraftCanvasWebView.java、DraftCanvasLauncher.java |
| quizforge-desktop-app/src/test/java/io/quizforge/desktop/poc/draftcanvas | DraftCanvasWebViewTest.java |
| tools | Start-DraftCanvas.ps1 |

## 2. 渲染、坐标、格式、桥接

真实 DOM 题卡 + 同一 World 内的透明 SVG 笔迹层；viewport 裁剪；工具栏独立。
题卡位置、笔迹点、笔宽、viewport 平移偏移均为 World 单位；
`screen=(world+viewportOffset)*zoom`。鼠标 client 坐标先减 viewport 边界，再换算为 World。
Pan 使用 screen 拖动增量 / zoom 更新 World 偏移。

实际 JSON schemaVersion 为 `1.0`，包含 viewport、questionCard、stable stroke IDs、tool、color、width、
points(x/y/pressure)。完整结构、缺省值及校验边界见 README。
题卡测试答案/输入不进入 Draft JSON；同一页面 loadDraft 保留输入，新页面使用默认输入。
Java↔JS：loadDraft/getDraft/destroy，另提供 setMode/diagnostics/ready。

## 3. 自动化

- `npm run build`：成功，纯本地 HTML/JS/CSS bundle，运行时无 CDN 或 Vite 服务。
- `npm test`：**15/15 通过**，包括 JSON 往返、zoom 后 World 坐标、pan、增笔、点/扫过擦除、
  Undo/Redo/Clear、载入、错误输入原子性和状态隔离。
- `DraftCanvasWebViewTest`：**3/3 通过**。本地资源加载、Java↔JS 往返、错误导入、销毁；
  真实 Robot 鼠标/键盘操作 DOM 题卡、空白和题卡表面绘制、pan、erase、undo/redo/clear；
  新页面恢复草稿、载入 zoom 后实际 DOM 位置和宽度检查。
- 完整 `mvn -B -Dquizforge.build.directory=target/draft-poc test`：**BUILD SUCCESS，623/623 通过**。
  Core 70、Infrastructure 303、Desktop 250；failures/errors/skipped 均为 0。
  运行耗时 12:18，结束于 2026-10-03 01:26:37 +08:00。
- SVG 可见性检查：实际 WebView snapshot 中存在 **2472 个笔迹色实心像素**，查看该
  诊断图确认题卡和空白区域均显示 SVG 笔迹；图为仓库 `target/draft-canvas-visual.png`。
  这属于自动化渲染与图像检查，不是人工操作验收。

复现命令见 README。Maven 日志在仓库 `target/draft-canvas-targeted.log` 与
`target/draft-canvas-full-test.log`；Surefire XML/text 在各模块 `target/draft-poc/surefire-reports/`。
早期原生测试有未选中/提交未到达的失败记录；测试窗口定位、焦点、等待与数字输入调整后真实流程通过。
没有以合成 PointerEvent 或 JS 设置答案代替真实输入。

## 4. 当前 Windows WebView 的 Pointer Events

原生 JavaFX Robot 产生的事件 `isTrusted=true`：

| 项目 | 实测 |
| --- | --- |
| PointerEvent 支持 | true |
| pointerdown / pointermove / pointerup | 均收到 |
| pointerType | mouse |
| pressure | down/拖动时 0.5，悬停与 up 时 0 |

鼠标的这些值不是触控笔的真实压感；未验证笔或触摸硬件。
实际完整事件记录见 Maven 日志中的 `DRAFT_CANVAS_NATIVE_POINTER_EVIDENCE`。
诊断 PNG 只用于检查 POC 窗口显示；草稿始终保存向量点 JSON，截图不是题卡或草稿的数据格式。

## 5. 人工验收与九项目标

**尚无使用者人工验收记录。** 自动化实际打开了独立原生窗口，并完成以下操作；不得将其称为人工验收。
结束时另启动了独立 `DraftCanvasLauncher` 交互窗口，窗口标题为
`QuizForge — Draft Canvas Core v1 POC`，进程响应正常，保留供使用者操作。后续可用启动脚本重新打开。

| 人工验收项 | 自动化证据 | 人工状态 |
| --- | --- | --- |
| A 单选、输入、提交 | 原生鼠标选择 B，真实键盘输入 42，点击 Submit | 待确认 |
| B 空白及题卡上写，不误选 | 原生 PEN 两笔，原 B 选项保持 | 待确认 |
| C 卡与笔迹一起 pan | 实际 DOM 位移与视口相符；World 笔迹点/题卡数据不变 | 待确认 |
| D Eraser | 原生点击命中笔迹并删除整笔 | 待确认 |
| E Undo/Redo/Clear | 原生点击操作覆盖增笔、擦除、清空；题卡保留 | 待确认 |
| F Serialization | 原生绘制并 pan 后导出/载入；新 WebView 页面恢复几何/笔迹 | 待确认 |

九项 POC 目标（可平移 World、live DOM 题卡、可点击选择输入、题卡上画笔、共同 pan、
擦除、撤销重做、清空保留卡、JSON 恢复）均有实现和自动化证据，人工验收仍待使用者确认。

## 6. 限制

固定测试 DOM 卡；没有正式 Practice 集成。整笔擦除；固定笔宽，记录 pressure 但不生成压感笔锋。
zoom 数据可载入渲染，无缩放 UI。题卡内部答案/输入、工具模式和撤销历史未序列化。
未测试触控笔、触屏、掌触或多指。尚未进行大量笔迹/长时间使用的性能评估，历史使用笔迹快照。
JSON 面板需手动复制/载入，无自动保存。
