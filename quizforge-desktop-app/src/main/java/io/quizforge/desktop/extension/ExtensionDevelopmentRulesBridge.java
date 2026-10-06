package io.quizforge.desktop.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.question.type.extension.ExtensionExecutionException;
import io.quizforge.infrastructure.extension.ExtensionPackageStore.TypeAssets;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;

/** Trusted workbench only: candidate rules use the same killable worker as installed packages. */
public final class ExtensionDevelopmentRulesBridge implements AutoCloseable {
    private final ObjectMapper json = new ObjectMapper();
    private final ExtensionRuleRuntime runtime;
    private final WebEngine engine;
    private final Set<String> types;
    private volatile boolean closed;
    public ExtensionDevelopmentRulesBridge(WebEngine engine, List<TypeAssets> assets) throws java.io.IOException {
        this.engine=engine;
        types=assets.stream().map(asset->asset.type().id()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String,Map<String,Object>> templates=new LinkedHashMap<>();
        for(var asset:assets)templates.put(asset.type().id(),json.readValue(asset.defaultQuestionSource(),new TypeReference<>(){}));
        String source=assets.stream().map(TypeAssets::rulesSource).distinct().collect(java.util.stream.Collectors.joining("\n;\n"));
        runtime=new ExtensionRuleRuntime(source,templates);
    }
    public void request(String id, String type, String operation, String input) {
        if(closed || id==null || !id.matches("[0-9]{1,16}"))return;
        CompletableFuture.supplyAsync(()->{
            try {
                if(closed || !types.contains(type) || !Set.of("createDraft","validate","duplicate","snapshot","targets","validateAnswer","grade").contains(operation)
                        || input==null || input.length()>ExtensionRuleProtocol.MAX_LINE) throw new IllegalArgumentException("Invalid rules request");
                runtime.ready().toCompletableFuture().join();
                var result=runtime.invoke(type,operation,json.readValue(input,new TypeReference<Map<String,Object>>(){}));
                return Map.<String,Object>of("id",id,"value",json.writeValueAsString(result));
            }catch(Exception failure){
                Throwable cause=failure;while(cause.getCause()!=null && !(cause instanceof ExtensionExecutionException))cause=cause.getCause();
                return Map.<String,Object>of("id",id,"error",Map.of("code",cause instanceof ExtensionExecutionException execution?execution.code():"EXTENSION_FAILED",
                        "message",cause.getMessage()==null?"开发规则执行失败":cause.getMessage()));
            }
        }).thenAccept(reply->Platform.runLater(()->{
            if(closed)return;
            JSObject window=null;
            try {
                window=(JSObject)engine.executeScript("window");window.setMember("__rulesReply",json.writeValueAsString(reply));
                engine.executeScript("window.qfNativeRules?.replyFromJson(window.__rulesReply)");
            }catch(Exception ignored){close();}
            finally{if(window!=null)try{window.removeMember("__rulesReply");}catch(RuntimeException ignored){}}
        }));
    }
    @Override public void close(){if(closed)return;closed=true;runtime.close();}
}
