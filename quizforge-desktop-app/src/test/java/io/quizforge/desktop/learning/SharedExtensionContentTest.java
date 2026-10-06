package io.quizforge.desktop.learning;

import io.quizforge.core.practice.PracticePayload;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedExtensionContentTest {
    @Test void largeEmbeddedImageFitsTheSupportedDocumentLimit() {
        var image="data:image/png;base64,"+"A".repeat(21_000_000);
        var doc="{\"data\":{\"main\":[{\"type\":\"image\",\"value\":\""+image+"\"}]}}";
        var encoded=Base64.getEncoder().encodeToString(doc.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var content=SharedContent.read(Map.of("kind","DOCUMENT","resourceId","res_doc","text",""),
                Map.of("resourceData",Map.of("res_doc",encoded)));
        var data=(Map<?,?>)((Map<?,?>)content.document()).get("data");
        assertEquals(image,((Map<?,?>)((List<?>)data.get("main")).getFirst()).get("value"));
    }
    @Test void nestedRichFieldsResolveFromFrozenResourcesWithoutRewritingLogicalData() {
        var raw = Map.of("kind","DOCUMENT","resourceId","res_doc","text","Frozen words");
        var doc = "{\"data\":{\"main\":[{\"value\":\"Frozen words\",\"bold\":true}]}}";
        var frozen = Map.of("id","q_ext","type","sample.custom","prompt",Map.of("kind","TEXT","text","Prompt"),
                "payload",Map.of("items",List.of(raw)),"answerSpec",Map.of("reference",raw),"analysis",raw);
        var standard=Map.of("version","1.0.0","question",frozen,"answerSpec",Map.of("reference",raw));
        var publicQuestion=new LinkedHashMap<String,Object>(frozen);publicQuestion.remove("answerSpec");publicQuestion.remove("analysis");
        var presentation=Map.of("extensionId","sample.custom","dataVersion",1,"question",publicQuestion,
                "data",Map.of("caption",raw),"resourceData",Map.of("res_doc",Base64.getEncoder().encodeToString(doc.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        var metadata=Map.of("extension",standard,"extensionPresentation",presentation);
        var projected=SharedPracticeViewModel.project("sample.custom",new SharedPracticeViewModel.Text("TEXT",""),
                new PracticePayload(List.of()),metadata,List.of(),List.of(),false,new PracticePayload(Map.of()));
        var display=(SharedPracticeViewModel.ExtensionPresentation)projected.presentation();
        var nested=(Map<?,?>)((List<?>)((Map<?,?>)display.question().get("payload")).get("items")).getFirst();
        assertTrue(nested.get("document") instanceof Map);
        assertNull(display.reference());assertFalse(display.question().containsKey("analysis"));
        assertTrue(((Map<?,?>)((Map<?,?>)display.data()).get("caption")).get("document") instanceof Map);
        assertFalse(raw.containsKey("document"));
        var submitted=(SharedPracticeViewModel.ExtensionPresentation)SharedPracticeViewModel.project("sample.custom",
                new SharedPracticeViewModel.Text("TEXT",""),new PracticePayload(List.of()),metadata,List.of(),List.of(),true,
                new PracticePayload(Map.of())).presentation();
        var reference=(Map<?,?>)submitted.reference();
        var answerSpec=(Map<?,?>)reference.get("answerSpec");
        assertTrue(((Map<?,?>)answerSpec.get("reference")).get("document") instanceof Map);
        assertFalse(submitted.question().containsKey("answerSpec"));
        assertTrue(((Map<?,?>)reference.get("analysis")).get("document") instanceof Map);
        assertTrue(((Map<?,?>)submitted.question().get("analysis")).get("document") instanceof Map);
        assertEquals("1.0.0",submitted.extensionVersion());
        var resourceDescriptor=Map.of("kind","DOCUMENT","id","res_doc");
        assertEquals(resourceDescriptor,SharedContent.resolveNested(resourceDescriptor,Map.of()));
    }
}
