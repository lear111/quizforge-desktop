package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.document.navigation.QuizForgeNavigationLinkCodec;
import io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec;
import io.quizforge.core.document.registered.QuizForgeReference;
import io.quizforge.core.document.registered.QuizForgeReferenceCodec;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.document.registered.MarkdownSourceRange;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Pos;

final class FilePane extends BorderPane {
    private final FilePresentationLoader loader;
    private final BiConsumer<WorkspaceId, FilePresentation> aiAction;
    private final QuestionBankFileEditService bankEdits;
    private final MarkdownFileEditService markdownEdits;
    private final MarkdownDocumentRegistration registration;
    private final Runnable refreshTree;
    private final TextClipboard clipboard;
    private final QuestionSourceLinkService sourceLinks;
    private final QuestionSourceNavigationAdapter sourceNavigation;
    private final FileViewerRouter router;
    private final io.quizforge.core.port.PracticeRuntimeProvider practice;
    private final QuizForgeReferenceCodec referencesCodec = new QuizForgeReferenceCodec();
    private final QuizForgeNavigationLinkCodec navigationCodec = new QuizForgeNavigationLinkCodec();
    private final MarkdownNavigationLinkCodec markdownLinks = new MarkdownNavigationLinkCodec();
    private WorkspaceId workspace;
    private FilePresentation current;
    private FileMode mode = FileMode.BROWSE;
    private enum Page { BROWSE, HISTORY_LIST, HISTORY_DETAIL }
    private Page page = Page.BROWSE;
    private PracticeHistoryView historyList;
    private PracticeHistoryDetailView historyDetail;
    private FileHeader header;
    private Node browseContent;
    private QuestionBankEditorView bankEditor;
    private MarkdownSourceEditorView markdownEditor;
    private Runnable onEditStart = () -> { };

    FilePane(FilePresentationLoader loader, BiConsumer<WorkspaceId, FilePresentation> aiAction,
            QuestionBankFileEditService bankEdits, MarkdownFileEditService markdownEdits,
            MarkdownDocumentRegistration registration, Runnable refreshTree, TextClipboard clipboard,
            QuestionSourceLinkService sourceLinks, Consumer<String> openLink,
            QuestionSourceNavigationAdapter sourceNavigation, io.quizforge.core.port.PracticeRuntimeProvider practice) {
        this.loader = loader;
        this.aiAction = aiAction;
        this.bankEdits = bankEdits;
        this.markdownEdits = markdownEdits;
        this.registration = registration;
        this.refreshTree = refreshTree;
        this.clipboard = clipboard;
        this.sourceLinks = sourceLinks;
        this.sourceNavigation = sourceNavigation;
        this.practice = practice;
        this.router = new FileViewerRouter(new SafeMarkdownPreview.SourceActions() {
            @Override public void create(MarkdownSourceRange block) { createSourceReference(block); }
            @Override public void copy(List<NamedMarkdownAnchor> anchors) { copySourceReference(anchors); }
        }, this::copyNavigationLink, openLink,
                refs -> new QuestionSourceListView(refs, workspace, sourceNavigation, null),
                (file, bank) -> practice.open(workspace, bank),
                file -> loader.resources(workspace,file.file().entry().relativePath()));
        setId("file-pane");
        clear();
    }

    void open(WorkspaceId workspace, String path) {
        FileViewerRouter.PracticeLayout previousLayout = current != null
                && java.util.Objects.equals(this.workspace, workspace)
                && current.file().entry().relativePath().equals(path)
                && browseContent instanceof FileViewerRouter.PracticeLayout layout ? layout : null;
        clear();
        this.workspace = workspace;
        try {
            current = loader.load(workspace, path);
            header = new FileHeader(current, this::toggleMode, this::invokeAi, this::openHistory);
            browseContent = router.view(current, FileMode.BROWSE);
            if (previousLayout != null && browseContent instanceof FileViewerRouter.PracticeLayout layout)
                layout.keepDividerPosition(previousLayout);
            showBrowseContent();
        } catch (RuntimeException error) {
            current = null;
            setTop(null);
            setCenter(UiTheme.quietState("无法打开文件", "文件可能已移动或无法读取，请刷新工作区。"));
        }
    }

