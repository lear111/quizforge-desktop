package io.quizforge.desktop.browser.webview2;
import io.quizforge.infrastructure.json.DocumentJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

class WebView2MessageAssemblerTest {
    @Test void largeDocumentSurvivesChunkedRoundTrip()throws Exception{
        var value=DocumentJson.mapper().valueToTree(Map.of("kind","document","document","x".repeat(9*1024*1024)+"中文\"\\\n😀"));
        String encoded=DocumentJson.mapper().writeValueAsString(value);var reader=new WebView2MessageAssembler();
        com.fasterxml.jackson.databind.JsonNode result=null;
        int chunk=WebView2MessageAssembler.CHUNK_CHARACTERS,total=(encoded.length()-1)/chunk+1;
        for(int index=0;index<total;index++){
            var packet=DocumentJson.mapper().valueToTree(Map.of("kind","qf-transport-chunk","id","test-1","index",index,"total",total,"length",encoded.length(),"data",encoded.substring(index*chunk,Math.min(encoded.length(),(index+1)*chunk))));
            assertTrue(DocumentJson.mapper().writeValueAsString(packet).length()<8*1024*1024);
            result=reader.accept(packet);
        }
        assertEquals(value,result);
    }
    @Test void malformedAndExpiredTransfersAreDiscardedAndLaterMessagesWork()throws Exception{
        AtomicLong now=new AtomicLong();var reader=new WebView2MessageAssembler(now::get);
        int chunk=WebView2MessageAssembler.CHUNK_CHARACTERS;
        var first=DocumentJson.mapper().valueToTree(Map.of("kind","qf-transport-chunk","id","a","index",0,"total",2,"length",chunk+1,"data","x".repeat(chunk)));
        var second=DocumentJson.mapper().valueToTree(Map.of("kind","qf-transport-chunk","id","a","index",1,"total",2,"length",chunk+1,"data","x"));
        assertThrows(java.io.IOException.class,()->reader.accept(second));
        assertNull(reader.accept(first));now.set(30001);assertThrows(java.io.IOException.class,()->reader.accept(second));
        var normal=DocumentJson.mapper().valueToTree(Map.of("kind","ping"));assertEquals(normal,reader.accept(normal));
        assertNull(reader.accept(first));assertThrows(java.io.IOException.class,()->reader.accept(first));assertEquals(normal,reader.accept(normal));
    }
}
