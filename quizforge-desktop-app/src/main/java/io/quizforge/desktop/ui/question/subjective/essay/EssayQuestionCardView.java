package io.quizforge.desktop.ui.question.subjective.essay;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.document.canvas.ContentEditSession;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.math.BigDecimal;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.layout.*;

/** Shared essay heading, rich prompt and reference rendering for practice and history. */
public final class EssayQuestionCardView {
    private EssayQuestionCardView(){}
    public static VBox card(QuestionContent prompt,BigDecimal score,int index,int total,String prefix,
            List<QBankResource> resources,QuestionResourceInput input){
        var card=new VBox(16);card.setId(prefix+"question-card");card.getStyleClass().addAll("question-card","essay-answer-card");
        var metadata=UiTheme.label(score==null?"分值：未记录":"分值："+score.stripTrailingZeros().toPlainString(),"muted");
        metadata.setId(prefix+"essay-metadata");
        var type=new VBox(8,UiTheme.label("作文题","question-type-badge"),metadata);
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var heading=new HBox(12,type,spacer,UiTheme.label("第 "+(index+1)+" / "+total+" 题","question-progress"));
        heading.setAlignment(Pos.BOTTOM_LEFT);card.getChildren().add(heading);
        var content=QuestionContentRenderer.render(prompt,resources,input,prefix+"prompt-");
        if(prompt instanceof TextContent){content.getStyleClass().remove("question-stem");content.getStyleClass().add("authoring-essay-text");}
        card.getChildren().add(content);return card;
    }
    public static VBox reference(QuestionContent reference,QuestionContent analysis,List<QBankResource> resources,
            QuestionResourceInput input,String prefix){
        var result=new VBox(12);
        if(reference==null && analysis==null)return result;
        var session=new ContentEditSession(reference!=null?reference:analysis,resources,input);
        var combined=reference!=null && analysis!=null?session.combine(reference,analysis):reference!=null?reference:analysis;
        result.getChildren().addAll(UiTheme.label("参考答案与解析","essay-section-title"),
                QuestionContentRenderer.render(combined,session.resources(),session::open,prefix));
        return result;
    }
}
