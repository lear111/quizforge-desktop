package io.quizforge.desktop.ui.question.objective.cloze;

import io.quizforge.core.question.content.*;
import io.quizforge.core.question.type.objective.cloze.*;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.document.canvas.CanvasEditorWindow;
import io.quizforge.desktop.ui.content.document.canvas.CanvasDocumentView;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.shared.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Authoring fields reuse the native content editor and the bank edit transaction. */
public final class ClozeEditorFields {
    private ClozeEditorFields(){}
    public static void render(QuestionEditorContext c){
        var q=c.model().bank().questions().get(c.index());
        var score=new TextField(q.scoreSpec().defaultMaxScore().toPlainString());score.setId("cloze-max-score");score.setPrefColumnCount(5);
        Runnable validateScore=()->c.model().setMaxScore(c.index(),new java.math.BigDecimal(score.getText().trim()));c.validators().add(validateScore);
        score.textProperty().addListener((o,a,b)->{try{validateScore.run();}catch(RuntimeException ignored){}});
        var scoreRow=new HBox(6,UiTheme.label("单题分值","editor-caption"),score);scoreRow.setAlignment(Pos.CENTER_LEFT);
        c.navigation().getChildren().add(c.navigation().getChildren().indexOf(c.spacer()),scoreRow);
        var edit=UiTheme.iconButton("edit","编辑正文",()->edit(c,true));edit.setId("cloze-edit-prompt");
        var header=new HBox(12,UiTheme.label("正文","essay-section-title"),edit);header.setAlignment(Pos.CENTER_LEFT);
        var radios=new java.util.LinkedHashMap<String,RadioButton>();
        var preview=new java.util.concurrent.atomic.AtomicReference<CanvasDocumentView>();
        Runnable update=()->{
            var current=c.model().bank().questions().get(c.index());
            var answers=java.util.Set.copyOf(((ClozeAnswerSpec)current.answerSpec()).correctOptionIds());
            radios.forEach((id,radio)->radio.setSelected(answers.contains(id)));
            preview.get().updateCloze(ClozeQuestionCardView.configuration((ClozePayload)current.payload(),answers,answers,false,false));
        };
        var correct=new java.util.HashSet<>(((ClozeAnswerSpec)q.answerSpec()).correctOptionIds());
        var payload=(ClozePayload)q.payload();
        preview.set(new CanvasDocumentView(q.prompt(),c.model().bank().resources(),c.resources(),"cloze-edit-preview-",
                ClozeQuestionCardView.configuration(payload,correct,correct,false,false),(number,option)->{
                    var blank=payload.blanks().stream().filter(b->b.number()==number).findFirst().orElseThrow();
                    c.model().setClozeCorrect(c.index(),blank.id(),option);update.run();
                }));
        c.body().getChildren().addAll(header,UiTheme.label("用 {{1}}、{{2}} 标记空位；点击正文空位或下方选项设置正确答案。","muted"),preview.get());
        var options=ClozeQuestionCardView.optionGrid();
        for(int row=0;row<payload.blanks().size();row++){
            var blank=payload.blanks().get(row);
            var number=UiTheme.label(blank.number()+".","essay-section-title");number.setWrapText(false);number.setMinWidth(Region.USE_PREF_SIZE);number.setId("cloze-edit-blank-"+blank.number());options.add(number,0,row);
            var group=new ToggleGroup();
            for(int i=0;i<blank.options().size();i++){
                final int optionIndex=i;var option=blank.options().get(i);
                var radio=new RadioButton((char)('A'+i)+".");radio.setToggleGroup(group);radio.setSelected(correct.contains(option.id()));radios.put(option.id(),radio);
                radio.setId("cloze-correct-"+blank.number()+"-"+i);
                radio.setOnAction(e->{c.model().setClozeCorrect(c.index(),blank.id(),option.id());update.run();});
                var field=new TextField(QuestionContentData.plainText(option.content()));field.setId("cloze-option-"+blank.number()+"-"+i);field.setMinWidth(0);field.setPrefColumnCount(5);HBox.setHgrow(field,Priority.ALWAYS);
                field.textProperty().addListener((o,a,b)->{c.model().setClozeOption(c.index(),blank.id(),optionIndex,b);update.run();});
                var cell=new HBox(4,radio,field);cell.setMinWidth(0);cell.setAlignment(Pos.CENTER_LEFT);options.add(cell,i+1,row);
            }
        }
        var add=UiTheme.button("新增小题","plus","",()->{c.model().addClozeBlank(c.index());c.refresh().run();});add.setId("cloze-add-blank");
        c.body().getChildren().addAll(options,add);
        var analysisHeader=new HBox(12,UiTheme.label("答案解析","essay-section-title"),UiTheme.iconButton("edit","编辑答案解析",()->edit(c,false)));
        analysisHeader.setAlignment(Pos.CENTER_LEFT);c.body().getChildren().add(analysisHeader);
        if(q.analysis()!=null)c.body().getChildren().add(QuestionContentRenderer.render(q.analysis(),c.model().bank().resources(),c.resources(),"cloze-analysis-preview-"));
    }
    private static void edit(QuestionEditorContext c,boolean prompt){
        try{
            var q=c.model().bank().questions().get(c.index());var content=prompt?q.prompt():q.analysis();
            var result=CanvasEditorWindow.openValidatedEditor(c.owner().get(),prompt?"编辑完形填空正文":"编辑答案解析",content==null?new TextContent(""):content,c.model().bank().resources(),c.resources(),
                    value->{if(prompt)ClozeQuestionType.validateNewNumbers(ClozeQuestionType.numbers(QuestionContentData.plainText(value)),((ClozePayload)q.payload()).blanks().size());});
            if(!result.saved())return;
            for(var added:result.addedResources()){
                if(c.model().bank().resources().stream().noneMatch(r->r.id().equals(added.resource().id())))c.model().addResource(added.resource());
                c.imported().put(added.resource().id(),added);
            }
            if(prompt)c.model().setPrompt(c.index(),result.content());else c.model().setAnalysis(c.index(),result.content());
            c.refresh().run();
        }catch(RuntimeException error){c.errors().getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));}
    }
}
