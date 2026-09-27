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
import io.quizforge.infrastructure.filesystem.RegisteredMarkdownCodec;
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

    @Test void documentOnlyRegistrationPreservesFrontMatterHeadingsAndHandwrittenAnchors() throws Exception {
        Path file = root.resolve("documents/navigation.md");
        String original = "---\r\ntitle: User title\r\ntags:\r\n  - java\r\n---\r\n"
                + "# Java\r\n## 示例\r\n正文 A\r\n## 示例\r\n正文 B\r\n"
                + "<!-- qf:anchor=定义 -->\r\n内容 A\r\n"
                + "<!-- qf:anchor=定义 -->\r\n内容 B\r\n";
        Files.writeString(file, original);
        var document = registration.registerDocument(workspace, "documents/navigation.md", original);
        String registered = Files.readString(file);
        assertTrue(document.documentAssetId().startsWith("doc_"));
        assertTrue(registered.startsWith("---\r\ntitle: User title\r\ntags:\r\n  - java\r\n"
                + "quizforge:\r\n  format: document\r\n  version: 1\r\n  assetId: "));
        assertEquals(original.substring(original.indexOf("# Java")),
                registered.substring(registered.indexOf("# Java")));
        assertFalse(registered.contains("qf:id="));
        assertEquals(2, document.anchors().size());
        assertEquals(2, document.anchors().getLast().occurrence());
        assertEquals("documents/navigation.md",
                index.findById(workspace, document.documentAssetId()).orElseThrow().currentPath());
        assertEquals(document.documentAssetId(), registration.registerDocument(workspace,
                "documents/navigation.md", registered).documentAssetId());
        assertEquals(registered, Files.readString(file));
    }

    @Test void documentOnlyRegistrationRejectsStaleSourceAndRollsBackOnRegistryFailure() throws Exception {
        Path file = root.resolve("documents/navigation-failure.md");
        String original = "# Keep this source\n\nParagraph.\n";
        Files.writeString(file, original);
        assertThrows(IllegalStateException.class, () -> registration.registerDocument(workspace,
                "documents/navigation-failure.md", "# Stale\n"));
        WorkspaceAssetScanner broken = id -> { throw new IllegalStateException("Registry unavailable"); };
        var failing = new MarkdownDocumentRegistrationService(paths, broken);
        assertThrows(IllegalStateException.class, () -> failing.registerDocument(workspace,
                "documents/navigation-failure.md", original));
        assertEquals(original, Files.readString(file));
    }

    @Test void ordinaryMarkdownIsNotIndexedUntilFirstAnchorAndThenCanMove() throws Exception {
        Path source = Files.createDirectories(root.resolve("custom/notes")).resolve("study.md");
        Files.writeString(source, "# Study\n\nA paragraph.\n");
        assertTrue(scanner.scan(workspace).isEmpty());
        assertTrue(registration.inspect(workspace, "custom/notes/study.md").isEmpty());

        registration.createAnchor(workspace, "custom/notes/study.md", Files.readString(source),
                3, 1, "Study source");
        var document = registration.inspect(workspace, "custom/notes/study.md").orElseThrow();
        var asset = index.findById(workspace, document.documentAssetId()).orElseThrow();
        assertEquals(AssetType.STANDARD_DOCUMENT, asset.assetType());
        assertEquals(document.contentId(), asset.contentId());
        assertEquals("custom/notes/study.md", asset.currentPath());
        assertEquals("1", asset.schemaVersion());
        var catalog = new LocalWorkspaceFileCatalog(paths, new QuestionBankV1Codec());
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

    @Test void sameAnchorIsIdempotentAndNewBlocksNeedOnlyTheirOwnAnchor() throws Exception {
        Path file = root.resolve("documents/study.md");
        Files.writeString(file, "# H\n\nOne.\n");
        registration.createAnchor(workspace, "documents/study.md", Files.readString(file),
                3, 1, "one");
        var first = registration.inspect(workspace, "documents/study.md").orElseThrow();
        String registered = Files.readString(file);
        registration.createAnchor(workspace, "documents/study.md", registered, 4, 1, "one");
        assertEquals(registered, Files.readString(file));
        Files.writeString(file, registered + "\nTwo.\n");
        registration.createAnchor(workspace, "documents/study.md", Files.readString(file),
                6, 1, "two");
        var third = registration.inspect(workspace, "documents/study.md").orElseThrow();
        assertEquals(first.documentAssetId(), third.documentAssetId());
        assertEquals(2, third.anchors().size());
        assertFalse(Files.readString(file).contains("qf:id=node_"));
        assertFalse(first.contentId().equals(third.contentId()));
        assertEquals(third.contentId(), index.findById(workspace, third.documentAssetId())
                .orElseThrow().contentId());
    }

    @Test void invalidMetadataAndDuplicateIdsNeverOverwriteOriginal() throws Exception {
        Path invalid = root.resolve("documents/invalid.md");
        String malformed = "---\nquizforge:\n  format: document\n  version: 1\n  assetId: wrong\n---\n# H\n";
        Files.writeString(invalid, malformed);
        assertThrows(IllegalArgumentException.class,
                () -> registration.createAnchor(workspace, "documents/invalid.md", malformed,
                        1, 1, "bad"));
        assertEquals(malformed, Files.readString(invalid));

        Path one = root.resolve("documents/one.md");
        Files.writeString(one, "# H\n");
        registration.createAnchor(workspace, "documents/one.md", Files.readString(one),
                1, 1, "one");
        var document = registration.inspect(workspace, "documents/one.md").orElseThrow();
        Path duplicate = root.resolve("documents/duplicate.md");
        String cloned = Files.readString(one);
        Files.writeString(duplicate, cloned);
        assertThrows(IllegalStateException.class,
                () -> registration.createAnchor(workspace, "documents/duplicate.md", cloned,
                        2, 1, "one"));
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
                () -> failing.createAnchor(workspace, "documents/failure.md", original,
                        3, 1, "source"));
        assertEquals(original, Files.readString(file));
        assertTrue(index.list(workspace).isEmpty());
    }

    @Test void scannerReportsInvalidRegisteredMetadataAndContinuesOtherFiles() throws Exception {
        Files.writeString(root.resolve("documents/bad.md"),
                "---\nquizforge:\n  format: document\n  version: 1\n  assetId: invalid\n---\n# H\n");
        Files.writeString(root.resolve("documents/good.md"), "# Good\n");
        registration.createAnchor(workspace, "documents/good.md", Files.readString(root.resolve("documents/good.md")),
                1, 1, "good");
        var good = registration.inspect(workspace, "documents/good.md").orElseThrow();
        var result = scanner.scanWithReport(workspace);
        assertEquals(1, result.assets().size());
        assertEquals(good.documentAssetId(), result.assets().getFirst().assetId());
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.currentPath().equals("documents/bad.md")));
    }

    @Test void firstSourceReferenceRegistersOnlySelectedBlockAndPreservesFrontMatter() throws Exception {
        Path file = root.resolve("documents/notes.md");
        String original = "---\r\ntitle: User title\r\ntags:\r\n  - java\r\n---\r\n"
                + "# Heading\r\n\r\nFirst paragraph.\r\n\r\nSecond paragraph.\r\n";
        Files.writeString(file, original);
        assertTrue(registration.inspect(workspace, "documents/notes.md").isEmpty());
        var anchor = registration.createAnchor(workspace, "documents/notes.md", original,
                5, 1, "第三题来源");
        var document = registration.inspect(workspace, "documents/notes.md").orElseThrow();
        String saved = Files.readString(file);
        assertTrue(saved.startsWith("---\r\ntitle: User title\r\ntags:\r\n  - java\r\n"));
        assertTrue(saved.contains("<!-- qf:anchor=第三题来源 -->\r\nSecond paragraph."));
        assertFalse(saved.contains("qf:id=node_"));
        assertEquals("documents/notes.md", index.findById(workspace, document.documentAssetId())
                .orElseThrow().currentPath());
        assertEquals(document.contentId(), index.findById(workspace, document.documentAssetId())
                .orElseThrow().contentId());
        assertEquals("第三题来源", anchor.name());
        assertEquals(1, anchor.occurrence());
    }

    @Test void sameNameUsesSourceOrderAndSameBlockDoesNotDuplicateMarker() throws Exception {
        Path file = root.resolve("documents/repeated.md");
        String original = "# Heading\n\nOne.\n\nTwo.\n";
        Files.writeString(file, original);
        var first = registration.createAnchor(workspace, "documents/repeated.md", original,
                3, 1, "shared");
        String one = Files.readString(file);
        var second = registration.createAnchor(workspace, "documents/repeated.md", one,
                6, 1, "shared");
        String two = Files.readString(file);
        assertEquals(first.name(), second.name());
        assertEquals(2, second.occurrence());
        var reused = registration.createAnchor(workspace, "documents/repeated.md", two,
                7, 1, "shared");
        assertEquals(second, reused);
        assertEquals(two, Files.readString(file));
        var different = registration.createAnchor(workspace, "documents/repeated.md", two,
                7, 1, "extra");
        assertEquals("extra", different.name());
        var parsed = registration.inspect(workspace, "documents/repeated.md").orElseThrow();
        assertEquals(3, parsed.anchors().size());
        assertEquals(1, parsed.anchors().get(0).occurrence());
        assertEquals(2, parsed.anchors().get(1).occurrence());
        assertEquals(parsed.anchors().get(1).blockRange(), parsed.anchors().get(2).blockRange());
    }

    @Test void handWrittenOrphanAndMalformedAnchorsRemainExplicit() throws Exception {
        var codec = new RegisteredMarkdownCodec();
        String source = codec.prepareAnchor("# H\n\nParagraph.\n", "documents/manual.md", 3, 1, "manual").source();
        var parsed = codec.parseIfRegistered(source, "documents/manual.md").orElseThrow();
        assertEquals("manual", parsed.anchors().getFirst().name());
        assertTrue(parsed.anchorErrors().isEmpty());
        String orphan = source + "\n<!-- qf:anchor=orphan -->\n";
        var broken = codec.parseIfRegistered(orphan, "documents/manual.md").orElseThrow();
        assertTrue(broken.anchors().getLast().orphan());
        assertTrue(broken.anchorErrors().stream().anyMatch(error -> error.startsWith("ORPHAN_ANCHOR")));
        assertTrue(codec.parseIfRegistered(source + "\n<!-- qf:anchor= -->\n", "documents/manual.md")
                .orElseThrow().anchorErrors().stream().anyMatch(error -> error.startsWith("EMPTY_ANCHOR_NAME")));
    }

    @Test void anchorCreationRejectsUnsafeNamesAndRollsBackRegistryFailure() throws Exception {
        Path file = root.resolve("documents/safe.md");
        String original = "# H\n\nKeep.\n";
        Files.writeString(file, original);
        assertThrows(IllegalArgumentException.class,
                () -> registration.createAnchor(workspace, "documents/safe.md", original, 3, 1, " "));
        assertThrows(IllegalArgumentException.class,
                () -> registration.createAnchor(workspace, "documents/safe.md", original, 3, 1, "x --> y"));
        assertThrows(IllegalStateException.class,
                () -> registration.createAnchor(workspace, "documents/safe.md", original + "changed", 3, 1, "ok"));
        var failing = new MarkdownDocumentRegistrationService(paths,
                id -> { throw new IllegalStateException("Registry unavailable"); });
        assertThrows(IllegalStateException.class,
                () -> failing.createAnchor(workspace, "documents/safe.md", original, 3, 1, "ok"));
        assertEquals(original, Files.readString(file));
    }

    @Test void addingAnchorChangesCurrentRevisionWhileMovingKeepsIdentity() throws Exception {
        Path file = root.resolve("documents/revision.md");
        String original = "# H\n\nOne.\n";
        Files.writeString(file, original);
        registration.createAnchor(workspace, "documents/revision.md", original, 3, 1, "first");
        var before = registration.inspect(workspace, "documents/revision.md").orElseThrow();
        String one = Files.readString(file);
        registration.createAnchor(workspace, "documents/revision.md", one, 4, 1, "second");
        var after = registration.inspect(workspace, "documents/revision.md").orElseThrow();
        assertEquals(before.documentAssetId(), after.documentAssetId());
        assertFalse(before.contentId().equals(after.contentId()));
        Path moved = Files.createDirectories(root.resolve("custom")).resolve("moved.md");
        Files.move(file, moved);
        scanner.scan(workspace);
        assertEquals("custom/moved.md", index.findById(workspace, before.documentAssetId())
                .orElseThrow().currentPath());
    }

    @Test void codeFenceTextThatLooksLikeAnAnchorIsNotAnAnchor() {
        var codec = new RegisteredMarkdownCodec();
        String source = "---\nquizforge:\n  format: document\n  version: 1\n"
                + "  assetId: doc_code\n---\n# H\n\n```md\n<!-- qf:anchor=not-real -->\n```\n";
        var parsed = codec.parseIfRegistered(source, "documents/code.md").orElseThrow();
        assertTrue(parsed.anchors().isEmpty());
        assertTrue(parsed.anchorErrors().isEmpty());
    }
}
