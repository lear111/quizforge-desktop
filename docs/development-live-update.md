# Development live updates

From the repository root:

```powershell
.\Start-QuizForge-LiveUi.cmd
# Equivalent: .\Start-QuizForge.cmd -LiveUi
```

Both root entry points are `.cmd` files and support double-click startup. The
shared PowerShell implementation lives in `tools/Start-QuizForge.ps1`; the
entry points forward flags to it. Use `Start-QuizForge.cmd` for ordinary startup.

`LiveUi` now enables all three development layers:

| Flag | Effect |
| --- | --- |
| `-LiveCss` | Existing JavaFX CSS watcher; no Node requirement |
| `-LiveWeb` | Canvas Vite server and actual WebSocket HMR, fixed `127.0.0.1:5173` |
| `-LiveJava` | Compile saved Java/resources and redefine classes in the running desktop JVM |
| `-LiveUi` | `LiveCss` + `LiveWeb` + `LiveJava` |
| No flag | Existing packaged editor / JavaFX startup; no development agent or Vite |

Java mode requires a JBR JDK supporting `-XX:+AllowEnhancedClassRedefinition`.
The launcher uses `QUIZFORGE_LIVE_JAVA_HOME`, then `JAVA_HOME`. An unsupported JVM
causes an explicit error, never a silent restart/fallback. The current machine's
JBR 21 supports method replacement and tested additions of methods/fields.
The agent is built locally from `tools/live-java`; no agent download is required.

## Java pipeline

1. The initial reactor build runs first with
   `-Dquizforge.build.directory=target/launcher`. All startup modes use this
   separate output so IDE compilation in `target/classes` cannot supply broken
   or stale classes to the launcher. Ordinary manual Maven builds still use `target`.
2. The development launcher copies each module's `target/launcher/classes` into a unique
   ignored `target/live-java/<run>/` directory. Runtime reactor JARs are replaced
   by these directories; external dependencies remain Maven-resolved JARs.
3. A development-only agent hashes `src/main/java`, `src/main/resources`, and
   POMs every 500 ms, with a 700 ms debounce. Canvas source uses its existing Vite
   watcher independently. Workspace assets and databases are not watched.
4. Resource-only edits are copied directly without starting Maven. For Java
   edits a single compiler invokes `mvn -B -pl quizforge-desktop-app -am -Dquizforge.build.directory=target/launcher -DskipTests compile`.
   Edits made during compilation are compiled again before publishing. Compiler
   failures leave the running JVM and its separate class directory unchanged.
5. On success, changed loaded classes are passed together to
   `Instrumentation.redefineClasses`. JBR supplies enhanced class redefinition.
   Changed class/resource files are then atomically replaced in the development
   directories, including classes not yet loaded. PID is unchanged.
6. The agent schedules `DevelopmentUiReloader` on the JavaFX thread. It redraws
   current question editing, choice practice, essay preview, summary, history,
   and Markdown views using their existing models/runtime. It does not reopen a
   QBank, invoke save/submit/retry, recreate sessions, or refresh Spring context.
7. Refresh preserves text (including invalid/uncommitted input), selection,
   focus, scroll, divider positions, and expanded editor sections. It waits for
   modal dialogs to close so an open Canvas editor's callbacks/draft remain
   valid. Vite HMR continues within that dialog.

`Ctrl+Alt+R` requests another redraw without compiling or restarting. Existing
JavaFX CSS updates remain immediate under `LiveCss`; copied file stylesheets
also receive a cache-busting URL in Java-only mode.

快捷键在 QuizForge 应用窗口中按下，用于重建当前页面；它不编译源码、不启动新进程，也不会补做新增题型登记或构造函数初始化。改变题型登记、缓存容量等静态/初始化配置后应重启应用。

## Boundaries requiring restart

- POM/dependency changes, bootstrap/configuration code, schema/migration files,
  service metadata, application configuration resources, or removed sources.
- New/changed Spring Bean wiring and lifecycle/constructor initialization.
  Existing Bean method bodies can update; this mode does not recreate Beans or
  promise automatic dependency injection changes.
- JVM arguments, native libraries, static initialization, class hierarchy or
  other changes rejected by the enhanced VM.
- Constructor-only changes to the main shell, chrome, settings dialogs, or
  existing standalone native editor windows. These are not automatically rebuilt.
- Bundled HTML/JS changes in an already open packaged WebView; use `LiveWeb`
  for Canvas updates. There is no polling WebView reload fallback.

Detected build/config/deletion or VM-rejected changes establish a restart
barrier for that launch. Later source edits cannot accidentally apply a partial
configuration change. No automatic restart, data migration, or Spring context
replacement occurs. Ordinary startup is unchanged.

## Logs and process ownership

