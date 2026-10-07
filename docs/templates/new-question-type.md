# 新增题型：SDK 2.3 五步

更新日期：2026-10-07。新页面使用 `pageApi: "simple"`。完整步骤见 [开发指南](../../extensions/DEVELOPMENT_GUIDE.md)，具体契约见 [接口参考](../../extensions/SIMPLE_PAGE_API.md)。

1. **复制模板。** 推荐 `extensions/packages/true-false/` 的 11 文件手写模板。修改包 ID、题型 ID、名称和版本；保留 sdkApiMajor:2、minSdkApiMinor:3、pageApi:"simple"。
2. **定义数据。** 修改 default.json、question/answer Schema；自定义私有数据使用 EXTENSION.data。准备并声明真实 examples/basic.qbank，与新建模板分开。
3. **写编辑页。** page.register 接收完整题目；save editDraft 更新草稿，save edit 正式保存；requestAction 管理增删复制、来源。公共富文本编辑使用 content.mountEditor。
4. **写练习与规则。** onLoad 恢复公开题目和作答；save draft/submit 保存与提交，requestAction retry 重试。type.js 同步校验并 reportResult，复合题可声明 targets。历史/预览复用练习页，白板和统计由应用提供。
5. **预览、打包、安装。** 独立开发预览检查；用 pack.mjs 打包到源码目录外；导入、授权、重启后显式连接开发目录。修改页面资产可热更新；修改规则/Schema/默认数据需独立预览或新版本安装，发布必须增加包版本。

目前有八种外部示例包。判断题直接维护 JS，另外七型先修改 *-source.js/shared 再构建。不要使用旧版 QF.editor/answer/bank/practice 命名空间，不另建数据库、原生桥或评分保存旁路。

模板阅读顺序与注释见 [判断题说明](../../extensions/QUESTION_TEMPLATE.md)；数据限制见 [数据校验](../../extensions/DATA_VALIDATION.md)。
