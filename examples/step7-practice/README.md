# Step 7 练习题库样例

这组文件用于手动验收 QuestionBank 的 Practice / Edit 模式：4 道题，包含 2 道单选和 2 道多选。

1. 将 `Java集合示例.md` 和 `Java集合练习.qbank` 复制到同一个现有 Workspace 的任意目录。无需放入默认分类目录。
2. 在 QuizForge Desktop 中刷新文件树，打开 `Java集合练习.qbank`。默认进入 Practice；右上角的单个模式按钮可切换到 Edit。
3. 题库采用 QBank v2 JSON。两个文件的来源关系通过 Markdown 文档的 assetId、contentId、anchorName 和 occurrence 建立。若修改了文档内容，题库的来源状态会变为不同修订；题库文件本身仍可打开。

`Java 学习长文档（滚动测试）.md` 是额外的正式知识文档，包含 8 章、48 节，用于测试长页滚动、列表、引用和代码块显示；它不属于上述题库的来源。

仓库中的这些文件是可复制的示例资产，不是程序运行时 Workspace 数据。
