# 新增题型：HTML SDK 2 五步

完整开发流程与新对话提示词见 [拓展开发指南](../../extensions/DEVELOPMENT_GUIDE.md)。推荐复制 [带注释判断题模板](../../extensions/QUESTION_TEMPLATE.md)；接口细节以 SDK 已实现能力为准。

1. 复制 `extensions/packages/single-choice` 或 `multiple-choice` 或 `true-false` 到独立开发目录，修改 manifest 的扩展身份、题型身份和版本。
2. 修改 default.json：使用题库中一条完整 Question 的同一结构，并同步 question/answer Schema。题型私有数据放 EXTENSION 的 data 中。
3. 编辑 editor.html/editor.js：使用 QF.editor 读写题目、QF.content 挂载公共富文本、QF.ids 创建身份。
4. 编辑 practice.html/practice.js 和 type.js：页面处理作答，逻辑校验并通过 ctx.reportResult 上报同步分值。白板、历史、提交确认、重试、统计由宿主管理。
5. 先用“独立开发预览”测试，使用独立 pack.mjs 打包到源码目录之外；导入、授权并重启后，可以显式加载开发目录检查页面热更新。发布修改需增加包版本。

当前提供单选、多选、判断题三份独立安装包；其余原题型暂时缺少新版扩展。详细可调用接口及边界见 [SDK 2](../../extensions/SDK_README.md)。
