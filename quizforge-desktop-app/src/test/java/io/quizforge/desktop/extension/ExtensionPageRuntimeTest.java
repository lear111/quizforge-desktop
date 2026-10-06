package io.quizforge.desktop.extension;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExtensionPageRuntimeTest {
    private static final String SESSION="qf-page-runtime-test";
    @BeforeAll static void fx()throws Exception {
        var ready=new CompletableFuture<Void>();
        try{Platform.startup(()->{Platform.setImplicitExit(false);ready.complete(null);});}catch(IllegalStateException already){ready.complete(null);}
        ready.get(10,TimeUnit.SECONDS);
    }
    private static String document(String extra){return """
        <!doctype html><meta charset=utf-8><meta http-equiv="Content-Security-Policy" content="default-src 'none';script-src 'nonce-qf-isolated-page-v1';style-src 'unsafe-inline'"><style>body{margin:0;background:white}label{display:block;height:50px}input[type=text]{height:35px}</style>
        <label><input type=radio id=option>Choose me</label><input type=text id=text>
        <script nonce=qf-isolated-page-v1>
          const post=m=>parent.postMessage({...m,channel:'qf-type-frame',session:'qf-page-runtime-test'},'*');
          document.querySelector('#option').addEventListener('change',()=>post({kind:'selected',checked:document.querySelector('#option').checked}));
          document.querySelector('#text').addEventListener('input',()=>post({kind:'typed',text:document.querySelector('#text').value}));
          window.addEventListener('message',event=>{if(event.source===parent&&event.data.kind==='loop')while(true){}});
          post({kind:'geometry',height:200,controls:[]});post({kind:'ready'});
        """+extra+"</script>";}
    @Test void pageRendersAndNativeMouseAndTextReachItsDom()throws Exception {
        var events=new LinkedBlockingQueue<Map<String,Object>>();
        String probe="const remote=document.createElement('script');remote.nonce='qf-isolated-page-v1';remote.src='data:text/javascript,parent.postMessage({channel:%22qf-type-frame%22,session:%22qf-page-runtime-test%22,kind:%22external-script-ran%22},%22*%22)';remote.onerror=()=>post({kind:'external-script-blocked'});document.body.append(remote);";
        try(var page=new ExtensionPageRuntime(document(probe),SESSION,720,events::add)){
            assertEquals("ready",await(events,"page","ready").get("kind"));
            assertEquals("external-script-blocked",await(events,"page","external-script-blocked").get("kind"));
            page.send(Map.of("command","viewport","width",720,"height",400,"offset",0));
            var image=await(events,"image",null);assertTrue(((String)image.get("png")).length()>200);assertTrue(page.workerPid()>0);
            var pixels=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode((String)image.get("png"))));
            int dark=0;for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){int rgb=pixels.getRGB(x,y);if((rgb>>>24)>200&&(rgb&0xff)<150&&((rgb>>>8)&0xff)<150&&((rgb>>>16)&0xff)<150)dark++;}
            assertTrue(dark>25,"The snapshot must contain rendered controls/text, not an empty bitmap");
            page.send(Map.of("command","input","event",Map.of("type","pointerdown","x",8,"y",8,"button",0,"buttons",1)));
            page.send(Map.of("command","input","event",Map.of("type","pointerup","x",8,"y",8,"button",0,"buttons",0)));
            assertEquals(true,await(events,"page","selected").get("checked"));
            page.send(Map.of("command","input","event",Map.of("type","pointerdown","x",40,"y",65,"button",0,"buttons",1)));
            page.send(Map.of("command","input","event",Map.of("type","pointerup","x",40,"y",65,"button",0,"buttons",0)));
            page.send(Map.of("command","input","event",Map.of("type","text","text","中A")));
            var text=await(events,"page","typed");if(!text.get("text").equals("中A"))text=await(events,"page","typed");
            assertEquals("中A",text.get("text"));
        }
    }
    @Test void synchronousLoopIsKilledWhileApplicationFxThreadRemainsAvailable()throws Exception {
        var events=new LinkedBlockingQueue<Map<String,Object>>();
        try(var page=new ExtensionPageRuntime(document(""),SESSION,720,events::add,ExtensionPageRuntime.STARTUP,Duration.ofSeconds(2))){
            await(events,"page","ready");long pid=page.workerPid();
            page.send(Map.of("command","send","message",Map.of("kind","loop","channel","qf-type-frame","session",SESSION)));
            var pulse=new CompletableFuture<Void>();Platform.runLater(()->pulse.complete(null));pulse.get(500,TimeUnit.MILLISECONDS);
            assertEquals("EXTENSION_TIMEOUT",await(events,"failure",null).get("code"));assertTrue(page.isClosed());
            ProcessHandle.of(pid).ifPresent(p->{try{p.onExit().get(5,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}assertFalse(p.isAlive());});
        }
        try(var restored=new ExtensionPageRuntime(document(""),SESSION,720,events::add)){await(events,"page","ready");assertFalse(restored.isClosed());}
    }
    private static Map<String,Object> await(BlockingQueue<Map<String,Object>> events,String kind,String message)throws Exception {
        long deadline=System.nanoTime()+ExtensionPageRuntime.STARTUP.plusSeconds(5).toNanos();
        while(System.nanoTime()<deadline){var event=events.poll(1,TimeUnit.SECONDS);if(event==null)continue;
            if(event.get("kind").equals("failure")&&!kind.equals("failure"))fail(event.toString());
            if(!kind.equals(event.get("kind")))continue;
            if(message==null)return event;
            @SuppressWarnings("unchecked")var value=(Map<String,Object>)event.get("message");if(message.equals(value.get("kind")))return value;
        }throw new AssertionError("Missing "+kind+"/"+message);
    }
}
