package io.quizforge.desktop.ui;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.*;
import io.quizforge.core.practice.*;
import java.util.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Essay draft and submitted answer share the same durable ACTIVE practice round. */
final class EssayAnswerPane extends VBox {
    private final Question question;
    private final List<QBankResource> existing;
    private final QuestionResourceInput input;
    private final Map<String,StagedContentResource> imported=new LinkedHashMap<>();
    private final VBox preview=new VBox(12);
    private final VBox feedback=new VBox(12);
    private final Label error=UiTheme.label("","incorrect");
    private final Button submit;
    private final Button edit;
    private final Button retry;
    private boolean locallySubmitted;
    private QuestionContent answer=new TextContent("");
    private final PersistentPracticeRuntime practice;
    private final Runnable changed;
    private EssayPracticeAnswer persisted=new EssayPracticeAnswer("",null);

    EssayAnswerPane(Question question,List<QBankResource> existing,QuestionResourceInput input){
        this(question,existing,input,null,()->{});
    }
    EssayAnswerPane(Question question,List<QBankResource> existing,QuestionResourceInput input,
            PersistentPracticeRuntime practice,Runnable changed){
        super(18);this.question=question;this.existing=List.copyOf(existing);this.input=input;
        this.practice=practice;this.changed=changed;
        setId("essay-answer-pane");setMinWidth(0);
        edit=UiTheme.button("编辑作答","edit","",this::edit);
        edit.setId("essay-edit-answer");edit.getStyleClass().add("essay-answer-edit");
        var space=new Region();HBox.setHgrow(space,Priority.ALWAYS);
        var heading=new HBox(8,UiTheme.label("作答","essay-section-title"),space,edit);
        heading.setAlignment(Pos.CENTER_LEFT);
        var box=new VBox(14,heading,preview);box.setId("essay-answer-box");
        box.getStyleClass().add("essay-answer-box");box.setMinWidth(0);preview.setMinWidth(0);
        submit=UiTheme.button("提交答案","check","primary",this::submit);
        submit.setId("essay-submit-answer");submit.getStyleClass().add("essay-submit");
        retry=UiTheme.button("重新答题","refresh","",this::retry);retry.setId("essay-retry");
        var actions=new HBox(12,submit,retry,error);actions.setAlignment(Pos.CENTER_LEFT);
        feedback.setId("essay-answer-feedback");feedback.setVisible(false);feedback.setManaged(false);
        getChildren().addAll(box,actions,feedback);restore();
    }

    void restore(){
        if(practice!=null){
            persisted=practice.essayAnswer(question.id());imported.clear();
            if(persisted.document()==null)answer=new TextContent(persisted.text());
            else {
                var session=new ContentEditSession(new TextContent(""),List.of(),QuestionResourceInput.NONE);
                answer=session.stageDocument(persisted.document(),persisted.text());
                session.save(answer).addedResources().forEach(resource->imported.put(resource.resource().id(),resource));
            }
        }
        feedback.setVisible(false);feedback.setManaged(false);refreshAnswer();
        if(submitted())showSubmission();
    }

