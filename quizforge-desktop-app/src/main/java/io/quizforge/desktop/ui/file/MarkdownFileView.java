package io.quizforge.desktop.ui.file;

import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.document.registered.MarkdownSourceRange;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.document.registered.QuizForgeReference;
import io.quizforge.core.document.registered.QuizForgeReferenceCodec;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.workspace.model.WorkspaceFileEntry;
import io.quizforge.core.workspace.model.WorkspaceFileKind;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.ui.markdown.MarkdownDocumentNavigator;
import io.quizforge.desktop.ui.markdown.MarkdownOutline;
import io.quizforge.desktop.ui.markdown.MarkdownSourceEditorView;
import io.quizforge.desktop.ui.markdown.SafeMarkdownPreview;
import io.quizforge.desktop.ui.shared.TextClipboard;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Markdown page owns its source edit, references and reading position. */
final class MarkdownFileView implements FileView {
    private final FilePageHost host;
    private final FilePresentationLoader loader;
    private final FileViewerRouter router;
    private final MarkdownFileEditService markdownEdits;
    private final MarkdownDocumentRegistration registration;
    private final Runnable refreshTree,onEditStart;
    private final TextClipboard clipboard;
    private final QuizForgeReferenceCodec referencesCodec=new QuizForgeReferenceCodec();
    private final MarkdownNavigationLinkCodec markdownLinks=new MarkdownNavigationLinkCodec();
    private final WorkspaceId workspace;
    private final FilePresentation current;
    private FileMode mode=FileMode.BROWSE;
    private FileHeader header;
    private Node browseContent;
    private MarkdownSourceEditorView markdownEditor;
    MarkdownFileView(FilePageHost host,FilePresentationLoader loader,FileViewerRouter router,MarkdownFileEditService edits,
            MarkdownDocumentRegistration registration,Runnable refreshTree,TextClipboard clipboard,
            WorkspaceId workspace,FilePresentation current,Runnable onEditStart) {
        this.host=host;this.loader=loader;this.router=router;this.markdownEdits=edits;this.registration=registration;
        this.refreshTree=refreshTree;this.clipboard=clipboard;this.workspace=workspace;this.current=current;this.onEditStart=onEditStart;
    }
    void show(){
        header=new FileHeader(current,this::toggleMode,()->{});
        browseContent=router.view(current,FileMode.BROWSE);showBrowseContent();
    }
    private void toggleMode(){
        if(mode==FileMode.EDIT && hasUnsavedChanges()){
            Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"文件修改尚未保存，返回将放弃修改。",ButtonType.CANCEL,ButtonType.OK);
            confirm.setHeaderText("存在未保存的修改");
            if(getScene()!=null)confirm.initOwner(getScene().getWindow());UiTheme.apply(confirm);
            if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
            open(workspace,current.file().entry().relativePath());return;
        }
        mode=mode==FileMode.BROWSE?FileMode.EDIT:FileMode.BROWSE;header.updateMode(mode);
        if(mode==FileMode.EDIT){
            onEditStart.run();if(browseContent instanceof SafeMarkdownPreview.BrowseLayout layout)layout.setHeader(null);
            setTop(header);markdownEditor=new MarkdownSourceEditorView(current.file().sourceText(),this::saveMarkdown);
            setCenter(new StackPane(markdownEditor));
        }else{markdownEditor=null;showBrowseContent();}
    }
    private void showBrowseContent(){
        if(browseContent instanceof SafeMarkdownPreview.BrowseLayout layout){setTop(null);layout.setHeader(header);}
        else setTop(header);
        setCenter(browseContent);
    }
    public boolean hasUnsavedChanges(){return markdownEditor!=null && markdownEditor.dirty();}
    public boolean saveUnsavedChanges(){if(hasUnsavedChanges())markdownEditor.saveChanges();return !host.dirty().getAsBoolean();}
    public Region visibleOutline(){return mode==FileMode.BROWSE && browseContent instanceof SafeMarkdownPreview.BrowseLayout layout?layout.outline():null;}
    public void refreshSourceStatus(){ }
    public boolean shouldRefresh(){return mode==FileMode.BROWSE && browseContent instanceof SafeMarkdownPreview.BrowseLayout;}
    private void setTop(Node node){host.top().accept(node);}
    private void setCenter(Node node){host.center().accept(node);}
    private Node getCenter(){return host.currentCenter().get();}
    private javafx.scene.Scene getScene(){return host.scene().get();}
    private void open(WorkspaceId workspace,String path){host.open().accept(workspace,path);}
    public FilePresentation currentFile(){return current;}
    public FileMode mode(){return mode;}

    private boolean markdownSource() {
        return current != null && current.file().sourceText() != null
                && current.file().entry().relativePath().toLowerCase(java.util.Locale.ROOT).endsWith(".md");
    }

    void createSourceReference(MarkdownSourceRange block) {
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

    void copySourceReference(List<NamedMarkdownAnchor> anchors) {
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

    public void copyAssetLink(WorkspaceId targetWorkspace, WorkspaceFileEntry entry) {
        try {
            String path = entry.relativePath();
            boolean active = current != null && workspace != null && workspace.equals(targetWorkspace)
                    && current.file().entry().relativePath().equals(path);
            if (active && host.dirty().getAsBoolean() && entry.assetId() == null)
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

    void copyNavigationLink(MarkdownOutline.Entry selected) {
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
                        && fresh.kind() != WorkspaceFileKind.REGISTERED_MARKDOWN)
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

    private void showNavigationError(RuntimeException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        alert.setHeaderText("无法复制链接");
        UiTheme.apply(alert);
        alert.showAndWait();
    }

    private void showReferenceError(RuntimeException error) {
        Alert alert = new Alert(Alert.AlertType.ERROR, error.getMessage(), ButtonType.OK);
        alert.setHeaderText("无法创建或复制来源引用");
        UiTheme.apply(alert);
        alert.showAndWait();
    }

    private void saveMarkdown(String edited) {
        String path = current.file().entry().relativePath();
        String assetId = current.kind() == io.quizforge.core.workspace.model.WorkspaceFileKind.REGISTERED_MARKDOWN
                && !current.draft() ? current.file().entry().assetId() : null;
        markdownEdits.save(workspace, path, current.file().sourceText(), edited, assetId);
        refreshTree.run();
        open(workspace, path);
    }

    public void refreshBrowseFromDisk() {
        if (mode != FileMode.BROWSE || current == null || !markdownSource()) return;
        String path = current.file().entry().relativePath();
        try {
            var fresh = loader.load(workspace, path);
            if (!java.util.Objects.equals(fresh.file().sourceText(), current.file().sourceText()))
                open(workspace, path);
        } catch (RuntimeException error) { open(workspace, path); }
    }

    public boolean jumpTo(MarkdownOutline.Kind kind, String label, int occurrence) {
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

    private record ReadyLink(FilePresentation file,boolean registeredNow){ }
    public void refreshForDevelopment() {
        if (mode != FileMode.BROWSE || current == null
                || !(browseContent instanceof SafeMarkdownPreview.BrowseLayout previous)) return;
        previous.setHeader(null);
        browseContent = router.view(current, FileMode.BROWSE);
        if (browseContent instanceof SafeMarkdownPreview.BrowseLayout next)
            next.setDividerPositions(previous.getDividerPositions());
        showBrowseContent();
    }
}
