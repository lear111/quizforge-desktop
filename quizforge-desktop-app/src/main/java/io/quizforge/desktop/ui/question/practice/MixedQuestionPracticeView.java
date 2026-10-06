package io.quizforge.desktop.ui.question.practice;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.QuestionBankPracticeSession;
import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.content.QuestionText;
import io.quizforge.desktop.dev.DevelopmentRefreshable;
import io.quizforge.desktop.ui.content.document.canvas.ContentEditingSupport;
import io.quizforge.desktop.ui.question.extension.ExtensionQuestionPreview;
import io.quizforge.desktop.ui.question.shared.QuestionCardLayout;
import io.quizforge.desktop.ui.question.shared.QuestionOutlineView;
import io.quizforge.desktop.ui.question.source.QuestionSourceListView;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import java.util.function.*;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.*;

/** All question types share one extension-based practice/preview surface and outline. */
public final class MixedQuestionPracticeView extends SplitPane implements DevelopmentRefreshable {
    private final BorderPane readerColumn=new BorderPane();
    private final QuestionOutlineView outline;
    private final QuestionResourceInput resources;
    private final Function<List<SourceRef>,QuestionSourceListView> sources;
    private final PersistentPracticeRuntime practice;
    private final PracticeSurfaceHost surface;
    private QuestionSourceListView currentSources;
    private QuestionBank bank;
    private int index;
    private IntConsumer editorJump;
    private double outlineDivider=.75;

