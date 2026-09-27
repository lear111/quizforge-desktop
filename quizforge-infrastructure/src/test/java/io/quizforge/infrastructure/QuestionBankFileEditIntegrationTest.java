package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.question.*;
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

class QuestionBankFileEditIntegrationTest {
    @TempDir Path temp;
    private WorkspaceService workspaces;
    private Workspace workspace;
    private WorkspacePathResolver paths;
    private LocalQuestionBankFileStorage banks;
    private LocalStandardDocumentFileStorage documents;
    private FileSystemWorkspaceAssetScanner scanner;
    private SqliteAssetIndexRepository index;
    private final QuestionBankV1Codec codec = new QuestionBankV1Codec();
    private final StandardKnowledgeDocumentV1 markdown = new StandardKnowledgeDocumentV1();
    private String bankPath;
    private String documentPath;
    private QuestionBankFile original;

    @BeforeEach void setup() {
        QuizForgeDataDirectory directory = new QuizForgeDataDirectory(temp.resolve("data"));
        paths = new WorkspacePathResolver(directory);
        workspaces = new WorkspaceService(new SqliteWorkspaceRepository(new SqliteDatabase(directory)),
                paths, Clock.systemUTC());
        workspace = workspaces.createWorkspace("Editor");
        banks = new LocalQuestionBankFileStorage(directory);
        documents = new LocalStandardDocumentFileStorage(directory);
        index = new SqliteAssetIndexRepository(paths);
        scanner = new FileSystemWorkspaceAssetScanner(paths, index, Clock.systemUTC());
        try (var staged = documents.stageCreate(workspace.id(), "Source", document())) {
            staged.publish(); staged.complete(); documentPath = staged.currentPath();
        }
        String revision = markdown.parseIfStandard(document()).orElseThrow().contentId();
        var source = new QuestionBankFile.SourceDocument("doc_source", revision, "Source");
        var ref = new QuestionBankFile.SourceRef("doc_source", revision,
                QuestionSourceAddress.section("section_one"), "Source", "One");
        var question = new QuestionBankFile.Entry("q_one", "SINGLE_CHOICE", "Original?", "Reason",
                List.of(ref), new QuestionBankFile.Data(List.of(
                        new QuestionBankFile.Option("opt_a", "A"),
                        new QuestionBankFile.Option("opt_b", "B")), List.of("opt_a")));
        original = new QuestionBankFile("quizforge-question-bank", "1.0", "qb_editor", "Bank",
                List.of(source), List.of(question));
        try (var staged = banks.stageCreate(workspace.id(), original.title(), codec.write(original))) {
            staged.publish(); staged.complete(); bankPath = staged.currentPath();
        }
        scanner.scan(workspace.id());
    }

    private String document() {
        return "---\nquizforge_format: \"study-document\"\nschema_version: \"1.0\"\n"
                + "quizforge_id: \"doc_source\"\ntitle: \"Source\"\nlanguage: \"en-US\"\n---\n"
                + "# Source\n\n## Chapter\n<!-- qf:id=chapter_one -->\n\n"
                + "### One\n<!-- qf:id=section_one -->\n\nLearning content\n";
    }

    private QuestionBankFileEditService service() {
        return new QuestionBankFileEditService(workspaces, banks, codec, scanner,
                new FormalMarkdownDocumentReader(documents));
    }

