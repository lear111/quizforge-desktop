package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.infrastructure.filesystem.FileSystemWorkspaceAssetScanner;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.WorkspaceManifestStore;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteAssetIndexRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceAssetFoundationIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);
    private static final String DOCUMENT = """
            ---
            quizforge_format: "study-document"
            schema_version: "1.0"
            quizforge_id: "doc_java"
            title: "Java Study"
            language: "en-US"
            ---
            # Java Study
            ## Collections
            <!-- qf:id=chapter_collections -->
            ### List
            <!-- qf:id=section_list -->
            Lists preserve order.
            """;
    @TempDir Path temporaryDirectory;
    private QuizForgeDataDirectory dataDirectory;
    private WorkspacePathResolver paths;
    private SqliteAssetIndexRepository index;
    private FileSystemWorkspaceAssetScanner scanner;
    private Workspace workspace;
    private Path root;

    private Path packageFixture() {
        Path file = root.resolve("question-banks/package.qbank");
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(file,
                QuestionBankV2CodecTest.valid("SINGLE_CHOICE"));
        return file;
    }

    @Test void packageScanHasLogicalRevision() {
        packageFixture();
        var bank = scanner.scan(workspace.id()).getFirst();
        assertEquals("qb_one", bank.assetId());
        assertEquals("2.0", bank.schemaVersion());
        assertEquals(new io.quizforge.infrastructure.filesystem.QuestionBankV2Codec()
                .contentId(QuestionBankV2CodecTest.valid("SINGLE_CHOICE")), bank.contentId());
    }

    @Test void emptyPackageHasRevisionAndUnsupportedNeighborDoesNotAbortScan() throws Exception {
        var draft = new io.quizforge.core.question.QuestionBank("qb_empty", "Empty draft",
                java.util.List.of(), java.util.List.of(), java.util.List.of());
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter()
                .write(root.resolve("question-banks/empty.qbank"), draft);
        Files.writeString(root.resolve("C.qbank"),
                "{\"format\":\"quizforge-question-bank\",\"schemaVersion\":\"1.0\"}");

        var result = scanner.scanWithReport(workspace.id());
        assertEquals(1, result.assets().size());
        var asset = index.findById(workspace.id(), "qb_empty").orElseThrow();
        assertEquals("question-banks/empty.qbank", asset.currentPath());
        assertEquals(new io.quizforge.infrastructure.filesystem.QuestionBankV2Codec()
                .contentId(draft), asset.contentId());
        assertEquals(1, result.issues().size());
        assertEquals("INVALID_ASSET_FILE", result.issues().getFirst().code());
        assertEquals("C.qbank", result.issues().getFirst().currentPath());
    }

    @Test void packageRenameMoveKeepsIdentityAndRevision() throws Exception {
        Path file = packageFixture(); var before = scanner.scan(workspace.id()).getFirst();
        Path folder = Files.createDirectories(root.resolve("custom/packages"));
        Files.move(file, folder.resolve("renamed.qbank"));
        var after = scanner.scan(workspace.id()).getFirst();
        assertEquals(before.assetId(), after.assetId()); assertEquals(before.contentId(), after.contentId());
        assertEquals("custom/packages/renamed.qbank", after.currentPath());
    }

    @Test void packageContentChangeUpdatesRegistryRevision() {
        Path file = packageFixture(); var before = scanner.scan(workspace.id()).getFirst();
        var bank = QuestionBankV2CodecTest.valid("SINGLE_CHOICE");
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(file,
                new io.quizforge.core.question.QuestionBank(bank.assetId(), "Changed", bank.stimuli(), bank.questions(), bank.resources()));
        var after = scanner.scan(workspace.id()).getFirst();
        assertEquals(before.assetId(), after.assetId()); assertFalse(before.contentId().equals(after.contentId()));
    }

    @Test void duplicatePackageIdentityIsExcludedFromRegistry() throws Exception {
        Path file = packageFixture(); scanner.scan(workspace.id());
        Files.copy(file, root.resolve("duplicate.qbank"));
        var result = scanner.scanWithReport(workspace.id());
        assertTrue(result.assets().isEmpty());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("DUPLICATE_ASSET_ID")));
        assertTrue(index.findById(workspace.id(), "qb_one").isEmpty());
    }

    @Test void deletedPackageIsRemovedOnRescan() throws Exception {
        Path file = packageFixture(); scanner.scan(workspace.id()); Files.delete(file);
        assertTrue(scanner.scan(workspace.id()).isEmpty());
        assertTrue(index.findById(workspace.id(), "qb_one").isEmpty());
    }

    @BeforeEach void setup() {
        dataDirectory = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        paths = new WorkspacePathResolver(dataDirectory);
        SqliteDatabase global = new SqliteDatabase(dataDirectory);
        WorkspaceService workspaces = new WorkspaceService(new SqliteWorkspaceRepository(global), paths, CLOCK);
        workspace = workspaces.createWorkspace("Java Learning");
        root = paths.workspaceRoot(workspace.id());
        index = new SqliteAssetIndexRepository(paths);
        scanner = new FileSystemWorkspaceAssetScanner(paths, index, CLOCK);
    }

    @Test void createsRealWorkspaceAndStableManifestIdentity() throws Exception {
        assertTrue(Files.isDirectory(root.resolve("sources")));
        assertTrue(Files.isDirectory(root.resolve("documents")));
        assertTrue(Files.isDirectory(root.resolve("question-banks")));
        assertTrue(Files.isDirectory(root.resolve(".quizforge")));
        assertTrue(Files.isRegularFile(root.resolve(".quizforge/workspace.db")));
        JsonNode manifest = new ObjectMapper().readTree(root.resolve(".quizforge/workspace.json").toFile());
        assertEquals("quizforge-workspace", manifest.path("format").asText());
        assertEquals("1.0", manifest.path("schemaVersion").asText());
        assertEquals(workspace.id().toString(), manifest.path("workspaceId").asText());
        assertEquals("Java Learning", manifest.path("name").asText());
        assertEquals(workspace.id(), new WorkspaceManifestStore().readId(root));
        Path renamed = root.resolveSibling("renamed-workspace");
        Files.move(root, renamed);
        assertEquals(workspace.id(), new WorkspaceManifestStore().readId(renamed));
        assertEquals(renamed.toRealPath(), paths.workspaceRoot(workspace.id()));
        Files.writeString(renamed.resolve("documents/study.md"), DOCUMENT);
        assertEquals("documents/study.md", scanner.scan(workspace.id()).getFirst().currentPath());
        try (Connection connection = new io.quizforge.infrastructure.persistence.WorkspaceAssetDatabase(renamed)
                .openConnection(); Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='asset_registry'")) {
            assertTrue(row.next());
        }
    }

    @Test void existingWorkspaceGetsFoundationWithoutMovingLegacyMaterial() throws Exception {
        Workspace legacy = new Workspace(WorkspaceId.newId(), "Old Workspace",
                CLOCK.instant(), CLOCK.instant());
        Path legacyRoot = dataDirectory.workspacesDirectory().resolve(legacy.id().toString());
        Files.createDirectories(legacyRoot.resolve("materials"));
        Files.writeString(legacyRoot.resolve("materials/original.md"), "legacy material");
        SqliteWorkspaceRepository repository = new SqliteWorkspaceRepository(new SqliteDatabase(dataDirectory));
        repository.save(legacy);
        WorkspaceService workspaces = new WorkspaceService(repository, paths, CLOCK);
        assertEquals(2, workspaces.listWorkspaces().size());
        assertTrue(Files.isDirectory(legacyRoot.resolve("sources")));
        assertTrue(Files.isDirectory(legacyRoot.resolve("documents")));
        assertTrue(Files.isDirectory(legacyRoot.resolve("question-banks")));
        assertEquals(legacy.id(), new WorkspaceManifestStore().readId(legacyRoot));
        assertTrue(Files.isRegularFile(legacyRoot.resolve(".quizforge/workspace.db")));
        assertEquals("legacy material", Files.readString(legacyRoot.resolve("materials/original.md")));
    }

    @Test void scansDeclaredAssetsAndSkipsOrdinaryAndInternalFiles() throws Exception {
        Path custom = Files.createDirectories(root.resolve("notes/semester-one"));
        Files.writeString(custom.resolve("study.md"), DOCUMENT, StandardCharsets.UTF_8);
        QBankTestPackageBuilder.write(root.resolve("question-banks/quiz.qbank"),
                new io.quizforge.infrastructure.filesystem.QuestionBankV2Codec().write(
                    new io.quizforge.core.question.QuestionBank("qb_java", "Java Quiz", java.util.List.of(),
                        QuestionBankV2CodecTest.valid("SINGLE_CHOICE").questions(), java.util.List.of())));
        Files.writeString(root.resolve("sources/plain.md"), "# An ordinary note");
        Files.writeString(root.resolve("documents/ignored.markdown"),
                DOCUMENT.replace("doc_java", "doc_ignored"));
        Files.writeString(root.resolve(".quizforge/internal.md"), DOCUMENT);
        var assets = scanner.scan(workspace.id());
        assertEquals(2, assets.size());
        var document = index.findById(workspace.id(), "doc_java").orElseThrow();
        assertEquals(AssetType.STANDARD_DOCUMENT, document.assetType());
        assertEquals("notes/semester-one/study.md", document.currentPath());
        assertEquals("Java Study", document.title());
        var bank = index.findById(workspace.id(), "qb_java").orElseThrow();
        assertEquals(AssetType.QUESTION_BANK, bank.assetType());
        assertEquals("question-banks/quiz.qbank", bank.currentPath());
        assertEquals("Java Quiz", bank.title());
        assertEquals(assets, index.list(workspace.id()));
        assertTrue(index.findById(workspace.id(), "doc_ignored").isEmpty());
    }

    @Test void renameAndMoveUpdatePathWithoutChangingAssetId() throws Exception {
        Path original = root.resolve("documents/study.md");
        Files.writeString(original, DOCUMENT);
        scanner.scan(workspace.id());
        assertEquals("documents/study.md", index.findById(workspace.id(), "doc_java")
                .orElseThrow().currentPath());
        String originalContentId = index.findById(workspace.id(), "doc_java")
                .orElseThrow().contentId();
        Path renamed = root.resolve("documents/renamed.md");
        Files.move(original, renamed);
        scanner.scan(workspace.id());
        assertEquals("documents/renamed.md", index.findById(workspace.id(), "doc_java")
                .orElseThrow().currentPath());
        assertEquals(originalContentId, index.findById(workspace.id(), "doc_java")
                .orElseThrow().contentId());
        Path other = Files.createDirectories(root.resolve("my-folders/notes"));
        Files.move(renamed, other.resolve("moved.md"));
        scanner.scan(workspace.id());
        assertEquals("my-folders/notes/moved.md", index.findById(workspace.id(), "doc_java")
                .orElseThrow().currentPath());
        assertEquals(originalContentId, index.findById(workspace.id(), "doc_java")
                .orElseThrow().contentId());
        assertEquals(1, index.list(workspace.id()).size());
    }

    @Test void damagedFilesDoNotStopScanAndDeletedIndexCanBeRebuilt() throws Exception {
        Files.writeString(root.resolve("documents/good.md"), DOCUMENT);
        Files.writeString(root.resolve("documents/broken.md"), "---\nquizforge_id: [\n---\n");
        QBankTestPackageBuilder.write(root.resolve("question-banks/broken.qbank"),
                "{\"assetId\":\"qb_broken\",\"questions\":[");
        QBankTestPackageBuilder.write(root.resolve("question-banks/wrong.qbank"),
                "{\"format\":\"something-else\",\"assetId\":\"qb_wrong\"}");
        var report = scanner.scanWithReport(workspace.id());
        assertEquals(1, report.assets().size());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.currentPath().equals(
                "question-banks/broken.qbank")));
        assertFalse(index.findById(workspace.id(), "qb_broken").isPresent());
        Path database = root.resolve(".quizforge/workspace.db");
        Files.delete(database);
        assertEquals(1, scanner.scan(workspace.id()).size());
        assertTrue(index.findById(workspace.id(), "doc_java").isPresent());
        assertTrue(Files.isRegularFile(database));
    }

    @Test void removedFileDisappearsFromDerivedIndex() throws Exception {
        Path file = root.resolve("documents/study.md");
        Files.writeString(file, DOCUMENT);
        scanner.scan(workspace.id());
        Files.delete(file);
        assertTrue(scanner.scan(workspace.id()).isEmpty());
        assertTrue(index.findById(workspace.id(), "doc_java").isEmpty());
    }

    @Test void contentEditUpdatesRevisionButKeepsIdentity() throws Exception {
        Path file = root.resolve("documents/study.md");
        Files.writeString(file, DOCUMENT);
        scanner.scan(workspace.id());
        String before = index.findById(workspace.id(), "doc_java").orElseThrow().contentId();
        Files.writeString(file, DOCUMENT.replace("Lists preserve order.", "Lists preserve insertion order."));
        scanner.scan(workspace.id());
        var after = index.findById(workspace.id(), "doc_java").orElseThrow();
        assertEquals("doc_java", after.assetId());
        assertFalse(before.equals(after.contentId()));
    }

    @Test void duplicateAssetIdIsReportedAndNotIndexed() throws Exception {
        Files.writeString(root.resolve("documents/first.md"), DOCUMENT);
        scanner.scan(workspace.id());
        Path custom = Files.createDirectories(root.resolve("custom"));
        Files.writeString(custom.resolve("second.md"), DOCUMENT);
        var result = scanner.scanWithReport(workspace.id());
        assertTrue(result.assets().isEmpty());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code().equals("DUPLICATE_ASSET_ID")
                && issue.detail().contains("doc_java")));
        assertTrue(index.findById(workspace.id(), "doc_java").isEmpty());
    }

    @Test void removingDefaultFoldersDoesNotConstrainAssetDiscovery() throws Exception {
        Files.delete(root.resolve("sources"));
        Files.delete(root.resolve("documents"));
        Files.delete(root.resolve("question-banks"));
        WorkspaceService workspaces = new WorkspaceService(
                new SqliteWorkspaceRepository(new SqliteDatabase(dataDirectory)), paths, CLOCK);
        workspaces.getWorkspace(workspace.id());
        assertFalse(Files.exists(root.resolve("sources")));
        assertFalse(Files.exists(root.resolve("documents")));
        assertFalse(Files.exists(root.resolve("question-banks")));
        Path custom = Files.createDirectories(root.resolve("Java/Collections"));
        Files.writeString(custom.resolve("study.md"), DOCUMENT);
        var asset = scanner.scan(workspace.id()).getFirst();
        assertEquals("Java/Collections/study.md", asset.currentPath());
        assertFalse(asset.currentPath().matches("^[A-Za-z]:.*"));
        try (Connection connection = new io.quizforge.infrastructure.persistence.WorkspaceAssetDatabase(root)
                .openConnection(); Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT current_path, content_id, schema_version "
                        + "FROM asset_registry WHERE asset_id='doc_java'")) {
            assertTrue(row.next());
            assertEquals("Java/Collections/study.md", row.getString("current_path"));
            assertEquals(asset.contentId(), row.getString("content_id"));
            assertEquals("1.0", row.getString("schema_version"));
        }
    }
}
