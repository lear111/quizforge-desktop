package io.quizforge.desktop.ui.file;

import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.service.QuestionBankFileEditService;
import io.quizforge.core.question.source.QuestionSourceLinkService;
import io.quizforge.core.workspace.model.WorkspaceFileKind;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.ui.markdown.MarkdownOutline;
import io.quizforge.desktop.ui.question.editor.QuestionBankEditorView;
import io.quizforge.desktop.ui.question.history.PracticeHistoryDetailView;
import io.quizforge.desktop.ui.question.history.PracticeHistoryView;
import io.quizforge.desktop.ui.question.practice.MixedQuestionPracticeView;
import io.quizforge.desktop.ui.question.practice.QuestionBankPracticeView;
import io.quizforge.desktop.ui.question.shared.QuestionPracticeLayout;
import io.quizforge.desktop.ui.question.source.HistorySourceNavigationAdapter;
import io.quizforge.desktop.ui.question.source.QuestionSourceNavigationAdapter;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Question-bank page owns editor, active practice and history navigation. */
final class QuestionBankFileView implements FileView {
    private final FilePageHost host;
    private final FilePresentationLoader loader;
    private final FileViewerRouter router;
    private final QuestionBankFileEditService bankEdits;
    private final QuestionSourceLinkService sourceLinks;
    private final QuestionSourceNavigationAdapter sourceNavigation;
    private final io.quizforge.core.port.PracticeRuntimeProvider practice;
    private final Runnable onEditStart;
    private final WorkspaceId workspace;
    private FilePresentation current;
    private FileMode mode=FileMode.BROWSE;
    private enum Page{BROWSE,HISTORY_LIST,HISTORY_DETAIL}
    private Page page=Page.BROWSE;
    private PracticeHistoryView historyList;
    private PracticeHistoryDetailView historyDetail;
    private FileHeader header;
    private Node browseContent;
    private QuestionBankEditorView bankEditor;
    QuestionBankFileView(FilePageHost host,FilePresentationLoader loader,FileViewerRouter router,QuestionBankFileEditService edits,
            QuestionSourceLinkService sourceLinks,QuestionSourceNavigationAdapter sourceNavigation,
            io.quizforge.core.port.PracticeRuntimeProvider practice,WorkspaceId workspace,FilePresentation current,Runnable onEditStart) {
        this.host=host;this.loader=loader;this.router=router;bankEdits=edits;this.sourceLinks=sourceLinks;
        this.sourceNavigation=sourceNavigation;this.practice=practice;this.workspace=workspace;this.current=current;this.onEditStart=onEditStart;
    }
    void show(Node previous){
        header=new FileHeader(current,this::toggleMode,this::openHistory);
        browseContent=router.view(current,FileMode.BROWSE);
        if(previous instanceof QuestionPracticeLayout old && browseContent instanceof QuestionPracticeLayout next)
            next.keepDividerPosition(old);
        showBrowseContent();
    }
    public boolean hasUnsavedChanges(){return bankEditor!=null && bankEditor.dirty();}
    public boolean saveUnsavedChanges(){if(hasUnsavedChanges())bankEditor.saveChanges();return !host.dirty().getAsBoolean();}
    public void refreshBrowseFromDisk(){ }
    public boolean jumpTo(MarkdownOutline.Kind kind,String label,int occurrence){return false;}
    public boolean shouldRefresh(){return false;}
    public void refreshForDevelopment(){ }
    private void setTop(Node node){host.top().accept(node);}
    private void setCenter(Node node){host.center().accept(node);}
    private Node getCenter(){return host.currentCenter().get();}
    private javafx.scene.Scene getScene(){return host.scene().get();}
    private void open(WorkspaceId workspace,String path){host.open().accept(workspace,path);}
    public FilePresentation currentFile(){return current;}
    public FileMode mode(){return mode;}

    private void toggleMode() {
        if (page == Page.HISTORY_LIST) return;
        if (mode == FileMode.EDIT && (bankEditor != null && bankEditor.dirty())) {
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
        if (mode == FileMode.EDIT && current.file().questionBank() != null) {
            bankEditor = new QuestionBankEditorView(current.file().questionBank(), workspace,
                    sourceLinks, sourceNavigation, this::saveBank,loader.resources(workspace,current.file().entry().relativePath()));
            StackPane centered = new StackPane(bankEditor);
            centered.setAlignment(Pos.TOP_CENTER);
            ScrollPane editorScroll = UiTheme.scroll(centered);
            if (browseContent instanceof QuestionPracticeLayout practiceLayout) {
                QuestionBankEditorView editor = bankEditor;
                editor.jumpTo(practiceLayout.outline().currentIndex());
                editor.onQuestionChange((bank, selected) ->
                        practiceLayout.outline().showEditor(bank, selected, target -> {
                            editor.jumpTo(target);
                            editorScroll.setVvalue(0);
                        }));
                practiceLayout.setContent(editorScroll);
            } else if(browseContent instanceof MixedQuestionPracticeView authoring) {
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
                    if (renewed instanceof QuestionPracticeLayout next
                            && browseContent instanceof QuestionPracticeLayout previous) {
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
        if (browseContent instanceof QuestionPracticeLayout practiceLayout) {
            setTop(null);
            practiceLayout.setHeader(header);
        } else if(browseContent instanceof MixedQuestionPracticeView authoring) {
            setTop(null);authoring.setHeader(header);
        } else {
            setTop(header);
        }
        setCenter(browseContent);
    }

    private void saveBank(io.quizforge.core.question.model.QuestionBank edited) {
        String path = current.file().entry().relativePath();
        bankEdits.save(workspace, path, current.file().entry().contentId(), current.file().bankRevision(), edited,
                bankEditor==null?io.quizforge.core.port.QuestionResourceInput.NONE:bankEditor.resources());
        open(workspace, path);
    }

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
            if (browseContent instanceof QuestionPracticeLayout practiceLayout)
                practiceLayout.setHeader(null);
            else if (browseContent instanceof MixedQuestionPracticeView authoring)
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
                    new HistorySourceNavigationAdapter(sourceNavigation),current.file().questionBank(),current.file().bankRevision(),
                    loader.resources(workspace,current.file().entry().relativePath()));
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

    public void refreshSourceStatus() {
        if (page == Page.HISTORY_DETAIL && historyDetail != null) historyDetail.refreshSources();
        else if (page == Page.HISTORY_LIST) return;
        else if (mode == FileMode.EDIT && bankEditor != null) bankEditor.refreshSources();
        else if (browseContent != null
                && browseContent.lookup("#question-practice") instanceof QuestionBankPracticeView practice)
            practice.refreshSources();
    }

    public Region visibleOutline() {
        if (getCenter() instanceof QuestionPracticeLayout practiceLayout) return practiceLayout.outline();
        if (getCenter() instanceof MixedQuestionPracticeView authoring) return authoring.outline();
        if (mode != FileMode.BROWSE) return null;
        if (getCenter() instanceof PracticeHistoryDetailView detail) return detail.outline();
        return null;
    }

}
