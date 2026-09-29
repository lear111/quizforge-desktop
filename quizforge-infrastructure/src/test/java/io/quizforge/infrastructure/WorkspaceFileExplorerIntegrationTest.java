package io.quizforge.infrastructure;

import io.quizforge.core.question.*;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceFileService;
import io.quizforge.core.workspace.WorkspaceFileType;
import io.quizforge.core.workspace.WorkspaceFileTree;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.infrastructure.filesystem.FileSystemWorkspaceAssetScanner;
import io.quizforge.infrastructure.filesystem.LocalWorkspaceFileCatalog;
import io.quizforge.infrastructure.filesystem.LocalWorkspaceFileOperations;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.QuestionBankV2Codec;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteAssetIndexRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceFileExplorerIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temporaryDirectory;
    private Workspace workspace;
    private Path root;
    private WorkspaceFileService service;
    private FileSystemWorkspaceAssetScanner scanner;
    private final QuestionBankV2Codec banks = new QuestionBankV2Codec();

    @BeforeEach void setup() {
        var data = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        var paths = new WorkspacePathResolver(data);
        var workspaces = new WorkspaceService(new SqliteWorkspaceRepository(new SqliteDatabase(data)), paths, CLOCK);
        workspace = workspaces.createWorkspace("Explorer");
        root = paths.workspaceRoot(workspace.id());
        scanner = new FileSystemWorkspaceAssetScanner(paths, new SqliteAssetIndexRepository(paths), CLOCK);
        service = new WorkspaceFileService(workspaces, scanner, new LocalWorkspaceFileCatalog(paths, banks),
                banks, new LocalWorkspaceFileOperations(paths));
    }

    @Test void listsSupportedFilesRecursivelyAndHidesOtherFilesAndInternalFolder() throws Exception {
        write("原始资料/note.md", "# Note\nOrdinary Markdown");
        write("Custom/Nested/PDF.pdf", "sample");
        Files.createDirectories(root.resolve("EmptyFolder"));
        write(".quizforge/secret.md", "# hidden");
        WorkspaceFileTree tree = service.refresh(workspace.id());
        assertEquals(WorkspaceFileKind.MARKDOWN, entry(tree, "原始资料/note.md").kind());
        assertEquals(WorkspaceFileKind.DIRECTORY, entry(tree, "Custom/Nested").kind());
        assertTrue(tree.entries().stream().noneMatch(file -> file.relativePath().endsWith(".pdf")));
        assertEquals(WorkspaceFileKind.DIRECTORY, entry(tree, "EmptyFolder").kind());
        assertTrue(tree.entries().stream().noneMatch(file -> file.relativePath().startsWith(".quizforge")));
        assertTrue(tree.hasFiles());
    }

    @Test void foldersSortBeforeFilesThenCaseInsensitiveName() throws Exception {
        Files.createDirectories(root.resolve("bFolder"));
        Files.createDirectories(root.resolve("AFolder"));
        write("z.md", "Z");
        write("B.md", "B");
        assertEquals(List.of("AFolder", "bFolder", "documents", "materials", "question-banks", "sources",
                        "B.md", "z.md"),
                service.refresh(workspace.id()).childrenOf("").stream().map(file -> file.name()).toList());
    }

    @Test void classifiesFormalInvalidAndOrdinaryMarkdownAndQuestionBanks() throws Exception {
        write("Java/Knowledge.md", formalDocument("doc_alpha", "Java", "Facts stay stable."));
        write("Java/Invalid.md", formalDocument("doc_bad", "Bad", "Facts stay stable.")
                .replace("<!-- qf:id=section_one -->", ""));
        write("Java/Ordinary.markdown", "# Ordinary\nNo QuizForge metadata.");
        write("面试/Bank.qbank", banks.write(validBank()));
        write("面试/Broken.qbank", "{ broken json");
        write("Java/image.png", "not decoded");
        WorkspaceFileTree tree = service.refresh(workspace.id());
        assertEquals(WorkspaceFileKind.STANDARD_DOCUMENT, entry(tree, "Java/Knowledge.md").kind());
        assertEquals("doc_alpha", entry(tree, "Java/Knowledge.md").assetId());
        assertEquals(WorkspaceFileKind.INVALID_STANDARD_DOCUMENT, entry(tree, "Java/Invalid.md").kind());
        assertNotNull(entry(tree, "Java/Invalid.md").issue());
        assertTrue(tree.entries().stream().noneMatch(file -> file.relativePath().endsWith(".markdown")));
        assertEquals(WorkspaceFileKind.QUESTION_BANK, entry(tree, "面试/Bank.qbank").kind());
        assertEquals(WorkspaceFileKind.INVALID_QUESTION_BANK, entry(tree, "面试/Broken.qbank").kind());
        assertTrue(tree.entries().stream().noneMatch(file -> file.relativePath().endsWith(".png")));
    }

    @Test void treeWorksWithoutDefaultFoldersAndFindsCustomDeepBank() throws Exception {
        Files.delete(root.resolve("sources"));
        Files.delete(root.resolve("documents"));
        Files.delete(root.resolve("question-banks"));
        write("My/Deep/Bank.qbank", banks.write(validBank()));
        WorkspaceFileTree tree = service.refresh(workspace.id());
        assertEquals(WorkspaceFileKind.QUESTION_BANK, entry(tree, "My/Deep/Bank.qbank").kind());
        assertTrue(tree.childrenOf("My/Deep").stream().anyMatch(file -> file.name().equals("Bank.qbank")));
    }

    @Test void openReadsRealFilesAndReferenceResolverUsesExistingRegistry() throws Exception {
        String markdown = formalDocument("doc_alpha", "Java", "Facts stay stable.");
        String json = banks.write(validBank());
        write("Java/Knowledge.md", markdown);
        write("面试/Bank.qbank", json);
        service.refresh(workspace.id());
        var document = service.open(workspace.id(), "Java/Knowledge.md");
        var bank = service.open(workspace.id(), "面试/Bank.qbank");
        assertEquals(markdown, document.sourceText());
        assertEquals("doc_alpha", document.entry().assetId());
        assertEquals(json, bank.sourceText());
        assertEquals(validBank(), bank.questionBank());
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                new QuestionBankReferenceResolver(scanner).resolve(workspace.id(), bank.questionBank())
                        .getFirst().status());
        // No legacy StandardDocument or QuestionBank row is created by this test.
        Files.writeString(root.resolve("Java/Knowledge.md"), markdown.replace("stay stable", "can change"));
        assertEquals(QuestionBankReferenceResolver.Status.DIFFERENT_REVISION,
                new QuestionBankReferenceResolver(scanner).resolve(workspace.id(), bank.questionBank())
                        .getFirst().status());
    }

    @Test void refreshSeesMovedFileAndUpdatesRegistryPath() throws Exception {
        write("Java/Knowledge.md", formalDocument("doc_alpha", "Java", "Facts stay stable."));
        service.refresh(workspace.id());
        Path newFile = Files.createDirectories(root.resolve("Moved/Here")).resolve("Renamed.md");
        Files.move(root.resolve("Java/Knowledge.md"), newFile);
        WorkspaceFileTree tree = service.refresh(workspace.id());
        assertEquals(WorkspaceFileKind.STANDARD_DOCUMENT, entry(tree, "Moved/Here/Renamed.md").kind());
        assertTrue(tree.entries().stream().noneMatch(file -> file.relativePath().equals("Java/Knowledge.md")));
        assertEquals("Moved/Here/Renamed.md", scanner.scan(workspace.id()).stream()
                .filter(asset -> asset.assetId().equals("doc_alpha")).findFirst().orElseThrow().currentPath());
    }

    @Test void emptyWorkspaceHasVisibleEmptyDirectories() {
        WorkspaceFileTree tree = service.refresh(workspace.id());
        assertFalse(tree.hasFiles());
        assertEquals(4, tree.childrenOf("").size());
    }

    @Test void createsMarkdownAndQuestionBankInCustomFolderAndRefreshesRegistry() throws Exception {
        String folder = service.createFolder(workspace.id(), "", "自定义");
        String markdown = service.createFile(workspace.id(), folder, "笔记", WorkspaceFileType.MARKDOWN);
        String qbank = service.createFile(workspace.id(), folder, "练习", WorkspaceFileType.QUESTION_BANK);
        assertEquals("自定义/笔记.md", markdown);
        assertEquals("", Files.readString(root.resolve(markdown)));
        var bank = banks.parseEmptyDraft(Files.readString(root.resolve(qbank)));
        assertEquals("练习", bank.title());
        assertEquals(2, service.refresh(workspace.id()).childrenOf(folder).size());
        assertEquals(1, scanner.scan(workspace.id()).size());
        assertThrows(RuntimeException.class, () -> service.createFile(workspace.id(), folder,
                "笔记", WorkspaceFileType.MARKDOWN));
        assertEquals("", Files.readString(root.resolve(markdown)));
    }

    @Test void renameMoveAndDeleteKeepRegistryInSyncAndProtectOtherFiles() throws Exception {
        String folder = service.createFolder(workspace.id(), "", "My Folder");
        String file = service.createFile(workspace.id(), folder, "Study", WorkspaceFileType.MARKDOWN);
        Files.writeString(root.resolve(file), formalDocument("doc_study", "Study", "Learning content."));
        String id = scanner.scan(workspace.id()).getFirst().assetId();
        assertEquals(root.resolve(file), service.absolutePath(workspace.id(), file));
        String renamed = service.rename(workspace.id(), file, "Renamed.md");
        String movedFolder = service.rename(workspace.id(), folder, "New Folder");
        assertEquals("New Folder/Renamed.md", movedFolder + "/Renamed.md");
        assertEquals(id, scanner.scan(workspace.id()).getFirst().assetId());
        assertEquals("New Folder/Renamed.md", scanner.scan(workspace.id()).getFirst().currentPath());
        write("New Folder/hidden.pdf", "hidden data");
        assertThrows(IllegalArgumentException.class,
                () -> service.rename(workspace.id(), "New Folder/Renamed.md", "Renamed.qbank"));
        service.delete(workspace.id(), movedFolder);
        assertFalse(Files.exists(root.resolve(movedFolder)));
        assertTrue(scanner.scan(workspace.id()).isEmpty());
        assertTrue(Files.exists(root.resolve(".quizforge/workspace.json")));
        assertThrows(IllegalArgumentException.class,
                () -> service.delete(workspace.id(), ".quizforge/workspace.json"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createFolder(workspace.id(), "", "../outside"));
    }

    private io.quizforge.core.workspace.WorkspaceFileEntry entry(WorkspaceFileTree tree, String path) {
        return tree.entries().stream().filter(file -> file.relativePath().equals(path)).findFirst().orElseThrow();
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String formalDocument(String id, String title, String content) {
        return "---\nquizforge_format: \"study-document\"\nschema_version: \"1.0\"\n"
                + "quizforge_id: \"" + id + "\"\ntitle: \"" + title + "\"\nlanguage: \"en-US\"\n---\n"
                + "# " + title + "\n## Chapter\n<!-- qf:id=chapter_one -->\n"
                + "<!-- qf:anchor=section_one -->\n### Section\n<!-- qf:id=section_one -->\n" + content + "\n";
    }

    private QuestionBank validBank() {
        String contentId = new io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentV1()
                .parseIfStandard(formalDocument("doc_alpha", "Java", "Facts stay stable."))
                .orElseThrow().contentId();
        return new QuestionBank("qb_alpha", "Java Questions", "2.0", List.of(), List.of(Question.choice("q_one", "SINGLE_CHOICE", new TextContent("What stays stable?"), new TextContent("The source says facts."), List.of(SourceRef.anchor("doc_alpha", contentId, "section_one", 1, "Java", "Section")), new ChoicePayload(List.of(new ChoiceOption("opt_a", new TextContent("Facts")),
                                new ChoiceOption("opt_b", new TextContent("Nothing")))), new ChoiceAnswerSpec(List.of("opt_a")))), List.of());
    }
}
