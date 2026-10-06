package io.quizforge.desktop.ui.file;

import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.desktop.ui.markdown.MarkdownOutline;
import io.quizforge.desktop.ui.markdown.SafeMarkdownPreview;
import io.quizforge.desktop.ui.question.practice.MixedQuestionPracticeView;



import io.quizforge.desktop.ui.question.source.QuestionSourceListView;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.scene.Node;

/** File kind and mode route independently; no business navigation or metadata panels. */
final class FileViewerRouter {
    private final SafeMarkdownPreview markdown = new SafeMarkdownPreview();
    private final SafeMarkdownPreview.SourceActions sourceActions;
    private final Consumer<MarkdownOutline.Entry> copyLink;
    private final Consumer<String> openLink;
    private final Function<List<SourceRef>, QuestionSourceListView> sources;
    private final BiFunction<FilePresentation, QuestionBank, io.quizforge.core.practice.PersistentPracticeRuntime> practiceRuntime;
    private final Function<FilePresentation,io.quizforge.core.port.QuestionResourceInput> resources;

    FileViewerRouter(SafeMarkdownPreview.SourceActions sourceActions,
            Consumer<MarkdownOutline.Entry> copyLink, Consumer<String> openLink,
            Function<List<SourceRef>, QuestionSourceListView> sources,
            Function<FilePresentation, io.quizforge.core.practice.PersistentPracticeRuntime> practiceRuntime) {
        this(sourceActions,copyLink,openLink,sources,practiceRuntime,file->io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    FileViewerRouter(SafeMarkdownPreview.SourceActions sourceActions, Consumer<MarkdownOutline.Entry> copyLink,Consumer<String> openLink,
            Function<List<SourceRef>,QuestionSourceListView> sources,Function<FilePresentation,io.quizforge.core.practice.PersistentPracticeRuntime> practiceRuntime,
            Function<FilePresentation,io.quizforge.core.port.QuestionResourceInput> resources) {
        this(sourceActions, copyLink, openLink, sources, (file, bank) -> practiceRuntime.apply(file), resources);
    }
    FileViewerRouter(SafeMarkdownPreview.SourceActions sourceActions, Consumer<MarkdownOutline.Entry> copyLink,Consumer<String> openLink,
            Function<List<SourceRef>,QuestionSourceListView> sources,
            BiFunction<FilePresentation,QuestionBank,io.quizforge.core.practice.PersistentPracticeRuntime> practiceRuntime,
            Function<FilePresentation,io.quizforge.core.port.QuestionResourceInput> resources) {
        this.sourceActions = sourceActions;
        this.copyLink = copyLink;
        this.openLink = openLink;
        this.sources = sources;
        this.practiceRuntime = practiceRuntime;
        this.resources=resources;
    }

    Node view(FilePresentation file, FileMode mode) {
        return switch (file.kind()) {
            case MARKDOWN -> markdown.view(file.file().sourceText(), null, sourceActions, copyLink, openLink);
            case REGISTERED_MARKDOWN -> file.empty()
                    ? UiTheme.quietState("此文档暂无内容", "开始编辑，添加内容。")
                    : markdown.view(file.file().sourceText(), file.registeredMarkdown(), sourceActions, copyLink, openLink);
            case QUESTION_BANK -> file.empty()
                    ? UiTheme.quietState("该题库暂无题目", "开始编辑，添加内容。")
                    : file.file().questionBank().questions().stream().anyMatch(question -> io.quizforge.core.question.type.QuestionTypes.find(question.type()).isEmpty())
                        ? new MixedQuestionPracticeView(file.file().questionBank(),resources.apply(file))
                    : new MixedQuestionPracticeView(file.file().questionBank(),resources.apply(file),
                            () -> practiceRuntime.apply(file, file.file().questionBank()), sources);
            case DIRECTORY -> welcome();
            case INVALID_REGISTERED_MARKDOWN, INVALID_QUESTION_BANK -> UiTheme.quietState("无法读取此文件",
                    file.file().entry().issue() == null ? "文件未通过格式校验。" : file.file().entry().issue());
            case OTHER -> UiTheme.quietState("暂不支持预览此类文件", "文件仍保存在当前工作区中。");
        };
    }

    List<MarkdownOutline.Entry> outlineEntries(FilePresentation file) {
        return markdown.outlineEntries(file.file().sourceText(), file.registeredMarkdown());
    }

    Node welcome() { return UiTheme.quietState("打开一个文件", "从左侧文件树中选择，开始阅读或练习。"); }

}
