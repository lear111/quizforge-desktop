package io.quizforge.desktop.ui.question.objective.reading;

import io.quizforge.core.question.content.*;
import io.quizforge.core.question.type.objective.reading.*;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.document.canvas.CanvasEditorWindow;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Reading-specific fields use the existing content editor and bank save transaction. */
public final class ReadingEditorFields {
    private ReadingEditorFields() { }

    public static void render(QuestionEditorContext c) {
        var question=c.model().bank().questions().get(c.index());
        var score=new TextField(question.scoreSpec().defaultMaxScore().toPlainString());
        score.setId("reading-max-score");score.setPrefColumnCount(5);
        Runnable validateScore=()->c.model().setMaxScore(c.index(),new java.math.BigDecimal(score.getText().trim()));
        c.validators().add(validateScore);
        score.textProperty().addListener((o,a,b)->{try{validateScore.run();}catch(RuntimeException ignored){}});
        var scoreRow=new HBox(6,UiTheme.label("单题分值","editor-caption"),score);scoreRow.setAlignment(Pos.CENTER_LEFT);
        c.navigation().getChildren().add(c.navigation().getChildren().indexOf(c.spacer()),scoreRow);
        var articleEdit=UiTheme.iconButton("edit","编辑阅读正文",()->edit(c,null,false));articleEdit.setId("reading-edit-prompt");
        c.body().getChildren().add(header("正文",articleEdit));
        c.body().getChildren().add(QuestionContentRenderer.render(question.prompt(),c.model().bank().resources(),c.resources(),"reading-edit-article-"));
        var payload=(ReadingPayload)question.payload();
        var correct=java.util.Set.copyOf(((ReadingAnswerSpec)question.answerSpec()).correctOptionIds());
        for(var item:payload.items()) {
            var itemEdit=UiTheme.iconButton("edit","编辑第 "+item.number()+" 题题干",()->edit(c,item.id(),false));
            itemEdit.setId("reading-edit-item-"+item.number());
            var remove=UiTheme.iconButton("trash","删除小题",()->remove(c,item.id()));
            remove.setId("reading-delete-item-"+item.number());remove.setDisable(payload.items().size()==1);
            var title=header(item.number()+".",itemEdit,remove);title.setId("reading-edit-question-"+item.number());
            c.body().getChildren().addAll(title,QuestionContentRenderer.render(item.prompt(),c.model().bank().resources(),c.resources(),"reading-edit-item-"+item.number()+"-"));
            var options=ReadingQuestionCardView.optionGrid();var group=new ToggleGroup();
            for(int i=0;i<4;i++) {
                int optionIndex=i;var option=item.options().get(i);
                var radio=new RadioButton((char)('A'+i)+".");radio.setToggleGroup(group);radio.setSelected(correct.contains(option.id()));
                radio.setId("reading-correct-"+item.number()+"-"+i);
                radio.setOnAction(e->c.model().setReadingCorrect(c.index(),item.id(),option.id()));
                var field=new TextField(QuestionContentData.plainText(option.content()));field.setId("reading-option-"+item.number()+"-"+i);
                field.setMinWidth(0);field.setPrefColumnCount(5);HBox.setHgrow(field,Priority.ALWAYS);
                field.textProperty().addListener((o,a,b)->{c.model().setReadingOption(c.index(),item.id(),optionIndex,b);options.requestLayout();});
                var cell=new HBox(4,radio,field);cell.setMinWidth(0);cell.setAlignment(Pos.CENTER_LEFT);options.add(cell,i,0);
            }
            c.body().getChildren().add(options);
        }
        var add=UiTheme.button("新增小题","plus","",()->{c.model().addReadingItem(c.index());c.refresh().run();});add.setId("reading-add-item");
        var analysisEdit=UiTheme.iconButton("edit","编辑答案与解析",()->edit(c,null,true));analysisEdit.setId("reading-edit-analysis");
        c.body().getChildren().addAll(add,header("答案与解析",analysisEdit));
        if(question.analysis()!=null)c.body().getChildren().add(QuestionContentRenderer.render(question.analysis(),c.model().bank().resources(),c.resources(),"reading-edit-analysis-"));
    }

    private static HBox header(String title,javafx.scene.Node... actions) {
        var row=new HBox(12,UiTheme.label(title,"essay-section-title"));row.getChildren().addAll(actions);row.setAlignment(Pos.CENTER_LEFT);return row;
    }

    private static void remove(QuestionEditorContext c,String itemId) {
        var dialog=new Alert(Alert.AlertType.CONFIRMATION,"确定删除这道小题吗？",ButtonType.CANCEL,ButtonType.OK);
        dialog.setTitle("删除小题");dialog.setHeaderText("删除后，后续小题会重新编号");
        if(c.owner().get()!=null)dialog.initOwner(c.owner().get());UiTheme.apply(dialog);
        if(dialog.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        c.model().deleteReadingItem(c.index(),itemId);c.refresh().run();
    }

    private static void edit(QuestionEditorContext c,String itemId,boolean analysis) {
        try {
            var question=c.model().bank().questions().get(c.index());
            var content=analysis?question.analysis():itemId==null?question.prompt():((ReadingPayload)question.payload()).items().stream().filter(item->item.id().equals(itemId)).findFirst().orElseThrow().prompt();
            var result=CanvasEditorWindow.openValidatedEditor(c.owner().get(),analysis?"编辑答案与解析":itemId==null?"编辑阅读正文":"编辑小题题干",content==null?new TextContent(""):content,c.model().bank().resources(),c.resources(),value->{});
            if(!result.saved())return;
            for(var added:result.addedResources()) {
                if(c.model().bank().resources().stream().noneMatch(resource->resource.id().equals(added.resource().id())))c.model().addResource(added.resource());
                c.imported().put(added.resource().id(),added);
            }
            if(analysis)c.model().setAnalysis(c.index(),result.content());
            else if(itemId==null)c.model().setPrompt(c.index(),result.content());
            else c.model().setReadingPrompt(c.index(),itemId,result.content());
            c.refresh().run();
        }catch(RuntimeException error){c.errors().getChildren().setAll(UiTheme.label(error.getMessage(),"editor-error"));}
    }
}
