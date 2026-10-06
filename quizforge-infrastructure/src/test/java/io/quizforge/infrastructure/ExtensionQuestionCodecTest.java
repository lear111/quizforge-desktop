package io.quizforge.infrastructure;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.model.extension.ExtensionAnswerSpec;
import io.quizforge.core.question.model.extension.ExtensionPayload;
import io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionQuestionCodecTest {
    private QuestionBank bank() {
        Question question = new Question("q_external","UNINSTALLED_CODEC_EXAMPLE",List.of(),new TextContent("Opaque question"),
                new ExtensionPayload(Map.of("dataVersion",1,"items",List.of(Map.of("id","item_test","text","First")),"extra",Map.of("flag",true))),
                new ExtensionAnswerSpec(Map.of("correct",true)),new ScoreSpec(BigDecimal.ONE),null,new TextContent("Explanation"),List.of());
        return new QuestionBank("qb_external","Extension bank",List.of(),List.of(question),List.of());
    }
    @Test void unknownExtensionRoundTripsWithoutRegisteringOrLosingItsData() {
        var codec = new QuestionBankV2Codec(); var bank = bank();
        assertEquals(bank,codec.parse(codec.write(bank)));
        assertEquals(codec.contentId(bank),codec.contentId(codec.parse(codec.write(bank))));
        assertTrue(QuestionTypes.find("UNINSTALLED_CODEC_EXAMPLE").isEmpty());
        assertTrue(codec.write(bank).contains("\"kind\" : \"EXTENSION\""));
    }
    @Test void anAlreadyCreatedCodecHandlesLaterInstalledTypes() {
        var codec = new QuestionBankV2Codec();
        QuestionTypes.register(new ExternalQuestionTypeDefinition("UNINSTALLED_CODEC_EXAMPLE","Codec example",QuestionTypeDefinition.Family.OBJECTIVE,"1.0.0",
                (operation,input)->Map.of("errors",List.of())));
        try { assertEquals(bank(),codec.parse(codec.write(bank()))); }
        finally { QuestionTypes.unregister("UNINSTALLED_CODEC_EXAMPLE"); }
    }
}
