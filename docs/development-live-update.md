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

Test-QuizForgeLiveJavaUi.ps1 会把三个模块源码与 tools 复制到 target/live-java-ui-smoke/<run>/checkout，真实 Maven 编译与源码替换仅发生在副本。-WithLiveCss 开启副本 CSS 监听；-CanvasDevUrl http://127.0.0.1:5173 可验证已明确提供的本地 Vite 服务，探针不会停止外部服务器。调用方启动自己拥有的服务时需自行清理进程树。
