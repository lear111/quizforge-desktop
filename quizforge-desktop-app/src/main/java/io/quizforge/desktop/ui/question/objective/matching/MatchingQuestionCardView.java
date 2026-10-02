package io.quizforge.desktop.ui.question.objective.matching;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.type.objective.matching.*;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.question.shared.QuestionCardLayout;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** The prompt contains the entire question; only unlocked answer slots accept input. */
public final class MatchingQuestionCardView extends VBox {
    private final Question question;
    private final MatchingPayload payload;
    private final Supplier<Map<String,String>> selected;
    private final BooleanSupplier submitted;
    private final BiConsumer<String,String> choose;
    private final String prefix;
    private final FlowPane slots=new FlowPane(8,10);
    private final VBox feedback=new VBox(10);
    private final List<QBankResource> resources;
    private final QuestionResourceInput input;
    private Button submit,retry;
    private int targetBlank;

    public MatchingQuestionCardView(Question q,int index,int total,List<QBankResource> resources,QuestionResourceInput input,
            String prefix,Supplier<Map<String,String>> selected,BooleanSupplier submitted,BiConsumer<String,String> choose,
            Runnable submitAction,Runnable retryAction,Node source) {
        super(18);question=q;payload=(MatchingPayload)q.payload();this.selected=selected;this.submitted=submitted;
        this.choose=choose;this.prefix=prefix;this.resources=List.copyOf(resources);this.input=input;
        setId(prefix+"matching-card");getStyleClass().add("question-card");setMinWidth(0);
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var heading=new HBox(12,UiTheme.label("新题型·段落排序","question-type-badge"),spacer,
                UiTheme.label("第 "+(index+1)+" / "+total+" 题","question-progress"));heading.setAlignment(Pos.CENTER_LEFT);
        int graded=MatchingQuestionType.gradableCount(q);
        getChildren().addAll(heading,UiTheme.label("待答位置："+graded+" · 单题分值："+q.scoreSpec().defaultMaxScore().stripTrailingZeros().toPlainString(),"question-progress"),
                QuestionContentRenderer.render(q.prompt(),resources,input,prefix+"matching-prompt-"),slots);
        heightProperty().addListener((o,a,b)->{if(targetBlank>0)Platform.runLater(this::positionTarget);});
        if(choose!=null) {
            submit=UiTheme.button("提交答案","check","primary",()->{
                if(QuestionCardLayout.confirmSubmission(this,graded-selected.get().size()))act(submitAction);
            });submit.setId(prefix+"matching-submit");
            retry=UiTheme.button("重试","refresh","",()->act(retryAction));retry.setId(prefix+"matching-retry");
            getChildren().add(new HBox(12,submit,retry));
        }
        getChildren().add(feedback);if(source!=null && !q.sourceRefs().isEmpty())getChildren().add(source);refresh();
    }
    private void select(String blank,String option) {
        targetBlank=0;if(choose==null || submitted.getAsBoolean())return;
        try{choose.accept(blank,option);refresh();}catch(RuntimeException error){showError(error);}
    }
    private void act(Runnable action){targetBlank=0;try{action.run();refresh();}catch(RuntimeException error){showError(error);}}
    private void showError(RuntimeException error){feedback.getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));}

    /** The same compact controls set correct answers in editing and draft answers in practice. */
    public static FlowPane answerSlots(MatchingPayload payload,Map<String,String> selection,Map<String,String> correct,
            boolean submitted,boolean readonly,boolean editing,String prefix,BiConsumer<String,String> choose,Consumer<String> toggleLock) {
        var strip=new FlowPane(8,10);
        strip.getStyleClass().add("matching-answer-strip");
        for(var blank:payload.blanks()) {
            String chosen=blank.locked()?correct.get(blank.id()):selection.get(blank.id());
            String letter=payload.options().stream().filter(o->o.id().equals(chosen)).map(MatchingOption::label).findFirst().orElse("___");
            var slot=new MenuButton(blank.number()+". "+letter);slot.setId(prefix+"matching-blank-"+blank.number());
            slot.getStyleClass().add("matching-answer-choice");
            slot.setMinWidth(0);slot.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(slot,Priority.ALWAYS);
            slot.setDisable(readonly || submitted || blank.locked());
            if(blank.locked()){slot.getStyleClass().add("matching-hint");slot.setTooltip(new Tooltip("已给出的提示，不计分"));}
            else if(submitted && chosen!=null)slot.getStyleClass().add(Objects.equals(chosen,correct.get(blank.id()))?"matching-correct":"matching-incorrect");
            for(var option:payload.options()) {
                boolean reserved=payload.blanks().stream().anyMatch(b->b.locked() && option.id().equals(correct.get(b.id())));
                var item=new RadioMenuItem(option.label());item.setSelected(option.id().equals(chosen));item.setDisable(reserved);
                item.setOnAction(e->choose.accept(blank.id(),option.id()));slot.getItems().add(item);
            }
            var row=new HBox(0,slot);row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("matching-answer-slot");
            if(blank.locked())row.getStyleClass().add("matching-hint");
            else if(submitted && chosen!=null)row.getStyleClass().add(Objects.equals(chosen,correct.get(blank.id()))?"matching-correct":"matching-incorrect");
            if(editing){
                var lock=UiTheme.iconButton(blank.locked()?"lock":"unlock",blank.locked()?"解除提示锁定":"锁定为已给出的提示",()->toggleLock.accept(blank.id()));
                lock.getStyleClass().add("matching-slot-action");
                lock.setId(prefix+"matching-lock-"+blank.number());row.getChildren().add(lock);
            }
            strip.getChildren().add(row);
        }
        return strip;
    }
    public void refresh() {
        var selection=selected.get();boolean done=submitted.getAsBoolean();var correct=((MatchingAnswerSpec)question.answerSpec()).assignments();
        slots.getChildren().setAll(answerSlots(payload,selection,correct,done,choose==null,false,prefix,this::select,null).getChildren());
        if(submit!=null){submit.setVisible(!done);submit.setManaged(!done);submit.setDisable(selection.isEmpty());retry.setVisible(done);retry.setManaged(done);}
        feedback.getChildren().clear();
        if(done) {
            var unit=question.scoreSpec().defaultMaxScore();
            var score=unit.multiply(java.math.BigDecimal.valueOf(MatchingQuestionType.matchingCount(question,selection))).stripTrailingZeros();
            var max=unit.multiply(java.math.BigDecimal.valueOf(MatchingQuestionType.gradableCount(question))).stripTrailingZeros();
            var result=UiTheme.label("得分："+score.toPlainString()+" / "+max.toPlainString(),"muted");result.setId(prefix+"matching-result");feedback.getChildren().add(result);
            if(question.analysis()!=null){feedback.getChildren().add(UiTheme.label("答案与解析","essay-section-title"));feedback.getChildren().add(QuestionContentRenderer.render(question.analysis(),resources,input,prefix+"matching-analysis-"));}
        }
    }
    public void focusBlank(int number) {
        if(payload.blanks().stream().noneMatch(b->b.number()==number))return;targetBlank=number;Platform.runLater(this::positionTarget);
        var settle=new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));settle.setOnFinished(e->{if(targetBlank==number)targetBlank=0;});settle.play();
    }
    private void positionTarget() {
        var target=lookup("#"+prefix+"matching-blank-"+targetBlank);if(target==null || getScene()==null)return;
        for(Node parent=getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollPane scroll){
            var content=scroll.getContent();content.applyCss();if(content instanceof javafx.scene.Parent root)root.layout();
            double height=content.getBoundsInLocal().getHeight()-scroll.getViewportBounds().getHeight();
            if(height>0){double y=content.sceneToLocal(target.localToScene(target.getBoundsInLocal())).getMinY()-content.getBoundsInLocal().getMinY();scroll.setVvalue(scroll.getVmin()+Math.max(0,Math.min(1,y/height))*(scroll.getVmax()-scroll.getVmin()));}return;
        }
    }
}
