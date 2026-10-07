# 题型扩展：真实开发与安装流程（HTML SDK 2）

应用不含任何内置题型。单选、多选、判断、完形、阅读、排序、翻译、作文均为普通外部包：没有启动自动安装、自动授权或指定源码目录监听。主程序只提供 SDK、浏览器、数据保存及权限边界。空数据目录启动时没有可新建的题型，已有题目缺少对应扩展时保留数据并提示安装。

## 开发文档入口

- **当前 SDK 2.3 首先阅读：** [精简页面接口与样例题库](SIMPLE_PAGE_API.md)。推荐判断题 2.3.2 的 11 文件模板，直接编辑页面脚本；其余七型为 2.3.2，所有包均带独立样例题库。

- **从头开发或在新对话继续：** [拓展开发指南](DEVELOPMENT_GUIDE.md)，包含文件职责、数据与保存流程、复制判断题的修改清单、打包与热更新，以及可复制的新对话提示词。
- **看现成带注释模板：** [判断题设计与源码导读](QUESTION_TEMPLATE.md)，源码在 `packages/true-false/`。
- **查已实现 API：** [SDK 2.3 接口参考](SIMPLE_PAGE_API.md)、[SDK 文档索引](SDK_README.md)、[数据校验](DATA_VALIDATION.md)。
- **安装与测试配置：** 继续阅读本文。目标接口草案不能作为已实现 API 的依据。

## 目录

| 目录 | 用途 |
| --- | --- |
| `packages/single-choice/` | 单选完整源码，扩展 ID `quizforge.types.single-choice` |
| `packages/multiple-choice/` | 多选完整源码，扩展 ID `quizforge.types.multiple-choice` |
| `packages/true-false/` | 判断题完整源码及通用视觉模板，扩展 ID `quizforge.types.true-false` |
| `tools/pack.mjs` | 独立离线打包工具；可以与源码一起复制到仓库外，只需 Node |
| `dist/*-2.3.2.qfext` | 八个题型的精简接口安装包；旧版本包保留存档，旧页面 API 已停止支持 |
| `dist/*.bundle.json` / `preview.html` | 网页只读预览产物，不是桌面安装包 |

八种题型 ID 保持原命名，最新包要求 SDK 2.3；宿主只读取 CHOICE/EXTENSION 封装，旧专项 kind 题库需手动迁移。历史迁移记录见 [迁移说明](READING_TYPES_MIGRATION.md)，最新源码构建见 [精简接口说明](SIMPLE_PAGE_API.md)。页面、默认 JSON、Schema、样例题库与评分规则全部在扩展目录。主程序资源及 JAR 排除题型包；构建主程序不会安装扩展。

## 用正式应用测试全流程

在仓库根目录执行：

```powershell
# 首次创建空白配置并启动正常桌面界面
powershell -ExecutionPolicy Bypass -File tools/Start-Extension-Lab.ps1 -Fresh

# 之后重启同一配置，保留已导入的扩展、题库和历史
powershell -ExecutionPolicy Bypass -File tools/Start-Extension-Lab.ps1 -SkipBuild
```

测试配置存放在 `target/extension-lab/profiles/<UUID>/`，当前目录记录于 `target/extension-lab/current-profile.txt`。`-Fresh` 创建新目录，不删除旧目录。它使用正式 DesktopApplication，与普通启动的区别只有数据目录。

1. 空配置中没有题型。进入“题型扩展”管理窗口。
2. “安装扩展”选择 `extensions/dist/` 下的 `*-2.1.1.qfext`，检查身份、版本与权限，确认安装。依次安装三个包。
3. 关闭并重新运行同一测试配置，三种题型进入新增菜单。
4. 新建题库，分别添加、编辑和保存三种题目，练习、提交、重试，再查看历史与草稿。
5. “加载开发目录…”显式连接相应 `extensions/packages/<名称>/`。修改 HTML、CSS、页面 JS，浏览区实时更新。它不会自动连接其他目录。
6. “停止实时预览”恢复安装包页面。评分规则、默认 JSON、Schema 或清单变化需增加版本号，重新打包、导入和重启；也可以先用“独立开发预览…”测试。

默认 `.quizforge` 配置里以前已安装的包仍保留，普通启动会加载这些外部安装包。旧包 ID 为 `quizforge.builtin.*`，新包 ID 为 `quizforge.types.*`；同一题型 ID 不能由不同扩展同时占用。请用以上空白配置测试新包，不要在保留旧包的配置里混装。旧历史可能引用旧包版本，此次不删除其存档或正式工作区。

当前运行时已删除旧页面接口的兼容代码，只接受 `pageApi: "simple"`。旧包会提示升级；与旧页面接口绑定的历史不能直接回放，冻结版本和用户数据不会自动迁移或覆盖。原题库数据可由新版扩展按其数据兼容规则继续读取。

## 独立开发、打包

复制整个源码目录和 `tools/pack.mjs` 到任意开发目录即可，不依赖主程序源码。修改扩展 ID、题型 ID、名称、默认 JSON、Schema、页面与规则，再执行：

```powershell
node ./pack.mjs ./my-type ./publisher.my-type-1.0.0.qfext
```

输出必须在源码目录外。新增自己的题型时使用发布者命名空间，并同步修改清单、默认 JSON、Schema 和 `type.js` 的题型 ID。已有包的同一 ID、版本不允许覆盖不同内容，发布修改需升级版本。

