package io.quizforge.desktop.ui.content;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.BlockNode;
import io.quizforge.core.question.content.BlockQuoteNode;
import io.quizforge.core.question.content.BulletListNode;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.HeadingNode;
import io.quizforge.core.question.content.InlineImageNode;
import io.quizforge.core.question.content.InlineNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.LineBreakNode;
import io.quizforge.core.question.content.LinkNode;
import io.quizforge.core.question.content.ListItemNode;
import io.quizforge.core.question.content.OrderedListNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.TextAlignment;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.content.TextMark;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.desktop.ui.content.document.canvas.CanvasDocumentView;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter;
import java.io.*;
import java.util.*;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** Structured JavaFX preview using the same content geometry as the editor canvas. */
public final class QuestionContentRenderer {
    private QuestionContentRenderer() { }

    public static Node render(QuestionContent content,List<QBankResource> resources,QuestionResourceInput input,String prefix) {
        if(content instanceof DocumentContent document) return new CanvasDocumentView(document,resources,input,prefix);
        if(content instanceof TextContent text) {
            Label plain=label(text.text(),"question-stem");
            plain.setStyle(QuestionContentLayout.previewStyle());
            plain.setMaxWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
            return plain;
        }
        VBox result=new VBox(QuestionContentLayout.PARAGRAPH_SPACING);
        result.setId(prefix+"rich-content");
        result.setMaxWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        result.setMinWidth(0);
        if(content==null)return result;
        Map<String,QBankResource> byId=new HashMap<>();resources.forEach(r->byId.put(r.id(),r));
        for(BlockNode block:((RichContent)content).document().blocks())
            result.getChildren().add(block(block,byId,input,prefix));
        return result;
    }

    private static Node block(BlockNode block,Map<String,QBankResource> resources,QuestionResourceInput input,String prefix) {
        return switch(block) {
            case ParagraphNode p -> paragraph(p.children(),p.alignment(),0,resources,input,prefix);
            case HeadingNode h -> paragraph(h.children(),h.alignment(),h.level(),resources,input,prefix);
            case BulletListNode list -> list(list.items(),false,1,resources,input,prefix);
            case OrderedListNode list -> list(list.items(),true,list.start(),resources,input,prefix);
            case BlockQuoteNode quote -> {
                VBox body=new VBox(QuestionContentLayout.PARAGRAPH_SPACING);
                quote.blocks().forEach(child->body.getChildren().add(block(child,resources,input,prefix)));
                body.setStyle("-fx-border-color:#b9aec9;-fx-border-width:0 0 0 3;-fx-padding:2 0 2 16;");
                yield body;
            }
            case BlockImageNode picture -> {
                VBox body=new VBox(4,image(resources.get(picture.resourceId()),input,picture.alt(),prefix+picture.resourceId(),
                        picture.widthPercent(),picture.alignment()));
                if(picture.caption()!=null)body.getChildren().add(label(picture.caption(),"muted"));
                yield body;
            }
            case BlockMathNode ignored -> label("[此公式暂不支持预览]","muted");
        };
    }

    private static Node list(List<ListItemNode> items,boolean numbered,int start,Map<String,QBankResource> resources,
            QuestionResourceInput input,String prefix) {
        VBox rows=new VBox(4);
        for(int i=0;i<items.size();i++) {
            Label marker=label(numbered?(start+i)+".":"•","question-list-marker");marker.setMinWidth(24);
            VBox body=new VBox(4);items.get(i).blocks().forEach(child->body.getChildren().add(block(child,resources,input,prefix)));
            HBox row=new HBox(4,marker,body);row.setAlignment(Pos.TOP_LEFT);HBox.setHgrow(body,Priority.ALWAYS);
            rows.getChildren().add(row);
        }
        rows.setStyle(QuestionContentLayout.previewStyle());return rows;
    }

