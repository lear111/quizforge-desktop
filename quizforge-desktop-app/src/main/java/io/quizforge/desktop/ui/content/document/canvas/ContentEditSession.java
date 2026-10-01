package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.desktop.ui.content.ContentEditResult;
import io.quizforge.desktop.ui.content.StagedContentResource;
import io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

/** Draft resources remain private until Save returns a result to the owning editor. */
public final class ContentEditSession {
    private final QuestionContent original;
    private final List<QBankResource> existing;
    private final QuestionResourceInput input;
    private final Map<String,StagedContentResource> staged=new LinkedHashMap<>();
    public ContentEditSession(QuestionContent original,List<QBankResource> existing,QuestionResourceInput input) {
        this.original=Objects.requireNonNull(original);this.existing=List.copyOf(existing);this.input=input;
    }
    public QBankResource stage(Path path) {
        var image=new QBankImageImporter().read(path);staged.put(image.resource().id(),new StagedContentResource(image.resource(),image.bytes()));return image.resource();
    }
    public DocumentContent stageDocument(String json,String text) {
        byte[] bytes=CanvasNativeDocument.bytes(json);
        String hash=CanvasNativeDocument.hash(bytes),id="res_canvas_"+hash;
        if(existing.stream().noneMatch(resource->resource.id().equals(id))) {
            var resource=new QBankResource(id,ResourceKind.DOCUMENT,CanvasNativeDocument.MEDIA_TYPE,"resources/"+id+".canvas.json",hash);
            staged.put(id,new StagedContentResource(resource,bytes));
        }
        return new DocumentContent(id,text);
    }
    public String document(DocumentContent content) {
        var resource=resources().stream().filter(r->r.id().equals(content.resourceId()) && r.kind()==ResourceKind.DOCUMENT
                && CanvasNativeDocument.MEDIA_TYPE.equals(r.mediaType())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("富文本文档资源缺失"));
        try(var stream=open(resource)) {
            if(stream==null)throw new IOException("富文本文档资源缺失");
            return CanvasNativeDocument.read(stream);
        }catch(IOException failure){throw new IllegalArgumentException("无法读取富文本文档",failure);}
    }
    public QuestionContent combine(QuestionContent first,QuestionContent second) {
        if(first instanceof TextContent a && second instanceof TextContent b)
            return new TextContent(a.text()+"\n\n"+b.text());
        try {
            var json=CanvasNativeDocument.JSON;
            Map<String,CanvasEditorAdapter.ImageData> images=first instanceof DocumentContent && second instanceof DocumentContent
                    ?Map.of():CanvasEditorBridge.imageData(this);
            var a=first instanceof DocumentContent d?json.readTree(document(d)):
                    json.readTree(CanvasEditorAdapter.toCanvasJson(first,images));
            var b=second instanceof DocumentContent d?json.readTree(document(d)):
                    json.readTree(CanvasEditorAdapter.toCanvasJson(second,images));
            var main=json.createArrayNode();
            main.addAll((com.fasterxml.jackson.databind.node.ArrayNode)(a.has("data")?a.path("data").path("main"):a.path("main")));
            main.addObject().put("value","\n\n");
            main.addAll((com.fasterxml.jackson.databind.node.ArrayNode)(b.has("data")?b.path("data").path("main"):b.path("main")));
            var combined=a.has("data")?(com.fasterxml.jackson.databind.node.ObjectNode)a.deepCopy():
                    b.has("data")?(com.fasterxml.jackson.databind.node.ObjectNode)b.deepCopy():json.createObjectNode();
            if(!combined.has("version"))combined.put("version","1.0.4");
            if(!combined.has("options"))combined.set("options",json.createObjectNode());
            var data=combined.get("data") instanceof com.fasterxml.jackson.databind.node.ObjectNode existingData
                    ?existingData:combined.putObject("data");
            data.set("main",main);
            return stageDocument(json.writeValueAsString(combined),QuestionContentData.plainText(first)+"\n\n"+QuestionContentData.plainText(second));
        }catch(IOException failure){throw new IllegalArgumentException("无法合并参考答案与解析",failure);}
    }
    public List<QBankResource> resources() {
        var all=new ArrayList<>(existing);staged.values().forEach(i->all.add(i.resource()));return List.copyOf(all);
    }
    public InputStream open(QBankResource resource) throws IOException {
        var image=staged.get(resource.id());return image==null?input.open(resource):image.open();
    }
    public ContentEditResult save(QuestionContent content) {
        var used=QuestionContentData.resourceIds(content);
        var known=new HashSet<String>();resources().forEach(r->known.add(r.id()));
        if(!known.containsAll(used))throw new IllegalArgumentException("编辑内容引用未知图片资源");
        var added=staged.values().stream().filter(i->used.contains(i.resource().id())).toList();
        var removed=new HashSet<>(QuestionContentData.resourceIds(original));removed.removeAll(used);
        return new ContentEditResult(true,content,added,removed);
    }
    public ContentEditResult cancel() {staged.clear();return ContentEditResult.cancelled();}
    public void retainResources(QuestionContent content) {
        var used=QuestionContentData.resourceIds(content);staged.keySet().removeIf(id->!used.contains(id));
    }
}
