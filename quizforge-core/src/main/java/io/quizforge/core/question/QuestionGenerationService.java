package io.quizforge.core.question;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiProviderErrors;
import io.quizforge.core.document.DocumentNormalizationService;
import io.quizforge.core.document.StandardDocumentStatus;
import io.quizforge.core.document.StandardDocumentView;
import io.quizforge.core.port.QuestionBankRepository;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.extension.ai.AiProviderException;
import io.quizforge.extension.document.DocumentStructureParser;
import io.quizforge.extension.document.StandardDocumentStructure;
import io.quizforge.extension.question.GeneratedOption;
import io.quizforge.extension.question.GeneratedQuestion;
import io.quizforge.extension.question.QuestionGenerationRequest;
import io.quizforge.extension.question.QuestionGenerationResult;
import io.quizforge.extension.question.QuestionGenerator;
import io.quizforge.extension.question.QuestionOutputParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class QuestionGenerationService {
    private final WorkspaceService workspaces;
    private final DocumentNormalizationService documents;
    private final DocumentStructureParser structureParser;
    private final QuestionGenerator generator;
    private final QuestionValidator validator;
    private final QuestionBankRepository banks;
    private final Clock clock;

    public QuestionGenerationService(WorkspaceService workspaces, DocumentNormalizationService documents,
            DocumentStructureParser structureParser, QuestionGenerator generator,
            QuestionValidator validator, QuestionBankRepository banks, Clock clock) {
        this.workspaces = workspaces;
        this.documents = documents;
        this.structureParser = structureParser;
        this.generator = generator;
        this.validator = validator;
        this.banks = banks;
        this.clock = clock;
    }

    public Optional<QuestionBankView> findByWorkspace(WorkspaceId workspaceId) {
        workspaces.getWorkspace(workspaceId);
        return banks.findByWorkspace(workspaceId).map(bank -> {
            boolean outdated = documents.findByWorkspace(workspaceId)
                    .map(view -> !view.document().id().equals(bank.sourceDocumentId())
                            || bank.generatedAt().isBefore(view.document().updatedAt()))
                    .orElse(true);
            return new QuestionBankView(bank, outdated);
        });
    }

    public StandardDocumentStructure structure(WorkspaceId workspaceId) {
        return parseStructure(validDocument(workspaceId).content());
    }

    private StandardDocumentStructure parseStructure(String content) {
        try { return structureParser.parse(content); }
        catch (IllegalArgumentException error) {
            throw new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_INVALID,
                    "The standard document structure is invalid.", error);
        }
    }

    public QuestionGenerationOutcome generate(WorkspaceId workspaceId, QuestionGenerationCommand command,
            Consumer<String> progress) {
        progress.accept("Preparing document");
        StandardDocumentView document = validDocument(workspaceId);
        if (command == null || command.scope() == null) throw fail(ErrorCode.INVALID_GENERATION_SCOPE, "Choose a scope.");
        String name = command.name() == null ? "" : command.name().trim();
        if (name.isEmpty()) throw fail(ErrorCode.QUESTION_BANK_NAME_INVALID, "Question bank name is required.");
        if (command.count() < 1 || command.count() > 50) throw fail(ErrorCode.INVALID_QUESTION_COUNT, "Question count must be 1–50.");
        Set<QuestionType> types = command.types();
        if (types == null || types.isEmpty() || types.stream().anyMatch(java.util.Objects::isNull))
            throw fail(ErrorCode.NO_QUESTION_TYPE_SELECTED, "Select at least one question type.");
        StandardDocumentStructure structure = parseStructure(document.content());
        StandardDocumentStructure.Chapter chapter = null;
        StandardDocumentStructure.Section section = null;
        if (command.scope() != GenerationScopeType.DOCUMENT) {
            chapter = structure.chapters().stream().filter(item -> item.id().equals(command.chapterId()))
                    .findFirst().orElseThrow(() -> fail(ErrorCode.CHAPTER_NOT_FOUND, "Chapter was not found."));
        }
        if (command.scope() == GenerationScopeType.SECTION) {
            section = chapter.sections().stream().filter(item -> item.id().equals(command.sectionId()))
                    .findFirst().orElseThrow(() -> fail(ErrorCode.SECTION_NOT_FOUND, "Section was not found."));
        }
        String markdown = section != null ? section.markdown()
                : chapter != null ? chapter.markdown() : structure.markdown();
        String sourceChapter = chapter == null ? "" : chapter.title();
        String sourceSection = section == null ? "" : section.title();
        progress.accept("Calling AI provider");
        QuestionGenerationResult generated;
        try {
            generated = generator.generate(new QuestionGenerationRequest(markdown, command.scope().name(),
                    sourceChapter, sourceSection, types.stream().map(Enum::name).sorted().toList(),
                    command.count()));
        } catch (AiProviderException error) {
            throw AiProviderErrors.map(error);
        } catch (QuestionOutputParseException error) {
            throw new QuizForgeException(ErrorCode.QUESTION_OUTPUT_PARSE_FAILED, error.getMessage(), error);
        } catch (QuizForgeException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new QuizForgeException(ErrorCode.QUESTION_GENERATION_FAILED, "Question generation failed.", error);
        }
        progress.accept("Parsing questions");
        List<GeneratedQuestion> candidates = generated.questions();
        progress.accept("Validating questions");
        Instant now = clock.instant();
        QuestionBankId bankId = QuestionBankId.newId();
        List<Question> accepted = new ArrayList<>();
        for (GeneratedQuestion candidate : candidates) {
            if (accepted.size() >= command.count()) break;
            if (!validator.valid(candidate, types, structure, command.scope(), command.chapterId(), command.sectionId())) continue;
            QuestionId questionId = QuestionId.newId();
            Set<String> correct = candidate.correctAnswers().stream().map(String::trim).collect(java.util.stream.Collectors.toSet());
            List<QuestionOption> options = new ArrayList<>();
            int position = 1;
            for (GeneratedOption option : candidate.options()) {
                options.add(new QuestionOption(UUID.randomUUID(), questionId, option.key().trim(),
                        option.content().trim(), correct.contains(option.key().trim()), position++));
            }
            accepted.add(new Question(questionId, bankId, QuestionType.valueOf(candidate.type()),
                    candidate.stem().trim(), candidate.analysis().trim(), candidate.sourceChapter(),
                    candidate.sourceSection(), accepted.size() + 1, now, options));
        }
        if (accepted.isEmpty()) throw fail(ErrorCode.NO_VALID_QUESTION_GENERATED, "No valid questions were generated.");
        QuestionBank bank = new QuestionBank(bankId, workspaceId, document.document().id(), name,
                command.scope(), sourceChapter, sourceSection, command.count(), now, now, now, accepted);
        progress.accept("Saving question bank");
        banks.replace(bank);
        return new QuestionGenerationOutcome(bank, command.count(), candidates.size(), accepted.size(),
                candidates.size() - accepted.size());
    }

    private StandardDocumentView validDocument(WorkspaceId workspaceId) {
        workspaces.getWorkspace(workspaceId);
        StandardDocumentView view = documents.findByWorkspace(workspaceId)
                .orElseThrow(() -> fail(ErrorCode.STANDARD_DOCUMENT_NOT_FOUND, "Generate a standard document first."));
        if (view.document().status() != StandardDocumentStatus.VALID)
            throw fail(ErrorCode.STANDARD_DOCUMENT_INVALID, "Standard document is not valid.");
        return view;
    }

    private QuizForgeException fail(ErrorCode code, String message) { return new QuizForgeException(code, message); }
}
