package io.quizforge.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockQuoteNode;
import io.quizforge.core.question.content.BulletListNode;
import io.quizforge.core.question.content.HeadingNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.LinkNode;
import io.quizforge.core.question.content.ListItemNode;
import io.quizforge.core.question.content.OrderedListNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextAlignment;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.content.TextMark;
import io.quizforge.core.question.model.EvaluationCriterion;
import io.quizforge.core.question.model.EvaluationSpec;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.model.extension.ExtensionAnswerSpec;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.extension.ExtensionPayload;
import io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter;
import io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader;
import io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.testing.EssayTestBanks;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EssayQuestionIntegrationTest {
    @TempDir Path temp;
    private final QuestionBankV2Codec codec=new QuestionBankV2Codec();
    private final ObjectMapper json=new ObjectMapper();
    private QuestionBank bank=EssayTestBanks.bank();
    private QuestionBank with(Question q,List<QBankResource> resources) {return new QuestionBank(bank.assetId(),bank.title(),List.of(),List.of(q),resources);}
    @Test void payloadAnswerAndExactScoreRoundTrip() {
        var restored=codec.parse(codec.write(bank));assertEquals(bank,restored);
        assertEquals(new BigDecimal("20.25"),restored.questions().getFirst().scoreSpec().defaultMaxScore());
        assertInstanceOf(ExtensionPayload.class,restored.questions().getFirst().payload());
        assertInstanceOf(ExtensionAnswerSpec.class,restored.questions().getFirst().answerSpec());
    }
    @ParameterizedTest @ValueSource(strings={"ESSAY","CLOZE","READING","MATCHING","TRANSLATION"})
    void removedLegacyStorageKindsAreRejected(String kind) throws Exception {
        var root=(ObjectNode)json.readTree(codec.write(bank));
        var question=(ObjectNode)root.path("questions").get(0);
        question.set("payload",json.valueToTree(Map.of("kind",kind)));
        question.set("answerSpec",json.valueToTree(Map.of("kind",kind)));
        assertThrows(QuizForgeException.class,()->codec.parse(root.toString()));
    }
    @Test void rejectsInvalidDiscriminatorPairing() {
        var old=bank.questions().getFirst();
        var invalid=new Question(old.id(),"SINGLE_CHOICE",List.of(),old.prompt(),new io.quizforge.core.question.model.choice.ChoicePayload(List.of()),old.answerSpec(),old.scoreSpec(),null,null,List.of());
        assertThrows(RuntimeException.class,()->codec.validate(with(invalid,List.of())));
    }
    @Test void richReferenceEvaluationSnapshotRoundTrip() {
        var old=bank.questions().getFirst();var reference=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("Rich sample"))))));
        var evaluation=new EvaluationSpec(List.of(new EvaluationCriterion("content","Content",new BigDecimal("0.6")),new EvaluationCriterion("language","Language",new BigDecimal("0.4"))),"Assess fairly.");
        var q=new Question(old.id(),old.type(),List.of(),old.prompt(),old.payload(),new ExtensionAnswerSpec(Map.of("referenceAnswer",QuestionContentData.encode(reference))),old.scoreSpec(),evaluation,null,List.of());
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
        var q=EssayTestBanks.essay("q_essay",EssayTestBanks.prompt(metadata.id()),null,null);
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
    @Test void opaqueExtensionDataKeepsResourcesDuringEditing() throws Exception {
        var type=new io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition("ESSAY","Essay",
            io.quizforge.core.question.type.QuestionTypeDefinition.Family.SUBJECTIVE,"test",(operation,input)->
                operation.equals("duplicate")?io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.object(input.get("question")):Map.of("errors",List.of()));
        io.quizforge.core.question.type.QuestionTypes.register(type);
        try {
        Path local=temp.resolve("image.png");Files.write(local,EssayTestBanks.image("png"));
        var a=new QBankImageImporter().read(local);var b=new QBankImageImporter().read(local);
        var model=new QuestionBankEditorModel(with(EssayTestBanks.essay("q_essay",EssayTestBanks.prompt(a.resource().id()),null,null),List.of(a.resource())));
        model.addResource(b.resource());model.setPrompt(0,EssayTestBanks.prompt(b.resource().id()));
        // Extension data can refer to either resource; the host cannot infer its private structure.
        assertEquals(List.of(a.resource(),b.resource()),model.bank().resources());
        model.duplicateQuestion(0);model.setPrompt(0,new TextContent("No picture"));assertEquals(2,model.bank().resources().size());
        model.deleteQuestion(1);assertEquals(2,model.bank().resources().size());
        }finally{io.quizforge.core.question.type.QuestionTypes.unregister("ESSAY");}
    }
}
