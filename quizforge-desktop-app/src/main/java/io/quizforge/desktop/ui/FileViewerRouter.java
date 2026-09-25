package io.quizforge.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/** File kind and mode route independently; no business navigation or metadata panels. */
final class FileViewerRouter {
    private final SafeMarkdownPreview markdown = new SafeMarkdownPreview();

    Node view(FilePresentation file, FileMode mode) {
        if (mode == FileMode.EDIT && file.supportsMode()) {
            Node placeholder = UiTheme.quietState(file.kind() == io.quizforge.core.workspace.WorkspaceFileKind.QUESTION_BANK
                    ? "题库编辑器" : "文档编辑器", "编辑功能将在后续版本提供，当前文件未发生修改。");
            placeholder.setId("editor-placeholder");
            return placeholder;
        }
        return switch (file.kind()) {
            case MARKDOWN -> markdown.view(file.file().sourceText());
            case STANDARD_DOCUMENT -> file.empty()
                    ? UiTheme.quietState("此文档暂无内容", "开始编辑，或使用 AI 生成")
                    : markdown.view(file.file().sourceText());
            case QUESTION_BANK -> file.empty()
                    ? UiTheme.quietState("该题库暂无题目", "开始编辑，或使用 AI 生成") : practice(file);
            case DIRECTORY -> welcome();
            case INVALID_STANDARD_DOCUMENT, INVALID_QUESTION_BANK -> UiTheme.quietState("无法读取此文件",
                    file.file().entry().issue() == null ? "文件未通过格式校验。" : file.file().entry().issue());
            case OTHER -> UiTheme.quietState("暂不支持预览此类文件", "文件仍保存在当前工作区中。");
        };
    }

    Node welcome() { return UiTheme.quietState("打开一个文件", "从左侧文件树中选择，开始阅读或练习。"); }

    private Node practice(FilePresentation file) {
        StackPane aligned = new StackPane(new QuestionBankPracticeView(file.file().questionBank()));
        aligned.setAlignment(Pos.TOP_CENTER);
        return UiTheme.scroll(aligned);
    }
}