    private static TextFlow paragraph(List<InlineNode> nodes,TextAlignment alignment,int level,
            Map<String,QBankResource> resources,QuestionResourceInput input,String prefix) {
        TextFlow flow=new TextFlow();flow.getStyleClass().add("question-rich-paragraph");
        flow.setStyle(QuestionContentLayout.previewStyle());
        flow.setTextAlignment(alignment==TextAlignment.CENTER?javafx.scene.text.TextAlignment.CENTER:
                alignment==TextAlignment.RIGHT?javafx.scene.text.TextAlignment.RIGHT:javafx.scene.text.TextAlignment.LEFT);
        if(level>0)flow.getStyleClass().add("question-rich-heading-"+level);
        append(nodes,flow,resources,input,prefix,false,level);
        return flow;
    }

    private static void append(List<InlineNode> nodes,TextFlow flow,Map<String,QBankResource> resources,
            QuestionResourceInput input,String prefix,boolean linked,int level) {
        for(InlineNode node:nodes) {
            if(node instanceof InlineTextNode t) {
                Text text=new Text(t.text());StringBuilder style=new StringBuilder();
                if(level>0 || t.marks().contains(TextMark.BOLD))style.append("-fx-font-weight:bold;");
                if(t.marks().contains(TextMark.ITALIC))style.append("-fx-font-style:italic;");
                if(t.marks().contains(TextMark.UNDERLINE) || linked)text.setUnderline(true);
                if(t.marks().contains(TextMark.STRIKE))text.setStrikethrough(true);
                if(linked)style.append("-fx-fill:#715b99;");
                if(level>0)style.append("-fx-font-size:").append(switch(level){case 1->25;case 2->21;default->18;}).append("px;");
                if(!style.isEmpty())text.setStyle(style.toString());flow.getChildren().add(text);
            } else if(node instanceof LineBreakNode)flow.getChildren().add(new Text("\n"));
            else if(node instanceof InlineImageNode picture)flow.getChildren().add(image(resources.get(picture.resourceId()),input,picture.alt(),prefix+picture.resourceId()));
            else if(node instanceof LinkNode link)append(link.children(),flow,resources,input,prefix,true,level);
            else flow.getChildren().add(new Text("[公式]"));
        }
    }

    public static Node image(QBankResource resource,QuestionResourceInput input,String alt,String id) {
        return image(resource,input,alt,id,null,null);
    }
    private static Node image(QBankResource resource,QuestionResourceInput input,String alt,String id,
            Integer widthPercent,TextAlignment alignment) {
        try {
            if(resource==null || resource.kind()!=ResourceKind.IMAGE)throw new IOException("Resource metadata missing");
            try(var stream=input.open(resource)) {
                if(stream==null)throw new IOException("Resource bytes missing");
                byte[] bytes=stream.readNBytes((int)QBankImageImporter.MAX_BYTES+1);
                if(bytes.length>QBankImageImporter.MAX_BYTES)throw new IOException("Image exceeds preview limit");
                Image loaded=new Image(new ByteArrayInputStream(bytes),QuestionContentLayout.QUESTION_CONTENT_WIDTH,
                        QuestionContentLayout.QUESTION_CONTENT_WIDTH,true,true);
                if(loaded.isError() || loaded.getWidth()==0)throw new IOException("Image decoding failed");
                ImageView view=new ImageView(loaded);view.setPreserveRatio(true);view.setSmooth(true);
                StackPane holder=new StackPane(view);holder.setId(id);holder.setMinWidth(0);
                holder.setMaxWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);holder.getStyleClass().add("question-image");
                double ratio=(widthPercent==null?100:widthPercent)/100d;
                view.fitWidthProperty().bind(Bindings.max(1,holder.widthProperty().multiply(ratio)));
                holder.setAlignment(alignment==TextAlignment.CENTER?Pos.CENTER:
                        alignment==TextAlignment.RIGHT?Pos.CENTER_RIGHT:Pos.CENTER_LEFT);
                holder.setAccessibleText(alt==null?"题干图片":alt);return holder;
            }
        }catch(IOException | RuntimeException failure) {
            Label missing=label("图片资源缺失或无法读取"+(alt==null?"":" · "+alt),"missing-resource");
            missing.setId(id+"-missing");return missing;
        }
    }
    private static Label label(String value,String style) {
        Label label=UiTheme.label(value,style);label.setWrapText(true);label.setMaxWidth(Double.MAX_VALUE);return label;
    }
}