仓库提供的三个包可以分别构建：

```powershell
node extensions/tools/pack.mjs extensions/packages/single-choice extensions/dist/quizforge.types.single-choice-2.1.2.qfext
node extensions/tools/pack.mjs extensions/packages/multiple-choice extensions/dist/quizforge.types.multiple-choice-2.1.2.qfext
node extensions/tools/pack.mjs extensions/packages/true-false extensions/dist/quizforge.types.true-false-2.1.2.qfext
```

同时生成三个包及网页预览（与主程序构建分开）：

```powershell
npm --prefix quizforge-desktop-app/editor-web/draft-canvas run build
node quizforge-desktop-app/editor-web/draft-canvas/scripts/build-extensions.mjs
```

后一个脚本扫描 `extensions/packages/`，也接受自定义源码集合目录、输出目录两个参数，不按题型名称做特殊处理。具体接口、安全限制与权限见 [SDK_README.md](SDK_README.md)；通用样式见 [QUESTION_TEMPLATE.md](QUESTION_TEMPLATE.md)。Windows 渲染后端与显式验收入口见 [WEBVIEW2_BACKEND.md](WEBVIEW2_BACKEND.md)。

## 2.1.0 验收记录（2026-10-06）

- 离线打包测试 7 项通过：三个源码目录及打包器复制到仓库外，分别生成与发行包完全一致的安装包；非法资源路径、符号链接、未知权限及 SDK 版本检查通过。
- 页面与 SDK 定向测试 22 项通过。
- 正式 ExtensionManager 安装链路测试 1 项通过：空启动、显式包检查和导入、未授权状态保留、重启启用、新建／复制／评分／题库编码往返、显式开发目录、停止预览、同版本内容不可覆盖、缺少包时保留数据。
- 实际 WebView2 正式练习组件 38 项检查通过；验收驱动显式导入三个外部包并提供测试授权，没有使用应用内置资源。验证涵盖选择、草稿笔迹和平移、提交、重试、题目切换、编辑中文输入、历史尝试只读和关闭保存屏障。
- 主程序 JAR 中 `.qfext` 和 `extensions/` 资源数量为 0。独立正式桌面测试配置已启动，安装包数量为 0。
- 未执行全量回归；安装窗口中的文件选择、人工权限确认留给用户按上文真实流程操作。

本次浏览器证据：`target/webview2-product-acceptance/92d1d7db-3740-4a56-bcf4-e48a6ab7784e/` 下的 `extension-imports.json`、`verification.json` 与截图。

## 2.1.1 优化与验证（2026-10-06）

- 单选、多选：连续修改不同选项不再从旧题目构造更新；设置正确答案仅更新选中状态，保留文本输入节点和编辑位置。
- 三种题型：过期读取不再覆盖最新选择；待保存期间保留正在操作的答案，最终重新读取宿主状态。撤销提交能力或转为只读时关闭确认面板。
- 编辑与来源控件响应当前能力和授权。业务更新失败显示错误并读取保留数据，不以脚本异常终止页面；无效分值不覆盖上次有效分值。
- 公共 SDK：题目/答案/结果读取等待此前写入；富文本组件支持 destroy、响应权限变化，卸载后丢弃迟到回调。事件中应立即发起更新，任意后续异步工作不属于保存屏障。
- 包版本升级为 2.1.1，要求 SDK 2.1；原 2.1.0 安装包保留，SHA-256 与修改前一致。正式配置仍由用户手动导入、确认权限及重启；测试配置未修改正式题库和历史。

已验证：前端完整测试 125 项、离线打包 7 项、Java 定向测试 33 项均通过；真实 WebView2 编辑 17 项、正式单选/多选练习及尝试历史 38 项、判断题练习与历史 6 项检查通过，共 61 项真实浏览器检查。Java 测试中原来依赖内置单选注册的混合题库用例已改为显式测试注册；安装测试按当前源码清单选择包，避免历史发行包数量影响验收。未运行应用全量 Java/UI 回归。

复现前端、打包及 Java 定向检查：

```powershell
npm --prefix quizforge-desktop-app/editor-web/draft-canvas test
node extensions/tools/pack.test.mjs
mvn.cmd '-Dquizforge.build.directory=target/extension-optimization-20261006' '-Dtest=ExternalQuestionTypeDefinitionTest,ExternalChoiceTemplateTest,ExtensionPackageStoreTest,ExtensionDataValidationTest,ExtensionDevelopmentSourceTest,ExtensionQuestionCodecTest,ExtensionPracticeIntegrationTest,ExtensionManagerStartupTest' '-Dsurefire.failIfNoSpecifiedTests=false' -pl quizforge-desktop-app -am test
```

本轮汇总位于 `target/extension-optimization-20261006/verification-summary.json`；真实浏览器验收使用该目录的三个新包和独立配置。编辑证据：`target/webview2-editor-acceptance/d799d284-cbcf-4f07-ba52-56c7308fb0ea/verification.json`；练习与历史证据：`target/webview2-product-acceptance/8b1d0ef6-3e5e-47f9-b560-fc5054c80174/verification.json`。

判断题证据：`target/webview2-product-acceptance/a2b4a4cf-88b3-4474-9f6a-b17ee7207679/verification.json`，验证错误作答保存、保留选择、正确答案反馈、重试清空、再次正确提交和冻结尝试只读。
