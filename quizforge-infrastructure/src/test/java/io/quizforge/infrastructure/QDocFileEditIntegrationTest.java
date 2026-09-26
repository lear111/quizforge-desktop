package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.document.qdoc.*;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.infrastructure.filesystem.*;
import io.quizforge.infrastructure.persistence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QDocFileEditIntegrationTest {
    @TempDir Path temp;
    private WorkspaceService workspaces;
    private Workspace workspace;
    private WorkspacePathResolver paths;
    private LocalStandardDocumentFileStorage storage;
    private FileSystemWorkspaceAssetScanner scanner;
    private SqliteAssetIndexRepository index;
    private final QDocV1Codec codec = new QDocV1Codec();

    @BeforeEach void setup() {
        QuizForgeDataDirectory directory = new QuizForgeDataDirectory(temp.resolve("data"));
        paths = new WorkspacePathResolver(directory);
        workspaces = new WorkspaceService(new SqliteWorkspaceRepository(new SqliteDatabase(directory)),
                paths, Clock.systemUTC());
        workspace = workspaces.createWorkspace("Editor");
        storage = new LocalStandardDocumentFileStorage(directory);
        index = new SqliteAssetIndexRepository(paths);
        scanner = new FileSystemWorkspaceAssetScanner(paths, index, Clock.systemUTC(), false);
    }

    private QDocDocument model(String body) {
        var section = new DocumentNode("section_one", DocumentNodeType.SECTION, "Section",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, body)));
        var chapter = new DocumentNode("chapter_one", DocumentNodeType.CHAPTER, "Chapter", List.of(section));
        return new QDocDocument("quizforge-document", "1.0", "doc_editor",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Knowledge", "en-US", List.of(chapter));
    }

    private String create(QDocDocument document) {
        try (var staged = storage.stageCreate(workspace.id(), document.title(), codec.write(document))) {
            staged.publish();
            staged.complete();
            scanner.scan(workspace.id());
            return staged.currentPath();
        }
    }

    @Test void loadEditSaveReloadKeepsAssetIdAndUpdatesContentIdAndRegistry() throws Exception {
        QDocDocument original = model("Old content");
        String path = create(original);
        QDocDocument loaded = codec.parse(storage.read(workspace.id(), path));
        QDocEditorModel edit = new QDocEditorModel(loaded, DocumentTemplate.GENERAL_KNOWLEDGE);
        edit.setBlockText(List.of(0, 0, 0), "New content");
        String previous = codec.contentId(loaded);
        var saved = new QDocFileEditService(workspaces, storage, scanner, codec)
                .save(workspace.id(), path, previous, edit.document());
        assertEquals(original.id(), saved.assetId());
        assertEquals(path, saved.currentPath());
        assertNotEquals(previous, saved.contentId());
        assertEquals(edit.document(), codec.parse(Files.readString(paths.workspaceRoot(workspace.id()).resolve(path))));
        assertEquals(saved, index.findById(workspace.id(), original.id()).orElseThrow());
    }

    @Test void validationFailureAndExternalChangePreserveTheFile() throws Exception {
        String path = create(model("Old content"));
        String oldFile = storage.read(workspace.id(), path);
        QDocEditorModel edit = new QDocEditorModel(model("Old content"), DocumentTemplate.GENERAL_KNOWLEDGE);
        edit.setBlockText(List.of(0, 0, 0), "");
        var service = new QDocFileEditService(workspaces, storage, scanner, codec);
        assertThrows(IllegalArgumentException.class,
                () -> service.save(workspace.id(), path, codec.contentId(model("Old content")), edit.document()));
        assertEquals(oldFile, storage.read(workspace.id(), path));
        String external = codec.write(model("External content"));
        Files.writeString(paths.workspaceRoot(workspace.id()).resolve(path), external);
        assertThrows(IllegalStateException.class,
                () -> service.save(workspace.id(), path, codec.contentId(model("Old content")), model("My content")));
        assertEquals(external, storage.read(workspace.id(), path));
    }

    @Test void registryRefreshFailureRestoresPriorRevision() {
        String path = create(model("Old content"));
        String previous = codec.contentId(model("Old content"));
        var failing = new QDocFileEditService(workspaces, storage,
                id -> { throw new IllegalStateException("Injected registry failure"); }, codec);
        assertThrows(IllegalStateException.class,
                () -> failing.save(workspace.id(), path, previous, model("New content")));
        assertEquals(codec.write(model("Old content")), storage.read(workspace.id(), path));
        assertEquals(previous, scanner.scan(workspace.id()).stream().filter(a -> a.assetId().equals("doc_editor"))
                .findFirst().orElseThrow().contentId());
    }

    @Test void chapterParagraphRemainsReadableAsQuestionSource() {
        var section = new DocumentNode("section_one", DocumentNodeType.SECTION, "Section",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, "Section detail")));
        var chapter = new DocumentNode("chapter_one", DocumentNodeType.CHAPTER, "Chapter",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, "Chapter overview"), section));
        var document = new QDocDocument("quizforge-document", "1.0", "doc_editor",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Knowledge", "en-US", List.of(chapter));
        String path = create(document);
        var snapshot = new QDocFormalDocumentReader(storage).read(workspace.id(), path);
        assertEquals("Chapter overview", snapshot.chapters().getFirst().content());
        assertEquals("Section detail", snapshot.chapters().getFirst().sections().getFirst().content());
    }
}
