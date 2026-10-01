package io.quizforge.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.InlineImageNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.EvaluationSpec;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.model.Stimulus;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.infrastructure.filesystem.qbank.PackageLimits;
import io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader;
import io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.filesystem.qbank.ResourceContentProvider;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class QBankPackageTest {
    @TempDir Path temp;
    private final QBankPackageReader reader = new QBankPackageReader();
    private final QBankPackageWriter writer = new QBankPackageWriter();
    private final QuestionBankV2Codec codec = new QuestionBankV2Codec();
    private final ObjectMapper json = new ObjectMapper();
    private Path file() { return temp.resolve("bank.qbank"); }
    private QuestionBank bank() { return QuestionBankV2CodecTest.valid("SINGLE_CHOICE"); }
    private QuestionBank resourceBank() {
        var bank = bank();
        return new QuestionBank(bank.assetId(), bank.title(), bank.stimuli(), bank.questions(), List.of(
            new QBankResource("res_z", ResourceKind.IMAGE, "image/png", "resources/z.png", "a".repeat(64)),
            new QBankResource("res_a", ResourceKind.AUDIO, "audio/wav", "resources/a.wav", "b".repeat(64))));
    }
    private byte[] image() { return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aF1kAAAAASUVORK5CYII="); }
    private byte[] audio() {
        // Valid mono PCM WAV: one signed 16-bit sample at 8000 Hz.
        return HexFormat.of().parseHex("524946462600000057415645666d74201000000001000100401f0000803e00000200100064617461020000000000");
    }
    private ResourceContentProvider bytes() { return resource -> new ByteArrayInputStream(resource.kind() == ResourceKind.IMAGE ? image() : audio()); }
    private Map<String,byte[]> entries() throws Exception { writer.write(file(), bank()); return QBankTestPackageBuilder.entries(file()); }
    private void reject(Map<String,byte[]> entries, ErrorCode code) throws Exception {
        QBankTestPackageBuilder.zip(file(), entries);
        assertEquals(code, assertThrows(QuizForgeException.class, () -> reader.read(file())).code());
    }
    private void changeJson(Map<String,byte[]> entries, String name, java.util.function.Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode node = (ObjectNode) json.readTree(entries.get(name)); mutation.accept(node);
        entries.put(name, json.writeValueAsBytes(node));
    }
    @ParameterizedTest @ValueSource(strings={"SINGLE_CHOICE", "MULTIPLE_CHOICE"})
    void emptyResourcesBothChoiceTypesRoundTrip(String type) throws Exception {
        QuestionBank original = QuestionBankV2CodecTest.valid(type);
        assertEquals(original, writer.write(file(), original));
        assertEquals(original, reader.read(file()));
        assertEquals(List.of("manifest.json", "bank.json"), new ArrayList<>(QBankTestPackageBuilder.entries(file()).keySet()));
        var entries = QBankTestPackageBuilder.entries(file());
        JsonNode manifest = json.readTree(entries.get("manifest.json")), body = json.readTree(entries.get("bank.json"));
        assertEquals(Set.of("stimuli", "questions"), fieldNames(body));
        assertEquals(Set.of("format", "schemaVersion", "assetId", "title", "resources"), fieldNames(manifest));
    }
    @Test void draftRoundTripStillCannotStartPractice() {
        var draft = new QuestionBank("qb_draft", "Draft", List.of(), List.of(), List.of());
        writer.write(file(), draft); assertEquals(draft, reader.read(file()));
        assertThrows(QuizForgeException.class, () -> codec.validate(reader.read(file())));
    }
    @Test void stimuliExactDecimalSourceAndEvaluationRoundTrip() {
        var base = bank(); var q = base.questions().getFirst();
        var question = new Question(q.id(), q.type(), List.of("stim_one"), q.prompt(), q.payload(), q.answerSpec(),
                new ScoreSpec(new BigDecimal("0.123456789012345678901234567890")),
                new EvaluationSpec(List.of(), "Guidance"), q.analysis(), q.sourceRefs());
        var original = new QuestionBank(base.assetId(), base.title(), List.of(new Stimulus("stim_one", new TextContent("Article"))), List.of(question), List.of());
        writer.write(file(), original); assertEquals(original, reader.read(file()));
    }
    @Test void optionalMissingAndNullProduceSameDomainAndRevision() throws Exception {
        var q = bank().questions().getFirst();
        var original = new QuestionBank("qb_one", "Bank", List.of(), List.of(new Question(q.id(), q.type(), q.stimulusRefs(), q.prompt(), q.payload(), q.answerSpec(), q.scoreSpec(), null, null, q.sourceRefs())), List.of());
        writer.write(file(), original);
        var entries = QBankTestPackageBuilder.entries(file());
        JsonNode body = json.readTree(entries.get("bank.json"));
        assertFalse(body.path("questions").get(0).has("analysis"));
        assertFalse(body.path("questions").get(0).has("evaluationSpec"));
        changeJson(entries, "bank.json", node -> {
            ObjectNode question = (ObjectNode) node.path("questions").get(0);
            question.putNull("analysis"); question.putNull("evaluationSpec");
            var ref = (ObjectNode) question.path("sourceRefs").get(0);
            ref.remove("documentTitle"); ref.remove("sectionTitle");
        });
        QBankTestPackageBuilder.zip(file(), entries);
        var missing = reader.read(file());
        changeJson(entries, "bank.json", node -> {
            var ref = (ObjectNode) node.path("questions").get(0).path("sourceRefs").get(0);
            ref.putNull("documentTitle"); ref.putNull("sectionTitle");
        });
        QBankTestPackageBuilder.zip(file(), entries);
        assertEquals(missing, reader.read(file())); assertEquals(codec.contentId(missing), codec.contentId(reader.read(file())));
        assertNull(reader.read(file()).questions().getFirst().analysis());
    }
    @Test void imageAudioBinaryHashesAndEntryOrder() throws Exception {
        var normalized = writer.write(file(), resourceBank(), bytes());
        assertEquals(List.of("manifest.json", "bank.json", "resources/a.wav", "resources/z.png"), new ArrayList<>(QBankTestPackageBuilder.entries(file()).keySet()));
        assertNotEquals(resourceBank().resources().getFirst().sha256(), normalized.resources().getFirst().sha256());
        try (var loaded = reader.open(file())) {
            for (var resource : loaded.bank().resources()) try (var stream = loaded.open(resource)) {
                byte[] expected = resource.kind() == ResourceKind.IMAGE ? image() : audio();
                assertArrayEquals(expected, stream.readAllBytes());
                assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected)), resource.sha256());
            }
        }
        assertEquals(normalized, reader.read(file()));
        String manifest = new String(QBankTestPackageBuilder.entries(file()).get("manifest.json"), StandardCharsets.UTF_8);
        assertFalse(manifest.contains("locator")); assertFalse(manifest.contains("base64"));
    }
    @Test void missingResourceRejected() throws Exception {
        writer.write(file(), resourceBank(), bytes()); var entries = QBankTestPackageBuilder.entries(file());
        entries.remove("resources/z.png"); reject(entries, ErrorCode.MISSING_RESOURCE);
    }
    @Test void resourceMismatchRejectedButMetadataInspectionDoesNotInflateResources() throws Exception {
        var normalized = writer.write(file(), resourceBank(), bytes()); var entries = QBankTestPackageBuilder.entries(file());
        entries.put("resources/z.png", new byte[]{1,2,3}); reject(entries, ErrorCode.RESOURCE_HASH_MISMATCH);
        assertEquals(normalized, reader.inspect(file()));
    }
    @ParameterizedTest @ValueSource(strings={"id", "path"})
    void duplicateManifestResourceRejected(String field) throws Exception {
        writer.write(file(), resourceBank(), bytes()); var entries = QBankTestPackageBuilder.entries(file());
        changeJson(entries, "manifest.json", manifest -> {
            var resources = manifest.path("resources");
            ((ObjectNode) resources.get(1)).set(field, resources.get(0).get(field));
        });
        reject(entries, ErrorCode.INVALID_MANIFEST);
    }
    @ParameterizedTest @ValueSource(strings={"../evil", "/evil", "C:/evil", "\\evil", "resources/../../evil", "resources//evil", "resources/./evil", "resources/evil\\file"})
    void unsafeEntryRejected(String name) throws Exception {
        var entries = entries(); entries.put(name, new byte[]{1}); reject(entries, ErrorCode.UNSAFE_ZIP_ENTRY);
    }
    @ParameterizedTest @ValueSource(strings={"manifest.json", "bank.json"})
    void duplicateJsonEntryRejected(String name) throws Exception {
        var entries = entries(); String alias = name.equals("manifest.json") ? "manifesz.json" : "banz.json";
        entries.put(alias, entries.get(name)); QBankTestPackageBuilder.zip(file(), entries);
        // ZipOutputStream refuses duplicates; patch equal-length local and central names in the test fixture.
        byte[] bytes = Files.readAllBytes(file()); byte[] from = alias.getBytes(StandardCharsets.UTF_8), to = name.getBytes(StandardCharsets.UTF_8);
        for (int i=0; i <= bytes.length-from.length; i++) {
            boolean match=true; for (int j=0;j<from.length;j++) if(bytes[i+j]!=from[j]) {match=false;break;}
            if(match) System.arraycopy(to,0,bytes,i,to.length);
        }
        Files.write(file(), bytes);
        assertEquals(ErrorCode.UNSAFE_ZIP_ENTRY, assertThrows(QuizForgeException.class, () -> reader.read(file())).code());
    }
    @ParameterizedTest @ValueSource(strings={"manifest.json", "bank.json"})
    void missingMandatoryJsonRejected(String name) throws Exception {
        var entries = entries(); entries.remove(name); reject(entries, name.equals("manifest.json") ? ErrorCode.MISSING_MANIFEST : ErrorCode.MISSING_BANK);
    }
    @Test void plainJsonAndTruncatedZipRejected() throws Exception {
        Files.writeString(file(), codec.write(bank()));
        assertEquals(ErrorCode.INVALID_PACKAGE, assertThrows(QuizForgeException.class, () -> reader.read(file())).code());
        writer.write(file(), bank()); byte[] zip = Files.readAllBytes(file()); Files.write(file(), Arrays.copyOf(zip,zip.length/2));
        assertEquals(ErrorCode.INVALID_PACKAGE, assertThrows(QuizForgeException.class, () -> reader.read(file())).code());
    }
    @ParameterizedTest @ValueSource(strings={"format", "schemaVersion", "assetId", "title", "resources"})
    void invalidManifestFieldsHaveDistinctReasons(String field) throws Exception {
        var entries = entries();
        changeJson(entries, "manifest.json", node -> node.put(field, field.equals("title") ? "" : "wrong"));
        ErrorCode expected = field.equals("format") ? ErrorCode.UNSUPPORTED_FORMAT : field.equals("schemaVersion") ? ErrorCode.UNSUPPORTED_SCHEMA_VERSION : ErrorCode.INVALID_MANIFEST;
        reject(entries, expected);
    }
    @ParameterizedTest @ValueSource(strings={"kind", "mediaType", "sha256", "id"})
    void invalidResourceMetadataRejected(String field) throws Exception {
        writer.write(file(),resourceBank(),bytes()); var entries=QBankTestPackageBuilder.entries(file());
        changeJson(entries,"manifest.json",node -> ((ObjectNode)node.path("resources").get(0)).put(field,"BAD"));
        reject(entries,ErrorCode.INVALID_MANIFEST);
    }
    @Test void resourceCannotEscapeResourceDirectory() throws Exception {
        writer.write(file(),resourceBank(),bytes()); var entries=QBankTestPackageBuilder.entries(file());
        changeJson(entries,"manifest.json",node -> ((ObjectNode)node.path("resources").get(0)).put("path","other.png"));
        reject(entries,ErrorCode.UNSAFE_ZIP_ENTRY);
    }
    @Test void invalidBankUsesExistingDomainValidation() throws Exception {
        var entries = entries();
        changeJson(entries, "bank.json", body -> ((ObjectNode) body.path("questions").get(0).path("scoreSpec")).put("defaultMaxScore", 0));
        reject(entries, ErrorCode.INVALID_BANK);
    }
    @Test void duplicateJsonPropertyAndTrailingTokensRejected() throws Exception {
        var entries = entries(); entries.put("bank.json", "{\"stimuli\":[],\"stimuli\":[],\"questions\":[]}".getBytes(StandardCharsets.UTF_8));
        reject(entries, ErrorCode.INVALID_BANK);
        entries=entries(); entries.put("manifest.json", (new String(entries.get("manifest.json"),StandardCharsets.UTF_8)+" {}").getBytes(StandardCharsets.UTF_8));
        reject(entries, ErrorCode.INVALID_MANIFEST);
    }
    @Test void unknownEntriesRejected() throws Exception {
        var entries=entries(); entries.put("resources/unlisted",new byte[]{1}); reject(entries,ErrorCode.INVALID_PACKAGE);
    }
    @Test void entryAndAllSizeLimitsEnforcedWithSmallFixtures() throws Exception {
        writer.write(file(),resourceBank(),bytes());
        var defaults=PackageLimits.DEFAULT;
        for (var limits : List.of(
                new PackageLimits(2,defaults.manifestBytes(),defaults.bankBytes(),defaults.resourceBytes(),defaults.totalBytes()),
                new PackageLimits(10,1,defaults.bankBytes(),defaults.resourceBytes(),defaults.totalBytes()),
                new PackageLimits(10,defaults.manifestBytes(),1,defaults.resourceBytes(),defaults.totalBytes()),
                new PackageLimits(10,defaults.manifestBytes(),defaults.bankBytes(),1,defaults.totalBytes()),
                new PackageLimits(10,defaults.manifestBytes(),defaults.bankBytes(),defaults.resourceBytes(),1)))
            assertEquals(ErrorCode.PACKAGE_LIMIT_EXCEEDED,assertThrows(QuizForgeException.class,()->new QBankPackageReader(limits).read(file())).code());
    }
    @Test void forgedDeclaredSizeStillEnforcesActualResourceLimit() throws Exception {
        writer.write(file(),resourceBank(),bytes());
        QBankTestPackageBuilder.declaredSizes(file(),Map.of("resources/z.png",1));
        var limits=new PackageLimits(10,100000,100000,50,1000000);
        assertEquals(ErrorCode.PACKAGE_LIMIT_EXCEEDED,assertThrows(QuizForgeException.class,()->new QBankPackageReader(limits).read(file())).code());
    }
    @Test void forgedDeclaredSizeStillEnforcesActualTotalLimit() throws Exception {
        writer.write(file(),resourceBank(),bytes()); var entries=QBankTestPackageBuilder.entries(file());
        Map<String,Integer> sizes=new HashMap<>(); entries.keySet().forEach(name->sizes.put(name,1));
        long actual=entries.values().stream().mapToLong(value->value.length).sum();
        QBankTestPackageBuilder.declaredSizes(file(),sizes);
        var limits=new PackageLimits(10,100000,100000,100000,actual-1);
        assertEquals(ErrorCode.PACKAGE_LIMIT_EXCEEDED,assertThrows(QuizForgeException.class,()->new QBankPackageReader(limits).read(file())).code());
    }
    @Test void richOptionalImageFieldsNormalizeNullAndMissingInPackage() throws Exception {
        var base=resourceBank(); var q=base.questions().getFirst();
        var rich=new RichContent(new RichDocument(List.of(
                new ParagraphNode(List.of(new InlineTextNode("Figure"),new InlineImageNode("res_z",null))),
                new BlockImageNode("res_z",null,null))));
        var question=new Question(q.id(),q.type(),q.stimulusRefs(),rich,q.payload(),q.answerSpec(),q.scoreSpec(),
                new EvaluationSpec(List.of(),null),q.analysis(),q.sourceRefs());
        var original=new QuestionBank(base.assetId(),base.title(),base.stimuli(),List.of(question),base.resources());
        var normalized=writer.write(file(),original,bytes()); var entries=QBankTestPackageBuilder.entries(file());
        changeJson(entries,"bank.json",body->{
            var item=(ObjectNode)body.path("questions").get(0);
            ((ObjectNode)item.path("evaluationSpec")).putNull("evaluatorGuidance");
            var blocks=item.path("prompt").path("document").path("blocks");
            ((ObjectNode)blocks.get(0).path("children").get(1)).putNull("alt");
            ((ObjectNode)blocks.get(1)).putNull("alt").putNull("caption");
        });
        QBankTestPackageBuilder.zip(file(),entries);
        assertEquals(normalized,reader.read(file())); assertEquals(codec.contentId(normalized),codec.contentId(reader.read(file())));
    }
    @Test void successfulAtomicWriteReplacesTargetAndCleansTemps() throws Exception {
        writer.write(file(),bank()); String revision=codec.contentId(reader.read(file()));
        var next=new QuestionBank(bank().assetId(),"Changed",bank().stimuli(),bank().questions(),bank().resources());
        writer.write(file(),next); assertEquals(next,reader.read(file())); assertNotEquals(revision,codec.contentId(reader.read(file())));
        assertOnlyTarget();
    }
    @Test void providerFailureDoesNotChangeAnyOldBytesAndCleansTemps() throws Exception {
        writer.write(file(),bank()); byte[] before=Files.readAllBytes(file());
        assertThrows(QuizForgeException.class,()->writer.write(file(),resourceBank(),resource->new InputStream(){
            @Override public int read() throws IOException { throw new IOException("Injected provider failure"); }
        }));
        assertArrayEquals(before,Files.readAllBytes(file())); assertOnlyTarget();
    }
    @Test void sizeFailureDoesNotChangeOldBytesAndCleansTemps() throws Exception {
        writer.write(file(),bank()); byte[] before=Files.readAllBytes(file());
        var limited=new QBankPackageWriter(new PackageLimits(10,100000,100000,1,100000));
        assertThrows(QuizForgeException.class,()->limited.write(file(),resourceBank(),bytes()));
        assertArrayEquals(before,Files.readAllBytes(file())); assertOnlyTarget();
    }
    @Test void duplicateWriterPathCannotCorruptTarget() throws Exception {
        writer.write(file(),bank()); byte[] before=Files.readAllBytes(file()); var source=resourceBank(); var first=source.resources().getFirst();
        var duplicate=new QBankResource("res_duplicate",first.kind(),first.mediaType(),first.locator(),first.sha256());
        var invalid=new QuestionBank(source.assetId(),source.title(),source.stimuli(),source.questions(),List.of(first,duplicate));
        assertEquals(ErrorCode.INVALID_MANIFEST,assertThrows(QuizForgeException.class,()->writer.write(file(),invalid,bytes())).code());
        assertArrayEquals(before,Files.readAllBytes(file())); assertOnlyTarget();
    }
    @Test void zipOrderTimestampAndCompressionDoNotAffectLogicalRevision() throws Exception {
        var normalized=writer.write(file(),resourceBank(),bytes()); String expected=codec.contentId(normalized);
        var entries=QBankTestPackageBuilder.entries(file()); var names=new ArrayList<>(entries.keySet()); Collections.reverse(names);
        var reverse=new LinkedHashMap<String,byte[]>(); names.forEach(name->reverse.put(name,entries.get(name)));
        byte[] first=Files.readAllBytes(file()); QBankTestPackageBuilder.zip(file(),reverse,1700000000000L,0);
        assertFalse(Arrays.equals(first,Files.readAllBytes(file()))); assertEquals(expected,codec.contentId(reader.read(file())));
    }
    @Test void changedQuestionScoreAndResourceBytesChangeLogicalRevision() {
        var original=writer.write(file(),resourceBank(),bytes()); String before=codec.contentId(original);
        var q=original.questions().getFirst();
        for (var question : List.of(
                new Question(q.id(),q.type(),q.stimulusRefs(),new TextContent("Changed"),q.payload(),q.answerSpec(),q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs()),
                new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),q.payload(),q.answerSpec(),new ScoreSpec(new BigDecimal("2.5")),q.evaluationSpec(),q.analysis(),q.sourceRefs()))) {
            var changed=new QuestionBank(original.assetId(),original.title(),original.stimuli(),List.of(question),original.resources());
            assertNotEquals(before,codec.contentId(writer.write(file(),changed,bytes())));
        }
        var changed=writer.write(file(),original,resource->new ByteArrayInputStream(new byte[]{9,8,7}));
        assertNotEquals(original.resources().getFirst().sha256(),changed.resources().getFirst().sha256());
        assertNotEquals(before,codec.contentId(changed));
    }
    private void assertOnlyTarget() throws IOException { try(var files=Files.list(temp)) {assertEquals(List.of(file()),files.toList());} }
    private Set<String> fieldNames(JsonNode node) { Set<String> names=new HashSet<>(); node.fieldNames().forEachRemaining(names::add); return names; }
}
