package io.quizforge.core.document;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiProviderErrors;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.port.AiProviderResolver;
import io.quizforge.core.port.MaterialFileStorage;
import io.quizforge.core.port.MaterialRepository;
import io.quizforge.core.port.StandardDocumentFileStorage;
import io.quizforge.core.port.StandardDocumentRepository;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.extension.ai.AiProviderException;
import io.quizforge.extension.document.DocumentProcessRequest;
import io.quizforge.extension.document.DocumentProcessor;
import io.quizforge.extension.document.DocumentValidationResult;
import io.quizforge.extension.document.DocumentValidator;
import io.quizforge.extension.document.SourceMaterial;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class DocumentNormalizationService {
    private final WorkspaceService workspaces;
    private final MaterialRepository materials;
    private final MaterialFileStorage materialFiles;
    private final StandardDocumentRepository documents;
    private final StandardDocumentFileStorage documentFiles;
    private final AiProviderResolver providers;
    private final DocumentProcessor processor;
    private final DocumentValidator validator;
    private final Clock clock;
    private final int maxInputChars;

    public DocumentNormalizationService(WorkspaceService workspaces, MaterialRepository materials,
            MaterialFileStorage materialFiles, StandardDocumentRepository documents,
            StandardDocumentFileStorage documentFiles, AiProviderResolver providers,
            DocumentProcessor processor, DocumentValidator validator, Clock clock, int maxInputChars) {
        if (maxInputChars <= 0 || !processor.formatId().equals(validator.formatId())
                || !processor.formatVersion().equals(validator.formatVersion())) {
            throw new IllegalArgumentException("Invalid document processing configuration");
        }
        this.workspaces = workspaces;
        this.materials = materials;
        this.materialFiles = materialFiles;
        this.documents = documents;
        this.documentFiles = documentFiles;
        this.providers = providers;
        this.processor = processor;
        this.validator = validator;
        this.clock = clock;
        this.maxInputChars = maxInputChars;
    }

    public Optional<StandardDocumentView> findByWorkspace(WorkspaceId workspaceId) {
        workspaces.getWorkspace(workspaceId);
        return documents.findByWorkspace(workspaceId)
                .map(document -> new StandardDocumentView(document, documentFiles.read(workspaceId)));
    }

    public StandardDocumentView generate(WorkspaceId workspaceId, List<MaterialId> materialIds,
            Consumer<String> progress) {
        workspaces.getWorkspace(workspaceId);
        if (materialIds == null || materialIds.isEmpty()) {
            throw new QuizForgeException(ErrorCode.NO_MATERIAL_SELECTED,
                    "Select at least one material.");
        }
        progress.accept("Preparing materials");
        Set<MaterialId> uniqueIds = new LinkedHashSet<>(materialIds);
        List<SourceMaterial> sources = new ArrayList<>();
        long length = 0;
        for (MaterialId id : uniqueIds) {
            Material material = materials.findById(id)
                    .filter(entry -> entry.workspaceId().equals(workspaceId))
                    .orElseThrow(() -> new QuizForgeException(ErrorCode.MATERIAL_NOT_IN_WORKSPACE,
                            "Selected material is not in this workspace."));
            String content = materialFiles.read(material);
            length += (long) material.originalFileName().length() + content.length();
            if (length > maxInputChars) {
                throw new QuizForgeException(ErrorCode.DOCUMENT_INPUT_TOO_LARGE,
                        "Selected materials are too large for the current MVP processing mode.");
            }
            sources.add(new SourceMaterial(material.originalFileName(), content));
        }
        progress.accept("Calling AI provider");
        String candidate;
        try {
            candidate = processor.process(new DocumentProcessRequest(sources, providers.resolve()))
                    .candidateContent();
        } catch (AiProviderException error) {
            throw AiProviderErrors.map(error);
        }
        progress.accept("Validating document");
        DocumentValidationResult validation = validator.validate(candidate);
        if (!validation.valid()) {
            throw new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_VALIDATION_FAILED,
                    "Generated document failed validation: " + String.join(", ", validation.errors()));
        }
        progress.accept("Saving document");
        Optional<StandardDocument> previous = documents.findByWorkspace(workspaceId);
        Instant now = clock.instant();
        StandardDocument document = new StandardDocument(
                previous.map(StandardDocument::id).orElseGet(StandardDocumentId::newId),
                workspaceId, validation.title(), processor.formatId(), processor.formatVersion(),
                "study.md", StandardDocumentStatus.VALID,
                previous.map(StandardDocument::createdAt).orElse(now), now, List.copyOf(uniqueIds));
        try (StandardDocumentFileStorage.StagedDocument staged = documentFiles.stage(workspaceId, candidate)) {
            staged.publish();
            try {
                documents.save(document);
            } catch (RuntimeException failure) {
                try {
                    staged.rollback();
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
            staged.complete();
        }
        return new StandardDocumentView(document, candidate);
    }
}
