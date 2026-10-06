package io.quizforge.desktop.browser.webview2;

import com.fasterxml.jackson.databind.JsonNode;
import io.quizforge.infrastructure.json.DocumentJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.*;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Opt-in real browser acceptance; separate temporary profile and native DLL. */
public final class WebView2TransportAcceptance extends Application {
    static volatile boolean passed;
    private WebView2Browser browser;
    private final Map<String,CompletableFuture<JsonNode>> received=new ConcurrentHashMap<>();
    private final CompletableFuture<Void> boot=new CompletableFuture<>();
    private final List<String> checks=new ArrayList<>();
    @Override public void start(Stage stage)throws Exception {
        Path directory=WebView2TemporaryDirectories.create("transport-acceptance-");
        var area=new Region();stage.setTitle("QuizForge transport acceptance "+UUID.randomUUID());stage.setScene(new Scene(area,720,500));stage.show();
        browser=new WebView2Browser(stage,area,directory,event->{
            if("boot".equals(event.path("kind").asText()))boot.complete(null);
            String id=event.path("id").asText();var pending=received.get(id);if(pending!=null)pending.complete(event);
        });
        CompletableFuture.runAsync(()->{
            try{
                boot.get(20,TimeUnit.SECONDS);
                var bundles=new ArrayList<JsonNode>();
                for(String slug:List.of("single-choice","multiple-choice","true-false")){
                    var manifest=DocumentJson.mapper().readTree(Files.readString(Path.of("extensions/packages",slug,"manifest.json")));
                    bundles.add(DocumentJson.mapper().readTree(Files.readString(Path.of("extensions/dist",manifest.path("id").asText()+"-"+manifest.path("version").asText()+".bundle.json"))));
                }
                browser.evaluate("window.transportPackages="+DocumentJson.mapper().writeValueAsString(bundles)+";Promise.all(window.transportPackages.map(bundle=>window.questionExtensions.install(bundle))).then(()=>window.transportSchemasReady=true,e=>window.transportSchemasError=String(e));true").toCompletableFuture().get(8,TimeUnit.SECONDS);
                long schemaDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                while(!browser.evaluate("window.transportSchemasReady===true").toCompletableFuture().get(8,TimeUnit.SECONDS).asBoolean()){
                    if(System.nanoTime()>schemaDeadline)throw new AssertionError("Schema workers failed: "+browser.evaluate("window.transportSchemasError").toCompletableFuture().get(8,TimeUnit.SECONDS));Thread.sleep(30);
                }
                checks.add("three installed types validate in trusted Workers under real WebView2 CSP");
                browser.evaluate("window.__qfVerificationTemplate=window.transportPackages.find(b=>b.manifest.id==='quizforge.types.single-choice').assets[0].defaultQuestion;window.webview2Verification.showEditor();true").toCompletableFuture().get(8,TimeUnit.SECONDS);
                long editorDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                while(!browser.evaluate("Boolean(document.querySelector('[data-qf-ready=\"true\"]'))").toCompletableFuture().get(8,TimeUnit.SECONDS).asBoolean()){
                    if(System.nanoTime()>editorDeadline)throw new AssertionError("Extension editor failed to render");Thread.sleep(30);
                }
                checks.add("installed single choice editor renders after asynchronous schema validation");
                browser.evaluate("window.webview2Verification.hideEditor();true").toCompletableFuture().get(8,TimeUnit.SECONDS);
                String document="<p>"+"x".repeat(9*1024*1024)+"中文\"\\😀</p>";
                // The core document bound accepts this; transport must not impose the old 8 Mi limit.
                new io.quizforge.core.practice.EssayPracticeAnswer("fixture",document);
                browser.post(Map.of("kind","page-state","questionId","transport-test","state",Map.of("document",document)));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                while(browser.evaluate("JSON.parse(window.pageHost.state('transport-test')).data?.document?.length??0").toCompletableFuture().get(8,TimeUnit.SECONDS).asInt()!=document.length()){
                    if(System.nanoTime()>deadline)throw new AssertionError("State not applied");Thread.sleep(20);
                }
                JsonNode size=browser.evaluate("JSON.parse(window.pageHost.state('transport-test')).data?.document?.length??0").toCompletableFuture().get(8,TimeUnit.SECONDS);
                if(size.asInt()!=document.length())throw new AssertionError("Host document truncated: "+size);
                checks.add("9 Mi document reaches trusted practice page losslessly");
                received.put("sdk-large",new CompletableFuture<>());
                browser.evaluate("window.pageHost.request('transport-test','sdk-large','transport-test',JSON.stringify({document:JSON.parse(window.pageHost.state('transport-test')).data.document}));true").toCompletableFuture().get(8,TimeUnit.SECONDS);
                JsonNode returned=received.get("sdk-large").get(20,TimeUnit.SECONDS);
                if(!document.equals(returned.path("argument").path("document").asText()))throw new AssertionError("SDK reverse document changed");
                checks.add("9 Mi document returns through actual pageHost request and chunk transport");
                received.put("raw-large",new CompletableFuture<>());
                browser.evaluate("window.chrome.webview.postMessage({kind:'raw-test',id:'raw-large',document:'x'.repeat(9*1024*1024)});true").toCompletableFuture().get(8,TimeUnit.SECONDS);
                if(received.get("raw-large").get(20,TimeUnit.SECONDS).path("document").asText().length()!=9*1024*1024)throw new AssertionError("Raw result truncated");
                checks.add("legacy oversized native event is chunked without closing browser");
                JsonNode evaluation=browser.evaluate("'x'.repeat(9*1024*1024)").toCompletableFuture().get(8,TimeUnit.SECONDS);
                if(evaluation.asText().length()!=9*1024*1024)throw new AssertionError("Evaluation truncated");checks.add("9 Mi evaluation result is assembled");
                browser.evaluate("window.chrome.webview.postMessage({kind:'qf-transport-chunk',id:'bad',index:1,total:2,length:1048577,data:'x'});true").toCompletableFuture().get(8,TimeUnit.SECONDS);
                if(!browser.evaluate("1+1").toCompletableFuture().get(8,TimeUnit.SECONDS).isInt())throw new AssertionError("Browser died after malformed event");
                checks.add("malformed transfer is rejected and browser remains responsive");
                passed=true;System.out.println("WEBVIEW2_LARGE_MESSAGE_VERIFY_PASS "+checks);
            }catch(Throwable failure){failure.printStackTrace();System.err.println("WEBVIEW2_LARGE_MESSAGE_VERIFY_FAILED");}
            finally{
                Platform.runLater(()->{
                    browser.close();WebView2TemporaryDirectories.release(directory,browser.closeCompletion());browser.closeCompletion().whenComplete((value,error)->{
                        if(error!=null){passed=false;error.printStackTrace();}
                        else System.out.println("WEBVIEW2_TRANSPORT_CLOSE_COMPLETE");
                        Platform.runLater(()->{stage.hide();Platform.exit();});
                    });
                });
            }
        });
    }
}
