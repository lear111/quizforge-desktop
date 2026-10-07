# 八种独立题型：源码与接口入口

更新日期：2026-10-07。全部使用 [SDK 2.3 精简页面接口](../SIMPLE_PAGE_API.md)，每个包都包含真实 `examples/basic.qbank`。应用不自动安装或授权。

## 源码与版本

| 目录 | 题型 ID | 当前包版本 | 实际维护入口 |
| --- | --- | --- | --- |
| [true-false](true-false/manifest.json) | TRUE_FALSE | 2.3.2 | 11 文件；直接改 editor.js、practice.js、type.js |
| [single-choice](single-choice/manifest.json) | SINGLE_CHOICE | 2.3.2 | editor-source.js、practice-source.js；type.js |
| [multiple-choice](multiple-choice/manifest.json) | MULTIPLE_CHOICE | 2.3.2 | editor-source.js、practice-source.js；type.js |
| [cloze](cloze/manifest.json) | CLOZE | 2.3.2 | 页面/规则 *-source.js 与 shared |
| [reading](reading/manifest.json) | READING | 2.3.2 | 页面/规则 *-source.js 与 shared |
| [matching](matching/manifest.json) | MATCHING | 2.3.2 | 页面/规则 *-source.js 与 shared |
| [translation](translation/manifest.json) | TRANSLATION | 2.3.2 | 页面/规则 *-source.js 与 shared |
| [essay](essay/manifest.json) | ESSAY | 2.3.2 | 页面/规则 *-source.js 与 shared |

包版本与 SDK 版本独立；当前八型要求 minSdkApiMinor:3。判断题是推荐入门模板，JS 已有语法、事件与数据流中文注释。文件职责见 [模板说明](../QUESTION_TEMPLATE.md)，完整流程见 [开发指南](../DEVELOPMENT_GUIDE.md)。

## 页面如何调用应用

| 页面功能 | 当前接口 |
| --- | --- |
| 首次加载、状态/权限/尝试恢复 | QF.page.register 的 onLoad |
| 编辑草稿与正式题库保存 | QF.save editDraft / edit |
| 新增、复制、删除、整题移动 | QF.requestAction 对应 action |
| 来源添加、移除、打开 | QF.requestAction addSource / removeSource / openSource |
| 作答自动保存、正式提交 | QF.save draft / submit |
| 重试、题目/尝试切换 | QF.requestAction retry / goToQuestion / goToAttempt |
| 解析与文章的公共内容 | QF.content.renderAsync / mountEditor |
| 初始位置、尺寸、布局 | QF.page.configure initialLayout |
| 是否启用默认白板 | QF.page.configure useDraft；不声明默认启用 |
| 评分和多小题目标 | type.js 中 defineQuestionType / reportResult / targets |

新页面没有 QF.editor、answer、bank、practice、host、learning、whiteboard 命名空间。历史与预览复用 practice.html，只读能力由应用执行。上一题/下一题由宿主保留。

题卡的白色圆角样式属于包内 CSS，可自由修改。纸张颜色、纹理、白板工具和保存继续用应用默认组件。普通文本用 textContent/value，富文本渲染完成后可插入拓展自己的交互控件。

## 构建与发布

```powershell
# 判断题手写模板：只需 Node，直接打包。
node extensions/tools/pack.mjs ./my-type ./publisher.my-type-1.0.0.qfext
# 其余样例修改 *-source.js/shared 后需要构建。
node extensions/tools/build-page-extensions.mjs
# 网页预览及 bundle 分发物。
node quizforge-desktop-app/editor-web/draft-canvas/scripts/build-extensions.mjs
```

build-page-extensions 遇到无 *-source.js 的手写入口时不覆盖 JS。安装后显式加载开发目录，HTML、运行 JS、CSS 才会更新浏览区。发布修改增加包版本；已安装同版本不能覆盖不同内容，历史依赖的旧包保留。

迁移旧题库的约束与先前验证记录见 [迁移说明](../READING_TYPES_MIGRATION.md)。旧记录中的 2.2.x 版本不是当前开发接口。
