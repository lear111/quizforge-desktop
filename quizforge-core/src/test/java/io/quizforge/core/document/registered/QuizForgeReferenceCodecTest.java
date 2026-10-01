package io.quizforge.core.document.registered;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuizForgeReferenceCodecTest {
    private final QuizForgeReferenceCodec codec = new QuizForgeReferenceCodec();
    private static final String REVISION = "qfd:v2:" + "a".repeat(64);

    @Test void documentReferenceRoundTrips() {
        var reference = QuizForgeReference.document("doc_123");
        assertEquals("quizforge://document/doc_123", codec.encode(reference));
        assertEquals(reference, codec.decode(codec.encode(reference)));
    }

    @Test void anchorReferenceEncodesNameOccurrenceAndContentId() {
        var reference = QuizForgeReference.anchor("doc_123", REVISION, "第三题来源", 2);
        String uri = codec.encode(reference);
        assertEquals("quizforge://document/doc_123/anchor/%E7%AC%AC%E4%B8%89%E9%A2%98%E6%9D%A5%E6%BA%90?occurrence=2&contentId=qfd%3Av2%3A"
                + "a".repeat(64), uri);
        assertEquals(reference, codec.decode(uri));
    }

    @Test void invalidSchemeOrMissingIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("https://document/doc_123"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("quizforge://document/"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("quizforge://document/doc_123/anchor/?occurrence=1&contentId=" + REVISION));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("quizforge://document/doc_123/anchor/Name?occurrence=0&contentId=" + REVISION));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("quizforge://document/doc_123/anchor/Name?occurrence=1&contentId=broken"));
    }
}
