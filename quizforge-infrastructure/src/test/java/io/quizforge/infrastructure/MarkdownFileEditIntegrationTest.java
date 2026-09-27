package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.infrastructure.filesystem.FileSystemWorkspaceAssetScanner;
import io.quizforge.infrastructure.filesystem.LocalStandardDocumentFileStorage;
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

class MarkdownFileEditIntegrationTest {
    @TempDir Path temporaryDirectory;
    private Workspace workspace;
    private Path root;
    private MarkdownFileEditService edits;
    private FileSystemWorkspaceAssetScanner scanner;

    @BeforeEach void setup() {
        var data = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        var paths = new WorkspacePathResolver(data);
        var workspaces = new WorkspaceService(new SqliteWorkspaceRepository(new SqliteDatabase(data)),
                paths, Clock.systemUTC());
        workspace = workspaces.createWorkspace("Markdown editor");
        root = paths.workspaceRoot(workspace.id());
        scanner = new FileSystemWorkspaceAssetScanner(paths, new SqliteAssetIndexRepository(paths),
                Clock.systemUTC());
        edits = new MarkdownFileEditService(workspaces, new LocalStandardDocumentFileStorage(data), scanner);
    }

    @Test void ordinaryMarkdownSavesExactSourceIncludingLeadingBrace() throws Exception {
        Path file = root.resolve("notes.md");
        Files.writeString(file, "# Note\n");
        edits.save(workspace.id(), "notes.md", "# Note\n", "{some Markdown text}\n", null);
        assertEquals("{some Markdown text}\n", Files.readString(file));
        assertTrue(scanner.scan(workspace.id()).isEmpty());
    }

    @Test void externalChangeDoesNotGetOverwritten() throws Exception {
        Path file = root.resolve("notes.md");
        Files.writeString(file, "first");
        Files.writeString(file, "external edit");
        assertThrows(IllegalStateException.class,
                () -> edits.save(workspace.id(), "notes.md", "first", "my edit", null));
        assertEquals("external edit", Files.readString(file));
    }

    @Test void formalMarkdownKeepsIdentityAndChangesRevision() throws Exception {
        Path file = root.resolve("study.md");
        String original = document("Original body.");
        Files.writeString(file, original);
        String before = scanner.scan(workspace.id()).getFirst().contentId();
        edits.save(workspace.id(), "study.md", original, document("Updated body."), "doc_study");
        var after = scanner.scan(workspace.id()).getFirst();
        assertEquals("doc_study", after.assetId());
        assertNotEquals(before, after.contentId());
        assertEquals("study.md", after.currentPath());
    }

    @Test void invalidFormalEditRollsBackOriginalAndRegistry() throws Exception {
        Path file = root.resolve("study.md");
        String original = document("Original body.");
        Files.writeString(file, original);
        String before = scanner.scan(workspace.id()).getFirst().contentId();
        assertThrows(IllegalStateException.class, () -> edits.save(workspace.id(), "study.md",
                original, document("Updated body.").replace("doc_study", "doc_forged"), "doc_study"));
        assertEquals(original, Files.readString(file));
        assertEquals(before, scanner.scan(workspace.id()).getFirst().contentId());
    }

    private String document(String body) {
        return "---\nquizforge_format: study-document\nschema_version: \"1.0\"\n"
                + "quizforge_id: doc_study\ntitle: Study\nlanguage: en-US\n---\n# Study\n"
                + "## Chapter\n<!-- qf:id=chapter_one -->\n### Section\n"
                + "<!-- qf:id=section_one -->\n" + body + "\n";
    }
}
