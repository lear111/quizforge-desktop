package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** File kind and mode route independently; no business navigation or metadata panels. */
final class FileViewerRouter {
    private final SafeMarkdownPreview markdown = new SafeMarkdownPreview();
    private final SafeMarkdownPreview.SourceActions sourceActions;
    private final Consumer<MarkdownOutline.Entry> copyLink;
    private final Consumer<String> openLink;
    private final Function<List<SourceRef>, QuestionSourceListView> sources;
    private final Function<FilePresentation, io.quizforge.core.practice.PersistentPracticeRuntime> practiceRuntime;

    FileViewerRouter(SafeMarkdownPreview.SourceActions sourceActions,
            Consumer<MarkdownOutline.Entry> copyLink, Consumer<String> openLink,
            Function<List<SourceRef>, QuestionSourceListView> sources,
            Function<FilePresentation, io.quizforge.core.practice.PersistentPracticeRuntime> practiceRuntime) {
        this.sourceActions = sourceActions;
        this.copyLink = copyLink;
        this.openLink = openLink;
        this.sources = sources;
        this.practiceRuntime = practiceRuntime;
    }

    Node view(FilePresentation file, FileMode mode) {
        return switch (file.kind()) {
            case MARKDOWN -> markdown.view(file.file().sourceText(), null, sourceActions, copyLink, openLink);
            case STANDARD_DOCUMENT -> file.empty()
                    ? UiTheme.quietState("此文档暂无内容", "开始编辑，或使用 AI 生成")
                    : markdown.view(file.file().sourceText(), file.registeredMarkdown(), sourceActions, copyLink, openLink);
            case QUESTION_BANK -> file.empty()
                    ? UiTheme.quietState("该题库暂无题目", "开始编辑，或使用 AI 生成")
                    : io.quizforge.core.question.QuestionText.supports(file.file().questionBank()) ? practice(file)
                    : UiTheme.quietState("暂不支持此题库内容", "当前题目界面暂不支持 RICH 内容和共享材料，原始内容已保留在题库文件中。");
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
        var practice = new QuestionBankPracticeView(practiceRuntime.apply(file), sources);
        var scroll = QuestionCardLayout.scroll(practice);
        scroll.getContent().setId("practice-stage");
        scroll.setId("practice-scroll");
        return new PracticeLayout(scroll, practice.outline());
    }

    static final class PracticeLayout extends SplitPane {
        private final BorderPane readerColumn = new BorderPane();
        private final QuestionOutlineView outline;
        private boolean initialDividerSet;

        PracticeLayout(ScrollPane reader, QuestionOutlineView outline) {
            this.outline = outline;
            setId("practice-layout");
            getStyleClass().add("practice-browse-layout");
            setMinWidth(0);
            readerColumn.setMinWidth(320);
            readerColumn.setCenter(reader);
            getItems().addAll(readerColumn, outline);
            SplitPane.setResizableWithParent(outline, false);
            widthProperty().addListener((ignored, before, width) -> {
                if (initialDividerSet || width.doubleValue() <= 500) return;
                initialDividerSet = true;
                setDividerPositions((width.doubleValue() - 260) / width.doubleValue());
            });
        }

        void setHeader(Node header) { readerColumn.setTop(header); }
        void setContent(Node content) { readerColumn.setCenter(content); }
        void keepDividerPosition(PracticeLayout previous) {
            initialDividerSet = true;
            setDividerPositions(previous.getDividerPositions());
        }
        QuestionOutlineView outline() { return outline; }
    }
}
