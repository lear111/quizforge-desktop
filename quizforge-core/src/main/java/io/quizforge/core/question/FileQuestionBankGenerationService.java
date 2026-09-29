package io.quizforge.core.question;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiProviderErrors;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.FormalDocumentReader;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.port.QuestionBankFileStorage;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.extension.ai.AiProviderException;
import io.quizforge.extension.question.QuestionOutputParseException;
import io.quizforge.extension.question.SourceAwareQuestionGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** New file-backed pipeline; legacy SQLite question generation remains separate. */
public final class FileQuestionBankGenerationService {
    public record Outcome(Asset asset, QuestionBank bank, int requested, int accepted, int rejected) { }

    private final WorkspaceService workspaces;
    private final WorkspaceAssetScanner scanner;
    private final FormalDocumentReader documents;
    private final SourceAwareQuestionGenerator generator;
    private final QuestionBankV2Assembler assembler;
    private final QuestionBankFileCodec codec;
    private final QuestionBankFileStorage files;

    public FileQuestionBankGenerationService(WorkspaceService workspaces, WorkspaceAssetScanner scanner,
            FormalDocumentReader documents, SourceAwareQuestionGenerator generator,
            QuestionBankV2Assembler assembler, QuestionBankFileCodec codec, QuestionBankFileStorage files) {
        this.workspaces = workspaces;
        this.scanner = scanner;
        this.documents = documents;
        this.generator = generator;
        this.assembler = assembler;
        this.codec = codec;
        this.files = files;
    }

    public List<Asset> listDocuments(WorkspaceId workspaceId) {
        workspaces.getWorkspace(workspaceId);
        return scanner.scan(workspaceId).stream().filter(a -> a.assetType() == AssetType.STANDARD_DOCUMENT
                && a.currentPath().toLowerCase(java.util.Locale.ROOT).endsWith(".md")
                && a.contentId() != null && a.contentId().startsWith("qfd:v1:")).toList();
    }

    public List<Asset> listBanks(WorkspaceId workspaceId) {
        workspaces.getWorkspace(workspaceId);
        return scanner.scan(workspaceId).stream().filter(a -> a.assetType() == AssetType.QUESTION_BANK).toList();
    }

    public QuestionBank read(WorkspaceId workspaceId, String bankAssetId) {
        Asset asset = findAsset(workspaceId, bankAssetId, AssetType.QUESTION_BANK);
        QuestionBank bank = files.read(workspaceId, asset.currentPath());
        codec.validate(bank);
        return bank;
    }

