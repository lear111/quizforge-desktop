package io.quizforge.desktop.ui.question.practice;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.QuestionBankPracticeSession;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.question.type.objective.choice.QuestionText;
import io.quizforge.desktop.dev.DevelopmentRefreshable;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.document.canvas.ContentEditingSupport;
import io.quizforge.desktop.ui.question.shared.QuestionCardLayout;
import io.quizforge.desktop.ui.question.shared.QuestionOutlineView;
import io.quizforge.desktop.ui.question.shared.QuestionTypeCatalog;
import io.quizforge.desktop.ui.question.source.QuestionSourceListView;
import io.quizforge.desktop.ui.question.subjective.essay.EssayAnswerPane;
import io.quizforge.desktop.ui.question.subjective.essay.EssayQuestionCardView;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Mixed question types share a durable ACTIVE round and their own answer controls. */
public final class MixedQuestionPracticeView extends SplitPane implements DevelopmentRefreshable {
    private final BorderPane readerColumn=new BorderPane();
    private final QuestionOutlineView outline;
    private final QuestionResourceInput resources;
    private final Supplier<PersistentPracticeRuntime> practiceLoader;
    private final Function<List<SourceRef>, QuestionSourceListView> sources;
    private PersistentPracticeRuntime practice;
    private PracticeSurfaceHost surface;
    public PracticeSurfaceHost surface(){return surface;}
    private void setPracticeContent(Node content){if(surface==null)readerColumn.setCenter(content);else{surface.setNormalContent(content);readerColumn.setCenter(surface);}}
    private boolean showingSummary;
    private QuestionBank bank;
    private int index;
    private IntConsumer editorJump;
    private io.quizforge.desktop.ui.question.objective.reading.ReadingQuestionCardView readingCard;
    private io.quizforge.desktop.ui.question.objective.matching.MatchingQuestionCardView matchingCard;
    private io.quizforge.desktop.ui.question.subjective.translation.TranslationQuestionCardView translationCard;
    public MixedQuestionPracticeView(QuestionBank bank,QuestionResourceInput resources){
        this(bank, resources, null, refs -> null);
    }
    public MixedQuestionPracticeView(QuestionBank bank,QuestionResourceInput resources,
            Supplier<PersistentPracticeRuntime> practiceLoader, Function<List<SourceRef>,QuestionSourceListView> sources){
        this.practiceLoader=practiceLoader;this.sources=sources;
        this.bank=bank;this.resources=resources;setId("qbank-authoring-layout");getStyleClass().add("practice-browse-layout");
        if(practiceLoader!=null)ensurePractice();
        outline=new QuestionOutlineView(practice==null?new QuestionBankPracticeSession(bank):practice.session(),this::show,"authoring-question-");
        outline.setId("authoring-outline");
        outline.setItemJump(this::showItem);
        readerColumn.setMinWidth(320);
        getItems().addAll(readerColumn,outline);SplitPane.setResizableWithParent(outline,false);
        widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>(){
            public void changed(javafx.beans.value.ObservableValue<? extends Number> o,Number before,Number value){
                if(value.doubleValue()>500){setDividerPositions((value.doubleValue()-260)/value.doubleValue());widthProperty().removeListener(this);}
            }
        });
        if(practiceLoader!=null){
            ensurePractice();
            String current=practice.session().current().id();
            for(int i=0;i<bank.questions().size();i++)if(bank.questions().get(i).id().equals(current)){index=i;break;}
            surface=new PracticeSurfaceHost(practice,this::refreshFromRuntime,()->show(index-1),this::next);
            surface.setDraftAvailable(()->index>=0 && index<bank.questions().size()
                    && bank.questions().get(index).id().equals(practice.session().current().id())
                    && QuestionText.supports(bank.questions().get(index)));
        }
        render();
    }
    public static boolean requiresMixedView(QuestionBank bank){return bank.questions().stream().anyMatch(q->QuestionTypes.isEssay(q.type()) || QuestionTypes.isCloze(q.type()) || QuestionTypes.isReading(q.type()) || QuestionTypes.isMatching(q.type()) || QuestionTypes.isTranslation(q.type()));}
    public static boolean hasPracticeChoices(QuestionBank bank){return bank.questions().stream().anyMatch(QuestionText::supports);}
    public static boolean supportsEditing(QuestionBank bank){
        return bank.questions().stream().allMatch(q->QuestionTypes.isTranslation(q.type())
                ? q.stimulusRefs().isEmpty() && editableContent(q.prompt()) && (q.analysis()==null || editableContent(q.analysis()))
                    && ((io.quizforge.core.question.type.subjective.translation.TranslationAnswerSpec)q.answerSpec()).referenceAnswers().values().stream().allMatch(MixedQuestionPracticeView::editableContent)
                : QuestionTypes.isMatching(q.type())
                ? q.stimulusRefs().isEmpty() && editableContent(q.prompt()) && (q.analysis()==null || editableContent(q.analysis()))
                : QuestionTypes.isReading(q.type())
                ? q.stimulusRefs().isEmpty() && editableContent(q.prompt()) && (q.analysis()==null || editableContent(q.analysis()))
                    && ((io.quizforge.core.question.type.objective.reading.ReadingPayload)q.payload()).items().stream().allMatch(item->editableContent(item.prompt()))
                : QuestionTypes.isCloze(q.type())
                ? q.stimulusRefs().isEmpty() && editableContent(q.prompt()) && (q.analysis()==null || editableContent(q.analysis()))
                : QuestionTypes.isEssay(q.type())
                ? q.stimulusRefs().isEmpty() && editableContent(q.prompt())
                : q.stimulusRefs().isEmpty() && q.prompt() instanceof TextContent
                    && (q.analysis()==null || q.analysis() instanceof TextContent)
                    && q.choicePayload().options().stream().allMatch(o->o.content() instanceof TextContent));
    }
    private static boolean editableContent(QuestionContent content){
        if(content instanceof DocumentContent)return true;
        return ContentEditingSupport.supports(content);
    }
    public void setHeader(Node header){readerColumn.setTop(header);}
    public void showEditor(Node editor,IntConsumer jump){editorJump=jump;readerColumn.setCenter(editor);refreshOutline();}
    public void updateEditor(QuestionBank bank,int selected){this.bank=bank;index=selected;refreshOutline();}
    public int currentIndex(){return index;}
    private void refreshFromRuntime(){
        if(!practice.session().finished()){
            String current=practice.session().current().id();
            for(int i=0;i<bank.questions().size();i++)if(bank.questions().get(i).id().equals(current)){index=i;break;}
        }
        render();
    }
    public Region outline(){return (Region)getItems().get(1);}
    @Override public void refreshForDevelopment() { render(); }
    @Override public boolean shouldRefreshForDevelopment() { return editorJump == null; }
    private void render(){
        readingCard=null;matchingCard=null;translationCard=null;
        if(practice!=null && practice.session().finished()){
            showingSummary=true;
            setPracticeContent(QuestionCardLayout.scroll(new QuestionBankPracticeView(practice,sources,null,this::practiceChanged)));
            refreshOutline();return;
        }
        showingSummary=false;
        var current=bank.questions().get(index);
        if(practice!=null){
            int selected=practiceIndex(current.id());
            if(selected>=0 && (practice.session().finished() || practice.session().index()!=selected))practice.goTo(selected);
        }
        if(practiceLoader!=null && QuestionText.supports(current)){
            ensurePractice();
            int selected=practiceIndex(current.id());
            if(practice.session().finished() || practice.session().index()!=selected)practice.goTo(selected);
            var navigation=new QuestionBankPracticeView.Navigation(index,bank.questions().size(),index==0,
                    index==bank.questions().size()-1,()->show(index-1),this::next);
            setPracticeContent(QuestionCardLayout.scroll(new QuestionBankPracticeView(practice,sources,navigation,this::practiceChanged)));
            refreshOutline();return;
        }
        var q=bank.questions().get(index);
        if(QuestionTypes.isTranslation(q.type())) {
            translationCard=new io.quizforge.desktop.ui.question.subjective.translation.TranslationQuestionCardView(q,index,bank.questions().size(),bank.resources(),resources,"practice-",
                    ()->practice==null?java.util.Map.of():practice.session().translationAnswers(),
                    ()->practice!=null && practice.session().state()==QuestionBankPracticeSession.State.SUBMITTED,
                    practice==null?null:(item,answer)->{practice.assignTranslation(item,answer);refreshOutline();},
                    ()->{practice.submit();refreshOutline();},()->{practice.retry();refreshOutline();},sources.apply(q.sourceRefs()));
            var previous=QuestionCardLayout.navigation("arrow-left","上一题",()->show(index-1));previous.setId("authoring-previous-question");previous.setDisable(index==0);
            boolean last=index==bank.questions().size()-1;
            var next=QuestionCardLayout.navigation("arrow",last && practiceLoader!=null?"查看本次练习":"下一题",this::next);next.setId("authoring-next-question");next.setDisable(last && practiceLoader==null);
            var stage=new VBox(QuestionCardLayout.row(previous,translationCard,next));QuestionCardLayout.configure(stage);setPracticeContent(QuestionCardLayout.scroll(stage));refreshOutline();return;
        }
        if(QuestionTypes.isMatching(q.type())) {
            matchingCard=new io.quizforge.desktop.ui.question.objective.matching.MatchingQuestionCardView(q,index,bank.questions().size(),bank.resources(),resources,"practice-",
                    ()->practice==null?java.util.Map.of():practice.session().matchingAnswers(),
                    ()->practice!=null && practice.session().state()==QuestionBankPracticeSession.State.SUBMITTED,
                    practice==null?null:(blank,option)->{practice.assignMatching(blank,option);refreshOutline();},
                    ()->{practice.submit();refreshOutline();},()->{practice.retry();refreshOutline();},sources.apply(q.sourceRefs()));
            var previous=QuestionCardLayout.navigation("arrow-left","上一题",()->show(index-1));previous.setId("authoring-previous-question");previous.setDisable(index==0);
            boolean last=index==bank.questions().size()-1;
            var next=QuestionCardLayout.navigation("arrow",last && practiceLoader!=null?"查看本次练习":"下一题",this::next);next.setId("authoring-next-question");next.setDisable(last && practiceLoader==null);
            var stage=new VBox(QuestionCardLayout.row(previous,matchingCard,next));QuestionCardLayout.configure(stage);setPracticeContent(QuestionCardLayout.scroll(stage));refreshOutline();return;
        }
        if(QuestionTypes.isReading(q.type())) {
            readingCard=new io.quizforge.desktop.ui.question.objective.reading.ReadingQuestionCardView(q,index,bank.questions().size(),bank.resources(),resources,"practice-",
                    ()->practice==null?java.util.Set.of():practice.session().selected(),
                    ()->practice!=null && practice.session().state()==QuestionBankPracticeSession.State.SUBMITTED,
                    practice==null?null:option->{practice.select(option);refreshOutline();},
                    ()->{practice.submit();refreshOutline();},()->{practice.retry();refreshOutline();},sources.apply(q.sourceRefs()));
            var previous=QuestionCardLayout.navigation("arrow-left","上一题",()->show(index-1));previous.setId("authoring-previous-question");previous.setDisable(index==0);
            boolean last=index==bank.questions().size()-1;
            var next=QuestionCardLayout.navigation("arrow",last && practiceLoader!=null?"查看本次练习":"下一题",this::next);next.setId("authoring-next-question");next.setDisable(last && practiceLoader==null);
            var stage=new VBox(QuestionCardLayout.row(previous,readingCard,next));QuestionCardLayout.configure(stage);setPracticeContent(QuestionCardLayout.scroll(stage));refreshOutline();return;
        }
        if(QuestionTypes.isCloze(q.type())){
            var card=new io.quizforge.desktop.ui.question.objective.cloze.ClozeQuestionCardView(q,index,bank.questions().size(),bank.resources(),resources,"practice-",
                    ()->practice==null?java.util.Set.of():practice.session().selected(),
                    ()->practice!=null && practice.session().state()==QuestionBankPracticeSession.State.SUBMITTED,
                    practice==null?null:option->{practice.select(option);refreshOutline();},
                    ()->{practice.submit();refreshOutline();},()->{practice.retry();refreshOutline();},sources.apply(q.sourceRefs()));
            var previous=QuestionCardLayout.navigation("arrow-left","上一题",()->show(index-1));previous.setId("authoring-previous-question");previous.setDisable(index==0);
            boolean last=index==bank.questions().size()-1;
            var next=QuestionCardLayout.navigation("arrow",last && practiceLoader!=null?"查看本次练习":"下一题",this::next);next.setId("authoring-next-question");next.setDisable(last && practiceLoader==null);
            var stage=new VBox(QuestionCardLayout.row(previous,card,next));QuestionCardLayout.configure(stage);setPracticeContent(QuestionCardLayout.scroll(stage));refreshOutline();return;
        }
        var card=QuestionTypes.isEssay(q.type())?EssayQuestionCardView.card(q.prompt(),q.scoreSpec().defaultMaxScore(),index,bank.questions().size(),
                "authoring-",bank.resources(),resources):new VBox(16);
        if(!QuestionTypes.isEssay(q.type())){
        card.setId("authoring-question-card");card.getStyleClass().add("question-card");
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var heading=new HBox(12,UiTheme.label(type(q.type()),"question-type-badge"),spacer,UiTheme.label("第 "+(index+1)+" / "+bank.questions().size()+" 题","question-progress"));
        heading.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(heading);
        var prompt=QuestionContentRenderer.render(q.prompt(),bank.resources(),resources,"authoring-prompt-");
        card.getChildren().add(prompt);
        }
        if(QuestionTypes.isEssay(q.type()))card.getChildren().add(essayResponse(q));
        if(q.payload() instanceof ChoicePayload choice)for(int i=0;i<choice.options().size();i++) {
            var option = new HBox(10,UiTheme.label(String.valueOf((char)('A'+i)),"question-option"),
                    QuestionContentRenderer.render(choice.options().get(i).content(),bank.resources(),resources,"authoring-option-"+i+"-"));
            card.getChildren().add(option);
        }
        var previous=QuestionCardLayout.navigation("arrow-left","上一题",()->show(index-1));previous.setId("authoring-previous-question");previous.setDisable(index==0);
        boolean last=index==bank.questions().size()-1;
        var next=QuestionCardLayout.navigation("arrow",last && practiceLoader!=null?"查看本次练习":"下一题",this::next);
        next.setId("authoring-next-question");next.setDisable(last && practiceLoader==null);
        var stage=new VBox(QuestionCardLayout.row(previous,card,next));QuestionCardLayout.configure(stage);setPracticeContent(QuestionCardLayout.scroll(stage));refreshOutline();
    }
    @SuppressWarnings("unchecked")
    private EssayAnswerPane essayResponse(Question question){
        var answers=(java.util.Map<String,EssayAnswerPane>)getProperties().computeIfAbsent(
                "quizforge.essay.pageAnswers",ignored->new java.util.HashMap<String,EssayAnswerPane>());
        var pane=answers.computeIfAbsent(question.id(),ignored->new EssayAnswerPane(question,bank.resources(),resources,practice,this::refreshOutline));
        pane.restore();return pane;
    }
    private void ensurePractice(){if(practice==null)practice=practiceLoader.get();}
    private int practiceIndex(String id){
        for(int i=0;i<practice.session().bank().questions().size();i++)
            if(practice.session().bank().questions().get(i).id().equals(id))return i;
        return -1;
    }
    private void next(){
        if(surface!=null){surface.navigate(this::nextNow);return;}nextNow();
    }
    private void nextNow(){
        if(index<bank.questions().size()-1){showNow(index+1);return;}
        if(practiceLoader==null)return;
        ensurePractice();practice.goTo(practice.session().bank().questions().size()-1);practice.next();render();
    }
    private void practiceChanged(){
        int before=index;
        if(!practice.session().finished()){
            String id=practice.session().current().id();
            for(int i=0;i<bank.questions().size();i++)if(bank.questions().get(i).id().equals(id)){index=i;break;}
        }
        if(before!=index || showingSummary!=practice.session().finished())render();
        else refreshOutline();
    }
    private void show(int target){
        if(editorJump==null && surface!=null){surface.navigate(()->showNow(target));return;}showNow(target);
    }
    private void showNow(int target){
        if(target<0 || target>=bank.questions().size())return;
        if(editorJump==null && practice!=null){
            int selected=practiceIndex(bank.questions().get(target).id());
            // Legacy RICH choice previews are outside the durable, text-only Practice round.
            if(selected>=0)practice.goTo(selected);
        }
        index=target;
        if(editorJump!=null)editorJump.accept(index);
        else {
            if(practice!=null && practice.session().finished())practice.goTo(practice.session().index());
            render();
        }
        refreshOutline();
    }
    private void showItem(int target,int itemNumber) {
        if(editorJump==null && surface!=null){surface.navigate(()->showItemNow(target,itemNumber));return;}
        showItemNow(target,itemNumber);
    }
    private void showItemNow(int target,int itemNumber) {
        if(editorJump!=null || target!=index || readingCard==null && matchingCard==null && translationCard==null)showNow(target);
        if(editorJump==null && translationCard!=null)translationCard.focusItem(itemNumber);
        else if(editorJump==null && matchingCard!=null)matchingCard.focusBlank(itemNumber);
        else if(editorJump==null && readingCard!=null)readingCard.focusItem(itemNumber);
    }
    private void refreshOutline(){
        int selected=editorJump!=null || practice==null || !practice.session().finished()?index:-1;
        outline.showEditor(bank,selected,this::show);
    }
    private static String type(String type){return QuestionTypeCatalog.label(type);}
}
