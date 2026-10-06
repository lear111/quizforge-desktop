# 扩展运行故障隔离

> 2026-10-05 当前实现。评分接口保持同步；全局 AI、异步评分和性能优化不属于本轮。

Windows 正式练习、草稿、题目编辑和历史题卡现已切换为 WebView2；下文的页面 JVM、PNG 转发及 AppContainer 页面保障只适用于仍使用原后端的页面（例如独立开发预览）或显式选择原后端。规则进程隔离仍沿用下文方案。新页面后端的实际范围与限制见 [WEBVIEW2_BACKEND.md](WEBVIEW2_BACKEND.md)。

## 规则执行

- 桌面每个已安装包版本拥有独立的 Java 规则进程，绑定其原始 `type.js` 和默认模板。进程重启不会采用开发目录的新规则，也不会替换历史的冻结版本。
- 初始化最多等待 45 秒；初始化完成后，每次规则操作最多等待 3 秒。启动和调用使用独立计时，包含对应 IPC 输入、执行和响应等待。
- 超时后宿主强制终止进程。脚本异常、错误响应或进程异常退出也会释放该规则进程；后续调用重新启动原版本。
- 一个版本出错不会终止其他版本的进程。关闭应用或开发窗口会终止对应进程；Windows 由可信启动器监视父进程并关闭工作进程的 Job，其他桌面平台仍由工作进程检查父进程。
- IPC 使用私有标准输入/输出管道，JSON 帧最多 16 MiB，规则返回 JSON 最多 8 MiB。没有开放网络监听端口、Java 对象桥、题库路径或数据库接口。
- 进程沿用离线 WebKit 规则环境：CSP 禁止外部资源和连接，弹窗关闭，离开规则页的导航会终止进程。工作进程只继承必要系统环境项，未继承 AI 密钥或开发代理配置。JVM 堆上限为 128 MiB，直接缓冲区上限 32 MiB；这些不是整个进程或 WebKit 原生内存的上限。
- 独立桌面开发预览通过异步宿主桥调用同一规则进程，候选源码不能在预览页的 UI 线程上执行评分。网页环境的临时规则预览仍使用 sandbox iframe，未获得桌面的进程终止保障；Hub 的只读题目预览不调用评分。

## 错误码与数据保护

| 错误码 | 含义 |
| --- | --- |
| `EXTENSION_TIMEOUT` | 规则启动或调用超时，进程已终止 |
| `EXTENSION_FAILED` | 规则抛错、进程退出或响应无效；不产生评分结果 |
| `EXTENSION_UNAVAILABLE` | 规则未成功初始化或运行环境已关闭 |

错误沿用 SDK 的失败 Reply。宿主不把异常转换成零分或正确/错误状态。提交仍在原事务中：判分失败不追加作答历史、不改变提交状态、不删除已持久化的答案或白板。用户主动再次调用时才重试；宿主不自动重发评分或保存操作。

## 页面报错与恢复

桌面的题型编辑、练习、历史和独立开发预览现在将每个活动题型页面放进独立 Java/WebKit 进程。主应用只运行可信页面外壳、白板和接口分发器，拓展 HTML/CSS/事件脚本在工作进程的 `sandbox="allow-scripts"` iframe 内运行。工作进程的 Java 桥只存在于可信父页，不能被题型页面访问；数据写入、权限判断和评分仍由主应用掌管。

画面通过有界 PNG 切片传回主应用，主应用转发鼠标、键盘、输入法提交和已有 SDK 消息。白板继续由主应用渲染，可覆盖题卡，练习模式仍为固定白板。拓展不用改写 HTML、CSS、`editor.js` 或 `practice.js`，也没有新增宿主权限。

页面同时应用 nonce 校验与仅允许内联脚本的 CSP，两者取交集。带有效 nonce 的外部脚本也被阻止；包内脚本仍可执行。

- 页面工作进程冷启动上限 45 秒，宿主页面初始化等待上限 60 秒；工作进程 UI 线程连续 5 秒不能执行可信心跳时，宿主强制终止该进程。心跳不能由拓展的 `postMessage` 伪造。
- 普通事件报错、同步死循环、原生页面崩溃和进程意外退出都只停止该题型页面；其他页面、白板和主应用仍可操作。宿主提供“重新加载题卡”，不会自动重放提交或保存。
- IPC 使用私有管道，沿用 16 MiB 帧上限。画面切片最大 2048 × 1536，界面回调合并旧画面；输入队列最多 128 条，每秒最多接受 256 条非心跳消息，过载停止页面。
- 页面关闭、原宿主导航、扩展管理器关闭及父进程退出都会终止工作进程。迟到消息按原页面会话丢弃，不能影响新题卡。
- 开发窗口先启动独立规则环境，再启动候选页面，整体等待上限为 100 秒；失败保留上一次预览。后台使用虚拟嵌入窗口绘制，不显示操作系统窗口或抢占应用焦点。
- SDK 方法、参数校验、权限撤销、作答事务和历史只读规则没有改为由工作进程执行。历史使用原冻结数据恢复。