    private void toggleMode() {
        if (page == Page.HISTORY_LIST) return;
        if (mode == FileMode.EDIT && (bankEditor != null && bankEditor.dirty()
                || markdownEditor != null && markdownEditor.dirty())) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "题库修改尚未保存。确定返回将放弃修改；如需保留，请取消并先保存题库。", ButtonType.CANCEL, ButtonType.OK);
            confirm.setTitle("返回浏览");
            confirm.setHeaderText("存在未保存的修改");
            if(getScene()!=null && getScene().getWindow()!=null) {
                confirm.initOwner(getScene().getWindow());
                confirm.initModality(javafx.stage.Modality.WINDOW_MODAL);
            }
            UiTheme.apply(confirm);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            open(workspace, current.file().entry().relativePath());
            return;
        }
        mode = mode == FileMode.BROWSE ? FileMode.EDIT : FileMode.BROWSE;
        if (mode == FileMode.EDIT) onEditStart.run();
        header.updateMode(mode);
        if (mode == FileMode.EDIT && browseContent instanceof SafeMarkdownPreview.BrowseLayout markdown) {
            markdown.setHeader(null);
            setTop(header);
        }
        if (mode == FileMode.EDIT && markdownSource()) {
            markdownEditor = new MarkdownSourceEditorView(current.file().sourceText(), this::saveMarkdown);
            setCenter(new StackPane(markdownEditor));
        } else if (mode == FileMode.EDIT && current.kind() == io.quizforge.core.workspace.WorkspaceFileKind.QUESTION_BANK
                && current.file().questionBank() != null) {
            bankEditor = new QuestionBankEditorView(current.file().questionBank(), workspace,
                    sourceLinks, sourceNavigation, this::saveBank,loader.resources(workspace,current.file().entry().relativePath()));
            StackPane centered = new StackPane(bankEditor);
            centered.setAlignment(Pos.TOP_CENTER);
            ScrollPane editorScroll = UiTheme.scroll(centered);
            if (browseContent instanceof FileViewerRouter.PracticeLayout practiceLayout) {
                QuestionBankEditorView editor = bankEditor;
                editor.jumpTo(practiceLayout.outline().currentIndex());
                editor.onQuestionChange((bank, selected) ->
                        practiceLayout.outline().showEditor(bank, selected, target -> {
                            editor.jumpTo(target);
                            editorScroll.setVvalue(0);
                        }));
                practiceLayout.setContent(editorScroll);
            } else if(browseContent instanceof QuestionBankAuthoringView authoring) {
                var editor=bankEditor;editor.jumpTo(authoring.currentIndex());
                editor.onQuestionChange(authoring::updateEditor);
                authoring.showEditor(editorScroll,target->{editor.jumpTo(target);editorScroll.setVvalue(0);});
            } else {
                setCenter(editorScroll);
            }
        } else {
            if (mode == FileMode.BROWSE && current.kind() == WorkspaceFileKind.QUESTION_BANK) {
                // Re-read the file and synchronize the active revision on every Edit -> Practice entry.
                try {
                    current = loader.load(workspace, current.file().entry().relativePath());
                    Node renewed = router.view(current, FileMode.BROWSE);
                    if (renewed instanceof FileViewerRouter.PracticeLayout next
                            && browseContent instanceof FileViewerRouter.PracticeLayout previous) {
                        next.keepDividerPosition(previous);
                        previous.setHeader(null);
                    }
                    browseContent = renewed;
                } catch (RuntimeException error) {
                    browseContent = UiTheme.quietState("无法恢复练习", error.getMessage());
                }
                bankEditor = null;
            }
            if (mode == FileMode.BROWSE) showBrowseContent();
            else setCenter(router.view(current, mode));
            if (mode == FileMode.BROWSE) refreshSourceStatus();
        }
    }

    private void showBrowseContent() {
        if (browseContent instanceof SafeMarkdownPreview.BrowseLayout markdown) {
            setTop(null);
            markdown.setHeader(header);
        } else if (browseContent instanceof FileViewerRouter.PracticeLayout practiceLayout) {
            setTop(null);
            practiceLayout.setHeader(header);
        } else if(browseContent instanceof QuestionBankAuthoringView authoring) {
            setTop(null);authoring.setHeader(header);
        } else {
            setTop(header);
        }
        setCenter(browseContent);
    }

    private boolean markdownSource() {
        return current.file().sourceText() != null
                && current.file().entry().relativePath().toLowerCase(java.util.Locale.ROOT).endsWith(".md");
    }

    private void createSourceReference(MarkdownSourceRange block) {
        if (current == null || !markdownSource()) return;
        ButtonType create = new ButtonType("Create and Copy", ButtonBar.ButtonData.OK_DONE);
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Source Reference");
        dialog.setHeaderText("Source anchor name");
        dialog.setContentText("Anchor name");
        dialog.getDialogPane().getButtonTypes().setAll(create, ButtonType.CANCEL);
        UiTheme.apply(dialog);
        if (dialog.showAndWait().isEmpty()) return;
        String name = dialog.getEditor().getText();
        try {
            String path = current.file().entry().relativePath();
            NamedMarkdownAnchor anchor = registration.createAnchor(workspace, path,
                    current.file().sourceText(), block.startLine(), block.startColumn(), name);
            FilePresentation fresh = loader.load(workspace, path);
            var document = fresh.registeredMarkdown();
            if (document == null) throw new IllegalStateException("Source document was not indexed");
            copyAnchor(document, anchor);
            refreshTree.run();
            open(workspace, path);
        } catch (RuntimeException error) { showReferenceError(error); }
    }

    private void copySourceReference(List<NamedMarkdownAnchor> anchors) {
        if (current == null || anchors.isEmpty()) return;
        NamedMarkdownAnchor selected = anchors.getFirst();
        if (anchors.size() > 1) {
            List<String> options = anchors.stream().map(anchor ->
                    anchor.name() + " #" + anchor.occurrence()).toList();
            ChoiceDialog<String> dialog = new ChoiceDialog<>(options.getFirst(), options);
            dialog.setTitle("Copy Source Reference");
            dialog.setHeaderText("Choose source anchor");
            dialog.setContentText("Anchor");
            UiTheme.apply(dialog);
            String choice = dialog.showAndWait().orElse(null);
            if (choice == null) return;
            selected = anchors.get(options.indexOf(choice));
        }
        try {
            String path = current.file().entry().relativePath();
            if (current.registeredMarkdown() == null) {
                if (selected.orphan()) throw new IllegalArgumentException("ORPHAN_ANCHOR");
                registration.createAnchor(workspace, path, current.file().sourceText(),
                        selected.blockRange().startLine(), selected.blockRange().startColumn(),
                        selected.name());
                refreshTree.run();
            }
            FilePresentation fresh = loader.load(workspace, path);
            var document = fresh.registeredMarkdown();
            if (document == null || current.registeredMarkdown() != null
                    && !document.contentId().equals(current.registeredMarkdown().contentId()))
                throw new IllegalStateException("Markdown changed externally; reload before copying");
            NamedMarkdownAnchor chosen = selected;
            selected = document.anchors().stream().filter(anchor -> anchor.name().equals(chosen.name())
                    && anchor.occurrence() == chosen.occurrence()).findFirst().orElse(null);
            if (selected == null || selected.orphan())
                throw new IllegalArgumentException("ORPHAN_ANCHOR");
            copyAnchor(document, selected);
            if (current.registeredMarkdown() == null) open(workspace, path);
        } catch (RuntimeException error) { showReferenceError(error); }
    }

    private void copyAnchor(io.quizforge.core.document.registered.RegisteredMarkdownDocument document,
            NamedMarkdownAnchor anchor) {
        if (anchor.orphan()) throw new IllegalArgumentException("ORPHAN_ANCHOR");
        String uri = referencesCodec.encode(QuizForgeReference.anchor(document.documentAssetId(),
                document.contentId(), anchor.name(), anchor.occurrence()));
        clipboard.write(uri);
    }

    void copyAssetLink(WorkspaceId targetWorkspace, WorkspaceFileEntry entry) {
        try {
            String path = entry.relativePath();
            boolean active = current != null && workspace != null && workspace.equals(targetWorkspace)
                    && current.file().entry().relativePath().equals(path);
            if (active && hasUnsavedChanges() && entry.assetId() == null)
                throw new IllegalStateException("Save Markdown changes before creating a link");
            ReadyLink ready = readyForLink(targetWorkspace, path, null);
            if (ready.registeredNow()) {
                refreshTree.run();
                if (active && mode == FileMode.BROWSE) reopenPreviewPreservingScroll(path);
            }
            clipboard.write(markdownLinks.format(path, QuizForgeNavigationLink.asset(
                    ready.file().file().entry().assetId())));
        } catch (RuntimeException error) { showNavigationError(error); }
    }

    private void copyNavigationLink(MarkdownOutline.Entry selected) {
        if (current == null || selected.orphan()) return;
        try {
            String path = current.file().entry().relativePath();
            ReadyLink ready = readyForLink(workspace, path, current.file().sourceText());
            MarkdownOutline.Entry target = router.outlineEntries(ready.file()).stream()
                    .filter(entry -> entry.kind() == selected.kind()
                            && entry.label().equals(selected.label())
                            && entry.occurrence() == selected.occurrence() && !entry.orphan())
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "Navigation target changed; reopen the document"));
            String assetId = ready.file().file().entry().assetId();
            QuizForgeNavigationLink link = target.kind() == MarkdownOutline.Kind.HEADING
                    ? QuizForgeNavigationLink.heading(assetId, target.label(), target.occurrence())
                    : QuizForgeNavigationLink.anchor(assetId, target.label(), target.occurrence());
            if (ready.registeredNow()) {
                refreshTree.run();
                reopenPreviewPreservingScroll(path);
            }
            clipboard.write(markdownLinks.format(path, link));
        } catch (RuntimeException error) { showNavigationError(error); }
    }

    private ReadyLink readyForLink(WorkspaceId targetWorkspace, String path, String expectedSource) {
        FilePresentation fresh = loader.load(targetWorkspace, path);
        if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(".md")
                || fresh.kind() != WorkspaceFileKind.MARKDOWN
                        && fresh.kind() != WorkspaceFileKind.STANDARD_DOCUMENT)
            throw new IllegalArgumentException("Only readable Markdown can be linked");
        if (expectedSource != null && !expectedSource.equals(fresh.file().sourceText()))
            throw new IllegalStateException("Markdown changed externally; reopen before copying");
        boolean registeredNow = fresh.file().entry().assetId() == null;
        if (registeredNow) {
            registration.registerDocument(targetWorkspace, path, fresh.file().sourceText());
            fresh = loader.load(targetWorkspace, path);
        }
        if (fresh.file().entry().assetId() == null)
            throw new IllegalStateException("Markdown document was not indexed");
        return new ReadyLink(fresh, registeredNow);
    }

    private void reopenPreviewPreservingScroll(String path) {
        SafeMarkdownPreview.BrowseLayout previous = markdownBrowseLayout();
        double readingPosition = previous == null ? 0 : previous.reader().getVvalue();
        double outlinePosition = previous == null ? 0 : previous.outlineScroll().getVvalue();
        open(workspace, path);
        SafeMarkdownPreview.BrowseLayout renewed = markdownBrowseLayout();
        if (renewed != null) {
            renewed.reader().setVvalue(readingPosition);
            renewed.outlineScroll().setVvalue(outlinePosition);
        }
    }

    private SafeMarkdownPreview.BrowseLayout markdownBrowseLayout() {
        return browseContent instanceof SafeMarkdownPreview.BrowseLayout markdown ? markdown : null;
    }

    Region visibleOutline() {
        if (getCenter() instanceof FileViewerRouter.PracticeLayout practiceLayout) return practiceLayout.outline();
        if (getCenter() instanceof QuestionBankAuthoringView authoring) return authoring.outline();
        if (mode != FileMode.BROWSE) return null;
        if (getCenter() instanceof SafeMarkdownPreview.BrowseLayout markdown) return markdown.outline();
        if (getCenter() instanceof PracticeHistoryDetailView detail) return detail.outline();
        return null;
    }

    private void showNavigationError(RuntimeException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        alert.setHeaderText("无法复制链接");
        UiTheme.apply(alert);
        alert.showAndWait();
    }

    private record ReadyLink(FilePresentation file, boolean registeredNow) { }

    private void showReferenceError(RuntimeException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        alert.setHeaderText("无法创建或复制来源引用");
        UiTheme.apply(alert);
        alert.showAndWait();
    }

    private void saveMarkdown(String edited) {
        String path = current.file().entry().relativePath();
        String assetId = current.kind() == io.quizforge.core.workspace.WorkspaceFileKind.STANDARD_DOCUMENT
                && !current.draft() ? current.file().entry().assetId() : null;
        markdownEdits.save(workspace, path, current.file().sourceText(), edited, assetId);
        refreshTree.run();
        open(workspace, path);
    }

    private void saveBank(io.quizforge.core.question.QuestionBank edited) {
        String path = current.file().entry().relativePath();
        bankEdits.save(workspace, path, current.file().entry().contentId(), current.file().bankRevision(), edited,
                bankEditor==null?io.quizforge.core.port.QuestionResourceInput.NONE:bankEditor.resources());
        open(workspace, path);
    }

    private void invokeAi() {
        if (markdownEditor != null && markdownEditor.dirty()) {
            Alert warning = new Alert(Alert.AlertType.INFORMATION,
                    "Save or leave edit mode before generating content.", ButtonType.OK);
            UiTheme.apply(warning);
            warning.showAndWait();
            return;
        }
        try {
            FilePresentation fresh = loader.load(workspace, current.file().entry().relativePath());
            if (fresh.offersAi()) aiAction.accept(workspace, fresh);
            else open(workspace, current.file().entry().relativePath());
        } catch (RuntimeException error) {
            open(workspace, current.file().entry().relativePath());
        }
    }

    void clear() {
        current = null;
        workspace = null;
        browseContent = null;
        bankEditor = null;
        markdownEditor = null;
        mode = FileMode.BROWSE;
        page = Page.BROWSE;
        historyList = null;
        historyDetail = null;
        setTop(null);
        setCenter(router.welcome());
    }

    FilePresentation currentFile() { return current; }
    private void openHistory() {
        if (page != Page.BROWSE) return;
        if (mode == FileMode.EDIT && bankEditor != null) toggleMode();
        if (current == null || workspace == null || mode != FileMode.BROWSE
                || current.kind() != WorkspaceFileKind.QUESTION_BANK || current.file().entry().assetId() == null) return;
        try {
            historyList = new PracticeHistoryView(practice.history(workspace), current.file().entry().assetId(),
                    current.file().entry().name(), this::returnToBank, this::openHistoryDetail);
            page = Page.HISTORY_LIST;
            header.showHistory(false);
            header.showMode(false);
            if (browseContent instanceof FileViewerRouter.PracticeLayout practiceLayout)
                practiceLayout.setHeader(null);
            else if (browseContent instanceof QuestionBankAuthoringView authoring)
                authoring.setHeader(null);
            setTop(header);
            setCenter(historyList);
        } catch (RuntimeException error) {
            setCenter(UiTheme.quietState("无法读取练习历史", error.getMessage()));
        }
    }

    private void openHistoryDetail(String sessionId) {
        if (page != Page.HISTORY_LIST || historyList == null) return;
        try {
            var detail = practice.history(workspace).loadArchivedSessionDetail(current.file().entry().assetId(), sessionId);
            historyDetail = new PracticeHistoryDetailView(detail, this::returnToHistoryList, workspace,
                    new HistorySourceNavigationAdapter(sourceNavigation));
            setTop(null);
            historyDetail.setHeader(header);
            setCenter(historyDetail);
            page = Page.HISTORY_DETAIL;
        } catch (RuntimeException failure) {
            historyList.showLoadError(failure);
        }
    }

    private void returnToHistoryList() {
        page = Page.HISTORY_LIST;
        if (historyDetail != null) historyDetail.setHeader(null);
        setTop(header);
        setCenter(historyList);
    }

    private void returnToBank() {
        page = Page.BROWSE;
        header.showHistory(true);
        header.showMode(true);
        showBrowseContent();
    }
    void refreshForDevelopment() {
        if (mode != FileMode.BROWSE || page != Page.BROWSE || current == null
                || !(browseContent instanceof SafeMarkdownPreview.BrowseLayout previous)) return;
        previous.setHeader(null);
        browseContent = router.view(current, FileMode.BROWSE);
        if (browseContent instanceof SafeMarkdownPreview.BrowseLayout next)
            next.setDividerPositions(previous.getDividerPositions());
        showBrowseContent();
    }
    void refreshBrowseFromDisk() {
        if (mode != FileMode.BROWSE || current == null || !markdownSource()) return;
        String path = current.file().entry().relativePath();
        try {
            var fresh = loader.load(workspace, path);
            if (!java.util.Objects.equals(fresh.file().sourceText(), current.file().sourceText()))
                open(workspace, path);
        } catch (RuntimeException error) { open(workspace, path); }
    }
    void refreshSourceStatus() {
        if (page == Page.HISTORY_DETAIL && historyDetail != null) historyDetail.refreshSources();
        else if (page == Page.HISTORY_LIST) return;
        else if (mode == FileMode.EDIT && bankEditor != null) bankEditor.refreshSources();
        else if (browseContent != null
                && browseContent.lookup("#question-practice") instanceof QuestionBankPracticeView practice)
            practice.refreshSources();
    }
    void onEditStart(Runnable action) { onEditStart = action; }
    boolean jumpTo(MarkdownOutline.Kind kind, String label, int occurrence) {
        if (mode != FileMode.BROWSE || !(browseContent instanceof SafeMarkdownPreview.BrowseLayout layout)) return false;
        Object entriesValue = layout.getProperties().get("quizforge.outlineEntries");
        Object navigatorValue = layout.getProperties().get("quizforge.navigator");
        if (!(entriesValue instanceof List<?> entries)
                || !(navigatorValue instanceof MarkdownDocumentNavigator navigator)) return false;
        for (Object value : entries) {
            if (value instanceof MarkdownOutline.Entry entry && entry.kind() == kind
                    && entry.label().equals(label) && entry.occurrence() == occurrence)
                return navigator.jumpWhenReady(entry);
        }
        return false;
    }
    boolean hasUnsavedChanges() {
        return bankEditor != null && bankEditor.dirty()
                || markdownEditor != null && markdownEditor.dirty();
    }
    FileMode mode() { return mode; }
}
