package io.quizforge.desktop.ui.question.objective.cloze;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.type.objective.cloze.*;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.document.canvas.CanvasDocumentView;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.*;
import java.util.function.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** One selection state drives both inline blanks and the full option list. */
public final class ClozeQuestionCardView extends VBox {
    private final Question question;
    private final ClozePayload payload;
    private final Set<String> correct;
    private final Supplier<Set<String>> selected;
    private final BooleanSupplier submitted;
    private final Consumer<String> choose;
    private final CanvasDocumentView passage;
    private final Map<String,RadioButton> buttons=new LinkedHashMap<>();
    private final VBox feedback=new VBox(10);
    private Button submit,retry;
    private final List<QBankResource> resources;
    private final QuestionResourceInput input;
    private final String prefix;

    public ClozeQuestionCardView(Question q,int index,int total,List<QBankResource> resources,QuestionResourceInput input,
            String prefix,Supplier<Set<String>> selected,BooleanSupplier submitted,Consumer<String> choose,Runnable submitAction,Runnable retryAction,Node source){
        super(18);question=q;payload=(ClozePayload)q.payload();correct=Set.copyOf(((ClozeAnswerSpec)q.answerSpec()).correctOptionIds());
        this.selected=selected;this.submitted=submitted;this.choose=choose;this.prefix=prefix;this.resources=List.copyOf(resources);this.input=input;
        setId(prefix+"cloze-card");getStyleClass().add("question-card");setMinWidth(0);
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var heading=new HBox(12,UiTheme.label("完形填空","question-type-badge"),spacer,UiTheme.label("第 "+(index+1)+" / "+total+" 题","question-progress"));
        heading.setAlignment(Pos.CENTER_LEFT);getChildren().addAll(heading,UiTheme.label("单题分值："+q.scoreSpec().defaultMaxScore().stripTrailingZeros().toPlainString(),"question-progress"));
        passage=new CanvasDocumentView(q.prompt(),resources,input,prefix+"cloze-",configuration(),(number,option)->select(option));
        getChildren().add(passage);
        var options=optionGrid();
        for(int row=0;row<payload.blanks().size();row++){
            var blank=payload.blanks().get(row);
            var number=UiTheme.label(blank.number()+".","essay-section-title");number.setWrapText(false);number.setMinWidth(Region.USE_PREF_SIZE);number.setId(prefix+"cloze-blank-"+blank.number());options.add(number,0,row);
            var group=new ToggleGroup();
            for(int i=0;i<blank.options().size();i++){
                var option=blank.options().get(i);var radio=new RadioButton((char)('A'+i)+". "+QuestionContentData.plainText(option.content()));
                radio.setToggleGroup(group);radio.setWrapText(true);radio.setMaxWidth(Double.MAX_VALUE);
                radio.setId(prefix+"cloze-option-"+blank.number()+"-"+i);radio.getStyleClass().add("cloze-answer-option");
                radio.setMinWidth(0);radio.setOnAction(e->select(option.id()));buttons.put(option.id(),radio);options.add(radio,i+1,row);
            }
        }
        getChildren().add(options);
        if(choose!=null){
            submit=UiTheme.button("提交答案","check","primary",()->{
                var selection=selected.get();
                int unanswered=(int)payload.blanks().stream().filter(b->b.options().stream().noneMatch(o->selection.contains(o.id()))).count();
                if(io.quizforge.desktop.ui.question.shared.QuestionCardLayout.confirmSubmission(this,unanswered))act(submitAction);
            });submit.setId(prefix+"cloze-submit");
            retry=UiTheme.button("重试","refresh","",()->act(retryAction));retry.setId(prefix+"cloze-retry");
            getChildren().add(new HBox(12,submit,retry));
        }
        getChildren().add(feedback);if(source!=null && !q.sourceRefs().isEmpty())getChildren().add(source);
        refresh();
    }
    private void select(String option){
        if(choose==null || submitted.getAsBoolean())return;
        try{choose.accept(option);refresh();}catch(RuntimeException error){feedback.getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));refreshButtons();}
    }
    private void act(Runnable action){try{action.run();refresh();}catch(RuntimeException error){feedback.getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));}}
    private Map<String,Object> configuration(){
        return configuration(payload,selected.get(),correct,submitted.getAsBoolean(),choose==null);
    }
    static GridPane optionGrid(){
        var grid=new GridPane();grid.setHgap(8);grid.setVgap(16);grid.setMinWidth(0);
        var number=new ColumnConstraints(32,Region.USE_COMPUTED_SIZE,Region.USE_PREF_SIZE);
        grid.getColumnConstraints().add(number);
        for(int column=0;column<4;column++){
            var constraints=new ColumnConstraints(0,0,Double.MAX_VALUE);
            constraints.setHgrow(Priority.ALWAYS);grid.getColumnConstraints().add(constraints);
        }
        return grid;
    }
    static Map<String,Object> configuration(ClozePayload payload,Set<String> selection,Set<String> correct,boolean submitted,boolean readonly){
        return Map.of("submitted",submitted,"readonly",readonly,"blanks",payload.blanks().stream().map(b->{
            var f=new LinkedHashMap<String,Object>();f.put("number",b.number());
            f.put("options",b.options().stream().map(o->Map.of("id",o.id(),"text",QuestionContentData.plainText(o.content()))).toList());
            f.put("selected",b.options().stream().map(o->o.id()).filter(selection::contains).findFirst().orElse(""));
            f.put("correct",b.options().stream().map(o->o.id()).filter(correct::contains).findFirst().orElse(""));return f;
        }).toList());
    }
    private void refreshButtons(){
        var selection=selected.get();boolean done=submitted.getAsBoolean();
        buttons.forEach((id,b)->{b.setSelected(selection.contains(id));b.setDisable(choose==null || done);
            b.getStyleClass().removeAll("cloze-correct","cloze-incorrect");
            if(done && correct.contains(id))b.getStyleClass().add("cloze-correct");
            else if(done && selection.contains(id))b.getStyleClass().add("cloze-incorrect");});
        if(submit!=null){submit.setVisible(!done);submit.setManaged(!done);submit.setDisable(selection.isEmpty());retry.setVisible(done);retry.setManaged(done);}
    }
    public void refresh(){
        refreshButtons();passage.updateCloze(configuration());feedback.getChildren().clear();
        if(submitted.getAsBoolean()){
            long matched=selected.get().stream().filter(correct::contains).count();
            var score=question.scoreSpec().defaultMaxScore().multiply(java.math.BigDecimal.valueOf(matched)).stripTrailingZeros();
            var total=question.scoreSpec().defaultMaxScore().multiply(java.math.BigDecimal.valueOf(payload.blanks().size())).stripTrailingZeros();
            var label=UiTheme.label("得分："+score.toPlainString()+" / "+total.toPlainString(),"muted");label.setId(prefix+"cloze-result");feedback.getChildren().add(label);
            if(question.analysis()!=null){
                feedback.getChildren().add(UiTheme.label("答案与解析","essay-section-title"));
                feedback.getChildren().add(QuestionContentRenderer.render(question.analysis(),resources,input,prefix+"cloze-analysis-"));
            }
        }
    }
}
