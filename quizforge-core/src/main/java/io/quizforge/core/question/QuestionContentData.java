package io.quizforge.core.question;

import java.util.*;

/** Structured persistence snapshots; no HTML, paths, JSON library or binary content. */
public final class QuestionContentData {
    private QuestionContentData() { }
    public static Map<String,Object> encode(QuestionContent content) {
        if (content instanceof TextContent text) return Map.of("kind","TEXT","text",text.text());
        if (content instanceof DocumentContent doc) return Map.of("kind","DOCUMENT","resourceId",doc.resourceId(),"text",doc.text());
        var rich=(RichContent)content;
        return Map.of("kind","RICH","document",Map.of("blocks",rich.document().blocks().stream().map(QuestionContentData::block).toList()));
    }
    private static Map<String,Object> block(BlockNode node) {
        return switch(node) {
            case ParagraphNode p -> optional(Map.of("type","PARAGRAPH","children",p.children().stream().map(QuestionContentData::inline).toList()),"alignment",p.alignment());
            case HeadingNode h -> optional(Map.of("type","HEADING","level",h.level(),"children",h.children().stream().map(QuestionContentData::inline).toList()),"alignment",h.alignment());
            case BulletListNode l -> Map.of("type","BULLET_LIST","items",l.items().stream().map(QuestionContentData::item).toList());
            case OrderedListNode l -> Map.of("type","ORDERED_LIST","start",l.start(),"items",l.items().stream().map(QuestionContentData::item).toList());
            case BlockQuoteNode q -> Map.of("type","BLOCK_QUOTE","blocks",q.blocks().stream().map(QuestionContentData::block).toList());
            case BlockImageNode i -> optional(Map.of("type","IMAGE","resourceId",i.resourceId()),"alt",i.alt(),"caption",i.caption(),"widthPercent",i.widthPercent(),"alignment",i.alignment());
            case BlockMathNode m -> Map.of("type","MATH","tex",m.tex());
        };
    }
    private static Map<String,Object> inline(InlineNode node) {
        return switch(node) {
            case InlineTextNode t -> t.marks().isEmpty()?Map.of("type","TEXT","text",t.text())
                    :Map.of("type","TEXT","text",t.text(),"marks",t.marks());
            case InlineImageNode i -> optional(Map.of("type","IMAGE","resourceId",i.resourceId()),"alt",i.alt());
            case InlineMathNode m -> Map.of("type","MATH","tex",m.tex());
            case LineBreakNode ignored -> Map.of("type","LINE_BREAK");
            case LinkNode l -> Map.of("type","LINK","href",l.href(),"children",l.children().stream().map(QuestionContentData::inline).toList());
        };
    }
    public static Map<String,Object> optional(Map<String,?> required,Object... pairs) {
        Map<String,Object> data=new LinkedHashMap<>(required);
        for(int i=0;i<pairs.length;i+=2) if(pairs[i+1]!=null) data.put((String)pairs[i],pairs[i+1]);
        return data;
    }
    private static Map<String,Object> item(ListItemNode item) {
        return Map.of("blocks",item.blocks().stream().map(QuestionContentData::block).toList());
    }
    public static QuestionContent decode(Object value) {
        var data=map(value);
        if("TEXT".equals(data.get("kind"))) return new TextContent((String)data.get("text"));
        if("DOCUMENT".equals(data.get("kind"))) return new DocumentContent((String)data.get("resourceId"),(String)data.get("text"));
        if(!"RICH".equals(data.get("kind"))) throw new IllegalStateException("Invalid content snapshot");
        return new RichContent(new RichDocument(list(map(data.get("document")).get("blocks")).stream().map(QuestionContentData::decodeBlock).toList()));
    }
    private static BlockNode decodeBlock(Object value) {
        var data=map(value);
        return switch((String)data.get("type")) {
            case "PARAGRAPH" -> new ParagraphNode(list(data.get("children")).stream().map(QuestionContentData::decodeInline).toList(),alignment(data.get("alignment")));
            case "HEADING" -> new HeadingNode(((Number)data.get("level")).intValue(),list(data.get("children")).stream().map(QuestionContentData::decodeInline).toList(),alignment(data.get("alignment")));
            case "BULLET_LIST" -> new BulletListNode(list(data.get("items")).stream().map(QuestionContentData::decodeItem).toList());
            case "ORDERED_LIST" -> new OrderedListNode(((Number)data.get("start")).intValue(),list(data.get("items")).stream().map(QuestionContentData::decodeItem).toList());
            case "BLOCK_QUOTE" -> new BlockQuoteNode(list(data.get("blocks")).stream().map(QuestionContentData::decodeBlock).toList());
            case "IMAGE" -> new BlockImageNode((String)data.get("resourceId"),(String)data.get("alt"),(String)data.get("caption"),data.get("widthPercent")==null?null:((Number)data.get("widthPercent")).intValue(),alignment(data.get("alignment")));
            case "MATH" -> new BlockMathNode((String)data.get("tex"));
            default -> throw new IllegalStateException("Invalid block snapshot");
        };
    }
    private static ListItemNode decodeItem(Object value) {
        return new ListItemNode(list(map(value).get("blocks")).stream().map(QuestionContentData::decodeBlock).toList());
    }
    private static TextAlignment alignment(Object value) {return value==null?null:TextAlignment.valueOf((String)value);}
    private static InlineNode decodeInline(Object value) {
        var data=map(value);
        return switch((String)data.get("type")) {
            case "TEXT" -> new InlineTextNode((String)data.get("text"),data.get("marks")==null?List.of():list(data.get("marks")).stream().map(v->TextMark.valueOf((String)v)).toList());
            case "IMAGE" -> new InlineImageNode((String)data.get("resourceId"),(String)data.get("alt"));
            case "MATH" -> new InlineMathNode((String)data.get("tex"));
            case "LINE_BREAK" -> new LineBreakNode();
            case "LINK" -> new LinkNode((String)data.get("href"),list(data.get("children")).stream().map(QuestionContentData::decodeInline).toList());
            default -> throw new IllegalStateException("Invalid inline snapshot");
        };
    }
    public static String plainText(QuestionContent content) {
        if(content==null) return "";
        if(content instanceof TextContent t) return t.text();
        if(content instanceof DocumentContent doc) return doc.text();
        return ((RichContent)content).document().blocks().stream().map(QuestionContentData::blockText)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
    private static String blockText(BlockNode b) {
        return switch(b) {
            case ParagraphNode p -> p.children().stream().map(QuestionContentData::inlineText).reduce("",String::concat);
            case HeadingNode h -> h.children().stream().map(QuestionContentData::inlineText).reduce("",String::concat);
            case BulletListNode l -> l.items().stream().map(i->i.blocks().stream().map(QuestionContentData::blockText).reduce("",String::concat)).collect(java.util.stream.Collectors.joining("\n"));
            case OrderedListNode l -> l.items().stream().map(i->i.blocks().stream().map(QuestionContentData::blockText).reduce("",String::concat)).collect(java.util.stream.Collectors.joining("\n"));
            case BlockQuoteNode q -> q.blocks().stream().map(QuestionContentData::blockText).collect(java.util.stream.Collectors.joining("\n"));
            case BlockImageNode ignored -> "[图片]";
            case BlockMathNode ignored -> "[公式]";
        };
    }
    private static String inlineText(InlineNode node) {
        return switch(node) {
            case InlineTextNode t -> t.text();
            case LineBreakNode ignored -> "\n";
            case InlineImageNode ignored -> "[图片]";
            case InlineMathNode ignored -> "[公式]";
            case LinkNode l -> l.children().stream().map(QuestionContentData::inlineText).reduce("",String::concat);
        };
    }
    public static Set<String> imageIds(QuestionContent content) {
        Set<String> result=new HashSet<>();
        if(content instanceof RichContent rich) for(var b:rich.document().blocks()) collectBlockImages(b,result);
        return Set.copyOf(result);
    }
    public static Set<String> resourceIds(QuestionContent content) {
        return content instanceof DocumentContent doc ? Set.of(doc.resourceId()) : imageIds(content);
    }
    private static void collectBlockImages(BlockNode b,Set<String> ids) {
        if(b instanceof BlockImageNode i) ids.add(i.resourceId());
        else if(b instanceof ParagraphNode p) collectImages(p.children(),ids);
        else if(b instanceof HeadingNode h) collectImages(h.children(),ids);
        else if(b instanceof BulletListNode l) l.items().forEach(i->i.blocks().forEach(n->collectBlockImages(n,ids)));
        else if(b instanceof OrderedListNode l) l.items().forEach(i->i.blocks().forEach(n->collectBlockImages(n,ids)));
        else if(b instanceof BlockQuoteNode q) q.blocks().forEach(n->collectBlockImages(n,ids));
    }
    private static void collectImages(List<InlineNode> nodes,Set<String> ids) {
        for(var n:nodes) { if(n instanceof InlineImageNode i) ids.add(i.resourceId()); if(n instanceof LinkNode l) collectImages(l.children(),ids); }
    }
    public static Map<?,?> map(Object value) {
        if(value instanceof Map<?,?> map) return map;
        throw new IllegalStateException("Invalid structured snapshot");
    }
    private static List<?> list(Object value) {
        if(value instanceof List<?> list) return list;
        throw new IllegalStateException("Invalid snapshot list");
    }
}
