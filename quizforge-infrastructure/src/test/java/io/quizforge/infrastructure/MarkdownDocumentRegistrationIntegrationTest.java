package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.infrastructure.filesystem.FileSystemWorkspaceAssetScanner;
import io.quizforge.infrastructure.filesystem.LocalWorkspaceFileCatalog;
import io.quizforge.infrastructure.filesystem.MarkdownDocumentRegistrationService;
import io.quizforge.infrastructure.filesystem.QuestionBankV1Codec;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteAssetIndexRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownDocumentRegistrationIntegrationTest {
    @TempDir Path temporary;
    private WorkspaceId workspace;
    private Path root;
    private WorkspacePathResolver paths;
    private SqliteAssetIndexRepository index;
    private FileSystemWorkspaceAssetScanner scanner;
    private MarkdownDocumentRegistrationService registration;

    @BeforeEach void setup() {
        var data = new QuizForgeDataDirectory(temporary.resolve("data"));
        paths = new WorkspacePathResolver(data);
        var database = new SqliteDatabase(data);
        workspace = new WorkspaceService(new SqliteWorkspaceRepository(database), paths, Clock.systemUTC())
                .createWorkspace("Registered Markdown").id();
        root = paths.workspaceRoot(workspace);
        index = new SqliteAssetIndexRepository(paths);
        scanner = new FileSystemWorkspaceAssetScanner(paths, index, Clock.systemUTC());
        registration = new MarkdownDocumentRegistrationService(paths, scanner);
    }

    @Test void ordinaryMarkdownIsNotIndexedUntilRegisteredAndThenCanMove() throws Exception {
        Path source = Files.createDirectories(root.resolve("custom/notes")).resolve("study.md");
        Files.writeString(source, "# Study\n\nA paragraph.\n");
        assertTrue(scanner.scan(workspace).isEmpty());
        assertTrue(registration.inspect(workspace, "custom/notes/study.md").isEmpty());

        var document = registration.register(workspace, "custom/notes/study.md");
        var asset = index.findById(workspace, document.documentAssetId()).orElseThrow();
        assertEquals(AssetType.STANDARD_DOCUMENT, asset.assetType());
        assertEquals(document.contentId(), asset.contentId());
        assertEquals("custom/notes/study.md", asset.currentPath());
        assertEquals("1", asset.schemaVersion());
        var catalog = new LocalWorkspaceFileCatalog(paths, new QuestionBankV1Codec(), false);
        assertEquals(WorkspaceFileKind.STANDARD_DOCUMENT,
                catalog.inspect(workspace, "custom/notes/study.md").kind());

        Path moved = Files.createDirectories(root.resolve("anywhere")).resolve("renamed.md");
        Files.move(source, moved);
        scanner.scan(workspace);
        var after = index.findById(workspace, document.documentAssetId()).orElseThrow();
        assertEquals("anywhere/renamed.md", after.currentPath());
        assertEquals(document.contentId(), after.contentId());
        assertEquals(document.documentAssetId(), registration.inspect(workspace, "anywhere/renamed.md")
                .orElseThrow().documentAssetId());
    }

    @Test void registrationIsIdempotentAndNewBlocksKeepOldIds() throws Exception {
        Path file = root.resolve("documents/study.md");
        Files.writeString(file, "# H\n\nOne.\n");
        var first = registration.register(workspace, "documents/study.md");
        String registered = Files.readString(file);
        var second = registration.register(workspace, "documents/study.md");
        assertEquals(first.documentAssetId(), second.documentAssetId());
        assertEquals(registered, Files.readString(file));
        Files.writeString(file, registered + "\nTwo.\n");
        var third = registration.ensureAddressing(workspace, "documents/study.md");
        assertEquals(first.documentAssetId(), third.documentAssetId());
        assertEquals(first.addressableBlocks().getFirst().nodeId(),
                third.addressableBlocks().getFirst().nodeId());
        assertEquals(3, third.addressableBlocks().size());
        assertFalse(first.contentId().equals(third.contentId()));
        assertEquals(third.contentId(), index.findById(workspace, third.documentAssetId())
                .orElseThrow().contentId());
    }

    @Test void invalidMetadataAndDuplicateIdsNeverOverwriteOriginal() throws Exception {
        Path invalid = root.resolve("documents/invalid.md");
        String malformed = "---\nquizforge:\n  format: document\n  version: 1\n  assetId: wrong\n---\n# H\n";
        Files.writeString(invalid, malformed);
        assertThrows(IllegalArgumentException.class,
                () -> registration.register(workspace, "documents/invalid.md"));
        assertEquals(malformed, Files.readString(invalid));

        Path one = root.resolve("documents/one.md");
        Files.writeString(one, "# H\n");
        var document = registration.register(workspace, "documents/one.md");
        Path duplicate = root.resolve("documents/duplicate.md");
        String cloned = Files.readString(one);
        Files.writeString(duplicate, cloned);
        assertThrows(IllegalStateException.class,
                () -> registration.register(workspace, "documents/duplicate.md"));
        assertEquals(cloned, Files.readString(duplicate));
        assertFalse(index.findById(workspace, document.documentAssetId()).isPresent());
        assertTrue(scanner.scanWithReport(workspace).issues().stream().anyMatch(issue ->
                issue.code().equals("DUPLICATE_ASSET_ID")));
    }

    @Test void registryFailureRollsBackPublishedMarkdown() throws Exception {
        Path file = root.resolve("documents/failure.md");
        String original = "# Keep this exact source\r\n\r\nParagraph.\r\n";
        Files.writeString(file, original);
        WorkspaceAssetScanner broken = id -> { throw new IllegalStateException("Registry unavailable"); };
        var failing = new MarkdownDocumentRegistrationService(paths, broken);
        assertThrows(IllegalStateException.class,
                () -> failing.register(workspace, "documents/failure.md"));
        assertEquals(original, Files.readString(file));
        assertTrue(index.list(workspace).isEmpty());
    }

    @Test void scannerReportsInvalidRegisteredMetadataAndContinuesOtherFiles() throws Exception {
        Files.writeString(root.resolve("documents/bad.md"),
                "---\nquizforge:\n  format: document\n  version: 1\n  assetId: invalid\n---\n# H\n");
        Files.writeString(root.resolve("documents/good.md"), "# Good\n");
        var good = registration.register(workspace, "documents/good.md");
        var result = scanner.scanWithReport(workspace);
        assertEquals(1, result.assets().size());
        assertEquals(good.documentAssetId(), result.assets().getFirst().assetId());
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.currentPath().equals("documents/bad.md")));
    }
}