题卡初始化失败、加载超时、未捕获事件异常或 Promise 异常会通知可信宿主。宿主移除对应题型页面、撤销其后续调用能力、拒绝待刷新请求，并提供“重新加载题卡”。练习/历史重新加载使用应用当前权威数据；编辑重新加载使用最后一次被宿主接受的编辑草稿。白板的几何和笔迹不因题卡重建而重置。尚未提交给接口的输入不保证保留。

已经开始的合法宿主写操作仍可能完成，撤销页面不是数据库回滚。重载需等待宿主当前练习操作完成，避免把未完成的写入覆盖为旧数据。

页面已停止时，外部导航和保存屏障只使用宿主已接受的数据，不等待失效页面刷新；白板仍能保存。尚未开始的排队页面写请求被拒绝，不能在页面停止后继续修改题目。

## 当前边界

### Windows 低权限后端

Windows 10/11 上，页面与规则使用同一个 AppContainer 启动后端。普通权限的可信启动器不执行拓展代码，只负责准备进程、权限、私有标准管道和生命周期。拓展 HTML/JS/WebKit 均在低完整性工作 JVM 内运行，主应用继续负责 SDK 权限、数据校验、保存、判分结果接收及只读历史。

- 每个工作 JVM 使用不同的 AppContainer 身份，仅拥有一个自定义运行资源 capability，没有 Internet、局域网或服务监听 capability。操作系统检查同时约束基础用户身份和 AppContainer 身份。
- 应用复制当前 Java 运行文件、类路径与模块路径到 `%LOCALAPPDATA%/QuizForge/extension-sandbox-v1/runtimes/`。类路径目录只复制字节码、原生库、模块 JAR、服务声明和工作进程需要的规则运行 JS，不复制题库、`.env` 或应用配置文件。只对这些副本授予运行 capability 的读取/执行权限；不对题库、工作区、用户目录或 Maven 仓库增加访问授权。运行依赖变更会生成新的运行快照，旧版本规则仍绑定原来的源码。
- 缓存根目录与目录外的宿主 PID 标记只授权可信用户/系统；运行副本和每个私有目录使用受保护的 DACL，防止继承父目录里其他应用的额外授权。运行副本的权限在发布时设置一次，后续启动不反复修改正在使用的副本。
- 每次启动有独立临时目录及用户目录，使用该进程的 AppContainer SID 授权，并设置低完整性标签。其他工作进程不能读取或修改此目录。临时目录可写，运行副本不可写；主应用的数据位置与凭据不通过环境变量传入。
- 随应用提供的 JavaFX/JNA 原生库从可信运行依赖预先提取到只读 `natives/`，避免低权限 JVM 在启动时自行解压库。不能通过修改工作目录或临时目录替换这些库。
- JVM 使用无控制台的 `javaw.exe`，通过显式继承的私有标准输入/输出管道通信，标准错误丢弃。只继承这三个标准句柄，Job 和宿主文件句柄不传给工作进程。
- 每个进程拥有单独的不可见桌面，该桌面只授权当前 AppContainer 身份与可信宿主。Windows 自动生成的非交互窗口站供这些桌面使用；不修改用户交互桌面 `WinSta0\\Default` 的权限，也不显示或切换桌面。Job 额外禁止剪贴板读写、桌面切换、系统显示/参数修改与注销操作。
- 在创建 JVM 时就加入 kill-on-close Job，Job 最多容纳一个进程；另外使用 Windows 子进程策略禁止派生进程。启动器被强制结束或主应用退出，工作 JVM 随 Job 结束，不依赖拓展主动响应。
- Windows 隔离配置失败时停止加载拓展，**没有普通权限工作进程的降级路径**。启动器在确认 AppContainer 与 Job 创建成功后发送私有启动握手；失败沿用 `EXTENSION_UNAVAILABLE` 或进程失败响应，不产生作答评分。
- 正常退出清理临时目录与 AppContainer profile。启动器被强制结束时由宿主补做清理；宿主异常退出留下的目录由下次启动依据目录外的宿主 PID 标记回收，不使用工作进程可修改的配置决定删除路径。不回收仍属于存活宿主的目录。

