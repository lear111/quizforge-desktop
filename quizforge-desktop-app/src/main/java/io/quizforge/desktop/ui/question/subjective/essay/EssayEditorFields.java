package io.quizforge.desktop.ui.question.subjective.essay;

import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.StagedContentResource;
import io.quizforge.desktop.ui.content.document.canvas.CanvasEditorWindow;
import io.quizforge.desktop.ui.content.document.canvas.ContentEditSession;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.shared.EditorUi;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Essay fields compose the content editor without controlling bank publication. */
public final class EssayEditorFields {
    private final QuestionEditorContext context;
    private final QuestionBankEditorModel model;
    private final int index;
    private final VBox body,errors;
    private final java.util.Map<String,StagedContentResource> imported;
    private final List<Runnable> validateFields;
    private EssayEditorFields(QuestionEditorContext context) {
        this.context=context;model=context.model();index=context.index();body=context.body();errors=context.errors();
        imported=context.imported();validateFields=context.validators();
    }
    public static void render(QuestionEditorContext context) {
        new EssayEditorFields(context).fields(context.model().bank().questions().get(context.index()),context.navigation(),context.spacer());
    }
    private io.quizforge.core.port.QuestionResourceInput resources(){return context.resources();}
    private TextArea area(String value,String id,int rows){return EditorUi.content(value,id,false);}
    private void showError(String message){errors.getChildren().setAll(UiTheme.label(message==null?"无法编辑内容":message,"editor-error"));}
    private void fields(Question question, HBox navigation, javafx.scene.layout.Region spacer) {
        TextField score=new TextField(question.scoreSpec().defaultMaxScore().toPlainString());score.setId("essay-max-score");score.setPrefColumnCount(5);
        Runnable commit=()->model.setMaxScore(index,new java.math.BigDecimal(score.getText().trim()));
        validateFields.add(commit);
        score.textProperty().addListener((o,a,b)->{
            try{commit.run();errors.getChildren().clear();}catch(RuntimeException error){showError("请填写有效的分值。");}
        });
        score.getStyleClass().add("essay-score-input");
        HBox scoreControl=new HBox(6,UiTheme.label("分值","editor-caption"),score);
        scoreControl.setAlignment(Pos.CENTER_LEFT);
        navigation.getChildren().add(navigation.getChildren().indexOf(spacer),scoreControl);
        final int questionIndex=index;
        var promptPreview=new VBox();promptPreview.setId("essay-prompt-preview");
        promptPreview.getStyleClass().add("essay-content-preview");
        Runnable refreshPreview=()->{
            promptPreview.getChildren().setAll(essayPreview(model.bank().questions().get(questionIndex).prompt(),"essay-edit-preview-"));
        };
        refreshPreview.run();
        Button editPrompt=UiTheme.iconButton("edit","编辑题干",()->{});editPrompt.setId("essay-edit-prompt");
        editPrompt.getStyleClass().add("essay-section-edit");
        editPrompt.setOnAction(event->editEssayContent("编辑题干",model.bank().questions().get(questionIndex).prompt(),
                content->model.setPrompt(questionIndex,content),refreshPreview));
        var promptTitle=UiTheme.label("题干","essay-section-title");
        var promptHeader=new HBox(12,promptTitle,editPrompt);promptHeader.setAlignment(Pos.CENTER_LEFT);
        var promptSection=new VBox(12,promptHeader,promptPreview);
        promptSection.getStyleClass().add("essay-content-section");

        var referencePreview=new VBox(14);referencePreview.setId("essay-reference-preview");
        referencePreview.getStyleClass().add("essay-content-preview");
        Runnable refreshReference=()->{
            var current=model.bank().questions().get(questionIndex);
            referencePreview.getChildren().clear();
            try {
                var session=new ContentEditSession(new TextContent(""),model.bank().resources(),resources());
                var combined=combinedReference(current,session);
                if(hasContent(combined)) {
                    var rendered=QuestionContentRenderer.render(combined,session.resources(),session::open,"essay-reference-preview-");
                    if(rendered instanceof Label label) {
                        label.getStyleClass().remove("question-stem");label.getStyleClass().add("authoring-essay-text");
                    }
                    referencePreview.getChildren().add(rendered);
                }
                else referencePreview.getChildren().add(UiTheme.label("暂无参考答案或解析","essay-preview-empty"));
            }catch(RuntimeException failure){
                referencePreview.getChildren().add(UiTheme.label("参考答案与解析无法显示","editor-error"));
                showError(failure.getMessage());
            }
        };
        refreshReference.run();
        Button editReference=UiTheme.iconButton("edit","编辑参考答案与解析",()->{});editReference.setId("essay-edit-reference");
        editReference.getStyleClass().add("essay-section-edit");
        editReference.setOnAction(event->{
            try {
                var session=new ContentEditSession(new TextContent(""),model.bank().resources(),resources());
                var combined=combinedReference(model.bank().questions().get(questionIndex),session);
                editEssayContent("编辑参考答案与解析",combined,content->{
                    model.setReferenceAnswer(questionIndex,optionalContent(content));
                    model.setAnalysis(questionIndex,(QuestionContent)null);
                },refreshReference,session);
            }catch(RuntimeException failure){showError(failure.getMessage());}
        });
        var referenceTitle=UiTheme.label("参考答案与解析","essay-section-title");
        var referenceHeader=new HBox(12,referenceTitle,editReference);referenceHeader.setAlignment(Pos.CENTER_LEFT);
        var referenceSection=new VBox(12,referenceHeader,referencePreview);
        referenceSection.getStyleClass().add("essay-content-section");

        TextArea guidance=area(question.evaluationSpec()==null?"":question.evaluationSpec().evaluatorGuidance(),"essay-evaluation-guidance",5);
        guidance.setPromptText("填写评分细则");guidance.getStyleClass().add("essay-guidance-input");
        guidance.textProperty().addListener((o,a,b)->model.setEvaluatorGuidance(questionIndex,b));
        var guidanceSection=new VBox(12,UiTheme.label("评分细则","essay-section-title"),guidance);
        guidanceSection.getStyleClass().add("essay-content-section");

        body.getChildren().addAll(promptSection,referenceSection,guidanceSection);
    }
    private javafx.scene.Node essayPreview(QuestionContent content,String prefix) {
        var node=QuestionContentRenderer.render(content,model.bank().resources(),resources(),prefix);
        if(node instanceof Label label) {
            label.getStyleClass().remove("question-stem");label.getStyleClass().add("authoring-essay-text");
        }
        return node;
    }
    private void editEssayContent(String title,QuestionContent content,Consumer<QuestionContent> apply,Runnable refresh) {
        editEssayContent(title,content,apply,refresh,null);
    }
    private void editEssayContent(String title,QuestionContent content,Consumer<QuestionContent> apply,Runnable refresh,ContentEditSession seed) {
        try {
            var result=CanvasEditorWindow.openEditor(context.owner().get(),title,content==null?new TextContent(""):content,
                    seed==null?model.bank().resources():seed.resources(),seed==null?resources():seed::open);
            if(result.saved()) {
                if(seed!=null && content!=null)for(var added:seed.save(content).addedResources())
                    if(QuestionContentData.resourceIds(result.content()).contains(added.resource().id())) {
                        model.addResource(added.resource());imported.put(added.resource().id(),added);
                    }
                for(var added:result.addedResources()){model.addResource(added.resource());imported.put(added.resource().id(),added);}
                apply.accept(result.content());refresh.run();errors.getChildren().clear();
            }
        }catch(RuntimeException failure){showError(failure.getMessage());}
    }
    private static QuestionContent combinedReference(Question question,ContentEditSession session) {
        var reference=question.essayAnswerSpec().referenceAnswer();var analysis=question.analysis();
        if(!hasContent(reference))return analysis;
        if(!hasContent(analysis))return reference;
        return session.combine(reference,analysis);
    }
    private static boolean hasContent(QuestionContent content){return content!=null && !(content instanceof TextContent text && text.text().isBlank());}
    private static QuestionContent optionalContent(QuestionContent content){return hasContent(content)?content:null;}

}
