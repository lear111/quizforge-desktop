package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.document.DocumentNormalizationService;
import io.quizforge.core.document.StandardDocument;
import io.quizforge.core.document.StandardDocumentId;
import io.quizforge.core.document.StandardDocumentStatus;
import io.quizforge.core.port.StandardDocumentFileStorage;
import io.quizforge.core.question.GenerationScopeType;
import io.quizforge.core.question.StoredQuestion;
import io.quizforge.core.question.StoredQuestionBank;
import io.quizforge.core.question.QuestionGenerationCommand;
import io.quizforge.core.question.QuestionGenerationService;
import io.quizforge.core.question.QuestionType;
import io.quizforge.core.question.QuestionValidator;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.extension.ai.AiProvider;
import io.quizforge.extension.ai.AiRequest;
import io.quizforge.extension.ai.AiResponse;
import io.quizforge.extension.document.DocumentProcessRequest;
import io.quizforge.extension.document.DocumentProcessResult;
import io.quizforge.extension.document.DocumentProcessor;
import io.quizforge.extension.document.DocumentValidationResult;
import io.quizforge.extension.document.DocumentValidator;
import io.quizforge.extension.document.StandardDocumentStructure;
import io.quizforge.extension.question.GeneratedOption;
import io.quizforge.extension.question.GeneratedQuestion;
import io.quizforge.extension.question.QuestionGenerationRequest;
import io.quizforge.extension.question.QuestionGenerationResult;
import io.quizforge.extension.question.QuestionGenerator;
import io.quizforge.infrastructure.filesystem.LocalMaterialFileStorage;
import io.quizforge.infrastructure.filesystem.LocalStandardDocumentFileStorage;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteMaterialRepository;
import io.quizforge.infrastructure.persistence.SqliteQuestionBankRepository;
import io.quizforge.infrastructure.persistence.SqliteStandardDocumentRepository;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class QuestionGenerationIntegrationTest {
    private static final Instant TIME = Instant.parse("2026-09-25T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(TIME, ZoneOffset.UTC);
    private static final String DOCUMENT = "entire-document";
    @TempDir Path temp;
    private QuizForgeDataDirectory directory;
    private SqliteDatabase database;
    private SqliteStandardDocumentRepository documentRepository;
    private SqliteQuestionBankRepository bankRepository;
    private Workspace workspace;
    private StandardDocument document;
    private QuestionGenerationService service;
    private final AtomicReference<QuestionGenerationRequest> request = new AtomicReference<>();
    private final AtomicReference<QuestionGenerationResult> response = new AtomicReference<>();
    private boolean generatorFails;

    @BeforeEach void setup() {
        directory = new QuizForgeDataDirectory(temp.resolve("data"));
        database = new SqliteDatabase(directory);
        var paths = new WorkspacePathResolver(directory);
        var workspaces = new WorkspaceService(new SqliteWorkspaceRepository(database), paths, CLOCK);
        workspace = workspaces.createWorkspace("Java");
        documentRepository = new SqliteStandardDocumentRepository(database);
        bankRepository = new SqliteQuestionBankRepository(database);
        StandardDocumentFileStorage files = new LocalStandardDocumentFileStorage(directory);
        document = new StandardDocument(StandardDocumentId.newId(), workspace.id(), "Java",
                "quizforge-standard-markdown", "1.0", "study.md", StandardDocumentStatus.VALID,
                TIME, TIME, List.of());
        documentRepository.save(document);
        try (var staged = files.stage(workspace.id(), DOCUMENT)) {
            staged.publish();
            staged.complete();
        }
        DocumentProcessor processor = new DocumentProcessor() {
            public String formatId() { return "quizforge-standard-markdown"; }
            public String formatVersion() { return "1.0"; }
            public DocumentProcessResult process(DocumentProcessRequest request) { throw new AssertionError(); }
        };
        DocumentValidator validator = new DocumentValidator() {
            public String formatId() { return "quizforge-standard-markdown"; }
            public String formatVersion() { return "1.0"; }
            public DocumentValidationResult validate(String content) { throw new AssertionError(); }
        };
        AiProvider provider = new AiProvider() {
            public String id() { return "fake"; }
            public AiResponse generate(AiRequest request) { throw new AssertionError(); }
        };
        DocumentNormalizationService documents = new DocumentNormalizationService(workspaces,
                new SqliteMaterialRepository(database), new LocalMaterialFileStorage(paths),
                documentRepository, files, () -> provider, processor, validator, CLOCK, 1000);
        StandardDocumentStructure structure = new StandardDocumentStructure("Java", DOCUMENT, List.of(
                new StandardDocumentStructure.Chapter("c1", "1. List", "chapter-one", List.of(
                        new StandardDocumentStructure.Section("s1", "1.1 ArrayList", "section-one"),
                        new StandardDocumentStructure.Section("s2", "1.2 LinkedList", "section-two"))),
                new StandardDocumentStructure.Chapter("c2", "2. Map", "chapter-two", List.of(
                        new StandardDocumentStructure.Section("s3", "2.1 HashMap", "section-three")))));
        QuestionGenerator generator = value -> {
            request.set(value);
            if (generatorFails) throw new IllegalStateException("fake failure");
            return response.get();
        };
        service = new QuestionGenerationService(workspaces, documents, ignored -> structure, generator,
                new QuestionValidator(), bankRepository, CLOCK);
        response.set(new QuestionGenerationResult(List.of(single("First"))));
    }

    private GeneratedQuestion single(String stem) {
        return new GeneratedQuestion("SINGLE_CHOICE", stem, List.of(
                new GeneratedOption("A", "yes"), new GeneratedOption("B", "no")),
                List.of("A"), "Because source says so", "1. List", "1.1 ArrayList");
    }

    private GeneratedQuestion multiple(String stem) {
        return new GeneratedQuestion("MULTIPLE_CHOICE", stem, List.of(
                new GeneratedOption("A", "yes"), new GeneratedOption("B", "also yes"),
                new GeneratedOption("C", "no")), List.of("A", "B"), "Because source says so",
                "1. List", "1.1 ArrayList");
    }

    private QuestionGenerationCommand command(GenerationScopeType scope, Set<QuestionType> types, int count) {
        return new QuestionGenerationCommand(" Java 题库 ", scope, "c1", "s1", types, count);
    }

    @Test void documentChapterSectionAndTypeSelections() {
        var both = Set.of(QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE);
        response.set(new QuestionGenerationResult(List.of(single("One"), multiple("Two"))));
        var whole = service.generate(workspace.id(), command(GenerationScopeType.DOCUMENT, both, 10), ignored -> {});
        assertEquals(DOCUMENT, request.get().documentContent());
        assertEquals(2, whole.accepted());
        assertEquals(2, bankRepository.findByWorkspace(workspace.id()).orElseThrow().questions().size());
        assertTrue(bankRepository.findByWorkspace(workspace.id()).orElseThrow().questions().get(1).options().get(1).correct());
        service.generate(workspace.id(), command(GenerationScopeType.CHAPTER, both, 10), ignored -> {});
        assertEquals("chapter-one", request.get().documentContent());
        service.generate(workspace.id(), command(GenerationScopeType.SECTION, both, 10), ignored -> {});
        assertEquals("section-one", request.get().documentContent());
        assertEquals(List.of("MULTIPLE_CHOICE", "SINGLE_CHOICE"), request.get().questionTypes());
        response.set(new QuestionGenerationResult(List.of(single("Only single"), multiple("Rejected"))));
        assertEquals(1, service.generate(workspace.id(), command(GenerationScopeType.DOCUMENT,
                Set.of(QuestionType.SINGLE_CHOICE), 10), ignored -> {}).accepted());
        response.set(new QuestionGenerationResult(List.of(multiple("Only multiple"), single("Rejected"))));
        assertEquals(1, service.generate(workspace.id(), command(GenerationScopeType.DOCUMENT,
                Set.of(QuestionType.MULTIPLE_CHOICE), 10), ignored -> {}).accepted());
    }

    @Test void partialZeroValidRegenerateAndRestart() {
        List<GeneratedQuestion> candidates = new ArrayList<>();
        for (int i = 0; i < 8; i++) candidates.add(single("Valid " + i));
        candidates.add(new GeneratedQuestion("UNKNOWN", "Invalid", List.of(), List.of(), "", "", ""));
        candidates.add(new GeneratedQuestion("SINGLE_CHOICE", "Invalid", List.of(), List.of(), "", "", ""));
        response.set(new QuestionGenerationResult(candidates));
        var outcome = service.generate(workspace.id(), command(GenerationScopeType.DOCUMENT,
                Set.of(QuestionType.SINGLE_CHOICE), 10), ignored -> {});
        assertEquals(10, outcome.generated());
        assertEquals(8, outcome.accepted());
        assertEquals(2, outcome.rejected());
        StoredQuestionBank old = bankRepository.findByWorkspace(workspace.id()).orElseThrow();
        var restarted = new SqliteQuestionBankRepository(new SqliteDatabase(directory));
        assertEquals(old, restarted.findByWorkspace(workspace.id()).orElseThrow());
        response.set(new QuestionGenerationResult(List.of(candidates.get(8))));
        QuizForgeException zero = assertThrows(QuizForgeException.class,
                () -> service.generate(workspace.id(), command(GenerationScopeType.DOCUMENT,
                        Set.of(QuestionType.SINGLE_CHOICE), 10), ignored -> {}));
        assertEquals(ErrorCode.NO_VALID_QUESTION_GENERATED, zero.code());
        assertEquals(old, restarted.findByWorkspace(workspace.id()).orElseThrow());
        generatorFails = true;
        assertThrows(QuizForgeException.class, () -> service.generate(workspace.id(),
                command(GenerationScopeType.DOCUMENT, Set.of(QuestionType.SINGLE_CHOICE), 10), ignored -> {}));
        assertEquals(old, restarted.findByWorkspace(workspace.id()).orElseThrow());
        generatorFails = false;
        response.set(new QuestionGenerationResult(List.of(single("New"))));
        StoredQuestionBank replacement = service.generate(workspace.id(), command(GenerationScopeType.DOCUMENT,
                Set.of(QuestionType.SINGLE_CHOICE), 10), ignored -> {}).bank();
        assertNotEquals(old.id(), replacement.id());
        assertEquals("New", restarted.findByWorkspace(workspace.id()).orElseThrow().questions().getFirst().stem());
        assertEquals(1, rowCount("question"));
        assertEquals(2, rowCount("question_option"));
    }

    @Test void invalidScopeAndInputsFailBeforeGenerator() {
        assertEquals(ErrorCode.CHAPTER_NOT_FOUND, code(command(GenerationScopeType.CHAPTER,
                Set.of(QuestionType.SINGLE_CHOICE), 10), "missing", "s1"));
        assertEquals(ErrorCode.SECTION_NOT_FOUND, code(command(GenerationScopeType.SECTION,
                Set.of(QuestionType.SINGLE_CHOICE), 10), "c1", "missing"));
        assertEquals(ErrorCode.INVALID_QUESTION_COUNT, code(command(GenerationScopeType.DOCUMENT,
                Set.of(QuestionType.SINGLE_CHOICE), 51), "c1", "s1"));
        assertEquals(ErrorCode.NO_QUESTION_TYPE_SELECTED, code(command(GenerationScopeType.DOCUMENT,
                Set.of(), 10), "c1", "s1"));
        assertEquals(ErrorCode.QUESTION_BANK_NAME_INVALID, code(new QuestionGenerationCommand(" ",
                GenerationScopeType.DOCUMENT, null, null, Set.of(QuestionType.SINGLE_CHOICE), 10), null, null));
        assertTrue(bankRepository.findByWorkspace(workspace.id()).isEmpty());
    }

    private ErrorCode code(QuestionGenerationCommand command, String chapter, String section) {
        var changed = new QuestionGenerationCommand(command.name(), command.scope(), chapter, section,
                command.types(), command.count());
        return assertThrows(QuizForgeException.class,
                () -> service.generate(workspace.id(), changed, ignored -> {})).code();
    }

    @Test void outdatedAndTransactionRollback() {
        StoredQuestionBank old = service.generate(workspace.id(), command(GenerationScopeType.DOCUMENT,
                Set.of(QuestionType.SINGLE_CHOICE), 10), ignored -> {}).bank();
        assertFalse(service.findByWorkspace(workspace.id()).orElseThrow().outdated());
        StandardDocument updated = new StandardDocument(document.id(), workspace.id(), "Java",
                document.formatId(), document.formatVersion(), document.fileName(), document.status(),
                TIME, TIME.plusSeconds(10), List.of());
        documentRepository.save(updated);
        assertTrue(service.findByWorkspace(workspace.id()).orElseThrow().outdated());
        StoredQuestion duplicated = old.questions().getFirst();
        StoredQuestionBank invalid = new StoredQuestionBank(old.id(), old.workspaceId(), old.sourceDocumentId(),
                old.name(), old.generationScopeType(), old.sourceChapter(), old.sourceSection(),
                old.requestedQuestionCount(), old.createdAt(), old.updatedAt(), old.generatedAt(),
                List.of(duplicated, duplicated));
        assertThrows(QuizForgeException.class, () -> bankRepository.replace(invalid));
        assertEquals(old, bankRepository.findByWorkspace(workspace.id()).orElseThrow());
        assertEquals(1, rowCount("question"));
        assertEquals(2, rowCount("question_option"));
    }

    private int rowCount(String table) {
        try (Connection connection = database.openConnection(); Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT count(*) FROM " + table)) {
            row.next();
            return row.getInt(1);
        } catch (Exception error) { throw new AssertionError(error); }
    }
}
