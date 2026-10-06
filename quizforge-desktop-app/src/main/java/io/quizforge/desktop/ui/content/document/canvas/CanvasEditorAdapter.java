package io.quizforge.desktop.ui.content.document.canvas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.BlockNode;
import io.quizforge.core.question.content.BulletListNode;
import io.quizforge.core.question.content.HeadingNode;
import io.quizforge.core.question.content.InlineImageNode;
import io.quizforge.core.question.content.InlineMathNode;
import io.quizforge.core.question.content.InlineNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.LineBreakNode;
import io.quizforge.core.question.content.LinkNode;
import io.quizforge.core.question.content.ListItemNode;
import io.quizforge.core.question.content.OrderedListNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentNormalizer;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextAlignment;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.content.TextMark;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.desktop.ui.content.QuestionContentLayout;
import java.util.*;

/** Maps the supported Canvas Editor element subset to portable QBank content. */
final class CanvasEditorAdapter {
    private static final ObjectMapper JSON = io.quizforge.infrastructure.json.DocumentJson.mapper();
    record ImageData(String dataUrl, int width, int height) { }
    private CanvasEditorAdapter() { }

    static String toCanvasJson(QuestionContent content, Map<String,ImageData> images) {
        var main = new ArrayList<Object>();
        if(content instanceof TextContent text) main.add(Map.of("value",text.text()));
        else for(var block:((RichContent)content).document().blocks()) encodeBlock(block,main,images);
        return ContentJson.write(Map.of("main",main));
    }
    static String imageJson(QBankResource resource,Map<String,ImageData> images) {
        return ContentJson.write(image(resource.id(),null,null,null,null,false,images));
    }

    private static void encodeBlock(BlockNode block,List<Object> out,Map<String,ImageData> images) {
        if(block instanceof ParagraphNode p) {
            if(!out.isEmpty())out.add(Map.of("value","\n"));
            encodeInline(p.children(),out,images,p.alignment());
        } else if(block instanceof HeadingNode h) {
            var values=new ArrayList<Object>();encodeInline(h.children(),values,images,h.alignment());
            var data=new LinkedHashMap<String,Object>();data.put("type","title");data.put("value","");
            data.put("level",switch(h.level()){case 1->"first";case 2->"second";default->"third";});
            data.put("valueList",values);out.add(data);
        } else if(block instanceof BulletListNode l) encodeList(l.items(),false,1,out,images);
        else if(block instanceof OrderedListNode l) encodeList(l.items(),true,l.start(),out,images);
        else if(block instanceof BlockImageNode i) out.add(image(i.resourceId(),i.alt(),i.caption(),i.widthPercent(),i.alignment(),false,images));
        else if(block instanceof BlockMathNode m) out.add(Map.of("type","latex","value",m.tex()));
        else throw new IllegalArgumentException("Canvas Editor POC 暂不支持此块节点："+block.getClass().getSimpleName());
    }
    private static void encodeList(List<ListItemNode> items,boolean ordered,int start,List<Object> out,Map<String,ImageData> images) {
        var values=new ArrayList<Object>();
        for(var item:items) {
            if(item.blocks().size()!=1 || !(item.blocks().getFirst() instanceof ParagraphNode p))
                throw new IllegalArgumentException("Canvas Editor POC 暂不支持嵌套列表");
            if(!values.isEmpty())values.add(Map.of("value","\n"));
            encodeInline(p.children(),values,images,p.alignment());
        }
        var data=new LinkedHashMap<String,Object>();data.put("type","list");data.put("value","");
        data.put("listType",ordered?"ol":"ul");data.put("listStyle",ordered?"decimal":"disc");
        data.put("valueList",values);if(ordered)data.put("extension",Map.of("qfStart",start));out.add(data);
    }
    private static void encodeInline(List<InlineNode> nodes,List<Object> out,Map<String,ImageData> images,TextAlignment align) {
        for(var node:nodes) {
            if(node instanceof InlineTextNode t) {
                var data=new LinkedHashMap<String,Object>();data.put("value",t.text());
                for(var mark:t.marks())data.put(switch(mark){case BOLD->"bold";case ITALIC->"italic";
                    case UNDERLINE->"underline";case STRIKE->"strikeout";},true);
                if(align!=null)data.put("rowFlex",align.name().toLowerCase(Locale.ROOT));out.add(data);
            } else if(node instanceof LineBreakNode) out.add(Map.of("value","\n","extension",Map.of("qfLineBreak",true)));
            else if(node instanceof InlineMathNode m) out.add(Map.of("type","latex","value",m.tex(),"extension",Map.of("qfInline",true)));
            else if(node instanceof InlineImageNode i)out.add(image(i.resourceId(),i.alt(),null,null,null,true,images));
            else if(node instanceof LinkNode l) {
                var values=new ArrayList<Object>();encodeInline(l.children(),values,images,align);
                out.add(Map.of("type","hyperlink","value","","url",l.href(),"valueList",values));
            } else throw new IllegalArgumentException("Canvas Editor POC 暂不支持此行内节点");
        }
    }
    private static Map<String,Object> image(String id,String alt,String caption,Integer widthPercent,
            TextAlignment alignment,boolean inline,Map<String,ImageData> images) {
        var bytes=images.get(id);if(bytes==null)throw new IllegalArgumentException("缺少图片资源："+id);
        int width=widthPercent==null?Math.min(bytes.width(),QuestionContentLayout.QUESTION_CONTENT_WIDTH):
                QuestionContentLayout.QUESTION_CONTENT_WIDTH*widthPercent/100;
        int height=Math.max(1,(int)Math.round((double)bytes.height()*width/bytes.width()));
        var ext=new LinkedHashMap<String,Object>();ext.put("qfInline",inline);ext.put("qfInitialWidth",width);ext.put("qfInitialHeight",height);
        if(alt!=null)ext.put("qfAlt",alt);if(caption!=null)ext.put("qfCaption",caption);
        if(widthPercent!=null)ext.put("qfWidthPercent",widthPercent);
        if(alignment!=null)ext.put("qfAlignment",alignment.name());
        var data=new LinkedHashMap<String,Object>();data.put("type","image");data.put("value",bytes.dataUrl());
        data.put("externalId",id);data.put("width",width);data.put("height",height);
        data.put("imgDisplay",inline?"inline":"block");data.put("extension",ext);
        return data;
    }

