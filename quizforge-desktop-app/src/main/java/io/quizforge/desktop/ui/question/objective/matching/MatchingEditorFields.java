package io.quizforge.desktop.ui.question.objective.matching;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.type.objective.matching.*;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.document.canvas.CanvasEditorWindow;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** One ordinary prompt and positional correct answers with optional locked hints. */
public final class MatchingEditorFields {
    private MatchingEditorFields() { }
    public static void render(QuestionEditorContext c) {
        var q=c.model().bank().questions().get(c.index());
        var score=new TextField(q.scoreSpec().defaultMaxScore().toPlainString());score.setId("matching-max-score");score.setPrefColumnCount(5);
        Runnable validate=()->c.model().setMaxScore(c.index(),new java.math.BigDecimal(score.getText().trim()));c.validators().add(validate);
        score.textProperty().addListener((o,a,b)->{try{validate.run();}catch(RuntimeException ignored){}});
        var scoreRow=new HBox(6,UiTheme.label("单题分值","editor-caption"),score);scoreRow.setAlignment(Pos.CENTER_LEFT);
        c.navigation().getChildren().add(c.navigation().getChildren().indexOf(c.spacer()),scoreRow);
        var edit=UiTheme.iconButton("edit","编辑题干",()->edit(c,false));edit.setId("matching-edit-prompt");
        c.body().getChildren().addAll(header("题干",edit),
                QuestionContentRenderer.render(q.prompt(),c.model().bank().resources(),c.resources(),"matching-edit-prompt-"),
                header("正确答案"),UiTheme.label("将 A–H 排序，锁定三个已给出的提示；其余五个位置计分。更换提示位置时先解锁。","muted"));
        var hintStatus=UiTheme.label("", "muted");hintStatus.setId("matching-edit-hint-status");c.body().getChildren().add(hintStatus);
        var slots=new FlowPane(8,10);c.body().getChildren().add(slots);
        var update=new java.util.concurrent.atomic.AtomicReference<Runnable>();
        update.set(()->{
            var current=c.model().bank().questions().get(c.index());var correct=((MatchingAnswerSpec)current.answerSpec()).assignments();
            int graded=MatchingQuestionType.gradableCount(current);
            hintStatus.setText("已给出："+(((MatchingPayload)current.payload()).blanks().size()-graded)+" / 3 · 待答："+graded);
            slots.getChildren().setAll(MatchingQuestionCardView.answerSlots((MatchingPayload)current.payload(),correct,correct,false,false,true,"matching-edit-",
                    (blank,option)->change(c,()->c.model().setMatchingCorrect(c.index(),blank,option),update.get()),
                    blank->change(c,()->{
                        var payload=(MatchingPayload)c.model().bank().questions().get(c.index()).payload();
                        boolean locked=payload.blanks().stream().filter(b->b.id().equals(blank)).findFirst().orElseThrow().locked();
                        c.model().setMatchingLocked(c.index(),blank,!locked);
                    },update.get())).getChildren());
        });update.get().run();
        var editAnalysis=UiTheme.iconButton("edit","编辑答案与解析",()->edit(c,true));editAnalysis.setId("matching-edit-analysis");
        c.body().getChildren().add(header("答案与解析",editAnalysis));
        if(q.analysis()!=null)c.body().getChildren().add(QuestionContentRenderer.render(q.analysis(),c.model().bank().resources(),c.resources(),"matching-edit-analysis-"));
    }
    private static HBox header(String title,javafx.scene.Node... actions){var row=new HBox(12,UiTheme.label(title,"essay-section-title"));row.getChildren().addAll(actions);row.setAlignment(Pos.CENTER_LEFT);return row;}
    private static void change(QuestionEditorContext c,Runnable action,Runnable update){
        try{action.run();update.run();c.errors().getChildren().clear();c.refresh().run();}
        catch(RuntimeException error){c.errors().getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));}
    }
    private static void edit(QuestionEditorContext c,boolean analysis) {
        try {
            var q=c.model().bank().questions().get(c.index());var content=analysis?q.analysis():q.prompt();
            var result=CanvasEditorWindow.openValidatedEditor(c.owner().get(),analysis?"编辑答案与解析":"编辑题干",content==null?new TextContent(""):content,
                    c.model().bank().resources(),c.resources(),value->{});
            if(!result.saved())return;
            for(var added:result.addedResources()){
                if(c.model().bank().resources().stream().noneMatch(r->r.id().equals(added.resource().id())))c.model().addResource(added.resource());
                c.imported().put(added.resource().id(),added);
            }
            if(analysis)c.model().setAnalysis(c.index(),result.content());else c.model().setPrompt(c.index(),result.content());c.refresh().run();
        }catch(RuntimeException error){c.errors().getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));}
    }
}
