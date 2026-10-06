package io.quizforge.desktop.ui.question.shared;

import io.quizforge.core.practice.PracticeSummary;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

/** Same score card for current practice and immutable archived rounds. */
public final class PracticeSummaryCard extends VBox {
    public PracticeSummaryCard(PracticeSummary summary,String headingText,String cardId,String idPrefix){
        Label score=UiTheme.label(PracticeScoreText.points(summary.score()),"practice-summary-score");score.setId(idPrefix+"-score");
        Label maximum=UiTheme.label("/ "+PracticeScoreText.points(summary.maxScore()),"practice-summary-max-score");maximum.setId(idPrefix+"-max-score");
        VBox points=new VBox(3,UiTheme.label("得分","muted"),score,maximum);points.setAlignment(Pos.CENTER);
        VBox legend=new VBox(14,status("正确",summary.correctCount(),"correct",idPrefix+"-correct-count"),
                status("错误",summary.incorrectCount(),"incorrect",idPrefix+"-incorrect-count"),
                status("未作答",summary.unfinishedCount(),"unanswered",idPrefix+"-unanswered-count"));
        legend.getStyleClass().add("practice-summary-status-list");
        if(summary.unscoredCount()>0)legend.getChildren().add(status("未评分",summary.unscoredCount(),"unanswered",idPrefix+"-unscored-count"));
        FlowPane results=new FlowPane(28,20,ring(summary,points),legend);results.getStyleClass().add("practice-summary-results");
        results.setMinWidth(0);results.setAlignment(Pos.CENTER);results.setColumnHalignment(HPos.CENTER);results.setRowValignment(VPos.CENTER);
        Label title=UiTheme.label(headingText,"practice-question-type");title.setWrapText(false);
        Label total=UiTheme.label("共 "+summary.totalCount()+" 题","muted");total.setId(idPrefix+"-total-count");total.setWrapText(false);
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox heading=new HBox(12,title,spacer,total);heading.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(heading,results);setId(cardId);getStyleClass().addAll("practice-question-card","practice-summary-card");setMinWidth(0);setMaxHeight(Region.USE_PREF_SIZE);
    }
    private static StackPane ring(PracticeSummary summary,VBox points){
        Pane segments=new Pane();segments.setMinSize(132,132);segments.setPrefSize(132,132);segments.setMaxSize(132,132);segments.getStyleClass().add("practice-summary-segments");
        Circle track=new Circle(66,66,52);track.getStyleClass().add("practice-summary-track");segments.getChildren().add(track);
        if(summary.totalCount()>0){double start=90;start=segment(segments,start,summary.correctCount(),summary.totalCount(),"correct");
            start=segment(segments,start,summary.incorrectCount(),summary.totalCount(),"incorrect");segment(segments,start,summary.unfinishedCount()+summary.unscoredCount(),summary.totalCount(),"unanswered");}
        StackPane ring=new StackPane(segments,points);ring.setMinSize(132,132);ring.setPrefSize(132,132);ring.setMaxSize(132,132);ring.getStyleClass().add("practice-summary-ring");return ring;
    }
    private static double segment(Pane segments,double start,int count,int total,String state){
        if(count<=0)return start;double length=360.0*count/total;
        Arc arc=new Arc(66,66,52,52,start,-length);arc.setType(ArcType.OPEN);arc.setFill(Color.TRANSPARENT);arc.setStrokeLineCap(StrokeLineCap.BUTT);
        arc.getStyleClass().addAll("practice-summary-segment",state);segments.getChildren().add(arc);return start-length;
    }
    private static HBox status(String label,int count,String state,String countId){
        Circle dot=new Circle(4);dot.getStyleClass().add("practice-summary-dot");
        Label name=UiTheme.label(label,"practice-summary-status-name"),value=UiTheme.label(Integer.toString(count),"practice-summary-status-count");value.setId(countId);
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox row=new HBox(9,dot,name,spacer,value);row.getStyleClass().addAll("practice-summary-status",state);row.setAlignment(Pos.CENTER_LEFT);return row;
    }
}