    static QuestionContent fromCanvasJson(String source,Set<String> knownImageIds) {
        try {
            var root=JSON.readTree(source);var main=root.path("main");if(!main.isArray())throw new IllegalArgumentException("缺少 main");
            boolean plain=true;var raw=new StringBuilder();
            for(var element:main) {
                if(!"text".equals(element.path("type").asText("text")) || element.path("bold").asBoolean()
                        || element.path("italic").asBoolean() || element.path("underline").asBoolean()
                        || element.path("strikeout").asBoolean() || element.path("rowFlex").isTextual()
                        || element.path("superscript").asBoolean() || element.path("subscript").asBoolean()
                        || element.path("font").isTextual() || element.path("size").isNumber()
                        || element.path("color").isTextual() || element.path("highlight").isTextual())plain=false;
                raw.append(requiredText(element,"value"));
            }
            if(plain)return new TextContent(raw.toString());
            var blocks=new ArrayList<BlockNode>();var paragraph=new ArrayList<InlineNode>();TextAlignment align=null;
            for(var element:main) {
                String type=element.path("type").asText("text");
                if("image".equals(type) && element.path("extension").path("qfInline").asBoolean()){
                    paragraph.add(parseInlineImage(element,knownImageIds));continue;
                }
                if("latex".equals(type) && element.path("extension").path("qfInline").asBoolean()){
                    paragraph.add(new InlineMathNode(requiredText(element,"value")));continue;
                }
                if("title".equals(type) || "list".equals(type) || "image".equals(type) || "latex".equals(type)) {
                    if(!paragraph.isEmpty()){blocks.add(new ParagraphNode(paragraph,align));paragraph=new ArrayList<>();align=null;}
                    blocks.add(parseSpecial(element,knownImageIds));continue;
                }
                if("hyperlink".equals(type)) {
                    var children=parseInlineList(element.path("valueList"),knownImageIds);
                    paragraph.add(new LinkNode(requiredText(element,"url"),children));continue;
                }
                if(!"text".equals(type))throw new IllegalArgumentException("不支持的 Canvas 元素："+type);
                if(element.path("extension").path("qfLineBreak").asBoolean()){
                    paragraph.add(new LineBreakNode());continue;
                }
                String value=requiredText(element,"value");
                if(element.path("rowFlex").isTextual())align=parseAlignment(element.path("rowFlex").asText());
                String[] lines=value.split("\\n",-1);
                for(int i=0;i<lines.length;i++) {
                    if(i>0 && !paragraph.isEmpty()){blocks.add(new ParagraphNode(paragraph,align));paragraph=new ArrayList<>();align=null;}
                    if(!lines[i].isEmpty())paragraph.add(parseText(element,lines[i]));
                }
            }
            if(!paragraph.isEmpty() || blocks.isEmpty())blocks.add(new ParagraphNode(paragraph,align));
            return QuestionContentNormalizer.normalize(new RichContent(new RichDocument(blocks)));
        } catch(RuntimeException e){throw new IllegalArgumentException("Canvas 内容无法无损转成 QBank："+e.getMessage(),e);}
        catch(Exception e){throw new IllegalArgumentException("Canvas 内容不是有效 JSON",e);}
    }
    private static BlockNode parseSpecial(JsonNode node,Set<String> ids) {
        return switch(requiredText(node,"type")) {
            case "title" -> new HeadingNode(switch(requiredText(node,"level")){case "first"->1;case "second"->2;case "third"->3;default->throw new IllegalArgumentException("暂不支持该标题级别");},
                    parseHeading(node.path("valueList"),ids),null);
            case "list" -> {
                String listType=requiredText(node,"listType");String listStyle=requiredText(node,"listStyle");
                if(!("ol".equals(listType) && "decimal".equals(listStyle) || "ul".equals(listType) && "disc".equals(listStyle)))
                    throw new IllegalArgumentException("当前 QBank 模型无法保存该列表样式");
                var itemNodes=new ArrayList<ListItemNode>();var current=new ArrayList<InlineNode>();
                for(var child:node.path("valueList")) {
                    if(!"text".equals(child.path("type").asText("text")))throw new IllegalArgumentException("暂不支持列表中的非文本元素");
                    String value=requiredText(child,"value");String[] parts=value.split("\\n",-1);
                    for(int i=0;i<parts.length;i++) {
                        if(i>0 && !current.isEmpty()) {
                            itemNodes.add(new ListItemNode(List.of(new ParagraphNode(current))));current=new ArrayList<>();
                        }
                        if(!parts[i].isEmpty())current.add(parseText(child,parts[i]));
                    }
                }
                if(!current.isEmpty() || itemNodes.isEmpty())itemNodes.add(new ListItemNode(List.of(new ParagraphNode(current))));
                if("ol".equals(listType))yield new OrderedListNode(node.path("extension").path("qfStart").asInt(1),itemNodes);
                if("ul".equals(listType))yield new BulletListNode(itemNodes);
                throw new IllegalArgumentException("不支持的列表类型");
            }
            case "image" -> parseImage(node,ids);
            case "latex" -> new BlockMathNode(requiredText(node,"value"));
            default -> throw new IllegalArgumentException("不支持的节点");
        };
    }
    private static List<InlineNode> parseHeading(JsonNode list,Set<String> ids) {
        // Canvas Editor injects bold into every heading span as its built-in title styling.
        var output=new ArrayList<InlineNode>();
        for(var element:list)if(element.path("color").isTextual() || element.path("highlight").isTextual() || element.path("font").isTextual())
            throw new IllegalArgumentException("当前 QBank 模型无法保存标题的字体或颜色");
        for(var element:list) {
            if("text".equals(element.path("type").asText("text"))) {
                var copy=(ObjectNode)element.deepCopy();copy.remove(List.of("size","bold"));
                output.add(parseText(copy,requiredText(copy,"value")));
            } else output.addAll(parseInlineList(JSON.createArrayNode().add(element),ids));
        }
        return output;
    }
    private static BlockImageNode parseImage(JsonNode node,Set<String> ids) {
        String id=requiredText(node,"externalId");if(!ids.contains(id))throw new IllegalArgumentException("未知图片资源："+id);
        var ext=node.path("extension");validateImageDimensions(node);
        if(!"block".equals(node.path("imgDisplay").asText("block")))
            throw new IllegalArgumentException("当前 QBank 模型无法保存该图片排列方式");
        return new BlockImageNode(id,optionalText(ext,"qfAlt"),optionalText(ext,"qfCaption"),
                ext.path("qfWidthPercent").isInt()?ext.path("qfWidthPercent").asInt():null,
                optionalText(ext,"qfAlignment")==null?null:TextAlignment.valueOf(ext.path("qfAlignment").asText()));
    }
    private static InlineImageNode parseInlineImage(JsonNode node,Set<String> ids) {
        String id=requiredText(node,"externalId");if(!ids.contains(id))throw new IllegalArgumentException("未知图片资源："+id);
        validateImageDimensions(node);
        if(!"inline".equals(node.path("imgDisplay").asText("inline")))
            throw new IllegalArgumentException("当前 QBank 模型无法保存该图片排列方式");
        return new InlineImageNode(id,optionalText(node.path("extension"),"qfAlt"));
    }
    private static void validateImageDimensions(JsonNode node) {
        var ext=node.path("extension");
        int initialWidth=ext.path("qfInitialWidth").asInt(node.path("width").asInt());
        int initialHeight=ext.path("qfInitialHeight").asInt(node.path("height").asInt());
        if(node.path("width").asInt(initialWidth)!=initialWidth || node.path("height").asInt(initialHeight)!=initialHeight)
            throw new IllegalArgumentException("图片尺寸已改变；当前 QBank 模型无法无损保存任意像素尺寸");
    }
    private static List<InlineNode> parseInlineList(JsonNode list,Set<String> ids) {
        if(!list.isArray())throw new IllegalArgumentException("缺少 valueList");
        var result=new ArrayList<InlineNode>();for(var child:list) {
            String type=child.path("type").asText("text");
            if("text".equals(type)){
                if(child.path("extension").path("qfLineBreak").asBoolean())result.add(new LineBreakNode());
                else {String value=requiredText(child,"value");if(!value.isEmpty())result.add(parseText(child,value));}
            }
            else if("latex".equals(type))result.add(new InlineMathNode(requiredText(child,"value")));
            else if("image".equals(type))result.add(parseInlineImage(child,ids));
            else throw new IllegalArgumentException("不支持的行内元素："+type);
        }return result;
    }
    private static InlineTextNode parseText(JsonNode node,String value) {
        if(!"text".equals(node.path("type").asText("text")))throw new IllegalArgumentException("暂不支持此文字类型");
        if(node.path("font").isTextual() || node.path("size").isNumber() || node.path("color").isTextual()
                || node.path("highlight").isTextual())
            throw new IllegalArgumentException("当前 QBank 模型无法保存字体、字号或颜色");
        if(node.path("superscript").asBoolean() || node.path("subscript").asBoolean())
            throw new IllegalArgumentException("当前 QBank 模型无法保存上标或下标");
        var marks=new ArrayList<TextMark>();if(node.path("bold").asBoolean())marks.add(TextMark.BOLD);
        if(node.path("italic").asBoolean())marks.add(TextMark.ITALIC);
        if(node.path("underline").asBoolean())marks.add(TextMark.UNDERLINE);
        if(node.path("strikeout").asBoolean())marks.add(TextMark.STRIKE);
        return new InlineTextNode(value,marks);
    }
    private static TextAlignment parseAlignment(String value) {
        return switch(value){case "left"->TextAlignment.LEFT;case "center"->TextAlignment.CENTER;
            case "right"->TextAlignment.RIGHT;default->throw new IllegalArgumentException("暂不支持此对齐方式："+value);};
    }
    private static String requiredText(JsonNode node,String field) {
        var value=node.path(field);if(!value.isTextual())throw new IllegalArgumentException("缺少文本属性："+field);return value.asText();
    }
    private static String optionalText(JsonNode node,String field) {
        var value=node.path(field);return value.isTextual()?value.asText():null;
    }
}
