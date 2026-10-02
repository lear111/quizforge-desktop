package io.quizforge.desktop.ui.question.subjective.translation;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.EssayPracticeAnswer;
import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.type.subjective.translation.*;
import io.quizforge.desktop.ui.content.ContentEditResult;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.document.canvas.*;
import io.quizforge.desktop.ui.question.shared.QuestionCardLayout;
import io.quizforge.desktop.ui.question.subjective.essay.EssayAnswerPane;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** One article, independent sentence drafts, and a single confirmed subjective submission. */
public final class TranslationQuestionCardView extends VBox {
    private final Question question;
    private final TranslationPayload payload;
    private final List<QBankResource> resources;
    private final QuestionResourceInput input;
    private final String prefix;
    private final Supplier<Map<String,EssayPracticeAnswer>> answers;
    private final BooleanSupplier submitted;
    private final BiConsumer<String,EssayPracticeAnswer> save;
    private final VBox responses=new VBox(18),feedback=new VBox(12);
    private final Label error=UiTheme.label("","editor-error");
    private Button submit,retry;
    private int targetItem;
    public TranslationQuestionCardView(Question q,int index,int total,List<QBankResource> resources,QuestionResourceInput input,
            String prefix,Supplier<Map<String,EssayPracticeAnswer>> answers,BooleanSupplier submitted,
            BiConsumer<String,EssayPracticeAnswer> save,Runnable submitAction,Runnable retryAction,Node source) {
        super(18);this.question=q;this.payload=(TranslationPayload)q.payload();this.resources=List.copyOf(resources);this.input=input;
        this.prefix=prefix;this.answers=answers;this.submitted=submitted;this.save=save;
        setId(prefix+"translation-card");getStyleClass().add("question-card");setMinWidth(0);
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var heading=new HBox(12,UiTheme.label("翻译题","question-type-badge"),spacer,UiTheme.label("第 "+(index+1)+" / "+total+" 题","question-progress"));heading.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(heading,UiTheme.label("共 "+payload.items().size()+" 句 · 单题分值："+q.scoreSpec().defaultMaxScore().stripTrailingZeros().toPlainString(),"question-progress"),
                CanvasDocumentView.translation(q.prompt(),resources,input,prefix+"translation-prompt-"),responses);
        if(save!=null) {
            submit=UiTheme.button("提交答案","check","primary",()->{
                int missing=payload.items().size()-(int)answers.get().values().stream().filter(a->!a.empty()).count();
                if(QuestionCardLayout.confirmSubmission(this,missing))act(submitAction);
            });submit.setId(prefix+"translation-submit");
            retry=UiTheme.button("重试","refresh","",()->act(retryAction));retry.setId(prefix+"translation-retry");
            getChildren().add(new HBox(12,submit,retry));
        }
        getChildren().addAll(error,feedback);if(source!=null && !q.sourceRefs().isEmpty())getChildren().add(source);
        heightProperty().addListener((o,a,b)->{if(targetItem>0)Platform.runLater(this::positionTarget);});refresh();
    }
    private void act(Runnable action){try{action.run();refresh();}catch(RuntimeException failure){error.setText(failure.getMessage());}}
    private void write(String itemId,EssayPracticeAnswer answer){
        if(save==null || submitted.getAsBoolean())return;
        try{save.accept(itemId,answer);error.setText("");updateActions();}catch(RuntimeException failure){error.setText("译文未保存："+failure.getMessage());submit.setDisable(true);}
    }
    public void refresh() {
        boolean done=submitted.getAsBoolean();var selected=answers.get();responses.getChildren().clear();feedback.getChildren().clear();
        var references=((TranslationAnswerSpec)question.answerSpec()).referenceAnswers();
        for(var item:payload.items()) {
            var value=selected.getOrDefault(item.id(),new EssayPracticeAnswer("",null));
            var title=UiTheme.label(item.number()+". "+item.text(),"essay-section-title");HBox.setHgrow(title,Priority.ALWAYS);
            var heading=new HBox(12,title);heading.setAlignment(Pos.CENTER_LEFT);
            var section=new VBox(10,heading);section.setId(prefix+"translation-item-"+item.number());
            if(save!=null && !done) {
                var edit=UiTheme.iconButton("edit","用富文本编辑第 "+item.number()+" 句译文",()->edit(item.id(),item.number()));
                edit.setId(prefix+"translation-edit-answer-"+item.number());heading.getChildren().add(edit);
            }
            if(save!=null && !done && value.document()==null) {
                var field=new TextArea(value.text());field.setWrapText(true);field.setPrefRowCount(3);field.setPromptText("输入第 "+item.number()+" 句译文");
                field.setId(prefix+"translation-answer-"+item.number());field.getStyleClass().add("translation-answer-field");
                field.textProperty().addListener((o,a,b)->write(item.id(),new EssayPracticeAnswer(b,null)));section.getChildren().add(field);
            }else if(value.empty())section.getChildren().add(UiTheme.label("未作答","muted"));
            else {
                var preview=EssayAnswerPane.renderSavedAnswer(value.payload(),prefix+"translation-answer-"+item.number()+"-");
                preview.setId(prefix+"translation-answer-"+item.number());section.getChildren().add(preview);
            }
            if(done && references.containsKey(item.id())) {
                section.getChildren().addAll(UiTheme.label("参考译文","muted"),QuestionContentRenderer.render(references.get(item.id()),resources,input,prefix+"translation-reference-"+item.number()+"-"));
            }
            responses.getChildren().add(section);
        }
        if(done) {
            var status=UiTheme.label("已提交 · 待评分","muted");status.setId(prefix+"translation-result");feedback.getChildren().add(status);
            if(question.analysis()!=null)feedback.getChildren().addAll(UiTheme.label("答案解析","essay-section-title"),QuestionContentRenderer.render(question.analysis(),resources,input,prefix+"translation-analysis-"));
        }
        updateActions();
    }
    private void updateActions(){
        if(submit==null)return;boolean done=submitted.getAsBoolean();submit.setVisible(!done);submit.setManaged(!done);
        submit.setDisable(answers.get().values().stream().allMatch(EssayPracticeAnswer::empty));retry.setVisible(done);retry.setManaged(done);
    }
    private void edit(String itemId,int number) {
        if(save==null || submitted.getAsBoolean())return;
        try {
            var value=answers.get().getOrDefault(itemId,new EssayPracticeAnswer("",null));
            var local=new ContentEditSession(new TextContent(value.text()),List.of(),QuestionResourceInput.NONE);
            QuestionContent initial=value.document()==null?new TextContent(value.text()):local.stageDocument(value.document(),value.text());
            CanvasEditorWindow.openEditor(getScene()==null?null:getScene().getWindow(),"编辑第 "+number+" 句译文",initial,local.resources(),local::open,
                    draft->saveRich(itemId,draft,local.resources(),local::open));
            refresh();
        }catch(RuntimeException failure){error.setText("译文编辑失败："+failure.getMessage());}
    }
    private void saveRich(String itemId,ContentEditResult draft,List<QBankResource> original,QuestionResourceInput originalInput) {
        var staged=new LinkedHashMap<String,io.quizforge.desktop.ui.content.StagedContentResource>();draft.addedResources().forEach(r->staged.put(r.resource().id(),r));
        var catalogue=new ArrayList<>(original);staged.values().forEach(r->catalogue.add(r.resource()));
        var local=new ContentEditSession(draft.content(),catalogue,r->staged.containsKey(r.id())?staged.get(r.id()).open():originalInput.open(r));
        var value=new EssayPracticeAnswer(QuestionContentData.plainText(draft.content()),draft.content() instanceof DocumentContent d?local.document(d):null);
        // Propagate a failed write to the editor so it retains the unsaved draft.
        save.accept(itemId,value);error.setText("");updateActions();
    }
    public void focusItem(int number) {
        if(payload.items().stream().noneMatch(item->item.number()==number))return;targetItem=number;Platform.runLater(this::positionTarget);
        var settle=new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));settle.setOnFinished(e->{if(targetItem==number)targetItem=0;});settle.play();
    }
    private void positionTarget() {
        var target=lookup("#"+prefix+"translation-item-"+targetItem);if(target==null || getScene()==null)return;
        for(Node parent=getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollPane scroll){
            var content=scroll.getContent();content.applyCss();if(content instanceof javafx.scene.Parent root)root.layout();
            double height=content.getBoundsInLocal().getHeight()-scroll.getViewportBounds().getHeight();
            if(height>0){double y=content.sceneToLocal(target.localToScene(target.getBoundsInLocal())).getMinY()-content.getBoundsInLocal().getMinY();scroll.setVvalue(scroll.getVmin()+Math.max(0,Math.min(1,y/height))*(scroll.getVmax()-scroll.getVmin()));}return;
        }
    }
}