这是普通 AppContainer，Windows 默认允许读取部分共享系统资源、字体、注册表和 COM 对象，**不是仅有几条路径可读的 LPAC/BFS 文件白名单**。用户额外开放给 AppContainer 的资源也受 Windows ACL 影响。没有为整个 WebKit 原生进程设置 CPU 或内存硬配额；128 MiB JVM 堆与 32 MiB 直接缓冲区限制仍不等于进程总内存限制。运行快照会占用磁盘，快照回收和渲染性能优化后续处理。

本机验证首次准备运行副本并取得页面可信心跳约 32 秒，复用副本的页面启动约 15–19 秒。启动预算因此调整为 45 秒；这是开发环境实测，不能当作其他设备的启动时长保证。运行后的 5 秒无心跳终止与 3 秒规则调用期限没有放宽。开发诊断可用 `-Dquizforge.sandbox.timings=true`，仅输出准备、握手、首个心跳耗时，不输出题目或作答内容。

桌面页面与开发工作台的外层接口等待为 60 秒，覆盖后端 45 秒冷启动、3 秒规则操作及 IPC；纯浏览器页面仍为 30 秒。外层等待不是延长脚本执行期限，也不自动重发保存或评分。

API 依据：[AppContainer](https://learn.microsoft.com/en-us/windows/win32/secauthz/implementing-an-appcontainer)、[进程创建属性](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-updateprocthreadattribute)、[Job objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)。

### 其他平台与画面转发边界

非 Windows 桌面目前保留独立进程与 iframe/CSP 限制，尚未实现操作系统低权限后端。Android/网页端不能宣称具备此 Windows 后端的权限保障。

这是第一版画面转发后端，每个活动页面需要独立进程，冷启动和画面传输存在额外成本。原生浮层下拉框改为页面内菜单；文本选择在工作进程内保留，但主应用中的画面不提供完整 DOM 可访问性和原生悬浮提示。复杂触摸手势及更完整的辅助功能仍需补齐；本轮不做进程池和渲染性能优化。

纯网页宿主仍使用浏览器 sandbox iframe，没有获得桌面宿主的可终止进程保障。未来 Android/网页宿主需要各自实现相应执行边界，不能把 iframe 定时器当作页面故障隔离。

正式规则接口仍为同步，界面可能等待一次调用的 3 秒期限；故障后的首次重启还可能等待 45 秒启动期限。这是有界等待，未实现非阻塞的异步评分。未来跨平台宿主需提供同等可终止执行环境，不能把网页 iframe 超时视为进程隔离。

## 针对性验证

```powershell
mvn -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/page-isolation-tests' '-Dtest=ExtensionPageRuntimeTest,IsolatedChoiceWebViewTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

```powershell
cd quizforge-desktop-app/editor-web/draft-canvas
node --test test/native-pages.test.js test/sdk-lifecycle.test.js test/sandbox-contract.test.js test/native-rules.test.js test/renderer-contract.test.js test/learning-mode.test.js test/data-validation.test.js test/page-actions.test.js
```

验证覆盖页面死循环终止、主应用线程仍响应、画面传输、实际鼠标选择及中文输入、页面故障后撤销能力，以及实际单选/多选编辑与保存、作答/提交/重试、热更新和只读历史。

Windows 原生权限验证：

```powershell
mvn -pl quizforge-desktop-app -am '-Dquizforge.build.directory=target/os-sandbox-check' '-Dtest=WindowsExtensionSandboxTest,ExtensionPageRuntimeTest,ExtensionRuleRuntimeTest#normalRulesAreIsolatedAndPageSyntaxIsNeverExecuted' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

探针使用临时测试文件与本机测试监听器，不读取真实题库或凭据。它直接检查 JVM 的 AppContainer/低完整性令牌、能力数量、宿主文件读写拒绝、运行副本写入拒绝、临时目录写入、跨工作进程临时目录隔离、出站/入站网络通信拒绝、子进程拒绝、输入桌面访问拒绝及强制结束后的工作进程退出。Windows 可以允许创建及绑定 socket，网络隔离在实际通信处生效，不能把“没有监听 capability”描述成所有 socket 创建都失败。