- `%LOCALAPPDATA%\QuizForge\logs\live-java.log`: readiness, compile/reload,
  `COMPILE_FAILED`, `RESTART_REQUIRED`, and page refresh messages.
- `%LOCALAPPDATA%\QuizForge\logs\live-java-error.log`: desktop/native errors.
- `target/live-java/<run>/compile.log`: last compiler output.

One Java development compiler is allowed per checkout. Windows Job ownership
cleans up the desktop and compiler descendants on launcher exit. The agent also
terminates an active compiler in its shutdown hook. Existing Vite process
ownership and environment restoration remain in the outer launcher.

## Verification commands

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/Test-QuizForgeLauncher.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/Test-QuizForgeLiveJava.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/Test-QuizForgeLiveJavaUi.ps1
mvn test
```

The JVM probe uses isolated fixtures and verifies structural replacement,
existing-instance state, compile-error recovery, and the restart barrier. The
native probe uses an in-memory bank (no workspace/DB), changes a real Java
toolbar label, waits for Maven and the same JavaFX window to update, restores the
source bytes, and verifies the reverse update and process cleanup. Temporary
probe files and logs stay under ignored `target/`.


## 优化后的路径与接口

2026-10-01：reactor 与 LiveJava 模块表收敛为 core、infrastructure、desktop-app。刷新入口为 io.quizforge.desktop.dev.DevelopmentUiReloader，组件实现 DevelopmentRefreshable，代理不再依赖 ui 包中的旧 FQN 或私有方法反射。

Canvas 源码在 quizforge-desktop-app/editor-web/canvas，构建输出在 src/main/resources/editor/canvas；JavaFX/Markdown 样式在 src/main/resources/styles。修改前端后执行 npm run build，普通启动使用这些包内文件。

## 只读富文本预览缓存

`CanvasDocumentView` 在首次附着到 Scene 时才创建 WebView；页面重建先尝试复用旧渲染器，切题后将已加载且正常显示的预览放入当前 Scene 的缓存。最多保留六个脱离场景的预览，当前仍显示的预览另计；单个题卡的正文、解析等分别占一个缓存项，不等于缓存六张题卡。

缓存按预览 ID、内容、实际引用资源和渲染模式匹配。只更改答案时同步选择配置，不重新加载正文；正文或引用资源变化时加载新内容。缓存期间清除旧页面回调、资源入口和完形弹窗，恢复时绑定当前页面。超出上限、关闭窗口或替换 Scene 时释放缓存；未完成加载的预览不入缓存，编辑窗口不参与缓存。首次加载完成前隐藏 WebView，避免原生工具栏闪现。

只读预览不保留脏状态检测所用的第二份序列化快照；原生编辑窗口仍保留快照。TEXT/RICH 转换只加载实际引用的图片，插入图片只读取本次图片。缓存命中减少页面重复加载，同时保留渲染器的内存；不同页面、图片和操作序列下的占用不能直接当作受控优化对比。

## 内存排查

先识别桌面进程；普通启动包含桌面入口，LiveJava 启动可能只显示 `@launch.args`，对应参数文件中的入口为 `io.quizforge.dev.QuizForgeDevLauncher`。VS Code 的 `org.eclipse.jdt.ls` 是编辑器服务，LiveWeb 的 node/Vite 和 esbuild 是开发子进程，统计时分别列出。

```powershell
# 在确认进程身份后替换此值，JAVA_HOME 指向当前 JDK/JBR
$quizForgeProcessId = 12345
Get-Process -Id $quizForgeProcessId | Select-Object Id,
  @{Name='WorkingSetMiB';Expression={[math]::Round($_.WorkingSet64/1MB,1)}},
  @{Name='PrivateCommitMiB';Expression={[math]::Round($_.PrivateMemorySize64/1MB,1)}}
& "$env:JAVA_HOME/bin/jcmd.exe" $quizForgeProcessId GC.heap_info
```

工作集是驻留内存，私有提交是进程申请的私有内存，Java 堆只是其中一部分；WebKit、字体、图片、线程及 JVM 本身也占内存。`GC.heap_info` 不主动执行 Full GC。应用未默认启用 Native Memory Tracking，不能据此给出可靠的原生分项占用，也不能仅凭未强制回收的对象直方图判定泄漏。对比需使用相同启动模式、题库和切题序列，并记录 Java 堆、进程工作集及缓存状态。

Test-QuizForgeLiveJavaUi.ps1 会把三个模块源码与 tools 复制到 target/live-java-ui-smoke/<run>/checkout，真实 Maven 编译与源码替换仅发生在副本。-WithLiveCss 开启副本 CSS 监听；-CanvasDevUrl http://127.0.0.1:5173 可验证已明确提供的本地 Vite 服务，探针不会停止外部服务器。调用方启动自己拥有的服务时需自行清理进程树。
