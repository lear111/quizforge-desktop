package io.quizforge.core.document.navigation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuizForgeNavigationLinkCodecTest {
    private final QuizForgeNavigationLinkCodec codec = new QuizForgeNavigationLinkCodec();

    @Test void assetLinkRoundTripsWithoutARevisionOrPath() {
        var link = QuizForgeNavigationLink.asset("doc_abc");
        assertEquals("quizforge://asset/doc_abc", codec.encode(link));
        assertEquals(link, codec.decode(codec.encode(link)));
    }

    @Test void headingLinkRoundTripsChineseSpacesAndSpecialCharacters() {
        var link = QuizForgeNavigationLink.heading("doc_abc", "  第三章 / ArrayList? #1  ", 2);
        String uri = codec.encode(link);
        assertTrue(uri.startsWith("quizforge://asset/doc_abc/heading/%E7%AC%AC"));
        assertTrue(uri.contains("%20%2F%20ArrayList%3F%20%231"));
        assertEquals(link, codec.decode(uri));
        assertFalse(uri.contains("contentId"));
    }

    @Test void anchorLinkRoundTripsChineseAndSpecialCharacters() {
        var link = QuizForgeNavigationLink.anchor("doc_abc", "第三题来源 & Java+集合", 2);
        String uri = codec.encode(link);
        assertTrue(uri.contains("%E7%AC%AC"));
        assertTrue(uri.contains("%20%26%20Java%2B"));
        assertEquals(link, codec.decode(uri));
    }

    @Test void occurrenceMustBePositiveAndNumeric() {
        for (String query : new String[] {"occurrence=0", "occurrence=-1",
                "occurrence=word", "occurrence=", "occurrence=999999999999999999999"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> codec.decode("quizforge://asset/doc_abc/heading/Example?" + query));
        }
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("quizforge://asset/doc_abc/heading/Example"));
        assertThrows(IllegalArgumentException.class,
                () -> QuizForgeNavigationLink.heading("doc_abc", "Example", 0));
        assertThrows(IllegalArgumentException.class,
                () -> QuizForgeNavigationLink.anchor("doc_abc", "Example", -1));
    }

    @Test void invalidTargetsAndNamesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("quizforge://asset/doc_abc/question/q_1?occurrence=1"));
        assertThrows(IllegalArgumentException.class,
                () -> QuizForgeNavigationLink.heading("doc_abc", "  ", 1));
        assertThrows(IllegalArgumentException.class,
                () -> QuizForgeNavigationLink.anchor("doc_abc", "bad --> name", 1));
        assertThrows(IllegalArgumentException.class,
                () -> QuizForgeNavigationLink.asset(""));
    }

    @Test void malformedUrisExtraParametersAndInvalidUtf8AreRejected() {
        for (String value : new String[] {
                "https://asset/doc_abc", "quizforge://document/doc_abc",
                "quizforge://asset/doc_abc?occurrence=1",
                "quizforge://asset/doc_abc/heading/Example?occurrence=1&contentId=abc",
                "quizforge://asset/doc_abc/heading/%FF?occurrence=1",
                "quizforge://asset/doc_abc/heading/%GG?occurrence=1",
                "quizforge://asset/doc_abc/heading/Example?occurrence=1#fragment",
                "quizforge://asset//heading/Example?occurrence=1"}) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(value), value);
        }
    }
}
