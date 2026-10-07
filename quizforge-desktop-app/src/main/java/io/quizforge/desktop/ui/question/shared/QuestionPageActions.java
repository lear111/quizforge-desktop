package io.quizforge.desktop.ui.question.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import io.quizforge.infrastructure.json.DocumentJson;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** Navigation, viewing mode and opening sources only. This bridge has no persistence mutations. */
public final class QuestionPageActions {
    private final WebView view;
    private final Supplier<String> identity;
    private final BooleanSupplier closed;
    private Supplier<Map<String,Object>> state;
    private BiFunction<String,Object,CompletionStage<Void>> command;
    private boolean busy;
    public QuestionPageActions(WebView view,Supplier<String> identity,BooleanSupplier closed){this.view=view;this.identity=identity;this.closed=closed;}
    public void configure(Supplier<Map<String,Object>> state,BiFunction<String,Object,CompletionStage<Void>> command){this.state=state;this.command=command;}
    public String state(String questionId){
        try {check(questionId);return DocumentJson.mapper().writeValueAsString(Map.of("ok",true,"data",state.get()));}
        catch(Exception failure){return encode(error(failure instanceof Unavailable?"CAPABILITY_UNAVAILABLE":"STALE_PAGE",failure.getMessage()));}
    }
    public void request(String questionId,String id,String action,String argument){
        Platform.runLater(()->{
            if(closed.getAsBoolean())return;
            if(busy){reply(id,error("BUSY","宿主操作正在完成"));return;}
            try {
                check(questionId);
                if(!Set.of("navigate","learning.mode","source.open","attempt.previous","attempt.next","attempt.current").contains(action))throw new IllegalArgumentException("不支持的页面操作");
                Object value=DocumentJson.mapper().readValue(argument,new TypeReference<Object>() { });
                busy=true;
                command.apply(action,value).whenComplete((ignored,failure)->{
                    Runnable complete=()->{
                        busy=false;
                        if(failure==null)reply(id,Map.of("ok",true,"data",Map.of()));
                        else reply(id,error("HOST_ACTION_FAILED",message(failure)));
                    };
                    if(Platform.isFxApplicationThread())complete.run();else Platform.runLater(complete);
                });
            }catch(Exception failure){busy=false;reply(id,error(failure instanceof Unavailable?"CAPABILITY_UNAVAILABLE":"HOST_ACTION_FAILED",message(failure)));}
        });
    }
    private void check(String questionId){
        if(closed.getAsBoolean()||questionId==null||!Objects.equals(questionId,identity.get()))throw new IllegalStateException("题目已切换或页面已关闭");
        if(state==null||command==null)throw new Unavailable();
    }
    private void reply(String id,Map<String,Object> reply){
        if(closed.getAsBoolean())return;
        var window=(JSObject)view.getEngine().executeScript("window");
        window.setMember("__qfPageReply",encode(Map.of("id",id,"reply",reply)));
        try{view.getEngine().executeScript("window.qfPageActions.replyFromJson(window.__qfPageReply)");}
        finally{window.removeMember("__qfPageReply");}
    }
    public static int index(Object value,int count){
        if(!(value instanceof Number number)||!Double.isFinite(number.doubleValue())||number.doubleValue()!=Math.rint(number.doubleValue())||number.doubleValue()<0||number.doubleValue()>=count)
            throw new IllegalArgumentException("索引超出范围");
        return number.intValue();
    }
    private static Map<String,Object> error(String code,String message){return Map.of("ok",false,"error",Map.of("code",code,"message",message==null?"页面操作失败":message,"retryable",false));}
    private static String encode(Object value){try{return DocumentJson.mapper().writeValueAsString(value);}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static String message(Throwable failure){while(failure.getCause()!=null)failure=failure.getCause();return failure.getMessage();}
    private static final class Unavailable extends IllegalStateException{Unavailable(){super("此宿主未提供页面操作");}}
}
