package io.quizforge.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.InlineImageNode;
import io.quizforge.core.question.content.InlineMathNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.LineBreakNode;
import io.quizforge.core.question.content.LinkNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.EvaluationCriterion;
import io.quizforge.core.question.model.EvaluationSpec;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.model.Stimulus;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.model.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.model.choice.ChoiceOption;
import io.quizforge.core.question.model.choice.ChoicePayload;
import io.quizforge.core.question.content.QuestionText;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionBankV2CodecTest {
    private final QuestionBankV2Codec codec = new QuestionBankV2Codec();
    static QuestionBank valid(String type) {
        var question = Question.choice("q_one",type,new TextContent("Stem"),new TextContent("Analysis"),
                List.of(SourceRef.anchor("doc_one","qfd:v2:"+"a".repeat(64),"定义",2,"Document","Source snapshot")),
                new ChoicePayload(List.of(new ChoiceOption("opt_a",new TextContent("A")),
                        new ChoiceOption("opt_b",new TextContent("B")),new ChoiceOption("opt_c",new TextContent("C")))),
                new ChoiceAnswerSpec("SINGLE_CHOICE".equals(type) ? List.of("opt_a") : List.of("opt_a","opt_b")));
        return new QuestionBank("qb_one","Bank",List.of(),List.of(question),List.of());
    }
    private QuestionBank withQuestions(QuestionBank bank,List<Question> questions) {
        return new QuestionBank(bank.assetId(),bank.title(),bank.schemaVersion(),bank.stimuli(),questions,bank.resources());
    }
    private Question copy(Question q,QuestionContent prompt,ScoreSpec score,EvaluationSpec evaluation,
            List<String> stimuli,ChoiceAnswerSpec answer,QuestionContent analysis) {
        return new Question(q.id(),q.type(),stimuli,prompt,q.payload(),answer,score,evaluation,analysis,q.sourceRefs());
    }
    private QuestionBank richBank() {
        var image = new QBankResource("res_img_01",ResourceKind.IMAGE,"image/png","resources/res_img_01.png","b".repeat(64));
        var audio = new QBankResource("res_audio_01",ResourceKind.AUDIO,"audio/mpeg","resources/res_audio_01.mp3","c".repeat(64));
        var rich = new RichContent(new RichDocument(List.of(
                new ParagraphNode(List.of(new InlineTextNode("已知函数 "),new InlineMathNode("f(x)=x^2"),
                        new InlineTextNode("，观察 "),new InlineImageNode(image.id(),"函数图像"),
                        new LineBreakNode(),new LinkNode("https://example.org",List.of(new InlineTextNode("参考"))))),
                new BlockImageNode(image.id(),"函数图像","Figure 1"),new BlockMathNode("\\int_0^1 x^2 dx"))));
        var base=valid("SINGLE_CHOICE");
        var q=base.questions().getFirst();
        var question=copy(q,rich,q.scoreSpec(),null,List.of("stim_article"),q.choiceAnswerSpec(),q.analysis());
        return new QuestionBank(base.assetId(),base.title(),List.of(new Stimulus("stim_article",new TextContent("阅读材料"))),
                List.of(question),List.of(image,audio));
    }
    @Test void textAndBothChoiceTypesRoundTrip() {
        for (String type : List.of("SINGLE_CHOICE","MULTIPLE_CHOICE")) {
            var bank=valid(type); assertEquals(bank,codec.parse(codec.write(bank)));
            assertInstanceOf(TextContent.class,bank.questions().getFirst().prompt());
            assertTrue(bank.questions().getFirst().choicePayload().options().stream().allMatch(o -> o.content() instanceof TextContent));
            assertTrue(codec.write(bank).contains("\"kind\" : \"TEXT\""));
        }
    }
    @Test void richInlineBlockResourceAndStimulusRoundTrip() {
        var bank=richBank();
        String source=codec.write(bank);
        assertEquals(bank,codec.parse(source));
        assertTrue(source.contains("\"type\" : \"PARAGRAPH\""));
        assertTrue(source.contains("res_img_01"));
        assertFalse(source.contains("@class"));
        assertFalse(source.contains("io.quizforge"));
        assertThrows(UnsupportedOperationException.class,() -> QuestionText.prompt(bank.questions().getFirst()));
        assertFalse(QuestionText.supports(bank));
    }
    @Test void defaultScoreAndExactDecimalsRoundTrip() {
        var bank=valid("SINGLE_CHOICE"); var q=bank.questions().getFirst();
        assertEquals(BigDecimal.ONE,q.scoreSpec().defaultMaxScore());
        for (String score : List.of("0.5","1","1.5","2.5","10","1.50","0.12345678901234567890123456789")) {
            var edited=withQuestions(bank,List.of(copy(q,q.prompt(),new ScoreSpec(new BigDecimal(score)),null,
                    List.of(),q.choiceAnswerSpec(),q.analysis())));
            assertEquals(new BigDecimal(score),codec.parse(codec.write(edited)).questions().getFirst().scoreSpec().defaultMaxScore());
        }
    }
    @Test void rejectsMissingAndNonpositiveScores() {
        var bank=valid("SINGLE_CHOICE"); var q=bank.questions().getFirst();
        for (var score : List.of(new ScoreSpec(BigDecimal.ZERO),new ScoreSpec(new BigDecimal("-0.5"))))
            assertThrows(QuizForgeException.class,() -> codec.validate(withQuestions(bank,List.of(copy(q,q.prompt(),score,null,List.of(),q.choiceAnswerSpec(),q.analysis())))));
        assertThrows(QuizForgeException.class,() -> codec.validate(withQuestions(bank,List.of(copy(q,q.prompt(),null,null,List.of(),q.choiceAnswerSpec(),q.analysis())))));
    }
    @Test void evaluationRoundTripAndWeightRules() {
        var bank=valid("SINGLE_CHOICE"); var q=bank.questions().getFirst();
        var spec=new EvaluationSpec(List.of(new EvaluationCriterion("crit_a","正确性",new BigDecimal("0.75")),
                new EvaluationCriterion("crit_b","解释",new BigDecimal("0.25"))),"仅依据题库标准");
        var edited=withQuestions(bank,List.of(copy(q,q.prompt(),q.scoreSpec(),spec,List.of(),q.choiceAnswerSpec(),q.analysis())));
        assertEquals(edited,codec.parse(codec.write(edited)));
        for (var criteria : List.of(
                List.of(new EvaluationCriterion("crit_a","x",BigDecimal.ZERO)),
                List.of(new EvaluationCriterion("crit_a","x",new BigDecimal("-1"))),
                List.of(new EvaluationCriterion("crit_a","x",new BigDecimal("0.8"))),
                List.of(new EvaluationCriterion("crit_a","x",new BigDecimal("0.5")),new EvaluationCriterion("crit_a","y",new BigDecimal("0.5"))))) {
            var invalid=copy(q,q.prompt(),q.scoreSpec(),new EvaluationSpec(criteria,""),List.of(),q.choiceAnswerSpec(),q.analysis());
            assertThrows(QuizForgeException.class,() -> codec.validate(withQuestions(bank,List.of(invalid))));
        }
    }
    @Test void optionalAnalysisAndEvaluationRemainAbsent() {
        var bank=valid("SINGLE_CHOICE"); var q=bank.questions().getFirst();
        var edited=withQuestions(bank,List.of(copy(q,q.prompt(),q.scoreSpec(),null,List.of(),q.choiceAnswerSpec(),null)));
        String json=codec.write(edited);
        assertFalse(json.contains("\"analysis\"")); assertFalse(json.contains("\"evaluationSpec\""));
        assertEquals(edited,codec.parse(json));
        assertEquals("",QuestionText.analysis(edited.questions().getFirst()));
    }
    @Test void optionalQuestionFieldsNormalizeNullAndMissing() throws Exception {
        for (String type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE")) {
            var bank = valid(type);
            var q = bank.questions().getFirst();
            var absent = withQuestions(bank, List.of(copy(q, q.prompt(), q.scoreSpec(), null,
                    List.of(), q.choiceAnswerSpec(), null)));
            String missingJson = codec.write(absent);
            var json = new ObjectMapper();
            for (var fields : List.of(List.of("analysis"), List.of("evaluationSpec"),
                    List.of("analysis", "evaluationSpec"))) {
                var explicitNull = (ObjectNode) json.readTree(missingJson);
                var question = (ObjectNode) explicitNull.path("questions").get(0);
                fields.forEach(question::putNull);
                var normalized = codec.parse(json.writeValueAsString(explicitNull));
                assertEquals(absent, normalized);
                assertNull(normalized.questions().getFirst().analysis());
                assertNull(normalized.questions().getFirst().evaluationSpec());
                assertEquals(codec.contentId(absent), codec.contentId(normalized));
                assertEquals(missingJson, codec.write(normalized));
            }
        }
    }
    @Test void optionalNestedMetadataNormalizeNullAndMissing() throws Exception {
        var bank = richBank();
        var q = bank.questions().getFirst();
        var rich = (RichContent) q.prompt();
        var nestedImage = new InlineImageNode(bank.resources().getFirst().id(), "Nested image");
        var prompt = new RichContent(new RichDocument(List.of(
                new ParagraphNode(List.of(new LinkNode("https://example.org", List.of(nestedImage)))),
                rich.document().blocks().get(1))));
        var evaluation = new EvaluationSpec(List.of(), "Guidance");
        var edited = new Question(q.id(), q.type(), q.stimulusRefs(), prompt,
                new ChoicePayload(List.of(new ChoiceOption("opt_a", rich),
                        new ChoiceOption("opt_b", new TextContent("B")))), q.answerSpec(), q.scoreSpec(),
                evaluation, rich, q.sourceRefs());
        var fixture = new QuestionBank(bank.assetId(), bank.title(),
                List.of(new Stimulus("stim_article", rich)), List.of(edited), bank.resources());
        var json = new ObjectMapper();
        var missing = (ObjectNode) json.readTree(codec.write(fixture));
        var explicitNull = missing.deepCopy();
        rewriteOptionalMetadata(missing, false);
        rewriteOptionalMetadata(explicitNull, true);
        var missingBank = codec.parse(json.writeValueAsString(missing));
        var nullBank = codec.parse(json.writeValueAsString(explicitNull));
        assertEquals(missingBank, nullBank);
        assertEquals(codec.contentId(missingBank), codec.contentId(nullBank));
        var normalized = missingBank.questions().getFirst();
        assertNull(normalized.evaluationSpec().evaluatorGuidance());
        assertNull(normalized.sourceRefs().getFirst().documentTitle());
        assertNull(normalized.sourceRefs().getFirst().sectionTitle());
        var block = (BlockImageNode) ((RichContent) normalized.prompt()).document().blocks().get(1);
        assertNull(block.alt());
        assertNull(block.caption());
        var paragraph = (ParagraphNode) ((RichContent) normalized.prompt()).document().blocks().getFirst();
        assertNull(((InlineImageNode) ((LinkNode) paragraph.children().getFirst()).children().getFirst()).alt());
        assertEquals(missing, json.readTree(codec.write(nullBank)));
        assertEquals(nullBank, codec.parse(codec.write(nullBank)));
    }
    @Test void optionalValuesIncludingBlankMetadataArePreserved() throws Exception {
        var json = new ObjectMapper();
        var valued = (ObjectNode) json.readTree(codec.write(richBank()));
        var question = (ObjectNode) valued.path("questions").get(0);
        question.set("evaluationSpec", json.readTree("{\"criteria\":[],\"evaluatorGuidance\":\"\"}"));
        question.set("analysis", json.readTree("{\"kind\":\"TEXT\",\"text\":\"\"}"));
        var ref = (ObjectNode) question.path("sourceRefs").get(0);
        ref.put("documentTitle", "");
        ref.put("sectionTitle", "");
        var block = (ObjectNode) question.path("prompt").path("document").path("blocks").get(1);
        block.put("alt", "");
        block.put("caption", "");
        var bank = codec.parse(json.writeValueAsString(valued));
        assertEquals(valued, json.readTree(codec.write(bank)));
        assertEquals(bank, codec.parse(codec.write(bank)));
        var absent = valued.deepCopy();
        rewriteOptionalMetadata(absent, false);
        ((ObjectNode) absent.path("questions").get(0)).remove("analysis");
        assertNotEquals(codec.contentId(bank), codec.contentId(codec.parse(json.writeValueAsString(absent))));
    }
    @Test void optionalNonNullValuesStillRequireTheirDeclaredTypes() throws Exception {
        var json = new ObjectMapper();
        var source = (ObjectNode) json.readTree(codec.write(richBank()));
        for (String path : List.of("/questions/0/analysis", "/questions/0/evaluationSpec",
                "/questions/0/prompt/document/blocks/0/children/3/alt",
                "/questions/0/prompt/document/blocks/1/alt", "/questions/0/prompt/document/blocks/1/caption",
                "/questions/0/sourceRefs/0/documentTitle", "/questions/0/sourceRefs/0/sectionTitle")) {
            for (String value : List.of("[]", "42", "1.25", "true")) {
                var malformed = source.deepCopy();
                int slash = path.lastIndexOf('/');
                ((ObjectNode) malformed.at(path.substring(0, slash))).set(path.substring(slash + 1), json.readTree(value));
                assertThrows(QuizForgeException.class, () -> codec.parse(json.writeValueAsString(malformed)), path + ": " + value);
            }
        }
        for (String value : List.of("[]", "42", "1.25", "true")) {
            var malformed = source.deepCopy();
            ((ObjectNode) malformed.path("questions").get(0)).set("evaluationSpec",
                    json.readTree("{\"criteria\":[],\"evaluatorGuidance\":" + value + "}"));
            assertThrows(QuizForgeException.class, () -> codec.parse(json.writeValueAsString(malformed)));
        }
    }
    @Test void nullDoesNotMakeRequiredPropertiesOptional() throws Exception {
        var json = new ObjectMapper();
        var source = (ObjectNode) json.readTree(codec.write(richBank()));
        for (String path : List.of("/questions/0/prompt", "/questions/0/payload", "/questions/0/answerSpec",
                "/questions/0/scoreSpec", "/questions/0/scoreSpec/defaultMaxScore",
                "/questions/0/sourceRefs/0/anchorName", "/questions/0/sourceRefs/0/occurrence",
                "/questions/0/prompt/document/blocks/0/children/5/href",
                "/questions/0/payload/options/0/content/text", "/resources/0/mediaType", "/resources/0/sha256",
                "/stimuli/0/content", "/questions/0/stimulusRefs")) {
            var malformed = source.deepCopy();
            int slash = path.lastIndexOf('/');
            ((ObjectNode) malformed.at(path.substring(0, slash))).putNull(path.substring(slash + 1));
            assertThrows(QuizForgeException.class, () -> codec.parse(json.writeValueAsString(malformed)), path);
        }
    }
    private void rewriteOptionalMetadata(JsonNode node, boolean explicitNull) {
        if (node instanceof ObjectNode object) {
            for (String name : List.of("alt", "caption", "evaluatorGuidance", "documentTitle", "sectionTitle")) {
                if (!object.has(name)) continue;
                if (explicitNull) object.putNull(name); else object.remove(name);
            }
        }
        if (node.isContainerNode()) node.forEach(child -> rewriteOptionalMetadata(child, explicitNull));
    }
    @Test void sourceSnapshotsRoundTripAndNoLegacySourceFieldsAreAccepted() {
        var bank=valid("SINGLE_CHOICE"); String json=codec.write(bank);
        var reread=codec.parse(json).questions().getFirst().sourceRefs().getFirst();
        assertEquals("Document",reread.documentTitle()); assertEquals("Source snapshot",reread.sectionTitle());
        assertEquals("定义",reread.anchorName()); assertEquals(2,reread.occurrence());
        assertFalse(json.contains("nodeId")); assertFalse(json.contains("sectionId"));
        assertThrows(QuizForgeException.class,() -> codec.parse(json.replace("\"anchorName\"","\"nodeId\"")));
        assertThrows(QuizForgeException.class,() -> codec.parse(json.replace("\"occurrence\" : 2","\"occurrence\" : 0")));
        assertThrows(QuizForgeException.class,() -> codec.parse(json.replace("\"occurrence\" : 2","\"occurrence\" : \"2\"")));
    }
    @Test void rejectsIncorrectOptionReferencesDuplicatesAndCardinality() {
        for (String type : List.of("SINGLE_CHOICE","MULTIPLE_CHOICE")) {
            var bank=valid(type); var q=bank.questions().getFirst();
            for (var ids : List.of(List.<String>of(),List.of("opt_absent"),List.of("opt_a","opt_a"),List.of("opt_a","opt_b","opt_c"))) {
                var edited=copy(q,q.prompt(),q.scoreSpec(),null,List.of(),new ChoiceAnswerSpec(ids),q.analysis());
                assertThrows(QuizForgeException.class,() -> codec.validate(withQuestions(bank,List.of(edited))));
            }
        }
        var multiple=valid("MULTIPLE_CHOICE"); var q=multiple.questions().getFirst();
        assertThrows(QuizForgeException.class,() -> codec.validate(withQuestions(multiple,List.of(copy(q,q.prompt(),q.scoreSpec(),null,List.of(),new ChoiceAnswerSpec(List.of("opt_a")),q.analysis())))));
    }
    @Test void rejectsDuplicateQuestionAndGlobalOptionIds() {
        var bank=valid("SINGLE_CHOICE"); var q=bank.questions().getFirst();
        assertThrows(QuizForgeException.class,() -> codec.validate(withQuestions(bank,List.of(q,q))));
        var duplicate=new Question("q_second",q.type(),q.stimulusRefs(),q.prompt(),q.payload(),q.answerSpec(),q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs());
        assertThrows(QuizForgeException.class,() -> codec.validate(withQuestions(bank,List.of(q,duplicate))));
    }
    @Test void rejectsDuplicateAndMissingResourcesAndStimuli() {
        var bank=richBank();
        assertThrows(QuizForgeException.class,() -> codec.validate(new QuestionBank(bank.assetId(),bank.title(),bank.stimuli(),bank.questions(),List.of(bank.resources().getFirst(),bank.resources().getFirst()))));
        assertThrows(QuizForgeException.class,() -> codec.validate(new QuestionBank(bank.assetId(),bank.title(),List.of(bank.stimuli().getFirst(),bank.stimuli().getFirst()),bank.questions(),bank.resources())));
        assertThrows(QuizForgeException.class,() -> codec.validate(new QuestionBank(bank.assetId(),bank.title(),List.of(),bank.questions(),bank.resources())));
        assertThrows(QuizForgeException.class,() -> codec.validate(new QuestionBank(bank.assetId(),bank.title(),bank.stimuli(),bank.questions(),List.of())));
    }
    @Test void rejectsAbsoluteTraversalAndBase64Locators() {
        var bank=richBank();
        for (String locator : List.of("C:/Users/test/a.png","/a.png","../a.png","resources/../a.png","C:\\a.png","data:image/png;base64,xxxx")) {
            var old=bank.resources().getFirst();
            var resource=new QBankResource(old.id(),old.kind(),old.mediaType(),locator,old.sha256());
            assertThrows(QuizForgeException.class,() -> codec.validate(new QuestionBank(bank.assetId(),bank.title(),bank.stimuli(),bank.questions(),List.of(resource))));
        }
    }
    @Test void emptyStimuliResourcesAndSourcesAreValidAndDraftsAreDistinct() {
        var bank=valid("SINGLE_CHOICE"); var q=bank.questions().getFirst();
        var standalone=new Question(q.id(),q.type(),List.of(),q.prompt(),q.payload(),q.answerSpec(),q.scoreSpec(),null,null,List.of());
        codec.validate(withQuestions(bank,List.of(standalone)));
        var draft=new QuestionBank("qb_draft","Draft",List.of(),List.of(),List.of());
        assertEquals(draft,codec.parseEmptyDraft(codec.write(draft)));
        assertThrows(QuizForgeException.class,() -> codec.parse(codec.write(draft)));
    }
    @Test void rejectsUnknownDiscriminatorsAndOldSchemaWithoutFallback() {
        String source=codec.write(valid("SINGLE_CHOICE"));
        for (String discriminator : List.of("TEXT","CHOICE","SINGLE_CHOICE"))
            assertThrows(QuizForgeException.class,() -> codec.parse(source.replace("\""+discriminator+"\"","\"UNKNOWN\"")));
        for (String version : List.of("1.0","1.1","1.2","3.0"))
            assertThrows(QuizForgeException.class,() -> codec.parse(source.replace("\"2.0\"","\""+version+"\"")));
        assertThrows(QuizForgeException.class,() -> codec.parse(source+"{}"));
        assertThrows(QuizForgeException.class,() -> codec.parse(source.replace("\"title\" : \"Bank\"","\"title\" : \"Bank\", \"title\" : \"Duplicate\"")));
        assertThrows(QuizForgeException.class,() -> codec.parse(source.replace("\"schemaVersion\"","\"unknown\"")));
        String rich=codec.write(richBank());
        for (String type : List.of("RICH","PARAGRAPH","MATH","IMAGE","LINE_BREAK","LINK"))
            assertThrows(QuizForgeException.class,() -> codec.parse(rich.replace("\""+type+"\"","\"UNKNOWN\"")));
        assertThrows(QuizForgeException.class,() -> codec.parse(rich.replace("\"kind\" : \"IMAGE\"","\"kind\" : 0")));
    }
    @Test void revisionTracksLogicalModelAndExcludesIdentityAndFormatting() {
        var bank=richBank(); var q=bank.questions().getFirst(); String id=codec.contentId(bank);
        assertTrue(id.matches("qfb:v2:[0-9a-f]{64}"));
        assertEquals(id,codec.contentId(codec.parse(codec.write(bank))));
        assertEquals(id,codec.contentId(new QuestionBank("qb_other",bank.title(),bank.stimuli(),bank.questions(),bank.resources())));
        assertNotEquals(id,codec.contentId(withQuestions(bank,List.of(copy(q,new TextContent("Changed"),q.scoreSpec(),null,q.stimulusRefs(),q.choiceAnswerSpec(),q.analysis())))));
        assertNotEquals(id,codec.contentId(withQuestions(bank,List.of(copy(q,q.prompt(),new ScoreSpec(new BigDecimal("1.5")),null,q.stimulusRefs(),q.choiceAnswerSpec(),q.analysis())))));
        var resource=bank.resources().getFirst();
        var changed=new QBankResource(resource.id(),resource.kind(),resource.mediaType(),resource.locator(),"d".repeat(64));
        assertNotEquals(id,codec.contentId(new QuestionBank(bank.assetId(),bank.title(),bank.stimuli(),bank.questions(),List.of(changed,bank.resources().getLast()))));
    }
    @Test void rejectsBlankRequiredContentAndKeepsOptionalBlankAnalysis() {
        var bank=valid("SINGLE_CHOICE"); var q=bank.questions().getFirst();
        for (QuestionContent blank : List.of(new TextContent("  "),
                new RichContent(new RichDocument(List.of())),
                new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode(" "),new LineBreakNode()))))))) {
            var edited=copy(q,blank,q.scoreSpec(),null,List.of(),q.choiceAnswerSpec(),q.analysis());
            assertThrows(QuizForgeException.class,() -> codec.validate(withQuestions(bank,List.of(edited))));
        }
        var blankAnalysis=copy(q,q.prompt(),q.scoreSpec(),null,List.of(),q.choiceAnswerSpec(),new TextContent(""));
        assertEquals(blankAnalysis,codec.parse(codec.write(withQuestions(bank,List.of(blankAnalysis)))).questions().getFirst());
        String source=codec.write(bank);
        assertThrows(QuizForgeException.class,() -> codec.parse(source.replace("\"text\" : \"Stem\"","\"text\" : null")));
        assertThrows(QuizForgeException.class,() -> codec.parse(source.replace("\"text\" : \"A\"","\"text\" : \" \"")));
    }
    @Test void hashIncludesEvaluationStimulusAndOrderAndNormalizesDecimalScale() {
        var base=valid("SINGLE_CHOICE"); var q=base.questions().getFirst();
        String id=codec.contentId(base);
        var evaluation=new EvaluationSpec(List.of(new EvaluationCriterion("criterion_one","Correctness",BigDecimal.ONE)),"Guidance");
        assertNotEquals(id,codec.contentId(withQuestions(base,List.of(copy(q,q.prompt(),q.scoreSpec(),evaluation,List.of(),q.choiceAnswerSpec(),q.analysis())))));
        assertEquals(id,codec.contentId(withQuestions(base,List.of(copy(q,q.prompt(),new ScoreSpec(new BigDecimal("1.00")),null,List.of(),q.choiceAnswerSpec(),q.analysis())))));
        var bank=richBank();
        var changedStimulus=new Stimulus(bank.stimuli().getFirst().id(),new TextContent("Changed material"));
        assertNotEquals(codec.contentId(bank),codec.contentId(new QuestionBank(bank.assetId(),bank.title(),List.of(changedStimulus),bank.questions(),bank.resources())));
        var edit=new QuestionBankEditorModel(base); edit.duplicateQuestion(0);
        var two=edit.bank();
        assertNotEquals(codec.contentId(two),codec.contentId(withQuestions(two,two.questions().reversed())));
        var referenced=copy(q,q.prompt(),q.scoreSpec(),null,List.of("stim_text"),q.choiceAnswerSpec(),q.analysis());
        assertFalse(QuestionText.supports(new QuestionBank(base.assetId(),base.title(),
                List.of(new Stimulus("stim_text",new TextContent("Text material"))),List.of(referenced),List.of())));
    }
}
