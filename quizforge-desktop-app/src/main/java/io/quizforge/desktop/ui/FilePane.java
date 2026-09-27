package io.quizforge.desktop.ui;

import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.document.navigation.QuizForgeNavigationLinkCodec;
import io.quizforge.core.document.registered.QuizForgeReference;
import io.quizforge.core.document.registered.QuizForgeReferenceCodec;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.document.registered.MarkdownSourceRange;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionBankFileEditService;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import java.util.function.BiConsumer;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Pos;

final class FilePane extends BorderPane {
    private final FilePresentationLoader loader;
    private final QuestionBankReferenceResolver references;
    private final BiConsumer<WorkspaceId, FilePresentation> aiAction;
    private final QuestionBankFileEditService bankEdits;
    private final MarkdownFileEditService markdownEdits;
    private final MarkdownDocumentRegistration registration;
    private final Runnable refreshTree;
    private final TextClipboard clipboard;
    private final FileViewerRouter router;
    private final QuizForgeReferenceCodec referencesCodec = new QuizForgeReferenceCodec();
    private final QuizForgeNavigationLinkCodec navigationCodec = new QuizForgeNavigationLinkCodec();
    private final AssetDetailsPopover details = new AssetDetailsPopover();
    private WorkspaceId workspace;
    private FilePresentation current;
    private FileMode mode = FileMode.BROWSE;
    private FileHeader header;
    private Node browseContent;
    private QuestionBankEditorView bankEditor;
    private MarkdownSourceEditorView markdownEditor;

    FilePane(FilePresentationLoader loader, QuestionBankReferenceResolver references,
            BiConsumer<WorkspaceId, FilePresentation> aiAction,
            QuestionBankFileEditService bankEdits, MarkdownFileEditService markdownEdits,
            MarkdownDocumentRegistration registration, Runnable refreshTree, TextClipboard clipboard) {
        this.loader = loader;
        this.references = references;
        this.aiAction = aiAction;
        this.bankEdits = bankEdits;
        this.markdownEdits = markdownEdits;
        this.registration = registration;
        this.refreshTree = refreshTree;
        this.clipboard = clipboard;
        this.router = new FileViewerRouter(new SafeMarkdownPreview.SourceActions() {
            @Override public void create(MarkdownSourceRange block) { createSourceReference(block); }
            @Override public void copy(List<NamedMarkdownAnchor> anchors) { copySourceReference(anchors); }
        }, this::copyNavigationLink);
        setId("file-pane");
        clear();
    }

    void open(WorkspaceId workspace, String path) {
        clear();
        this.workspace = workspace;
        try {
            current = loader.load(workspace, path);
            header = new FileHeader(current, this::toggleMode, anchor -> {
                try {
                    // Re-read the actual file and recompute revisions/references on every open.
                    var fresh = loader.load(this.workspace, current.file().entry().relativePath());
                    if (!fresh.asset()) { details.showFailure(anchor); return; }
                    details.show(anchor, fresh, this.workspace, references);
                } catch (RuntimeException error) { details.showFailure(anchor); }
            }, this::invokeAi);
            setTop(header);
            browseContent = router.view(current, FileMode.BROWSE);
            setCenter(browseContent);
        } catch (RuntimeException error) {
            current = null;
            setTop(null);
            setCenter(UiTheme.quietState("无法打开文件", "文件可能已移动或无法读取，请刷新工作区。"));
        }
    }

    private void toggleMode() {
        details.hide();
        if (mode == FileMode.EDIT && (bankEditor != null && bankEditor.dirty()
                || markdownEditor != null && markdownEditor.dirty())) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Unsaved changes will be discarded.", ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Leave edit mode?");
            UiTheme.apply(confirm);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            open(workspace, current.file().entry().relativePath());
            return;
        }
        mode = mode == FileMode.BROWSE ? FileMode.EDIT : FileMode.BROWSE;
        header.updateMode(mode);
        if (mode == FileMode.EDIT && markdownSource()) {
            markdownEditor = new MarkdownSourceEditorView(current.file().sourceText(), this::saveMarkdown);
            setCenter(new StackPane(markdownEditor));
        } else if (mode == FileMode.EDIT && current.kind() == io.quizforge.core.workspace.WorkspaceFileKind.QUESTION_BANK
                && current.file().questionBank() != null) {
            bankEditor = new QuestionBankEditorView(current.file().questionBank(), workspace,
                    bankEdits, this::saveBank);
            StackPane centered = new StackPane(bankEditor);
            centered.setAlignment(Pos.TOP_CENTER);
            setCenter(UiTheme.scroll(centered));
        } else setCenter(mode == FileMode.BROWSE ? browseContent : router.view(current, mode));
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
            clipboard.write(navigationCodec.encode(QuizForgeNavigationLink.asset(
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
            clipboard.write(navigationCodec.encode(link));
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
        ScrollPane reader = (ScrollPane) lookup("#markdown-preview-scroll");
        ScrollPane outline = (ScrollPane) lookup(".markdown-outline-scroll");
        double readingPosition = reader == null ? 0 : reader.getVvalue();
        double outlinePosition = outline == null ? 0 : outline.getVvalue();
        open(workspace, path);
        ScrollPane renewedReader = (ScrollPane) lookup("#markdown-preview-scroll");
        ScrollPane renewedOutline = (ScrollPane) lookup(".markdown-outline-scroll");
        if (renewedReader != null) renewedReader.setVvalue(readingPosition);
        if (renewedOutline != null) renewedOutline.setVvalue(outlinePosition);
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

    private void saveBank(io.quizforge.core.question.QuestionBankFile edited) {
        String path = current.file().entry().relativePath();
        bankEdits.save(workspace, path, current.file().entry().contentId(), current.file().sourceText(), edited);
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
        details.hide();
        current = null;
        workspace = null;
        browseContent = null;
        bankEditor = null;
        markdownEditor = null;
        mode = FileMode.BROWSE;
        setTop(null);
        setCenter(router.welcome());
    }

    FilePresentation currentFile() { return current; }
    boolean hasUnsavedChanges() {
        return bankEditor != null && bankEditor.dirty()
                || markdownEditor != null && markdownEditor.dirty();
    }
    FileMode mode() { return mode; }
    AssetDetailsPopover details() { return details; }
}
