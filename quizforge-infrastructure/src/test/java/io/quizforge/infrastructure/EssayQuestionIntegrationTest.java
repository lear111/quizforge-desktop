package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.question.*;
import io.quizforge.infrastructure.filesystem.*;
import io.quizforge.infrastructure.filesystem.qbank.*;
import io.quizforge.infrastructure.testing.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EssayQuestionIntegrationTest {
    @TempDir Path temp;
    private final QuestionBankV2Codec codec=new QuestionBankV2Codec();
    private final ObjectMapper json=new ObjectMapper();
    private QuestionBank bank=EssayTestBanks.bank();
    private QuestionBank with(Question q,List<QBankResource> resources) {return new QuestionBank(bank.assetId(),bank.title(),List.of(),List.of(q),resources);}
    @Test void payloadAnswerAndExactScoreRoundTrip() {
        var restored=codec.parse(codec.write(bank));assertEquals(bank,restored);
        assertEquals(new BigDecimal("20.25"),restored.questions().getFirst().scoreSpec().defaultMaxScore());
        assertInstanceOf(EssayPayload.class,restored.questions().getFirst().payload());
        assertInstanceOf(EssayAnswerSpec.class,restored.questions().getFirst().answerSpec());
    }
    @Test void optionalNullAndMissingHaveIdenticalDomainAndContentId() throws Exception {
        var root=(ObjectNode)json.readTree(codec.write(bank));
        var q=(ObjectNode)root.path("questions").get(1);
        assertFalse(q.path("payload").has("minWords"));assertFalse(q.path("payload").has("maxWords"));
        assertFalse(q.path("payload").has("placeholder"));assertFalse(q.path("answerSpec").has("referenceAnswer"));
        var missing=codec.parse(root.toString());
        ((ObjectNode)q.get("payload")).putNull("placeholder");
        ((ObjectNode)q.get("answerSpec")).putNull("referenceAnswer");
        var nulls=codec.parse(root.toString());assertEquals(missing,nulls);assertEquals(codec.contentId(missing),codec.contentId(nulls));
    }
    @Test void rejectsInvalidDiscriminatorPairing() {
        var old=bank.questions().getFirst();
        var invalid=new Question(old.id(),"SINGLE_CHOICE",List.of(),old.prompt(),old.payload(),old.answerSpec(),old.scoreSpec(),null,null,List.of());
        assertThrows(QuizForgeException.class,()->codec.validate(with(invalid,List.of())));
    }
    @Test void obsoleteWordLimitsAreIgnoredWithoutIgnoringOtherUnknownFields() throws Exception {
        for(String fields:List.of("{\"minWords\":160,\"maxWords\":200}",
                "{\"minWords\":null,\"maxWords\":null}","{\"minWords\":-1,\"maxWords\":0}")) {
            var root=(ObjectNode)json.readTree(codec.write(bank));
            ((ObjectNode)root.path("questions").get(0).path("payload")).setAll((ObjectNode)json.readTree(fields));
            var normalized=codec.parse(root.toString());
            assertEquals(bank,normalized);assertEquals(codec.contentId(bank),codec.contentId(normalized));
            assertFalse(codec.write(normalized).contains("minWords"));assertFalse(codec.write(normalized).contains("maxWords"));
        }
        var root=(ObjectNode)json.readTree(codec.write(bank));
        ((ObjectNode)root.path("questions").get(0).path("payload")).put("unknownSetting",true);
        assertThrows(QuizForgeException.class,()->codec.parse(root.toString()));
    }
    @Test void existingPackageWordLimitsDisappearOnSaveWithoutChangingRemainingContent() throws Exception {
        Path file=temp.resolve("old-word-limits.qbank");
        new QBankPackageWriter().write(file,bank);
        var entries=QBankTestPackageBuilder.entries(file);
        var body=(ObjectNode)json.readTree(entries.get("bank.json"));
        ((ObjectNode)body.path("questions").get(0).path("payload")).put("minWords",160).put("maxWords",200);
        entries.put("bank.json",json.writeValueAsBytes(body));QBankTestPackageBuilder.zip(file,entries);
        var restored=new QBankPackageReader().read(file);
        assertEquals(bank,restored);assertEquals(codec.contentId(bank),codec.contentId(restored));
        new QBankPackageWriter().write(file,restored);
        var saved=json.readTree(QBankTestPackageBuilder.entries(file).get("bank.json"));
        assertFalse(saved.path("questions").get(0).path("payload").has("minWords"));
        assertFalse(saved.path("questions").get(0).path("payload").has("maxWords"));
        assertEquals(bank,new QBankPackageReader().read(file));
    }
    @Test void richReferenceEvaluationSnapshotRoundTrip() {
        var old=bank.questions().getFirst();var reference=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("Rich sample"))))));
        var evaluation=new EvaluationSpec(List.of(new EvaluationCriterion("content","Content",new BigDecimal("0.6")),new EvaluationCriterion("language","Language",new BigDecimal("0.4"))),"Assess fairly.");
        var q=new Question(old.id(),old.type(),List.of(),old.prompt(),old.payload(),new EssayAnswerSpec(reference),old.scoreSpec(),evaluation,null,List.of());
        var expected=with(q,List.of());assertEquals(expected,codec.parse(codec.write(expected)));
    }
    @Test void editorRichNodesRoundTripThroughCodecAndPackage() throws Exception {
        Path local=temp.resolve("rich.png");Files.write(local,EssayTestBanks.image("png"));
        var image=new QBankImageImporter().read(local);
        var item=new ListItemNode(List.of(new ParagraphNode(List.of(new InlineTextNode("Entry")))));
        var prompt=new RichContent(new RichDocument(List.of(
                new HeadingNode(2,List.of(new InlineTextNode("Title",List.of(TextMark.BOLD))),TextAlignment.CENTER),
                new ParagraphNode(List.of(new InlineTextNode("Read ",List.of(TextMark.ITALIC)),
                        new LinkNode("https://example.org",List.of(new InlineTextNode("source")))),TextAlignment.LEFT),
                new BulletListNode(List.of(item)),new OrderedListNode(2,List.of(item)),
                new BlockQuoteNode(List.of(new ParagraphNode(List.of(new InlineTextNode("Quote"))))),
                new BlockImageNode(image.resource().id(),"Illustration",null,50,TextAlignment.RIGHT))));
        var old=bank.questions().getFirst();
        var q=new Question(old.id(),old.type(),old.stimulusRefs(),prompt,old.payload(),old.answerSpec(),
                old.scoreSpec(),old.evaluationSpec(),old.analysis(),old.sourceRefs());
        var expected=with(q,List.of(image.resource()));
        assertEquals(expected,codec.parse(codec.write(expected)));
        Path packageFile=temp.resolve("rich.qbank");
        new QBankPackageWriter().write(packageFile,expected,resource->image.open());
        assertEquals(expected,new QBankPackageReader().read(packageFile));
        String body=new String(QBankTestPackageBuilder.entries(packageFile).get("bank.json"),java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(body.contains("<h2>"));assertFalse(body.contains("file:"));assertFalse(body.contains("data:image"));
    }
    @Test void newOptionalRichFieldsTreatExplicitNullAsAbsent() throws Exception {
        var prompt=new RichContent(new RichDocument(List.of(new ParagraphNode(
                List.of(new InlineTextNode("Plain",List.of(TextMark.BOLD)))))));
        var old=bank.questions().getFirst();
        var q=new Question(old.id(),old.type(),old.stimulusRefs(),prompt,old.payload(),old.answerSpec(),
                old.scoreSpec(),old.evaluationSpec(),old.analysis(),old.sourceRefs());
        var missing=with(q,List.of());var root=(ObjectNode)json.readTree(codec.write(missing));
        var paragraph=(ObjectNode)root.path("questions").get(0).path("prompt").path("document").path("blocks").get(0);
        paragraph.putNull("alignment");
        assertEquals(missing,codec.parse(root.toString()));
        assertEquals(codec.contentId(missing),codec.contentId(codec.parse(root.toString())));
    }
    @ParameterizedTest @ValueSource(strings={"png","jpg"})
    void importPackageReopenStoresActualBytesAndHash(String format) throws Exception {
        byte[] bytes=EssayTestBanks.image(format);Path local=temp.resolve("same-name."+format);Files.write(local,bytes);
        var imported=new QBankImageImporter().read(local);var metadata=imported.resource();
        assertTrue(metadata.id().startsWith("res_"));assertEquals(ResourceKind.IMAGE,metadata.kind());
        assertEquals(format.equals("png")?"image/png":"image/jpeg",metadata.mediaType());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),metadata.sha256());
        var q=EssayTestBanks.essay("q_essay",EssayTestBanks.prompt(metadata.id()),new EssayPayload(null),null);
        var original=with(q,List.of(metadata));Path file=temp.resolve("essay.qbank");
        new QBankPackageWriter().write(file,original,r->imported.open());
        assertEquals(original,new QBankPackageReader().read(file));
        var entries=QBankTestPackageBuilder.entries(file);assertArrayEquals(bytes,entries.get(metadata.locator()));
        assertEquals(Set.of("manifest.json","bank.json",metadata.locator()),entries.keySet());
        String body=new String(entries.get("bank.json"),java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(body.contains(temp.toString()));assertFalse(body.contains("file:"));assertFalse(body.contains("base64"));
        try(var loaded=new QBankPackageReader().open(file);var resource=loaded.open(metadata)) {assertArrayEquals(bytes,resource.readAllBytes());}
    }
    @Test void sameNameImportsHaveDistinctStableIdentities() throws Exception {
        Path local=temp.resolve("same.png");Files.write(local,EssayTestBanks.image("png"));
        var a=new QBankImageImporter().read(local);var b=new QBankImageImporter().read(local);
        assertNotEquals(a.resource().id(),b.resource().id());assertNotEquals(a.resource().locator(),b.resource().locator());
        assertEquals(a.resource().sha256(),b.resource().sha256());
    }
    @Test void rejectsUnrecognizedAndUndecodableImages() throws Exception {
        for(byte[] bytes:List.of("not an image".getBytes(),new byte[]{(byte)137,80,78,71,13,10,26,10,0,0},new byte[]{(byte)255,(byte)216,(byte)255,0})) {
            Path local=temp.resolve("fake.png");Files.write(local,bytes);
            assertThrows(RuntimeException.class,()->new QBankImageImporter().read(local));
        }
    }
    @Test void deletionReplacementAndSharedImageUsageAreTracked() throws Exception {
        Path local=temp.resolve("image.png");Files.write(local,EssayTestBanks.image("png"));
        var a=new QBankImageImporter().read(local);var b=new QBankImageImporter().read(local);
        var model=new QuestionBankEditorModel(with(EssayTestBanks.essay("q_essay",EssayTestBanks.prompt(a.resource().id()),new EssayPayload(null),null),List.of(a.resource())));
        model.addResource(b.resource());model.setPrompt(0,EssayTestBanks.prompt(b.resource().id()));
        assertEquals(List.of(b.resource()),model.bank().resources());
        model.duplicateQuestion(0);model.setPrompt(0,new TextContent("No picture"));assertEquals(1,model.bank().resources().size());
        model.deleteQuestion(1);assertTrue(model.bank().resources().isEmpty());
    }
    @Test void cleanupHonorsStimulusReferenceAnswerAnalysisAndOtherQuestions() throws Exception {
        Path local=temp.resolve("shared.png");Files.write(local,EssayTestBanks.image("png"));
        var resource=new QBankImageImporter().read(local).resource();var prompt=EssayTestBanks.prompt(resource.id());
        var q=EssayTestBanks.essay("q_shared",prompt,new EssayPayload(null),prompt);
        var model=new QuestionBankEditorModel(new QuestionBank("qb_shared","Shared",List.of(new Stimulus("stim_shared",prompt)),List.of(q),List.of(resource)));
        model.setPrompt(0,new TextContent("Changed"));model.setReferenceAnswer(0,null);model.deleteQuestion(0);
        assertEquals(List.of(resource),model.bank().resources());
        var original=EssayTestBanks.essay("q_analysis",prompt,new EssayPayload(null),null);
        q=new Question(original.id(),original.type(),List.of(),original.prompt(),original.payload(),original.answerSpec(),original.scoreSpec(),null,prompt,List.of());
        model=new QuestionBankEditorModel(with(q,List.of(resource)));model.setPrompt(0,new TextContent("Changed"));
        assertEquals(List.of(resource),model.bank().resources());
    }
}
