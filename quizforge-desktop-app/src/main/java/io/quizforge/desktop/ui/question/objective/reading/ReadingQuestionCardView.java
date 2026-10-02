package io.quizforge.desktop.ui.question.objective.reading;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.type.objective.reading.*;
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

/** Article and child questions share one durable selection and submission. */
public final class ReadingQuestionCardView extends VBox {
    private final Question question;
    private final ReadingPayload payload;
    private final Set<String> correct;
    private final Supplier<Set<String>> selected;
    private final BooleanSupplier submitted;
    private final Consumer<String> choose;
    private final Map<String,RadioButton> buttons=new LinkedHashMap<>();
    private final Map<Integer,VBox> items=new LinkedHashMap<>();
    private final VBox feedback=new VBox(10);
    private final List<QBankResource> resources;
    private final QuestionResourceInput input;
    private final String prefix;
    private Button submit,retry;
    private int targetItem;

    public ReadingQuestionCardView(Question question,int index,int total,List<QBankResource> resources,QuestionResourceInput input,
            String prefix,Supplier<Set<String>> selected,BooleanSupplier submitted,Consumer<String> choose,Runnable submitAction,Runnable retryAction,Node source) {
        super(18);this.question=question;payload=(ReadingPayload)question.payload();correct=Set.copyOf(((ReadingAnswerSpec)question.answerSpec()).correctOptionIds());
        this.selected=selected;this.submitted=submitted;this.choose=choose;this.resources=List.copyOf(resources);this.input=input;this.prefix=prefix;
        setId(prefix+"reading-card");getStyleClass().add("question-card");setMinWidth(0);
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var heading=new HBox(12,UiTheme.label("阅读理解","question-type-badge"),spacer,UiTheme.label("第 "+(index+1)+" / "+total+" 题","question-progress"));heading.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(heading,UiTheme.label("单题分值："+question.scoreSpec().defaultMaxScore().stripTrailingZeros().toPlainString(),"question-progress"),
                QuestionContentRenderer.render(question.prompt(),resources,input,prefix+"reading-article-"));
        for(var item:payload.items()) {
            var section=new VBox(12);section.setMinWidth(0);section.setId(prefix+"reading-item-"+item.number());
            var number=UiTheme.label(item.number()+".","essay-section-title");number.setWrapText(false);number.setMinWidth(Region.USE_PREF_SIZE);
            var prompt=QuestionContentRenderer.render(item.prompt(),resources,input,prefix+"reading-prompt-"+item.number()+"-");HBox.setHgrow(prompt,Priority.ALWAYS);
            var title=new HBox(8,number,prompt);title.setAlignment(Pos.TOP_LEFT);section.getChildren().add(title);
            var options=optionGrid();var group=new ToggleGroup();
            for(int i=0;i<4;i++) {
                var option=item.options().get(i);var radio=new RadioButton((char)('A'+i)+". "+QuestionContentData.plainText(option.content()));
                radio.setId(prefix+"reading-option-"+item.number()+"-"+i);radio.setToggleGroup(group);radio.setWrapText(true);radio.setMinWidth(0);radio.setMaxWidth(Double.MAX_VALUE);
                radio.getStyleClass().add("reading-answer-option");radio.setOnAction(event->select(option.id()));buttons.put(option.id(),radio);options.add(radio,i,0);
            }
            section.getChildren().add(options);items.put(item.number(),section);getChildren().add(section);
        }
        if(choose!=null) {
            submit=UiTheme.button("提交答案","check","primary",()->{
                int unanswered=(int)payload.items().stream().filter(item->item.options().stream().noneMatch(option->selected.get().contains(option.id()))).count();
                if(QuestionCardLayout.confirmSubmission(this,unanswered))act(submitAction);
            });submit.setId(prefix+"reading-submit");
            retry=UiTheme.button("重试","refresh","",()->act(retryAction));retry.setId(prefix+"reading-retry");getChildren().add(new HBox(12,submit,retry));
        }
        getChildren().add(feedback);if(source!=null && !question.sourceRefs().isEmpty())getChildren().add(source);
        heightProperty().addListener((o,a,b)->{if(targetItem>0)Platform.runLater(this::positionTarget);});
        refresh();
    }

    static GridPane optionGrid() {
        return new AdaptiveOptionGrid();
    }

