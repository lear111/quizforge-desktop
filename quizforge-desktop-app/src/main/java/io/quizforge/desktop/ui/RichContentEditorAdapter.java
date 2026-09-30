package io.quizforge.desktop.ui;

import com.fasterxml.jackson.databind.*;
import io.quizforge.core.question.*;
import java.util.*;

/** Strict boundary between the bundled editor document and portable QBank content. */
final class RichContentEditorAdapter {
    private static final ObjectMapper JSON=new ObjectMapper();
    private RichContentEditorAdapter() { }

    static String toEditorJson(QuestionContent content) {
        List<Object> blocks=new ArrayList<>();
        if(content instanceof TextContent text) {
            for(String part:text.text().split("\\n\\n",-1)) {
                List<Object> children=new ArrayList<>();String[] lines=part.split("\\n",-1);
                for(int i=0;i<lines.length;i++) {
                    if(i>0)children.add(Map.of("type","hardBreak"));
                    if(!lines[i].isEmpty())children.add(Map.of("type","text","text",lines[i]));
                }
                blocks.add(Map.of("type","paragraph","content",children));
            }
        }else for(var block:((RichContent)content).document().blocks())blocks.add(encodeBlock(block));
        return json(Map.of("type","doc","content",blocks));
    }
    static String json(Object value) {
        try{return JSON.writeValueAsString(value);}catch(Exception failure){throw new IllegalArgumentException("无法编码编辑内容",failure);}
    }
    private static Object encodeBlock(BlockNode block) {
        return switch(block) {
            case ParagraphNode p -> structure("paragraph",p.children(),p.alignment(),null);
            case HeadingNode h -> structure("heading",h.children(),h.alignment(),h.level());
            case BulletListNode l -> Map.of("type","bulletList","content",l.items().stream().map(RichContentEditorAdapter::encodeItem).toList());
            case OrderedListNode l -> Map.of("type","orderedList","attrs",Map.of("start",l.start()),"content",l.items().stream().map(RichContentEditorAdapter::encodeItem).toList());
            case BlockQuoteNode q -> Map.of("type","blockquote","content",q.blocks().stream().map(RichContentEditorAdapter::encodeBlock).toList());
            case BlockImageNode i -> image("image",i.resourceId(),i.alt(),i.caption(),i.widthPercent(),i.alignment());
            case BlockMathNode ignored -> throw new IllegalArgumentException("当前编辑器不支持公式节点");
        };
    }
    private static Object encodeItem(ListItemNode item) {
        return Map.of("type","listItem","content",item.blocks().stream().map(RichContentEditorAdapter::encodeBlock).toList());
    }
    private static Object structure(String type,List<InlineNode> children,TextAlignment alignment,Integer level) {
        Map<String,Object> node=new LinkedHashMap<>();node.put("type",type);
        Map<String,Object> attrs=new LinkedHashMap<>();if(level!=null)attrs.put("level",level);
        if(alignment!=null)attrs.put("textAlign",alignment.name().toLowerCase(Locale.ROOT));
        if(!attrs.isEmpty())node.put("attrs",attrs);
        var content=new ArrayList<Object>();children.forEach(i->encodeInline(i,content,null));
        if(!content.isEmpty())node.put("content",content);return node;
    }
    private static Object image(String type,String id,String alt,String caption,Integer width,TextAlignment alignment) {
        Map<String,Object> attrs=new LinkedHashMap<>();attrs.put("resourceId",id);
        if(alt!=null)attrs.put("alt",alt);if(width!=null)attrs.put("widthPercent",width);
        if(caption!=null)attrs.put("caption",caption);
        if(alignment!=null)attrs.put("alignment",alignment.name());
        return Map.of("type",type,"attrs",attrs);
    }
    private static void encodeInline(InlineNode node,List<Object> output,String href) {
        if(node instanceof InlineTextNode t) {
            if(t.text().isEmpty())return;
            Map<String,Object> text=new LinkedHashMap<>();text.put("type","text");text.put("text",t.text());
            List<Object> marks=new ArrayList<>();for(var mark:t.marks())marks.add(Map.of("type",mark.name().toLowerCase(Locale.ROOT)));
            if(href!=null)marks.add(Map.of("type","link","attrs",Map.of("href",href)));
            if(!marks.isEmpty())text.put("marks",marks);output.add(text);
        }else if(node instanceof LineBreakNode)output.add(Map.of("type","hardBreak"));
        else if(node instanceof InlineImageNode i)output.add(image("inlineImage",i.resourceId(),i.alt(),null,null,null));
        else if(node instanceof LinkNode l)l.children().forEach(c->encodeInline(c,output,l.href()));
        else throw new IllegalArgumentException("当前编辑器不支持公式节点");
    }

