package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.HistoryDraftReplay;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.dev.DevelopmentRefreshable;
import io.quizforge.desktop.ui.question.source.HistorySourceListView;
import io.quizforge.desktop.ui.question.source.HistorySourceNavigationAdapter;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Archived answers and annotations share a single read-only learning surface. */
public final class PracticeHistoryDetailView extends BorderPane implements DevelopmentRefreshable {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final PracticeHistoryDetail detail;
    private final HistoryQuestionOutlineView outline;
    private final VBox headings=new VBox();
    private final HBox heading;
    private final Button returnButton;
    private final String titleText;
    private final HistorySurfaceHost surface;
    private final HistoryDraftAdapter drafts;
    private final WorkspaceId workspace;
    private final HistorySourceNavigationAdapter sources;
    private HistorySourceListView sourceList;
    private Node fileHeader;
    private int questionIndex,attemptIndex;

    public PracticeHistoryDetailView(PracticeHistoryDetail detail,Runnable back,WorkspaceId workspace,HistorySourceNavigationAdapter sources){
        this(detail,back,workspace,sources,null,null,io.quizforge.core.port.QuestionResourceInput.NONE,null);
    }
    public PracticeHistoryDetailView(PracticeHistoryDetail detail,Runnable back,WorkspaceId workspace,HistorySourceNavigationAdapter sources,
            io.quizforge.core.question.model.QuestionBank currentBank,String currentContentId,io.quizforge.core.port.QuestionResourceInput currentResources){
        this(detail,back,workspace,sources,currentBank,currentContentId,currentResources,null);
    }
    public PracticeHistoryDetailView(PracticeHistoryDetail detail,Runnable back,WorkspaceId workspace,HistorySourceNavigationAdapter sources,
            io.quizforge.core.question.model.QuestionBank currentBank,String currentContentId,io.quizforge.core.port.QuestionResourceInput currentResources,HistoryDraftAdapter drafts){
        this.detail=detail;this.workspace=workspace;this.sources=sources;this.drafts=drafts;
        setId("practice-history-detail");getStyleClass().add("history-detail");
        titleText=detail.bankTitle()+" · "+DATE.format(LocalDateTime.ofInstant(detail.archivedAt(),ZoneId.systemDefault()));
        Label title=UiTheme.label(titleText,"file-title");
        HBox.setHgrow(title,Priority.ALWAYS);title.setMinWidth(0);title.setMaxWidth(Double.MAX_VALUE);title.setWrapText(false);
        returnButton=UiTheme.button("返回历史记录","arrow-left","",back);returnButton.setId("history-detail-back");returnButton.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        heading=new HBox(12,returnButton,title);heading.getStyleClass().add("file-header");
        outline=new HistoryQuestionOutlineView(detail,(index,item)->showQuestion(index,item));
        surface=new HistorySurfaceHost(()->showQuestion(questionIndex-1),()->showQuestion(questionIndex+1),()->questionIndex>0,()->questionIndex<detail.questions().size());
        surface.configurePageActions(this::pageState,this::pageCommand);
        heading.getChildren().add(surface.toggleButton());
        headings.getChildren().add(heading);
        BorderPane readerColumn=new BorderPane(surface);readerColumn.setMinWidth(320);readerColumn.setTop(headings);
        SplitPane layout=new SplitPane(readerColumn,outline);layout.getStyleClass().add("history-browse-layout");layout.setMinWidth(0);SplitPane.setResizableWithParent(outline,false);
        final double[] divider={.75};
        surface.onUiChange(preferences->{
            boolean visible=preferences.getOrDefault("outline",true);
            if(visible && !layout.getItems().contains(outline)){layout.getItems().add(outline);layout.setDividerPositions(divider[0]);}
            else if(!visible && layout.getItems().contains(outline)){if(layout.getDividerPositions().length>0)divider[0]=layout.getDividerPositions()[0];layout.getItems().remove(outline);}
        });
        layout.widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>(){
            public void changed(javafx.beans.value.ObservableValue<? extends Number> value,Number before,Number width){
                if(width.doubleValue()<=500)return;layout.setDividerPositions((width.doubleValue()-260)/width.doubleValue());layout.widthProperty().removeListener(this);
            }
        });
        setCenter(layout);if(detail.questions().isEmpty())render();else showQuestion(0);
    }
    private void showQuestion(int index){if(index<0 || index>detail.questions().size())return;questionIndex=index;if(index==detail.questions().size()){attemptIndex=-1;render();return;}var row=detail.questions().get(index);attemptIndex=row.finalState()==io.quizforge.core.practice.PracticeSessionQuestion.State.SUBMITTED?row.attempts().size()-1:-1;render();}
    private void showQuestion(int index,int itemNumber){
        if(index<0 || index>=detail.questions().size())return;if(index!=questionIndex)showQuestion(index);
        var row=detail.questions().get(index);
        String target=io.quizforge.desktop.learning.SharedPracticeViewModel.targetId(row.questionType(),row.contentSnapshot(),itemNumber);if(target!=null)surface.focusTarget(target);
    }
    private void showAttempt(int index){if(index<0 || index>=detail.questions().get(questionIndex).attempts().size())return;attemptIndex=index;render();}
    private void render(){
        outline.refresh(detail,questionIndex,attemptIndex);
        if(questionIndex==detail.questions().size()){
            surface.attemptNavigation(false,false,()->{},()->{});
            sourceList=null;
            var card=new io.quizforge.desktop.ui.question.shared.PracticeSummaryCard(detail.summary(),"本次练习","history-summary","history-summary");
            var stage=new javafx.scene.layout.StackPane(card);stage.setPadding(new javafx.geometry.Insets(28,20,28,20));
            surface.showSummary(stage,detail.summary());return;
        }
        if(detail.questions().isEmpty()){surface.select(null);return;}
        var row=detail.questions().get(questionIndex);
        boolean hasPending=row.finalState()!=io.quizforge.core.practice.PracticeSessionQuestion.State.SUBMITTED;
        surface.attemptNavigation(attemptIndex<0?!row.attempts().isEmpty():attemptIndex>0,
            attemptIndex>=0&&(attemptIndex<row.attempts().size()-1||hasPending),
            ()->showAttempt(attemptIndex<0?row.attempts().size()-1:attemptIndex-1),
            ()->{if(attemptIndex==row.attempts().size()-1&&hasPending){attemptIndex=-1;render();}else showAttempt(attemptIndex+1);});
        sourceList=row.sourceRefs()!=null && row.sourceRefs().value() instanceof java.util.List<?> refs && !refs.isEmpty()?new HistorySourceListView(workspace,row.sourceRefs(),sources):null;
        surface.setSourceContent(sourceList);
        try{surface.select(drafts==null?new HistoryDraftAdapter.Replay(HistoryDraftReplay.unavailable("此记录缺少新版题卡快照"),null):drafts.load(row,attemptIndex<0?null:row.attempts().get(attemptIndex)));}
        catch(RuntimeException failure){surface.select(new HistoryDraftAdapter.Replay(HistoryDraftReplay.unavailable("历史题卡暂时无法读取："+failure.getMessage()),null));}
    }
    public HistoryQuestionOutlineView outline(){return outline;}
    private java.util.Map<String,Object> pageState(){
        var questions=java.util.stream.IntStream.range(0,detail.questions().size()).mapToObj(i->{var q=detail.questions().get(i);return java.util.Map.<String,Object>of("index",i,"id",q.questionId(),"type",q.questionType(),"state",q.finalState().name());}).toList();
        var attempts=questionIndex<detail.questions().size()?detail.questions().get(questionIndex).attempts():java.util.List.<PracticeHistoryDetail.Attempt>of();
        boolean pending=questionIndex<detail.questions().size()&&detail.questions().get(questionIndex).finalState()!=io.quizforge.core.practice.PracticeSessionQuestion.State.SUBMITTED;
        return java.util.Map.of("index",questionIndex,"count",questions.size(),"questions",questions,"sources",sourceList==null?java.util.List.of():sourceList.pageSources(),"learningMode",surface.mode()==HistorySurfaceMode.DRAFT?"DRAFT":"PRACTICE",
                "targets",io.quizforge.desktop.ui.question.shared.QuestionTargetNumbers.current(detail.questions().stream().map(io.quizforge.desktop.ui.question.shared.QuestionTargetNumbers::archived).toList(),questionIndex),
                "attemptNavigation",java.util.Map.of("canPrevious",attemptIndex>0||attemptIndex<0&&!attempts.isEmpty(),"canNext",attemptIndex>=0&&(attemptIndex<attempts.size()-1||pending),"isCurrent",false));
    }
    private java.util.concurrent.CompletionStage<Void> pageCommand(String action,Object argument){
        if(surface.busy())throw new IllegalStateException("历史题卡暂不可操作");
        switch(action){
            case "navigate" -> showQuestion(io.quizforge.desktop.ui.question.shared.QuestionPageActions.index(argument,detail.questions().size()+1));
            case "attempt.previous", "attempt.next" -> {
                if(questionIndex>=detail.questions().size())throw new IllegalArgumentException("总结页没有尝试记录");
                var row=detail.questions().get(questionIndex);
                boolean hasPending=row.finalState()!=io.quizforge.core.practice.PracticeSessionQuestion.State.SUBMITTED;
                if("attempt.previous".equals(action))showAttempt(attemptIndex<0?row.attempts().size()-1:attemptIndex-1);
                else if(attemptIndex==row.attempts().size()-1&&hasPending){attemptIndex=-1;render();}
                else showAttempt(attemptIndex+1);
            }
            case "attempt.current" -> throw new IllegalArgumentException("历史记录没有当前作答");
            case "learning.mode" -> {
                if(!"DRAFT".equals(argument)&&!"PRACTICE".equals(argument))throw new IllegalArgumentException("模式无效");
                surface.setMode("DRAFT".equals(argument)?HistorySurfaceMode.DRAFT:HistorySurfaceMode.RESULT);
            }
            case "source.open" -> {
                if(sourceList==null)throw new IllegalArgumentException("当前题目没有来源");
                sourceList.openSource(io.quizforge.desktop.ui.question.shared.QuestionPageActions.index(argument,sourceList.pageSources().size()));
            }
            default -> throw new IllegalArgumentException("不支持的页面操作");
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
    public HistorySurfaceHost surface(){return surface;}
    public void refreshSources(){if(sourceList!=null)sourceList.refresh();surface.refreshChrome();}
    public void destroy(){surface.destroy();setHeader(null);}
    public String titleText(){return titleText;}
    public Button returnButton(){heading.getChildren().remove(returnButton);return returnButton;}
    public Button headerDraftButton(){heading.getChildren().remove(surface.toggleButton());return surface.toggleButton();}
    public void setHeader(Node header){if(fileHeader!=null)headings.getChildren().remove(fileHeader);fileHeader=header;heading.setVisible(header==null);heading.setManaged(header==null);if(header!=null)headings.getChildren().addFirst(header);}
    @Override public void refreshForDevelopment(){render();}
}