    public MixedQuestionPracticeView(QuestionBank bank,QuestionResourceInput resources){this(bank,resources,null,refs->null);}
    public MixedQuestionPracticeView(QuestionBank bank,QuestionResourceInput resources,
            Supplier<PersistentPracticeRuntime> loader,Function<List<SourceRef>,QuestionSourceListView> sources){
        this.bank=bank;this.resources=resources;this.sources=sources;practice=loader==null?null:loader.get();
        setId("qbank-authoring-layout");getStyleClass().add("practice-browse-layout");
        outline=new QuestionOutlineView(practice==null?new QuestionBankPracticeSession(bank):practice.session(),this::show,"authoring-question-");
        outline.setId("authoring-outline");outline.setItemJump(this::showItem);readerColumn.setMinWidth(320);
        getItems().addAll(readerColumn,outline);SplitPane.setResizableWithParent(outline,false);
        widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>(){
            public void changed(javafx.beans.value.ObservableValue<? extends Number> o,Number before,Number width){
                if(width.doubleValue()>500){setDividerPositions((width.doubleValue()-260)/width.doubleValue());widthProperty().removeListener(this);}
            }
        });
        if(practice!=null){synchronizeIndex();surface=new PracticeSurfaceHost(practice,this::refreshFromRuntime,()->show(index-1),this::next);}
        else surface=null;
        if(surface!=null)surface.onUiChange(preferences->{if(editorJump==null)setOutlineVisible(preferences.getOrDefault("outline",true));});
        render();
    }
    public PracticeSurfaceHost surface(){return surface;}
    public void refreshSources(){if(currentSources!=null)currentSources.refresh();if(surface!=null)surface.refreshChrome();}
    public static boolean requiresMixedView(QuestionBank bank){return bank.questions().stream().anyMatch(q->!QuestionTypes.isChoice(q.type()));}
    public static boolean hasPracticeChoices(QuestionBank bank){return bank.questions().stream().anyMatch(QuestionText::supports);}
    public static boolean supportsEditing(QuestionBank bank){
        return bank.questions().stream().allMatch(q->q.stimulusRefs().isEmpty()&&editableContent(q.prompt())&&(q.analysis()==null||editableContent(q.analysis())));
    }
    private static boolean editableContent(QuestionContent content){return content instanceof DocumentContent||ContentEditingSupport.supports(content);}
    public void setHeader(Node header){readerColumn.setTop(header);}
    public void showEditor(Node editor,IntConsumer jump){editorJump=jump;readerColumn.setCenter(editor);refreshOutline();}
    public void showPractice(){editorJump=null;if(practice!=null){bank=practice.session().bank();synchronizeIndex();}render();}
    public void updateEditor(QuestionBank bank,int selected){this.bank=bank;index=selected;refreshOutline();}
    public int currentIndex(){return index;}
    public void setOutlineVisible(boolean visible){
        if(visible&&!getItems().contains(outline)){getItems().add(outline);setDividerPositions(outlineDivider);}
        else if(!visible&&getItems().contains(outline)){if(getDividerPositions().length>0)outlineDivider=getDividerPositions()[0];getItems().remove(outline);}
    }
    public Region outline(){return outline;}
    @Override public void refreshForDevelopment(){render();}
    @Override public boolean shouldRefreshForDevelopment(){return editorJump==null;}
    private void synchronizeIndex(){
        if(practice==null||practice.session().finished())return;
        String current=practice.session().current().id();
        for(int i=0;i<bank.questions().size();i++)if(bank.questions().get(i).id().equals(current)){index=i;break;}
    }
    private void refreshFromRuntime(){synchronizeIndex();render();}
    private void render(){
        if(editorJump!=null){refreshOutline();return;}
        if(practice!=null&&practice.session().finished()){
            var summary=new QuestionBankPracticeView(practice,sources,null,this::refreshFromRuntime);
            summary.setSurfaceHost(surface);summary.refreshPracticeState();
            var content=QuestionCardLayout.scroll(summary);content.getStyleClass().add("practice-summary-surface");
            surface.setSummaryContent(content);readerColumn.setCenter(surface);refreshOutline();return;
        }
        var question=bank.questions().get(index);
        if(surface!=null&&surface.supportsCurrent()){
            currentSources=practice.session().state()==QuestionBankPracticeSession.State.SUBMITTED&&!question.sourceRefs().isEmpty()?sources.apply(question.sourceRefs()):null;
            surface.setSourceContent(currentSources);surface.showQuestion();readerColumn.setCenter(surface);refreshOutline();return;
        }
        setOutlineVisible(true);
        Node preview=QuestionTypes.find(question.type()).isEmpty()
                ? UiTheme.quietState("缺少题型扩展", "需要安装 "+question.type()+" 对应扩展。原始题目与资源已保留。")
                : new ExtensionQuestionPreview(bank,index,resources);
        var previous=QuestionCardLayout.navigation("arrow-left","上一题",()->show(index-1));previous.setId("authoring-previous-question");previous.setDisable(index==0);
        var next=QuestionCardLayout.navigation("arrow","下一题",this::next);next.setId("authoring-next-question");next.setDisable(index==bank.questions().size()-1);
        var content=new StackPane(preview,previous,next);StackPane.setAlignment(previous,javafx.geometry.Pos.CENTER_LEFT);StackPane.setAlignment(next,javafx.geometry.Pos.CENTER_RIGHT);
        readerColumn.setCenter(content);refreshOutline();
    }
    private void next(){
        Runnable action=()->{if(practice!=null){practice.next();synchronizeIndex();render();}else if(index<bank.questions().size()-1)showNow(index+1);};
        if(surface!=null)surface.navigate(action);else action.run();
    }
    private void show(int target){
        if(target<0||target>=bank.questions().size())return;
        if(editorJump==null&&surface!=null)surface.navigate(()->showNow(target));else showNow(target);
    }
    private void showNow(int target){
        if(target<0||target>=bank.questions().size())return;index=target;
        if(editorJump!=null)editorJump.accept(index);
        else {if(practice!=null)practice.goTo(target);render();}
        refreshOutline();
    }
    private void showItem(int target,int itemNumber){
        if(target<0||target>=bank.questions().size())return;
        if(editorJump!=null||surface==null){show(target);return;}
        var q=bank.questions().get(target);
        String targetId=io.quizforge.desktop.learning.SharedPracticeViewModel.targetId(q.type(),practice.questionState(q.id()).sessionQuestion().snapshot().correctAnswer(),itemNumber);
        if(target==index&&targetId!=null){surface.focusTarget(targetId);return;}
        surface.navigate(()->showNow(target),targetId);
    }
    private void refreshOutline(){outline.showEditor(bank,practice!=null&&practice.session().finished()&&editorJump==null?-1:index,this::show);}
}
