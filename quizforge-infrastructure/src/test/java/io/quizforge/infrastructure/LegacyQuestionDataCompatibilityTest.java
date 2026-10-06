package io.quizforge.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.question.codec.QuestionDataCodec;
import io.quizforge.core.question.compat.cloze.*;
import io.quizforge.core.question.compat.essay.*;
import io.quizforge.core.question.compat.matching.*;
import io.quizforge.core.question.compat.reading.*;
import io.quizforge.core.question.compat.translation.*;
import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.model.choice.ChoiceOption;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyQuestionDataCompatibilityTest {
    @Test void storedFormatsRetainNestedIdentityContentAndHashWithoutInstalledTypes() throws Exception {
        var codec = new QuestionBankV2Codec();
        var json = new ObjectMapper();
        var rich = new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("Reference"))))));
        var options = List.of(new ChoiceOption("opt_a", new TextContent("A")), new ChoiceOption("opt_b", new TextContent("B")));
        var questions = List.of(
                question("CLOZE", new ClozePayload(List.of(new ClozeBlank("blank_a", 1, options))),
                        new ClozeAnswerSpec(List.of(new ClozeAnswerSpec.Answer("blank_a", "opt_b")))),
                question("READING", new ReadingPayload(List.of(new ReadingItem("item_a", 1, rich, options))),
                        new ReadingAnswerSpec(List.of(new ReadingAnswerSpec.Answer("item_a", "opt_b")))),
                question("MATCHING", new MatchingPayload(
                        IntStream.rangeClosed(1, 8).mapToObj(n -> new MatchingBlank("blank_" + n, n, n <= 3)).toList(),
                        IntStream.rangeClosed(1, 8).mapToObj(n -> new MatchingOption("opt_" + n, "" + (char) ('A' + n - 1))).toList()),
                        new MatchingAnswerSpec(IntStream.rangeClosed(1, 8).mapToObj(n -> new MatchingAnswerSpec.Answer("blank_" + n, "opt_" + n)).toList())),
                question("TRANSLATION", new TranslationPayload(List.of(new TranslationItem("item_a", 1, "Original"))),
                        new TranslationAnswerSpec(List.of(new TranslationAnswerSpec.Answer("item_a", rich)))),
                question("ESSAY", new EssayPayload("Write here"), new EssayAnswerSpec(rich)));
        for (var question : questions) {
            assertTrue(QuestionTypes.find(question.type()).isEmpty(), question.type());
            var bank = new QuestionBank("qb_legacy", "Legacy", List.of(), List.of(question), List.of());
            var source = codec.write(bank);
            assertEquals(bank, codec.parse(source), question.type());
            assertEquals(codec.contentId(bank), codec.contentId(codec.parse(source)), question.type());
            assertFalse(source.contains("io.quizforge"));
            var persisted = QuestionDataCodec.encodePersisted(question);
            assertEquals(question.type(), ((Map<?, ?>) persisted.get("payload")).get("kind"));
            assertEquals(question.type(), ((Map<?, ?>) persisted.get("answerSpec")).get("kind"));
            var tree = json.readTree(source);
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree).set("questions", json.valueToTree(List.of(persisted)));
            assertEquals(bank, codec.parse(tree.toString()), question.type() + " page/snapshot encoding");
        }
    }

    private Question question(String type, QuestionPayload payload, QuestionAnswerSpec answer) {
        return new Question("q_legacy", type, List.of(), new TextContent("Prompt"), payload, answer,
                new ScoreSpec(new BigDecimal("2.5")), null, null, List.of());
    }
}