    static QuestionContent fromEditorJson(String source,Set<String> resourceIds) {
        try {
            JsonNode root=JSON.readTree(source);fields(root,"type","content");requireType(root,"doc");
            List<BlockNode> blocks=new ArrayList<>();for(JsonNode node:array(root,"content"))blocks.add(parseBlock(node,resourceIds));
            return QuestionContentNormalizer.normalize(new RichContent(new RichDocument(blocks)));
        }catch(RuntimeException error){throw new IllegalArgumentException("编辑内容包含不支持或无效的节点："+error.getMessage(),error);}
        catch(Exception error){throw new IllegalArgumentException("编辑内容不是有效 JSON",error);}
    }
    private static BlockNode parseBlock(JsonNode node,Set<String> ids) {
        return switch(string(node,"type")) {
            case "paragraph" -> {fields(node,"type","attrs","content");JsonNode a=attrs(node);fields(a,"textAlign");yield new ParagraphNode(parseInline(node,ids),alignment(a.get("textAlign")));}
            case "heading" -> {fields(node,"type","attrs","content");JsonNode a=attrs(node);fields(a,"level","textAlign");yield new HeadingNode(integer(a,"level",0),parseInline(node,ids),alignment(a.get("textAlign")));}
            case "bulletList" -> {fields(node,"type","content");yield new BulletListNode(parseItems(node,ids));}
            case "orderedList" -> {fields(node,"type","attrs","content");JsonNode a=attrs(node);fields(a,"start");yield new OrderedListNode(integer(a,"start",1),parseItems(node,ids));}
            case "blockquote" -> {fields(node,"type","content");List<BlockNode> children=new ArrayList<>();for(JsonNode c:array(node,"content"))children.add(parseBlock(c,ids));yield new BlockQuoteNode(children);}
            case "image" -> {fields(node,"type","attrs");yield parseImage(node,ids);}
            default -> throw new IllegalArgumentException("未知块节点 "+string(node,"type"));
        };
    }
    private static List<ListItemNode> parseItems(JsonNode node,Set<String> ids) {
        List<ListItemNode> items=new ArrayList<>();for(JsonNode item:array(node,"content")) {
            fields(item,"type","content");requireType(item,"listItem");
            List<BlockNode> blocks=new ArrayList<>();for(JsonNode child:array(item,"content"))blocks.add(parseBlock(child,ids));
            items.add(new ListItemNode(blocks));
        }return items;
    }
    private static BlockImageNode parseImage(JsonNode node,Set<String> ids) {
        JsonNode attrs=attrs(node);fields(attrs,"resourceId","alt","caption","widthPercent","alignment");
        String id=resourceId(attrs,ids);Integer width=optionalInt(attrs,"widthPercent");
        return new BlockImageNode(id,optionalString(attrs,"alt"),optionalString(attrs,"caption"),width,alignment(attrs.get("alignment")));
    }
    private static List<InlineNode> parseInline(JsonNode parent,Set<String> ids) {
        List<InlineNode> output=new ArrayList<>();for(JsonNode node:optionalArray(parent,"content")) {
            switch(string(node,"type")) {
                case "text" -> {
                    fields(node,"type","text","marks");String value=string(node,"text");if(value.isEmpty())break;
                    List<TextMark> marks=new ArrayList<>();String href=null;
                    for(JsonNode mark:optionalArray(node,"marks")) {
                        String kind=string(mark,"type");
                        if("link".equals(kind)) {
                            fields(mark,"type","attrs");JsonNode a=attrs(mark);fields(a,"href","target","rel","class");
                            if(href!=null)throw new IllegalArgumentException("重复链接标记");href=string(a,"href");
                        }else {
                            fields(mark,"type");marks.add(switch(kind) {
                                case "bold" -> TextMark.BOLD;case "italic" -> TextMark.ITALIC;
                                case "underline" -> TextMark.UNDERLINE;case "strike" -> TextMark.STRIKE;
                                default -> throw new IllegalArgumentException("未知文字标记 "+kind);
                            });
                        }
                    }
                    InlineTextNode text=new InlineTextNode(value,marks);
                    if(href==null)output.add(text);else output.add(new LinkNode(href,List.of(text)));
                }
                case "hardBreak" -> {fields(node,"type");output.add(new LineBreakNode());}
                case "inlineImage" -> {fields(node,"type","attrs");JsonNode a=attrs(node);fields(a,"resourceId","alt","caption","widthPercent","alignment");
                    if(optionalString(a,"caption")!=null)throw new IllegalArgumentException("行内图片不支持标题");
                    if(optionalInt(a,"widthPercent")!=null || alignment(a.get("alignment"))!=null)
                        throw new IllegalArgumentException("行内图片不支持尺寸或对齐");
                    output.add(new InlineImageNode(resourceId(a,ids),optionalString(a,"alt")));}
                default -> throw new IllegalArgumentException("未知行内节点 "+string(node,"type"));
            }
        }return output;
    }
    private static String resourceId(JsonNode attrs,Set<String> ids) {
        String id=string(attrs,"resourceId");if(!ids.contains(id))throw new IllegalArgumentException("未知图片资源");return id;
    }
    private static TextAlignment alignment(JsonNode value) {
        if(value==null || value.isNull())return null;
        String text=value.textValue();if(text==null)throw new IllegalArgumentException("无效对齐属性");
        return switch(text.toUpperCase(Locale.ROOT)) {case "LEFT" -> TextAlignment.LEFT;case "CENTER" -> TextAlignment.CENTER;case "RIGHT" -> TextAlignment.RIGHT;default -> throw new IllegalArgumentException("无效对齐属性");};
    }
    private static String optionalString(JsonNode node,String name) {JsonNode value=node.get(name);return value==null || value.isNull()?null:string(node,name);}
    private static Integer optionalInt(JsonNode node,String name) {JsonNode value=node.get(name);return value==null || value.isNull()?null:integer(node,name,0);}
    private static int integer(JsonNode node,String name,int fallback) {JsonNode value=node.get(name);if(value==null || value.isNull())return fallback;if(!value.isIntegralNumber() || !value.canConvertToInt())throw new IllegalArgumentException("无效整数 "+name);return value.intValue();}
    private static String string(JsonNode node,String name) {JsonNode value=node.get(name);if(value==null || !value.isTextual())throw new IllegalArgumentException("无效字段 "+name);return value.textValue();}
    private static void requireType(JsonNode node,String type) {if(!type.equals(string(node,"type")))throw new IllegalArgumentException("预期 "+type);}
    private static JsonNode attrs(JsonNode node) {JsonNode value=node.get("attrs");if(value==null || value.isNull())return JSON.createObjectNode();if(!value.isObject())throw new IllegalArgumentException("无效 attrs");return value;}
    private static Iterable<JsonNode> optionalArray(JsonNode node,String name) {JsonNode value=node.get(name);if(value==null || value.isNull())return List.of();if(!value.isArray())throw new IllegalArgumentException("无效数组 "+name);return value;}
    private static Iterable<JsonNode> array(JsonNode node,String name) {JsonNode value=node.get(name);if(value==null || !value.isArray())throw new IllegalArgumentException("缺少数组 "+name);return value;}
    private static void fields(JsonNode node,String... allowed) {
        if(node==null || !node.isObject())throw new IllegalArgumentException("预期对象");
        Set<String> names=Set.of(allowed);node.fieldNames().forEachRemaining(name->{if(!names.contains(name))throw new IllegalArgumentException("未知字段 "+name);});
    }
}
