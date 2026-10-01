package io.quizforge.infrastructure;

import io.quizforge.core.asset.AssetType;
import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.service.WorkspaceService;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.markdown.LegacyMarkdownCodec;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.filesystem.workspace.FileSystemWorkspaceAssetScanner;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteAssetIndexRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class QuestionBankExampleTest {
    @TempDir Path temp;

    @Test void richFoundationPackageRoundTripsWithEmbeddedResourceBytes() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (!Files.isDirectory(root.resolve("examples"))) root = root.getParent();
        var codec = new QuestionBankV2Codec();
        var bank = codec.parse(QBankTestPackageBuilder.read(root.resolve("examples/qbank-v2/rich-foundation.qbank")));
        assertEquals(bank, codec.parse(codec.write(bank)));
        assertEquals(2, bank.resources().size());
        assertEquals(1, bank.stimuli().size());
        assertEquals(new java.math.BigDecimal("1.5"), bank.questions().getFirst().scoreSpec().defaultMaxScore());
        try (var loaded = new io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader()
                .open(root.resolve("examples/qbank-v2/rich-foundation.qbank"))) {
            for (var resource : loaded.bank().resources()) try (var input = loaded.open(resource)) {
                assertTrue(input.readAllBytes().length > 0);
            }
        }
    }

    @Test void longMarkdownExampleIsAValidFileAssetForScrolling() throws Exception {
        Path examples = Path.of(System.getProperty("user.dir")).toAbsolutePath().resolve("examples/step7-practice");
        if (!Files.isDirectory(examples)) examples = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath().getParent().resolve("examples/step7-practice");
        String source = Files.readString(examples.resolve("Java 学习长文档（滚动测试）.md"));
        var document = new LegacyMarkdownCodec().parseLegacy(source).orElseThrow();
        assertEquals("doc_38284f165e60479f8855be07452300d5", document.assetId());
        assertTrue(document.contentId().startsWith("qfd:v1:"));
        assertTrue(source.lines().count() > 500);
    }

    @Test void bundledExampleCanBeCopiedIntoAnyWorkspaceDirectoryAndResolved() throws Exception {
        Path examples = Path.of(System.getProperty("user.dir")).toAbsolutePath().resolve("examples/step7-practice");
        if (!Files.isDirectory(examples)) examples = Path.of(System.getProperty("user.dir"))
                .toAbsolutePath().getParent().resolve("examples/step7-practice");
        Path documentFile = examples.resolve("Java集合示例.md");
        Path bankFile = examples.resolve("Java集合练习.qbank");
        var documentCodec = new LegacyMarkdownCodec();
        var bankCodec = new QuestionBankV2Codec();
        var document = documentCodec.parseLegacy(Files.readString(documentFile)).orElseThrow();
        var bank = bankCodec.parse(QBankTestPackageBuilder.read(bankFile));
        assertEquals(4, bank.questions().size());
        assertEquals(2, bank.questions().stream().filter(q -> q.type().equals("SINGLE_CHOICE")).count());
        assertEquals(2, bank.questions().stream().filter(q -> q.type().equals("MULTIPLE_CHOICE")).count());
        assertEquals(document.assetId(), bank.sourceDocuments().getFirst().assetId());
        assertEquals(document.contentId(), bank.sourceDocuments().getFirst().contentId());
        Set<String> sections = Set.of("section_arraylist_demo", "section_hashset_demo");
        for (var question : bank.questions()) for (var ref : question.sourceRefs()) {
            assertEquals(document.assetId(), ref.documentAssetId());
            assertEquals(document.contentId(), ref.documentContentId());
            assertTrue(sections.contains(ref.anchorName()));
        }

        var directory = new QuizForgeDataDirectory(temp.resolve("data"));
        var paths = new WorkspacePathResolver(directory);
        var workspaces = new WorkspaceService(new SqliteWorkspaceRepository(new SqliteDatabase(directory)),
                paths, Clock.systemUTC());
        var workspace = workspaces.createWorkspace("Example");
        Path custom = Files.createDirectories(paths.workspaceRoot(workspace.id()).resolve("学习/我的例题"));
        Files.copy(documentFile, custom.resolve(documentFile.getFileName()));
        Files.copy(bankFile, custom.resolve(bankFile.getFileName()));
        var scanner = new FileSystemWorkspaceAssetScanner(paths,
                new SqliteAssetIndexRepository(paths), Clock.systemUTC());
        var assets = scanner.scan(workspace.id());
        assertEquals(2, assets.size());
        assertEquals(1, assets.stream().filter(a -> a.assetType() == AssetType.QUESTION_BANK).count());
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                new QuestionBankReferenceResolver(scanner).resolve(workspace.id(), bank).getFirst().status());
    }
}