    public SourceDocumentSnapshot inspectDocument(WorkspaceId workspaceId, String documentAssetId) {
        Asset asset = findAsset(workspaceId, documentAssetId, AssetType.STANDARD_DOCUMENT);
        SourceDocumentSnapshot snapshot = documents.read(workspaceId, asset.currentPath());
        if (!snapshot.assetId().equals(asset.assetId()) || !snapshot.contentId().equals(asset.contentId())) {
            throw new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_INVALID, "Document changed during lookup.");
        }
        return snapshot;
    }

    public Outcome create(WorkspaceId workspaceId, String title, List<StandardDocumentSelection> selections,
            Set<QuestionType> types, int count, Consumer<String> progress) {
        return generate(workspaceId, null, title, selections, types, count, progress);
    }

    public Outcome regenerate(WorkspaceId workspaceId, String bankAssetId, String title,
            List<StandardDocumentSelection> selections, Set<QuestionType> types, int count,
            Consumer<String> progress) {
        if (bankAssetId == null || bankAssetId.isBlank()) throw new IllegalArgumentException("Bank ID required");
        return generate(workspaceId, bankAssetId, title, selections, types, count, progress);
    }

    private Outcome generate(WorkspaceId workspaceId, String existingId, String title,
            List<StandardDocumentSelection> selections, Set<QuestionType> types, int count,
            Consumer<String> progress) {
        workspaces.getWorkspace(workspaceId);
        if (title == null || title.isBlank()) throw fail(ErrorCode.QUESTION_BANK_NAME_INVALID, "Title is required.");
        if (count < 1 || count > 50) throw fail(ErrorCode.INVALID_QUESTION_COUNT, "Count must be 1–50.");
        if (types == null || types.isEmpty() || types.contains(null))
            throw fail(ErrorCode.NO_QUESTION_TYPE_SELECTED, "Select question types.");
        if (selections == null || selections.isEmpty())
            throw fail(ErrorCode.INVALID_GENERATION_SCOPE, "Select at least one document.");
        Asset previous = existingId == null ? null : findAsset(workspaceId, existingId, AssetType.QUESTION_BANK);
        progress.accept("Reading source documents");
        List<SourceDocumentSnapshot> snapshots = new ArrayList<>();
        Map<String, Set<String>> selected = new HashMap<>();
        Map<String, Map<String, Set<String>>> selectedSubsections = new HashMap<>();
        Set<String> seenDocuments = new HashSet<>();
        for (StandardDocumentSelection selection : selections) {
            SourceDocumentSnapshot snapshot = inspectDocument(workspaceId, selection.documentAssetId());
            if (seenDocuments.add(snapshot.assetId())) snapshots.add(snapshot);
            Set<String> sections = resolveSections(snapshot, selection);
            selected.computeIfAbsent(snapshot.assetId(), ignored -> new HashSet<>()).addAll(sections);
            Map<String, Set<String>> bySection = selectedSubsections.computeIfAbsent(snapshot.assetId(),
                    ignored -> new HashMap<>());
            for (String sectionId : sections) {
                Set<String> included = bySection.computeIfAbsent(sectionId, ignored -> new HashSet<>());
                if (selection.scope() == GenerationScopeType.SUBSECTION) {
                    if (!included.contains("*")) included.add(selection.subsectionId());
                } else {
                    included.clear();
                    included.add("*");
                }
            }
        }
        String context = context(snapshots, selected, selectedSubsections);
        progress.accept("Calling AI provider");
        List<SourceAwareQuestionGenerator.Candidate> candidates;
        try {
            candidates = generator.generate(new SourceAwareQuestionGenerator.Request(context,
                    types.stream().map(Enum::name).sorted().toList(), count));
        } catch (AiProviderException error) {
            throw AiProviderErrors.map(error);
        } catch (QuestionOutputParseException error) {
            throw new QuizForgeException(ErrorCode.QUESTION_OUTPUT_PARSE_FAILED, error.getMessage(), error);
        } catch (QuizForgeException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new QuizForgeException(ErrorCode.QUESTION_GENERATION_FAILED,
                    "Question generation failed.", error);
        }
        progress.accept("Validating questions");
        QuestionBankV2Assembler.Result assembled = assembler.assemble(title.trim(), existingId, snapshots,
                selected, candidates, types, count);
        QuestionBank bank = assembled.bank();
        if (bank.questions().isEmpty()) throw fail(ErrorCode.NO_VALID_QUESTION_GENERATED, "No valid questions were generated.");
        codec.validate(bank);
        // Re-read every source immediately before publication. No candidate file exists yet.
        for (SourceDocumentSnapshot original : snapshots) {
            SourceDocumentSnapshot current;
            try { current = inspectDocument(workspaceId, original.assetId()); }
            catch (RuntimeException missing) {
                throw new QuizForgeException(ErrorCode.SOURCE_DOCUMENT_CHANGED_DURING_GENERATION,
                        "A source document disappeared during generation.", missing);
            }
            if (!original.contentId().equals(current.contentId())) {
                throw fail(ErrorCode.SOURCE_DOCUMENT_CHANGED_DURING_GENERATION,
                        "A source document changed during generation. Please retry.");
            }
        }
        progress.accept("Saving QuestionBank file");
        try (QuestionBankFileStorage.StagedFile staged = previous == null
                ? files.stageCreate(workspaceId, title, bank)
                : files.stageReplace(workspaceId, previous.currentPath(), bank)) {
            staged.publish();
            try {
                Asset registered = findAsset(workspaceId, bank.assetId(), AssetType.QUESTION_BANK);
                if (!registered.currentPath().equals(staged.currentPath())) {
                    throw fail(ErrorCode.QUESTION_BANK_STORAGE_FAILED, "Registered path does not match saved file.");
                }
                QuestionBank saved = files.read(workspaceId, registered.currentPath());
                staged.complete();
                return new Outcome(registered, saved, count, saved.questions().size(), assembled.rejected());
            } catch (RuntimeException failure) {
                if (previous == null) {
                    staged.complete();
                    throw new QuizForgeException(ErrorCode.QUESTION_BANK_STORAGE_FAILED,
                            "QuestionBank was saved at " + staged.currentPath()
                                    + ", but registry refresh failed. Rescan to recover it.", failure);
                }
                try { staged.rollback(); scanner.scan(workspaceId); }
                catch (RuntimeException recoveryFailure) { failure.addSuppressed(recoveryFailure); }
                throw failure;
            }
        }
    }

    private Set<String> resolveSections(SourceDocumentSnapshot document, StandardDocumentSelection selection) {
        Set<String> ids = new HashSet<>();
        for (SourceDocumentSnapshot.Chapter chapter : document.chapters()) {
            if (selection.scope() != GenerationScopeType.DOCUMENT && selection.chapterId() != null
                    && !chapter.id().equals(selection.chapterId())) continue;
            for (SourceDocumentSnapshot.Section section : chapter.sections()) {
                if ((selection.scope() == GenerationScopeType.SECTION
                        || selection.scope() == GenerationScopeType.SUBSECTION)
                        && !section.id().equals(selection.sectionId())) continue;
                if (selection.scope() == GenerationScopeType.SUBSECTION
                        && section.subsections().stream().noneMatch(subsection ->
                        subsection.id().equals(selection.subsectionId()) && !subsection.content().isBlank())) continue;
                ids.add(section.id());
            }
        }
        if (ids.isEmpty()) throw fail(ErrorCode.INVALID_GENERATION_SCOPE,
                "Selected chapter, section or subsection was not found or has no content.");
        return ids;
    }

    private String context(List<SourceDocumentSnapshot> documents, Map<String, Set<String>> selected,
            Map<String, Map<String, Set<String>>> selectedSubsections) {
        StringBuilder out = new StringBuilder();
        for (SourceDocumentSnapshot document : documents) {
            out.append("DOCUMENT\nassetId: ").append(document.assetId()).append("\ntitle: ")
                    .append(document.title()).append("\ncontentId: ").append(document.contentId()).append('\n');
            for (SourceDocumentSnapshot.Chapter chapter : document.chapters()) {
                if (!chapter.content().isBlank() && chapter.sections().stream().anyMatch(section ->
                        selected.get(document.assetId()).contains(section.id()))) {
                    out.append("CHAPTER\nchapterId: ").append(chapter.id()).append("\ntitle: ")
                            .append(chapter.title()).append('\n').append(chapter.content()).append('\n');
                }
                for (SourceDocumentSnapshot.Section section : chapter.sections()) {
                    if (!selected.get(document.assetId()).contains(section.id())) continue;
                    out.append("SECTION\nsectionId: ").append(section.id()).append("\ntitle: ")
                            .append(section.title()).append("\n");
                    Set<String> included = selectedSubsections.get(document.assetId()).get(section.id());
                    if (included.contains("*")) out.append(section.content()).append('\n');
                    else for (SourceDocumentSnapshot.Subsection subsection : section.subsections()) {
                        if (included.contains(subsection.id())) out.append("SUBSECTION\nsubsectionId: ")
                                .append(subsection.id()).append("\ntitle: ").append(subsection.title())
                                .append('\n').append(subsection.content()).append('\n');
                    }
                }
            }
        }
        return out.toString();
    }

    private Asset findAsset(WorkspaceId workspaceId, String assetId, AssetType type) {
        return scanner.scan(workspaceId).stream().filter(a -> a.assetType() == type && a.assetId().equals(assetId))
                .findFirst().orElseThrow(() -> fail(type == AssetType.QUESTION_BANK
                        ? ErrorCode.QUESTION_BANK_NOT_FOUND : ErrorCode.STANDARD_DOCUMENT_NOT_FOUND,
                        "Asset was not found in this workspace."));
    }

    private QuizForgeException fail(ErrorCode code, String message) { return new QuizForgeException(code, message); }
}
