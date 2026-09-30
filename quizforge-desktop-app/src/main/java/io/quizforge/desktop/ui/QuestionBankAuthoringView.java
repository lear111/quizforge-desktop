package io.quizforge.desktop.ui;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.*;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.function.Function;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Essay responses stay local to this page; supported choices use the persistent practice view. */
final class QuestionBankAuthoringView extends SplitPane {
    private final BorderPane readerColumn=new BorderPane();
    private final VBox outline=new VBox(10);
    private final QuestionResourceInput resources;
    private final Supplier<PersistentPracticeRuntime> practiceLoader;
    private final Function<List<SourceRef>, QuestionSourceListView> sources;
    private PersistentPracticeRuntime practice;
    private boolean showingSummary;
    private QuestionBank bank;
    private int index;
    private IntConsumer editorJump;
    QuestionBankAuthoringView(QuestionBank bank,QuestionResourceInput resources){
        this(bank, resources, null, refs -> null);
    }
    QuestionBankAuthoringView(QuestionBank bank,QuestionResourceInput resources,
            Supplier<PersistentPracticeRuntime> practiceLoader, Function<List<SourceRef>,QuestionSourceListView> sources){
        this.practiceLoader=practiceLoader;this.sources=sources;
        this.bank=bank;this.resources=resources;setId("qbank-authoring-layout");getStyleClass().add("practice-browse-layout");
        readerColumn.setMinWidth(320);outline.setId("authoring-outline");outline.getStyleClass().add("question-outline");
        var outlineScroll=UiTheme.scroll(outline);outlineScroll.setPrefWidth(260);outlineScroll.setMinWidth(100);
        getItems().addAll(readerColumn,outlineScroll);SplitPane.setResizableWithParent(outlineScroll,false);
        widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>(){
            public void changed(javafx.beans.value.ObservableValue<? extends Number> o,Number before,Number value){
                if(value.doubleValue()>500){setDividerPositions((value.doubleValue()-260)/value.doubleValue());widthProperty().removeListener(this);}
            }
        });render();
    }
    static boolean containsEssay(QuestionBank bank){return bank.questions().stream().anyMatch(q->"ESSAY".equals(q.type()));}
    static boolean hasPracticeChoices(QuestionBank bank){return bank.questions().stream().anyMatch(QuestionText::supports);}
    static boolean supportsEditing(QuestionBank bank){
        return bank.questions().stream().allMatch(q->"ESSAY".equals(q.type())
                ? q.stimulusRefs().isEmpty() && editableContent(q.prompt())
                : q.stimulusRefs().isEmpty() && q.prompt() instanceof TextContent
                    && (q.analysis()==null || q.analysis() instanceof TextContent)
                    && q.choicePayload().options().stream().allMatch(o->o.content() instanceof TextContent));
    }
    private static boolean editableContent(QuestionContent content){
        try { RichContentEditorAdapter.toEditorJson(content);return true; }
        catch(IllegalArgumentException unsupported) { return false; }
    }
    void setHeader(Node header){readerColumn.setTop(header);}
    void showEditor(Node editor,IntConsumer jump){editorJump=jump;readerColumn.setCenter(editor);refreshOutline();}
    void updateEditor(QuestionBank bank,int selected){this.bank=bank;index=selected;refreshOutline();}
    int currentIndex(){return index;}
    Region outline(){return (Region)getItems().get(1);}
    private void render(){
        if(practice!=null && practice.session().finished()){
            showingSummary=true;
            readerColumn.setCenter(QuestionCardLayout.scroll(new QuestionBankPracticeView(practice,sources,null,this::practiceChanged)));
            refreshOutline();return;
        }
        showingSummary=false;
        var current=bank.questions().get(index);
        if(practiceLoader!=null && QuestionText.supports(current)){
            ensurePractice();
            int selected=practiceIndex(current.id());
            if(practice.session().finished() || practice.session().index()!=selected)practice.goTo(selected);
            var navigation=new QuestionBankPracticeView.Navigation(index,bank.questions().size(),index==0,
                    index==bank.questions().size()-1,()->show(index-1),this::next);
            readerColumn.setCenter(QuestionCardLayout.scroll(new QuestionBankPracticeView(practice,sources,navigation,this::practiceChanged)));
            refreshOutline();return;
        }
        var q=bank.questions().get(index);var card=new VBox(16);card.setId("authoring-question-card");card.getStyleClass().add("question-card");
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var heading=new HBox(12,UiTheme.label(type(q.type()),"question-type-badge"),spacer,UiTheme.label("第 "+(index+1)+" / "+bank.questions().size()+" 题","question-progress"));
        heading.setAlignment(Pos.CENTER_LEFT);
        if("ESSAY".equals(q.type())){
            card.getStyleClass().add("essay-answer-card");
            String metadata="分值："+q.scoreSpec().defaultMaxScore().stripTrailingZeros().toPlainString();
            var label=UiTheme.label(metadata,"muted");label.setId("authoring-essay-metadata");
            heading.getChildren().set(0,new VBox(8,UiTheme.label("作文题","question-type-badge"),label));
            heading.setAlignment(Pos.BOTTOM_LEFT);
        }
        card.getChildren().add(heading);
        var prompt=QuestionContentRenderer.render(q.prompt(),bank.resources(),resources,"authoring-prompt-");
        if("ESSAY".equals(q.type()) && q.prompt() instanceof TextContent){
            prompt.getStyleClass().remove("question-stem");prompt.getStyleClass().add("authoring-essay-text");
        }
        card.getChildren().add(prompt);
        if("ESSAY".equals(q.type()))card.getChildren().add(essayResponse(q));
        if(q.payload() instanceof ChoicePayload choice)for(int i=0;i<choice.options().size();i++)card.getChildren().add(
                UiTheme.label((char)('A'+i)+"  "+QuestionText.option(choice.options().get(i)),"question-option"));
        var previous=QuestionCardLayout.navigation("arrow-left","上一题",()->show(index-1));previous.setId("authoring-previous-question");previous.setDisable(index==0);
        boolean last=index==bank.questions().size()-1;
        var next=QuestionCardLayout.navigation("arrow",last && hasPracticeChoices(bank)?"查看本次练习":"下一题",this::next);
        next.setId("authoring-next-question");next.setDisable(last && (practiceLoader==null || !hasPracticeChoices(bank)));
        var stage=new VBox(QuestionCardLayout.row(previous,card,next));QuestionCardLayout.configure(stage);readerColumn.setCenter(QuestionCardLayout.scroll(stage));refreshOutline();
    }
    @SuppressWarnings("unchecked")
    private EssayAnswerPane essayResponse(Question question){
        var answers=(java.util.Map<String,EssayAnswerPane>)getProperties().computeIfAbsent(
                "quizforge.essay.pageAnswers",ignored->new java.util.HashMap<String,EssayAnswerPane>());
        return answers.computeIfAbsent(question.id(),ignored->new EssayAnswerPane(question,bank.resources(),resources));
    }
    private void ensurePractice(){if(practice==null)practice=practiceLoader.get();}
    private int practiceIndex(String id){
        for(int i=0;i<practice.session().bank().questions().size();i++)
            if(practice.session().bank().questions().get(i).id().equals(id))return i;
        return -1;
    }
    private void next(){
        if(index<bank.questions().size()-1){show(index+1);return;}
        if(practiceLoader==null || !hasPracticeChoices(bank))return;
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
        if(target<0 || target>=bank.questions().size())return;
        index=target;
        if(editorJump!=null)editorJump.accept(index);
        else {
            if(practice!=null && practice.session().finished())practice.goTo(practice.session().index());
            render();
        }
        refreshOutline();
    }
    private void refreshOutline(){
        outline.getChildren().setAll(UiTheme.label("题目大纲","muted"));
        for(String type:List.of("SINGLE_CHOICE","MULTIPLE_CHOICE","ESSAY")){
            var grid=new FlowPane(8,8);grid.setAlignment(Pos.TOP_LEFT);
            for(int i=0;i<bank.questions().size();i++)if(type.equals(bank.questions().get(i).type())){
                final int target=i;Button button=new Button(Integer.toString(i+1));button.setId("authoring-question-"+(i+1));
                button.getStyleClass().add("question-number-cell");
                if(practice!=null && QuestionText.supports(bank.questions().get(i))){
                    int selected=practiceIndex(bank.questions().get(i).id());
                    boolean submitted=selected>=0 && practice.session().state(selected)==QuestionBankPracticeSession.State.SUBMITTED;
                    button.getStyleClass().add(submitted?practice.session().correct(selected)?"correct":"incorrect":"unsubmitted");
                }
                if(i==index && (editorJump!=null || practice==null || !practice.session().finished()))button.getStyleClass().add("current");
                button.setOnAction(e->show(target));grid.getChildren().add(button);
            }
            if(!grid.getChildren().isEmpty())outline.getChildren().addAll(UiTheme.label(type(type),"muted"),grid);
        }
    }
    private static String type(String type){return "ESSAY".equals(type)?"作文题":"SINGLE_CHOICE".equals(type)?"单选题":"多选题";}
}
