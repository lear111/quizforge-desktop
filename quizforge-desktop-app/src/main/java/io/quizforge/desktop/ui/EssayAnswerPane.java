package io.quizforge.desktop.ui;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.*;
import io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter;
import java.util.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Page-local answer draft. This UI does not create an Attempt or imply automatic scoring. */
final class EssayAnswerPane extends VBox {
    private final Question question;
    private final List<QBankResource> existing;
    private final QuestionResourceInput input;
    private final Map<String,QBankImageImporter.ImportedImage> imported=new LinkedHashMap<>();
    private final VBox preview=new VBox(12);
    private final VBox feedback=new VBox(12);
    private final Label error=UiTheme.label("","incorrect");
    private final Button submit;
    private QuestionContent answer=new TextContent("");

    EssayAnswerPane(Question question,List<QBankResource> existing,QuestionResourceInput input){
        super(18);this.question=question;this.existing=List.copyOf(existing);this.input=input;
        setId("essay-answer-pane");setMinWidth(0);
        var edit=UiTheme.button("编辑作答","edit","",this::edit);
        edit.setId("essay-edit-answer");edit.getStyleClass().add("essay-answer-edit");
        var space=new Region();HBox.setHgrow(space,Priority.ALWAYS);
        var heading=new HBox(8,UiTheme.label("作答","essay-section-title"),space,edit);
        heading.setAlignment(Pos.CENTER_LEFT);
        var box=new VBox(14,heading,preview);box.setId("essay-answer-box");
        box.getStyleClass().add("essay-answer-box");box.setMinWidth(0);preview.setMinWidth(0);
        submit=UiTheme.button("提交答案","check","primary",this::submit);
        submit.setId("essay-submit-answer");submit.getStyleClass().add("essay-submit");
        var actions=new HBox(12,submit,error);actions.setAlignment(Pos.CENTER_LEFT);
        feedback.setId("essay-answer-feedback");feedback.setVisible(false);feedback.setManaged(false);
        getChildren().addAll(box,actions,feedback);refreshAnswer();
    }

    private List<QBankResource> resources(){
        var all=new ArrayList<>(existing);imported.values().forEach(image->all.add(image.resource()));return all;
    }
    private QuestionResourceInput resourceInput(){
        return resource->{var image=imported.get(resource.id());return image==null?input.open(resource):image.open();};
    }
    private void edit(){
        try{
            var result=CanvasEditorWindow.openEditor(getScene()==null?null:getScene().getWindow(),
                    "编辑作答",answer,resources(),resourceInput());
            if(!result.saved())return;
            answer=result.content();result.addedResources().forEach(image->imported.put(image.resource().id(),image));
            var used=QuestionContentData.imageIds(answer);imported.keySet().removeIf(id->!used.contains(id));
            feedback.setVisible(false);feedback.setManaged(false);error.setText("");refreshAnswer();
        }catch(RuntimeException failure){error.setText("作答编辑失败："+failure.getMessage());}
    }
    private void refreshAnswer(){
        boolean empty=QuestionContentData.plainText(answer).isBlank() && QuestionContentData.imageIds(answer).isEmpty();
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
        feedback.getChildren().setAll(UiTheme.label("已提交 · 本页暂存，未评分","muted"));
        var reference=question.essayAnswerSpec().referenceAnswer();
        if(reference!=null){
            feedback.getChildren().add(UiTheme.label("参考答案","essay-section-title"));
            feedback.getChildren().add(QuestionContentRenderer.render(reference,existing,input,"essay-reference-"));
        }
        if(question.analysis()!=null){
            feedback.getChildren().add(UiTheme.label("解析","essay-section-title"));
            feedback.getChildren().add(QuestionContentRenderer.render(question.analysis(),existing,input,"essay-analysis-"));
        }
        if(question.evaluationSpec()!=null && question.evaluationSpec().evaluatorGuidance()!=null){
            feedback.getChildren().add(UiTheme.label("评分细则","essay-section-title"));
            feedback.getChildren().add(UiTheme.label(question.evaluationSpec().evaluatorGuidance(),"authoring-essay-text"));
        }
        feedback.setVisible(true);feedback.setManaged(true);submit.setText("已提交");submit.setDisable(true);
    }
}
