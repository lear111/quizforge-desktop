package io.quizforge.desktop.browser.webview2;

import com.fasterxml.jackson.databind.JsonNode;
import io.quizforge.infrastructure.json.DocumentJson;
import java.io.IOException;
import java.util.function.LongSupplier;

/** Trusted transport framing. Only a bounded ordered message may be assembled at a time. */
final class WebView2MessageAssembler {
    static final int CHUNK_CHARACTERS = 1024 * 1024;
    static final int MAX_MESSAGE_CHARACTERS = 128 * 1024 * 1024;
    private final LongSupplier clock;
    private String id;
    private int index,total,length;
    private long started;
    private StringBuilder encoded;
    WebView2MessageAssembler(){this(System::currentTimeMillis);}
    WebView2MessageAssembler(LongSupplier clock){this.clock=clock;}
    void reset(){id=null;encoded=null;index=total=length=0;}
    void expire(){if(encoded!=null&&clock.getAsLong()-started>30000)reset();}
    JsonNode accept(JsonNode packet)throws IOException {
        expire();
        if("native-error".equals(packet.path("kind").asText()))reset();
        if(!"qf-transport-chunk".equals(packet.path("kind").asText()))return packet;
        try {
            String messageId=packet.path("id").asText();
            int part=integer(packet,"index"),count=integer(packet,"total"),characters=integer(packet,"length");
            JsonNode data=packet.path("data");
            if(!messageId.matches("[a-zA-Z0-9-]{1,80}")||part<0||count<1||count>MAX_MESSAGE_CHARACTERS/CHUNK_CHARACTERS||part>=count||
                    characters<1||characters>MAX_MESSAGE_CHARACTERS||((characters-1)/CHUNK_CHARACTERS+1)!=count||!data.isTextual()||
                    data.asText().length()!=Math.min(CHUNK_CHARACTERS,characters-part*CHUNK_CHARACTERS))throw new IOException("Invalid browser transport chunk");
            if(part==0){
                if(encoded!=null)throw new IOException("Overlapping browser transport messages");
                id=messageId;index=0;total=count;length=characters;started=clock.getAsLong();encoded=new StringBuilder(Math.min(length,CHUNK_CHARACTERS*2));
            }
            if(encoded==null||!id.equals(messageId)||index!=part||total!=count||length!=characters)throw new IOException("Out-of-order browser transport chunk");
            encoded.append(data.asText());index++;
            if(index!=total)return null;
            String value=encoded.toString();reset();
            JsonNode result=DocumentJson.mapper().readTree(value);
            if(result==null||!result.isObject()||"qf-transport-chunk".equals(result.path("kind").asText()))throw new IOException("Invalid browser transport payload");
            return result;
        }catch(IOException|RuntimeException invalid){reset();throw invalid;}
    }
    private static int integer(JsonNode packet,String field)throws IOException{
        JsonNode value=packet.path(field);if(!value.isIntegralNumber()||!value.canConvertToInt())throw new IOException("Invalid transport integer");return value.intValue();
    }
}
