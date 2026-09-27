package io.quizforge.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/** File kind and mode route independently; no business navigation or metadata panels. */
final class FileViewerRouter {
    private final SafeMarkdownPreview markdown = new SafeMarkdownPreview();
    private final QDocDocumentView qdoc = new QDocDocumentView();

    Node view(FilePresentation file, FileMode mode) {
        return switch (file.kind()) {
            case MARKDOWN -> markdown.view(file.file().sourceText());
            case STANDARD_DOCUMENT -> file.empty()
                    ? UiTheme.quietState("此文档暂无内容", "开始编辑，或使用 AI 生成")
                    : file.document() == null ? markdown.view(file.file().sourceText())
                    : qdoc.view(file.document());
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
        var practice = new QuestionBankPracticeView(file.file().questionBank());
        practice.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        StackPane aligned = new StackPane(practice);
        aligned.setId("practice-stage");
        aligned.setAlignment(Pos.CENTER);
        var scroll = UiTheme.scroll(aligned);
        // Fill a short viewport, but let a long question grow and scroll naturally.
        aligned.minHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty()));
        return scroll;
    }
}
