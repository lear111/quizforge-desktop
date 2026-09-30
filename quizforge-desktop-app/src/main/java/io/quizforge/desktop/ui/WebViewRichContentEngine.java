package io.quizforge.desktop.ui;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.*;
import io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** Runs only the locally bundled Tiptap editor; QBank content never becomes script or HTML. */
final class WebViewRichContentEngine implements RichContentEditorEngine {
    private final WebView web=new WebView();
    private final Supplier<List<QBankResource>> resources;
    private final QuestionResourceInput input;
    private final Consumer<String> error;
    private final HeightBridge bridge=new HeightBridge();
    private QuestionContent content;
    private JSObject api;

    WebViewRichContentEngine(QuestionContent initial,Supplier<List<QBankResource>> resources,
            QuestionResourceInput input,Consumer<String> error) {
        content=Objects.requireNonNull(initial);this.resources=resources;this.input=input;this.error=error;
        web.setId("rich-content-surface");web.setContextMenuEnabled(false);
        web.setMinWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        web.setPrefWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        web.setMaxWidth(QuestionContentLayout.QUESTION_CONTENT_WIDTH);
        web.setMinHeight(400);web.setPrefHeight(500);
        var engine=web.getEngine();engine.setCreatePopupHandler(features->null);
        engine.setConfirmHandler(message->false);engine.setPromptHandler(prompt->null);
        engine.locationProperty().addListener((o,a,b)->{
            if(b!=null && !b.isEmpty() && !"about:blank".equals(b)) {
                engine.getLoadWorker().cancel();api=null;error.accept("编辑器禁止导航到外部地址");
            }
        });
        engine.getLoadWorker().stateProperty().addListener((o,a,b)->{
            if(b==Worker.State.SUCCEEDED)try {
                ((JSObject)engine.executeScript("window")).setMember("contentBridge",bridge);
                api=(JSObject)engine.executeScript("window.richEditor");load();
            }catch(RuntimeException failure){api=null;error.accept("本地编辑器加载失败："+failure.getMessage());}
        });
        try {
            String html=readResource("rich-content-editor.html");String bundle=readResource("tiptap-bundle.js");
            if(bundle.toLowerCase(Locale.ROOT).contains("</script"))throw new IllegalStateException("Bundle contains script terminator");
            engine.loadContent(html.replace("/*__BUNDLE__*/",bundle));
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private static String readResource(String name) throws IOException {
        try(var stream=WebViewRichContentEngine.class.getResourceAsStream(name)) {
            if(stream==null)throw new IOException("Missing bundled editor resource: "+name);
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    @Override public Node view(){return web;}
    @Override public boolean ready(){return api!=null;}
    @Override public void setContent(QuestionContent value){content=Objects.requireNonNull(value);if(ready())load();}
    @Override public QuestionContent getContent(){requireReady();return RichContentEditorAdapter.fromEditorJson((String)api.call("exportContent"),resourceIds());}
    private Set<String> resourceIds(){Set<String> ids=new HashSet<>();resources.get().forEach(r->ids.add(r.id()));return ids;}
    private void load(){api.call("load",RichContentEditorAdapter.toEditorJson(content),RichContentEditorAdapter.json(images()),QuestionContentLayout.editorConfig());}
    private Map<String,String> images(){
        Map<String,String> images=new HashMap<>();
        for(var resource:resources.get())if(resource.kind()==ResourceKind.IMAGE)try(var stream=input.open(resource)) {
            if(stream==null)continue;byte[] bytes=stream.readNBytes((int)QBankImageImporter.MAX_BYTES+1);
            if(bytes.length<=QBankImageImporter.MAX_BYTES && Set.of("image/png","image/jpeg").contains(resource.mediaType()))
                images.put(resource.id(),"data:"+resource.mediaType()+";base64,"+Base64.getEncoder().encodeToString(bytes));
        }catch(IOException | RuntimeException missing){/* Keep resource identity for explicit missing preview. */}
        return images;
    }
    @Override public void captureSelection(){if(ready())api.call("captureSelection");}
    @Override public boolean imageSelected(){return ready() && Boolean.TRUE.equals(api.call("imageSelected"));}
    @Override public void insertImage(QBankResource resource){requireReady();api.call("updateImages",RichContentEditorAdapter.json(images()));api.call("insertImage",resource.id());}
    @Override public void replaceSelectedImage(QBankResource resource){requireReady();api.call("updateImages",RichContentEditorAdapter.json(images()));api.call("replaceImage",resource.id());}
    @Override public void deleteSelectedImage(){requireReady();api.call("deleteImage");}
    @Override public void setImageWidth(int percent){requireReady();api.call("setImageWidth",percent);}
    @Override public void setImageAlignment(TextAlignment alignment){requireReady();api.call("setImageAlignment",alignment.name());}
    @Override public boolean command(String name,String argument){requireReady();return Boolean.TRUE.equals(api.call("command",name,argument));}
    private void requireReady(){if(!ready())throw new IllegalStateException("富文本编辑器尚未加载完成或内容不受支持");}
    public final class HeightBridge {
        public void heightChanged(double height){if(Double.isFinite(height))web.setPrefHeight(Math.max(400,height));}
    }
    WebView webView(){return web;}
}
