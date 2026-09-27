package io.quizforge.desktop.ui;

import io.quizforge.core.document.qdoc.QDocFileEditService;
import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.document.registered.QuizForgeReference;
import io.quizforge.core.document.registered.QuizForgeReferenceCodec;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.document.registered.MarkdownSourceRange;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionBankFileEditService;
import io.quizforge.core.workspace.WorkspaceId;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.geometry.Pos;

final class FilePane extends BorderPane {
    private final FilePresentationLoader loader;
    private final QuestionBankReferenceResolver references;
    private final BiConsumer<WorkspaceId, FilePresentation> aiAction;
    private final QDocFileEditService qdocEdits;
    private final QuestionBankFileEditService bankEdits;
    private final MarkdownFileEditService markdownEdits;
    private final MarkdownDocumentRegistration registration;
    private final Runnable refreshTree;
    private final FileViewerRouter router;
    private final QuizForgeReferenceCodec referencesCodec = new QuizForgeReferenceCodec();
    private final AssetDetailsPopover details = new AssetDetailsPopover();
    private WorkspaceId workspace;
    private FilePresentation current;
    private FileMode mode = FileMode.BROWSE;
    private FileHeader header;
    private Node browseContent;
    private QDocEditorView qdocEditor;
    private QuestionBankEditorView bankEditor;
    private MarkdownSourceEditorView markdownEditor;

    FilePane(FilePresentationLoader loader, QuestionBankReferenceResolver references,
            BiConsumer<WorkspaceId, FilePresentation> aiAction, QDocFileEditService qdocEdits,
            QuestionBankFileEditService bankEdits, MarkdownFileEditService markdownEdits,
            MarkdownDocumentRegistration registration, Runnable refreshTree) {
        this.loader = loader;
        this.references = references;
        this.aiAction = aiAction;
        this.qdocEdits = qdocEdits;
        this.bankEdits = bankEdits;
        this.markdownEdits = markdownEdits;
        this.registration = registration;
        this.refreshTree = refreshTree;
        this.router = new FileViewerRouter(new SafeMarkdownPreview.SourceActions() {
            @Override public void create(MarkdownSourceRange block) { createSourceReference(block); }
            @Override public void copy(List<NamedMarkdownAnchor> anchors) { copySourceReference(anchors); }
        });
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
        if (mode == FileMode.EDIT && (qdocEditor != null && qdocEditor.dirty()
                || bankEditor != null && bankEditor.dirty()
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
        } else if (mode == FileMode.EDIT && current.document() != null) {
            qdocEditor = new QDocEditorView(current.document(), this::saveQDoc, this::invokeAi);
            StackPane centered = new StackPane(qdocEditor);
            centered.setAlignment(Pos.TOP_CENTER);
            setCenter(UiTheme.scroll(centered));
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
        ClipboardContent content = new ClipboardContent();
        content.putString(uri);
        Clipboard.getSystemClipboard().setContent(content);
    }

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

    private void saveQDoc(io.quizforge.core.document.qdoc.QDocDocument edited) {
        String path = current.file().entry().relativePath();
        qdocEdits.save(workspace, path, current.file().entry().contentId(), edited);
        open(workspace, path);
    }

    private void saveBank(io.quizforge.core.question.QuestionBankFile edited) {
        String path = current.file().entry().relativePath();
        bankEdits.save(workspace, path, current.file().entry().contentId(), current.file().sourceText(), edited);
        open(workspace, path);
    }

    private void invokeAi() {
        if (qdocEditor != null && qdocEditor.dirty()) {
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
        qdocEditor = null;
        bankEditor = null;
        markdownEditor = null;
        mode = FileMode.BROWSE;
        setTop(null);
        setCenter(router.welcome());
    }

    FilePresentation currentFile() { return current; }
    boolean hasUnsavedChanges() {
        return qdocEditor != null && qdocEditor.dirty()
                || bankEditor != null && bankEditor.dirty()
                || markdownEditor != null && markdownEditor.dirty();
    }
    FileMode mode() { return mode; }
    AssetDetailsPopover details() { return details; }
}