    /** Reflow the same controls, preserving selections and keyboard order. */
    private static final class AdaptiveOptionGrid extends GridPane {
        private int columns;
        AdaptiveOptionGrid(){setMinWidth(0);setHgap(12);setVgap(8);}
        @Override protected double computePrefHeight(double width){reflow(width>=0?width:getWidth());return super.computePrefHeight(width);}
        @Override protected void layoutChildren(){reflow(getWidth());super.layoutChildren();}
        private void reflow(double width){
            if(width<=0 || getChildren().isEmpty())return;
            double available=width-snappedLeftInset()-snappedRightInset();
            int desired=1;
            for(int candidate:new int[]{4,2}){
                double cell=(available-getHgap()*(candidate-1))/candidate;
                if(cell>=120 && getChildren().stream().allMatch(node->fits(node,cell))){desired=candidate;break;}
            }
            if(columns!=desired){
                columns=desired;getColumnConstraints().clear();
                for(int i=0;i<columns;i++){var column=new ColumnConstraints(0,0,Double.MAX_VALUE);column.setPercentWidth(100.0/columns);column.setHgrow(Priority.ALWAYS);getColumnConstraints().add(column);}
            }
            for(int i=0;i<getChildren().size();i++){
                var node=getChildren().get(i);
                if(!Objects.equals(getColumnIndex(node),i%columns))setColumnIndex(node,i%columns);
                if(!Objects.equals(getRowIndex(node),i/columns))setRowIndex(node,i/columns);
            }
        }
        private static boolean fits(Node node,double width){
            String text;javafx.scene.text.Font font;double chrome;
            if(node instanceof RadioButton radio){
                text=radio.getText();font=radio.getFont();
                var indicator=radio.lookup(".radio");
                chrome=(indicator==null?20:Math.max(20,indicator.getLayoutBounds().getWidth()))+radio.getGraphicTextGap()
                        +radio.getInsets().getLeft()+radio.getInsets().getRight()+radio.getLabelPadding().getLeft()+radio.getLabelPadding().getRight();
            }else if(node instanceof HBox box && box.getChildren().size()==2
                    && box.getChildren().get(0) instanceof RadioButton radio && box.getChildren().get(1) instanceof TextField field){
                text=field.getText();font=field.getFont();chrome=radio.prefWidth(-1)+box.getSpacing()+field.getInsets().getLeft()+field.getInsets().getRight()+8;
            }else return node.prefWidth(-1)<=width;
            if(width<=chrome || text.split("\\R",-1).length>1)return false;
            // Choose multiple columns only when every option fits on one line.
            var measured=new javafx.scene.text.Text();measured.setFont(font);
            measured.setText(text);
            return measured.getLayoutBounds().getWidth()+chrome+2<=width;
        }
    }

    private void select(String option) {
        targetItem=0;if(choose==null || submitted.getAsBoolean())return;
        try{choose.accept(option);refresh();}catch(RuntimeException error){feedback.getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));refreshButtons();}
    }
    private void act(Runnable action) {
        targetItem=0;try{action.run();refresh();}catch(RuntimeException error){feedback.getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));}
    }
    private void refreshButtons() {
        var selection=selected.get();boolean done=submitted.getAsBoolean();
        buttons.forEach((id,button)->{button.setSelected(selection.contains(id));button.setDisable(choose==null || done);
            button.getStyleClass().removeAll("reading-correct","reading-incorrect");
            if(done && correct.contains(id))button.getStyleClass().add("reading-correct");
            else if(done && selection.contains(id))button.getStyleClass().add("reading-incorrect");});
        if(submit!=null){submit.setVisible(!done);submit.setManaged(!done);submit.setDisable(selection.isEmpty());retry.setVisible(done);retry.setManaged(done);}
    }
    public void refresh() {
        refreshButtons();feedback.getChildren().clear();
        if(submitted.getAsBoolean()) {
            long matched=selected.get().stream().filter(correct::contains).count();
            var unit=question.scoreSpec().defaultMaxScore();
            var result=UiTheme.label("得分："+unit.multiply(java.math.BigDecimal.valueOf(matched)).stripTrailingZeros().toPlainString()+" / "+unit.multiply(java.math.BigDecimal.valueOf(payload.items().size())).stripTrailingZeros().toPlainString(),"muted");
            result.setId(prefix+"reading-result");feedback.getChildren().add(result);
            if(question.analysis()!=null){feedback.getChildren().add(UiTheme.label("答案与解析","essay-section-title"));feedback.getChildren().add(QuestionContentRenderer.render(question.analysis(),resources,input,prefix+"reading-analysis-"));}
        }
    }

    /** Reposition after asynchronous native previews report their content height. */
    public void focusItem(int number) {
        if(!items.containsKey(number))return;targetItem=number;Platform.runLater(this::positionTarget);
        var settle=new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));settle.setOnFinished(event->{if(targetItem==number)targetItem=0;});settle.play();
    }
    private void positionTarget() {
        var item=items.get(targetItem);if(item==null || getScene()==null)return;
        for(Node parent=getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollPane scroll) {
            var content=scroll.getContent();content.applyCss();if(content instanceof javafx.scene.Parent root)root.layout();
            double height=content.getBoundsInLocal().getHeight()-scroll.getViewportBounds().getHeight();
            if(height>0){double y=content.sceneToLocal(item.localToScene(item.getBoundsInLocal())).getMinY()-content.getBoundsInLocal().getMinY();scroll.setVvalue(scroll.getVmin()+Math.max(0,Math.min(1,y/height))*(scroll.getVmax()-scroll.getVmin()));}return;
        }
    }
}
