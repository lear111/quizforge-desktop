package io.quizforge.desktop.ui.file;

import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.document.registered.MarkdownSourceRange;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.question.service.QuestionBankFileEditService;
import io.quizforge.core.question.source.QuestionSourceLinkService;
import io.quizforge.core.workspace.model.WorkspaceFileEntry;
import io.quizforge.core.workspace.model.WorkspaceFileKind;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.dev.DevelopmentRefreshable;
import io.quizforge.desktop.ui.markdown.MarkdownOutline;
import io.quizforge.desktop.ui.markdown.SafeMarkdownPreview;
import io.quizforge.desktop.ui.question.source.QuestionSourceListView;
import io.quizforge.desktop.ui.question.source.QuestionSourceNavigationAdapter;
import io.quizforge.desktop.ui.shared.TextClipboard;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;

/** Stable tab-owned file entry point; page-specific state belongs to its controller. */
public final class FilePane extends BorderPane implements DevelopmentRefreshable {
    private final FilePresentationLoader loader;
    private final QuestionBankFileEditService bankEdits;
    private final MarkdownFileEditService markdownEdits;
    private final MarkdownDocumentRegistration registration;
    private final Runnable refreshTree;
    private final TextClipboard clipboard;
    private final QuestionSourceLinkService sourceLinks;
    private final QuestionSourceNavigationAdapter sourceNavigation;
    private final FileViewerRouter router;
    private final io.quizforge.core.port.PracticeRuntimeProvider practice;
    private final FilePageHost host;
    private FileView pageView;
    private FilePresentation fallback;
    private WorkspaceId workspace;
    private Runnable onEditStart=()->{};
    public FilePane(FilePresentationLoader loader,
            QuestionBankFileEditService bankEdits, MarkdownFileEditService markdownEdits,
            MarkdownDocumentRegistration registration, Runnable refreshTree, TextClipboard clipboard,
            QuestionSourceLinkService sourceLinks, Consumer<String> openLink,
            QuestionSourceNavigationAdapter sourceNavigation, io.quizforge.core.port.PracticeRuntimeProvider practice) {
        this.loader = loader;
        this.bankEdits = bankEdits;
        this.markdownEdits = markdownEdits;
        this.registration = registration;
        this.refreshTree = refreshTree;
        this.clipboard = clipboard;
        this.sourceLinks = sourceLinks;
        this.sourceNavigation = sourceNavigation;
        this.practice = practice;
        this.router = new FileViewerRouter(new SafeMarkdownPreview.SourceActions() {
            @Override public void create(MarkdownSourceRange block) { if(pageView instanceof MarkdownFileView markdown)markdown.createSourceReference(block); }
            @Override public void copy(List<NamedMarkdownAnchor> anchors) { if(pageView instanceof MarkdownFileView markdown)markdown.copySourceReference(anchors); }
        }, entry->{if(pageView instanceof MarkdownFileView markdown)markdown.copyNavigationLink(entry);}, openLink,
                refs -> new QuestionSourceListView(refs, workspace, sourceNavigation, null),
                (file, bank) -> practice.open(workspace, bank,loader.resources(workspace,file.file().entry().relativePath())),
                file -> loader.resources(workspace,file.file().entry().relativePath()));
        host=new FilePageHost(this::setTop,this::setCenter,this::getCenter,this::getScene,this::open,this::hasUnsavedChanges);
        setId("file-pane");
        clear();
    }
    public void open(WorkspaceId workspace,String path){
        if(!prepareClose())return;
        Node previous=pageView!=null && java.util.Objects.equals(this.workspace,workspace)
                && pageView.currentFile().file().entry().relativePath().equals(path)?getCenter():null;
        clear();this.workspace=workspace;
        try{
            var current=loader.load(workspace,path);
            if(current.kind()==WorkspaceFileKind.MARKDOWN || current.kind()==WorkspaceFileKind.REGISTERED_MARKDOWN){
                var markdown=new MarkdownFileView(host,loader,router,markdownEdits,registration,refreshTree,clipboard,
                        workspace,current,()->onEditStart.run());pageView=markdown;markdown.show();
            }else if(current.kind()==WorkspaceFileKind.QUESTION_BANK){
                var bank=new QuestionBankFileView(host,loader,router,bankEdits,sourceLinks,sourceNavigation,practice,
                        workspace,current,()->onEditStart.run());pageView=bank;bank.show(previous);
            }else{fallback=current;setCenter(router.view(current,FileMode.BROWSE));}
        }catch(RuntimeException error){
            pageView=null;fallback=null;setTop(null);
            setCenter(UiTheme.quietState("无法打开文件",error.getMessage()==null?"文件可能已移动或无法读取，请刷新工作区。":error.getMessage()));
        }
    }
    public void clear(){if(pageView!=null)pageView.dispose();pageView=null;fallback=null;workspace=null;setTop(null);setCenter(router.welcome());}
    public boolean prepareClose(){return pageView==null || pageView.prepareClose();}
    public FilePresentation currentFile(){return pageView==null?fallback:pageView.currentFile();}
    public FileMode mode(){return pageView==null?FileMode.BROWSE:pageView.mode();}
    public boolean hasUnsavedChanges(){return pageView!=null && pageView.hasUnsavedChanges();}
    public boolean saveUnsavedChanges(){return pageView==null || pageView.saveUnsavedChanges();}
    public Region visibleOutline(){return pageView==null?null:pageView.visibleOutline();}
    public void refreshBrowseFromDisk(){if(pageView!=null)pageView.refreshBrowseFromDisk();}
    public void refreshSourceStatus(){if(pageView!=null)pageView.refreshSourceStatus();}
    public boolean jumpTo(MarkdownOutline.Kind kind,String label,int occurrence){return pageView!=null && pageView.jumpTo(kind,label,occurrence);}
    public void onEditStart(Runnable action){onEditStart=action;}
    public void copyAssetLink(WorkspaceId targetWorkspace,WorkspaceFileEntry entry){
        var current=currentFile();
        if(current!=null && pageView instanceof MarkdownFileView markdown)markdown.copyAssetLink(targetWorkspace,entry);
        else new MarkdownFileView(host,loader,router,markdownEdits,registration,refreshTree,clipboard,
                workspace,current,()->{}).copyAssetLink(targetWorkspace,entry);
    }
    @Override public boolean shouldRefreshForDevelopment(){return pageView!=null && pageView.shouldRefresh();}
    @Override public void refreshForDevelopment(){if(pageView!=null)pageView.refreshForDevelopment();}
}
