package io.quizforge.desktop.ui.question.subjective.translation;

import io.quizforge.core.question.content.*;
import io.quizforge.core.question.type.subjective.translation.*;
import io.quizforge.desktop.ui.content.ContentEditResult;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.document.canvas.CanvasDocumentView;
import io.quizforge.desktop.ui.content.document.canvas.CanvasEditorWindow;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.geometry.Pos;
import javafx.scene.layout.*;
import javafx.scene.control.TextField;

/** Sentence markers use the shared article editor; each generated item owns its reference. */
public final class TranslationEditorFields {
    private TranslationEditorFields() { }
    public static void render(QuestionEditorContext c) {
        var q=c.model().bank().questions().get(c.index());
        var score=new TextField(q.scoreSpec().defaultMaxScore().toPlainString());score.setId("translation-max-score");score.setPrefColumnCount(5);
        Runnable validate=()->c.model().setMaxScore(c.index(),new java.math.BigDecimal(score.getText().trim()));c.validators().add(validate);
        score.textProperty().addListener((o,a,b)->{try{validate.run();}catch(RuntimeException ignored){}});
        var scoreRow=new HBox(6,UiTheme.label("单题分值","editor-caption"),score);scoreRow.setAlignment(Pos.CENTER_LEFT);
        c.navigation().getChildren().add(c.navigation().getChildren().indexOf(c.spacer()),scoreRow);
        var edit=UiTheme.iconButton("edit","编辑翻译文章",()->edit(c,null,true));edit.setId("translation-edit-prompt");
        c.body().getChildren().addAll(header("正文",edit),
                UiTheme.label("用 {{需要翻译的句子}} 标记，无需写序号；按正文顺序自动编号并加下划线。","muted"),
                CanvasDocumentView.translation(q.prompt(),c.model().bank().resources(),c.resources(),"translation-edit-prompt-"),header("参考译文"));
        var references=((TranslationAnswerSpec)q.answerSpec()).referenceAnswers();
        for(var item:((TranslationPayload)q.payload()).items()) {
            var editReference=UiTheme.iconButton("edit","编辑第 "+item.number()+" 句参考译文",()->edit(c,item.id(),false));
            editReference.setId("translation-edit-reference-"+item.number());
            var section=new VBox(10,header(item.number()+". "+item.text(),editReference));section.setId("translation-edit-item-"+item.number());
            var reference=references.get(item.id());
            if(reference!=null)section.getChildren().add(QuestionContentRenderer.render(reference,c.model().bank().resources(),c.resources(),"translation-reference-"+item.number()+"-"));
            c.body().getChildren().add(section);
        }
        var analysis=UiTheme.iconButton("edit","编辑答案解析",()->edit(c,null,false));analysis.setId("translation-edit-analysis");
        c.body().getChildren().add(header("答案解析",analysis));
        if(q.analysis()!=null)c.body().getChildren().add(QuestionContentRenderer.render(q.analysis(),c.model().bank().resources(),c.resources(),"translation-edit-analysis-"));
    }
    private static HBox header(String title,javafx.scene.Node... actions) {
        var label=UiTheme.label(title,"essay-section-title");HBox.setHgrow(label,Priority.ALWAYS);
        var row=new HBox(12,label);row.getChildren().addAll(actions);row.setAlignment(Pos.CENTER_LEFT);return row;
    }
    private static void edit(QuestionEditorContext c,String itemId,boolean prompt) {
        try {
            var q=c.model().bank().questions().get(c.index());
            var content=prompt?q.prompt():itemId==null?q.analysis():((TranslationAnswerSpec)q.answerSpec()).referenceAnswers().get(itemId);
            var result=CanvasEditorWindow.openValidatedEditor(c.owner().get(),prompt?"编辑翻译文章":itemId==null?"编辑答案解析":"编辑参考译文",
                    content==null?new TextContent(""):content,c.model().bank().resources(),c.resources(),value->{
                        if(prompt && TranslationQuestionType.sentences(QuestionContentData.plainText(value)).isEmpty())
                            throw new IllegalArgumentException("请至少用 {{句子}} 标记一处需要翻译的内容");
                    });
            if(!result.saved())return;importResources(c,result);
            if(prompt)c.model().setPrompt(c.index(),result.content());
            else if(itemId==null)c.model().setAnalysis(c.index(),result.content());
            else c.model().setTranslationReference(c.index(),itemId,result.content());
            c.refresh().run();
        }catch(RuntimeException error){c.errors().getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));}
    }
    private static void importResources(QuestionEditorContext c,ContentEditResult result) {
        for(var added:result.addedResources()) {
            if(c.model().bank().resources().stream().noneMatch(r->r.id().equals(added.resource().id())))c.model().addResource(added.resource());
            c.imported().put(added.resource().id(),added);
        }
    }
}
