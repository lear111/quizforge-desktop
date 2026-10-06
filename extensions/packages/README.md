# 单选、多选、判断：使用公共功能接口的示例

三份题型均使用 SDK 2。题干、选项为普通文本；答案与解析支持富文本。题库数据结构、默认模板和评分规则沿用当前版本。判断题固定“正确／错误”两项，源码已按文件职责加入开发注释，建议新题型从它开始。

从零开发流程见 [拓展开发指南](../DEVELOPMENT_GUIDE.md)；判断题的数据字段与源码阅读路线见 [模板说明](../QUESTION_TEMPLATE.md)。

## 页面与接口

| 文件 | 自己绘制的 UI | 使用的接口 |
| --- | --- | --- |
| `editor.html` / `editor.js` | 题干、选项、正确答案、分值、解析 | `QF.editor`、`QF.ids`、`QF.content.mountEditor` |
| 同上 | 保存、新增题目、复制、删除确认 | `QF.bank` |
| 同上 | 添加、打开和移除来源 | `QF.sources` |
| `practice.html` / `practice.js` | 题目信息、选择、结果、富文本解析 | `QF.practice`、`QF.answer`、`QF.content.render` |
| 同上 | 提交确认、取消、重试 | `QF.practice.submit/retry` |
| 同上 | 默认启用右上角草稿入口、悬浮白板工具及缩放 | `QF.ui.configure` 的 `draftToggle`、`draftToolbar`、`draftZoom` |
| 同上 | 提交后打开来源 | `QF.sources.open` |
| `style.css` | 两个页面的样式 | 普通 CSS |
| 两个页面的 JS | 题卡宽度、初始对齐、浏览区留白 | `QF.layout.configure` |
| `type.js` | 校验、判分、复制和模板规则 | 逻辑运行时，不操作页面 |

页面通过 `QF.ui.configure` 隐藏已由自己绘制的默认 UI，通过 `QF.host.subscribe` 重新读取宿主状态。上一题/下一题由宿主保留。正式保存、评分、轮次、草稿持久化及权限检查仍由宿主执行。

## 开发时注意

- 普通文本使用 `textContent` 或输入控件的 `value`；不要拼接用户内容为 HTML。
- 编辑页显式声明居中、顶部自然排版；练习页显式声明居中布局。修改页面脚本中的 `QF.layout.configure` 即可调整；已有草稿尺寸和位置优先保留。
- 执行操作前记录下拉框或输入框的值；刷新状态可能重建控件。
- 保存、新增、复制和删除等操作执行期间禁用相关按钮，避免重复请求。
- 提交确认由本例 HTML 绘制，因此设置 `confirmation:false` 后再调用 `QF.practice.submit()`。
- 示例题型使用公共草稿布局，不在题卡中插入第二套草稿控件。
- 开发者可通过 `QF.ui.configure({draftToggle:false,draftToolbar:false,draftZoom:false})` 隐藏公共草稿入口、工具栏和缩放；这只控制显示，不修改草稿数据或操作权限。
- 如需自定义工具，可调用 `QF.learning`、`QF.whiteboard` 实现交互，并用 `QF.ui.mountControls` 标记自己的工具区，使其在画笔和拖动模式下仍能点击；答题区不作该标记。
- 历史和网页预览禁止作答。历史可切换查看草稿和缩放，但不允许修改笔迹、纸张或答案。
- 独立开发预览没有正式题库或白板接口，使用开发窗口提供的测试提交按钮。

接口参数、返回值和权限见 [公共 UI 功能接口](../PUBLIC_UI_API.md)。