    private List<QBankResource> resources(){
        var all=new ArrayList<>(existing);imported.values().forEach(image->all.add(image.resource()));return all;
    }
    private QuestionResourceInput resourceInput(){
        return resource->{var image=imported.get(resource.id());return image==null?input.open(resource):image.open();};
    }
    private void edit(){
        if(submitted())return;
        try{
            var originalResources=Map.copyOf(imported);
            QuestionResourceInput editorInput=resource -> {
                var local=originalResources.get(resource.id());return local==null?input.open(resource):local.open();
            };
            var result=practice==null ? CanvasEditorWindow.openEditor(getScene()==null?null:getScene().getWindow(),
                    "编辑作答",answer,resources(),editorInput) : CanvasEditorWindow.openEditor(getScene()==null?null:getScene().getWindow(),
                    "编辑作答",answer,resources(),editorInput,draft -> saveDraft(draft,originalResources));
            if(!result.saved())return;
            if(practice!=null){restore();error.setText("");return;}
            apply(result);
            feedback.setVisible(false);feedback.setManaged(false);error.setText("");refreshAnswer();
        }catch(RuntimeException failure){error.setText("作答编辑失败："+failure.getMessage());}
    }
    private void apply(ContentEditResult result){
        answer=result.content();result.addedResources().forEach(resource->imported.put(resource.resource().id(),resource));
        var used=QuestionContentData.resourceIds(answer);imported.keySet().removeIf(id->!used.contains(id));
    }
    private void saveDraft(ContentEditResult draft,Map<String,StagedContentResource> originalResources){
        var staged=new LinkedHashMap<>(originalResources);staged.putAll(imported);
        draft.addedResources().forEach(resource->staged.put(resource.resource().id(),resource));
        // Include the newly staged document in the session's resource catalogue.
        var catalogue=new ArrayList<>(existing);staged.values().forEach(resource->catalogue.add(resource.resource()));
        var session=new ContentEditSession(draft.content(),catalogue,resource -> {
            var added=staged.get(resource.id());return added==null?input.open(resource):added.open();
        });
        var value=new EssayPracticeAnswer(QuestionContentData.plainText(draft.content()),
                draft.content() instanceof DocumentContent document?session.document(document):null);
        if(!value.equals(persisted)){
            practice.saveEssayDraft(question.id(),value);persisted=value;changed.run();
        }
        apply(draft);
    }
    private void refreshAnswer(){
        boolean submitted=submitted();
        edit.setVisible(!submitted);edit.setManaged(!submitted);edit.setDisable(submitted);
        retry.setVisible(submitted);retry.setManaged(submitted);
        boolean empty=QuestionContentData.plainText(answer).isBlank() && QuestionContentData.resourceIds(answer).isEmpty();
        if(empty){
            String hint=question.essayPayload().placeholder();
            var label=UiTheme.label(hint==null || hint.isBlank()?"点击“编辑作答”，使用富文本编辑器完成作答。":hint,"essay-answer-empty");
            preview.getChildren().setAll(label);
        }else{
            var content=QuestionContentRenderer.render(answer,resources(),resourceInput(),"essay-response-");
            content.getStyleClass().remove("question-stem");content.getStyleClass().add("authoring-essay-text");
            preview.getChildren().setAll(content);
        }
        submit.setText("提交答案");submit.setDisable(empty);
    }
    private void submit(){
        try{
            if(practice!=null)practice.submit();
            locallySubmitted=true;showSubmission();changed.run();
        }catch(RuntimeException failure){error.setText("答案未提交："+failure.getMessage());}
    }
    private void showSubmission(){
        edit.setVisible(false);edit.setManaged(false);edit.setDisable(true);
        retry.setVisible(true);retry.setManaged(true);
        feedback.getChildren().setAll(UiTheme.label(practice==null?"已提交 · 本页暂存，未评分":"已提交 · 已保存，未评分","muted"));
        var reference=question.essayAnswerSpec().referenceAnswer();
        var analysis=question.analysis();
        if(reference!=null || analysis!=null){
            var session=new ContentEditSession(reference!=null?reference:analysis,existing,input);
            var combined=reference!=null && analysis!=null?session.combine(reference,analysis):reference!=null?reference:analysis;
            feedback.getChildren().add(UiTheme.label("参考答案与解析","essay-section-title"));
            feedback.getChildren().add(QuestionContentRenderer.render(combined,session.resources(),session::open,"essay-reference-"));
        }
        if(question.evaluationSpec()!=null && question.evaluationSpec().evaluatorGuidance()!=null){
            feedback.getChildren().add(UiTheme.label("评分细则","essay-section-title"));
            feedback.getChildren().add(UiTheme.label(question.evaluationSpec().evaluatorGuidance(),"authoring-essay-text"));
        }
        feedback.setVisible(true);feedback.setManaged(true);submit.setText("已提交");submit.setDisable(true);
    }

    private boolean submitted(){
        return practice==null?locallySubmitted:
                practice.questionState(question.id()).sessionQuestion().practiceState()==PracticeSessionQuestion.State.SUBMITTED;
    }
    private void retry(){
        try{
            if(practice!=null)practice.retry();
            else {locallySubmitted=false;answer=new TextContent("");imported.clear();}
            restore();error.setText("");changed.run();
        }catch(RuntimeException failure){error.setText("重新答题失败："+failure.getMessage());}
    }

    static javafx.scene.Node renderSavedAnswer(PracticePayload payload,String prefix){
        var value=EssayPracticeAnswer.from(payload);
        if(value.document()==null)return QuestionContentRenderer.render(new TextContent(value.text()),List.of(),QuestionResourceInput.NONE,prefix);
        var session=new ContentEditSession(new TextContent(""),List.of(),QuestionResourceInput.NONE);
        var content=session.stageDocument(value.document(),value.text());
        return QuestionContentRenderer.render(content,session.resources(),session::open,prefix);
    }
}
