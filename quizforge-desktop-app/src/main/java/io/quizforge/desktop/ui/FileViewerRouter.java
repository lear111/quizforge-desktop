package io.quizforge.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import io.quizforge.core.question.QuestionBankFile;

/** File kind and mode route independently; no business navigation or metadata panels. */
final class FileViewerRouter {
    private final SafeMarkdownPreview markdown = new SafeMarkdownPreview();
    private final SafeMarkdownPreview.SourceActions sourceActions;
    private final Consumer<MarkdownOutline.Entry> copyLink;
    private final Consumer<String> openLink;
    private final Function<List<QuestionBankFile.SourceRef>, QuestionSourceListView> sources;

    FileViewerRouter(SafeMarkdownPreview.SourceActions sourceActions,
            Consumer<MarkdownOutline.Entry> copyLink, Consumer<String> openLink,
            Function<List<QuestionBankFile.SourceRef>, QuestionSourceListView> sources) {
        this.sourceActions = sourceActions;
        this.copyLink = copyLink;
        this.openLink = openLink;
        this.sources = sources;
    }

    Node view(FilePresentation file, FileMode mode) {
        return switch (file.kind()) {
            case MARKDOWN -> markdown.view(file.file().sourceText(), null, sourceActions, copyLink, openLink);
            case STANDARD_DOCUMENT -> file.empty()
                    ? UiTheme.quietState("此文档暂无内容", "开始编辑，或使用 AI 生成")
                    : markdown.view(file.file().sourceText(), file.registeredMarkdown(), sourceActions, copyLink, openLink);
            case QUESTION_BANK -> file.empty()
                    ? UiTheme.quietState("该题库暂无题目", "开始编辑，或使用 AI 生成") : practice(file);
            case DIRECTORY -> welcome();
            case INVALID_STANDARD_DOCUMENT, INVALID_QUESTION_BANK -> UiTheme.quietState("无法读取此文件",
                    file.file().entry().issue() == null ? "文件未通过格式校验。" : file.file().entry().issue());
            case OTHER -> UiTheme.quietState("暂不支持预览此类文件", "文件仍保存在当前工作区中。");
        };
    }

    List<MarkdownOutline.Entry> outlineEntries(FilePresentation file) {
        return markdown.outlineEntries(file.file().sourceText(), file.registeredMarkdown());
    }

    Node welcome() { return UiTheme.quietState("打开一个文件", "从左侧文件树中选择，开始阅读或练习。"); }

    private Node practice(FilePresentation file) {
        var practice = new QuestionBankPracticeView(file.file().questionBank(), sources);
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