    @Test void saveRereadAndRescanKeepIdentityAndUpdateRevision() {
        var edit = new QuestionBankEditorModel(original);
        edit.setStem(0, "Changed?");
        edit.setOptionContent(0, 0, "Changed option");
        edit.setAnalysis(0, "Changed analysis");
        edit.setCorrect(0, "opt_b", true);
        var saved = service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank());
        assertEquals(original.id(), saved.assetId());
        assertNotEquals(codec.contentId(original), saved.contentId());
        assertEquals(codec.parse(codec.write(upgraded(edit.bank()))),
                codec.parse(banks.read(workspace.id(), bankPath)));
        assertEquals(saved, index.findById(workspace.id(), original.id()).orElseThrow());
        assertEquals(saved.contentId(), scanner.scan(workspace.id()).stream()
                .filter(asset -> asset.assetId().equals(original.id())).findFirst().orElseThrow().contentId());
    }

    @Test void invalidEditAndExternalChangeNeverOverwriteExistingFile() throws Exception {
        var edit = new QuestionBankEditorModel(original);
        edit.setStem(0, "");
        String old = banks.read(workspace.id(), bankPath);
        assertThrows(RuntimeException.class,
                () -> service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank()));
        assertEquals(old, banks.read(workspace.id(), bankPath));
        var externalEdit = new QuestionBankEditorModel(original);
        externalEdit.setTitle("External change");
        String external = codec.write(externalEdit.bank());
        Files.writeString(paths.workspaceRoot(workspace.id()).resolve(bankPath), external);
        edit.setStem(0, "My change");
        assertThrows(IllegalStateException.class,
                () -> service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank()));
        assertEquals(external, banks.read(workspace.id(), bankPath));
    }

    @Test void registryFailureRollsBackPublishedQuestionBank() {
        String before = banks.read(workspace.id(), bankPath);
        var edit = new QuestionBankEditorModel(original);
        edit.setTitle("Changed");
        var failing = new QuestionBankFileEditService(workspaces, banks, codec,
                id -> { throw new IllegalStateException("Injected registry failure"); },
                new FormalMarkdownDocumentReader(documents));
        assertThrows(IllegalStateException.class,
                () -> failing.save(workspace.id(), bankPath, codec.contentId(original), edit.bank()));
        assertEquals(before, banks.read(workspace.id(), bankPath));
    }

    @Test void onlyValidMarkdownSectionsCanBecomeNewReferences() {
        assertEquals("doc_source", service().availableSources(workspace.id()).getFirst().assetId());
        assertEquals("section_one", service().source(workspace.id(), "doc_source")
                .chapters().getFirst().sections().getFirst().id());
        var edit = new QuestionBankEditorModel(original);
        var ref = original.questions().getFirst().sourceRefs().getFirst();
        edit.addSourceRef(0, new QuestionBankFile.SourceRef(ref.documentAssetId(),
                ref.documentContentId(), "section_missing", ref.documentTitle(), "Missing"));
        assertThrows(IllegalArgumentException.class,
                () -> service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank()));
        assertEquals(codec.write(original), banks.read(workspace.id(), bankPath));
    }

    @Test void historicalReferencesRemainPortableWhenSourceChanges() throws Exception {
        var edit = new QuestionBankEditorModel(original);
        edit.setTitle("Edited after source change");
        String changedSource = document().replace("Learning content", "Revised");
        Files.writeString(paths.workspaceRoot(workspace.id()).resolve(documentPath), changedSource);
        var saved = service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank());
        assertEquals(original.sourceDocuments(), codec.parse(banks.read(workspace.id(), bankPath)).sourceDocuments());
        assertNotEquals(markdown.parseIfStandard(changedSource).orElseThrow().contentId(),
                original.sourceDocuments().getFirst().contentId());
    }

    @Test void emptyDraftCanBecomeFormalBankWithoutLosingExternalEditProtection() throws Exception {
        String path = "draft.qbank";
        String draft = "{\"format\":\"quizforge-question-bank\",\"schemaVersion\":\"1.0\","
                + "\"id\":\"qb_draft\",\"title\":\"Draft\",\"sourceDocuments\":[],\"questions\":[]}";
        Files.writeString(paths.workspaceRoot(workspace.id()).resolve(path), draft);
        QuestionBankFile empty = codec.parseEmptyDraft(draft);
        var edit = new QuestionBankEditorModel(empty);
        edit.addQuestion("SINGLE_CHOICE");
        var source = service().source(workspace.id(), "doc_source");
        edit.addSourceRef(0, new QuestionBankFile.SourceRef(source.assetId(), source.contentId(),
                "section_one", source.title(), "One"));
        Files.writeString(paths.workspaceRoot(workspace.id()).resolve(path), draft + "\n");
        assertThrows(IllegalStateException.class,
                () -> service().save(workspace.id(), path, null, draft, edit.bank()));
        assertEquals(draft + "\n", banks.read(workspace.id(), path));
        var saved = service().save(workspace.id(), path, null, draft + "\n", edit.bank());
        assertEquals("qb_draft", saved.assetId());
        assertEquals(codec.contentId(upgraded(edit.bank())), saved.contentId());
        assertEquals(codec.parse(codec.write(upgraded(edit.bank()))),
                codec.parse(banks.read(workspace.id(), path)));
    }

    private QuestionBankFile upgraded(QuestionBankFile bank) {
        var questions = bank.questions().stream().map(question -> new QuestionBankFile.Entry(
                question.id(), question.type(), question.stem(), question.analysis(),
                question.sourceRefs().stream().map(ref -> QuestionBankFile.SourceRef.anchor(
                        ref.documentAssetId(), ref.documentContentId(), ref.address().value(), 1,
                        ref.documentTitle(), ref.sectionTitle())).toList(), question.data())).toList();
        return new QuestionBankFile(bank.format(), "1.2", bank.id(), bank.title(),
                bank.sourceDocuments(), questions);
    }
}
