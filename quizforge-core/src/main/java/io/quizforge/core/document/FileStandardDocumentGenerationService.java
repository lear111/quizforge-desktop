package io.quizforge.core.document;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiProviderErrors;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.port.AiProviderResolver;
import io.quizforge.core.port.FileDocumentStorage;
import io.quizforge.core.port.KnowledgeDocumentAssembler;
import io.quizforge.core.port.MaterialFileStorage;
import io.quizforge.core.port.MaterialRepository;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.extension.ai.AiProviderException;
import io.quizforge.extension.document.DocumentProcessRequest;
import io.quizforge.extension.document.DocumentProcessor;
import io.quizforge.extension.document.DocumentValidationResult;
import io.quizforge.extension.document.DocumentValidator;
import io.quizforge.extension.document.SourceMaterial;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Creates file-backed documents from legacy Material input through a format assembler. */
public final class FileStandardDocumentGenerationService {
    private final WorkspaceService workspaces;
    private final MaterialRepository materials;
    private final MaterialFileStorage materialFiles;
    private final AiProviderResolver providers;
    private final DocumentProcessor processor;
    private final DocumentValidator draftValidator;
    private final KnowledgeDocumentAssembler assembler;
    private final FileDocumentStorage files;
    private final WorkspaceAssetScanner scanner;
    private final int maxInputChars;

    public FileStandardDocumentGenerationService(WorkspaceService workspaces, MaterialRepository materials,
            MaterialFileStorage materialFiles, AiProviderResolver providers, DocumentProcessor processor,
            DocumentValidator draftValidator, KnowledgeDocumentAssembler assembler,
            FileDocumentStorage files, WorkspaceAssetScanner scanner, int maxInputChars) {
        if (maxInputChars <= 0 || !processor.formatId().equals(draftValidator.formatId())
                || !processor.formatVersion().equals(draftValidator.formatVersion())) {
            throw new IllegalArgumentException("Invalid document processing configuration");
        }
        this.workspaces = workspaces;
        this.materials = materials;
        this.materialFiles = materialFiles;
        this.providers = providers;
        this.processor = processor;
        this.draftValidator = draftValidator;
        this.assembler = assembler;
        this.files = files;
        this.scanner = scanner;
        this.maxInputChars = maxInputChars;
    }

    public List<Asset> list(WorkspaceId workspaceId) {
        workspaces.getWorkspace(workspaceId);
        return scanner.scan(workspaceId).stream()
                .filter(asset -> asset.assetType() == AssetType.STANDARD_DOCUMENT).toList();
    }

    public Optional<FileDocumentView> findById(WorkspaceId workspaceId, String assetId) {
        return list(workspaceId).stream().filter(asset -> asset.assetId().equals(assetId))
                .findFirst().map(asset -> new FileDocumentView(asset,
                        files.read(workspaceId, asset.currentPath())));
    }

    public FileDocumentView create(WorkspaceId workspaceId, List<MaterialId> materialIds,
            Consumer<String> progress) {
        return generate(workspaceId, materialIds, null, progress);
    }

    public FileDocumentView regenerate(WorkspaceId workspaceId, String assetId,
            List<MaterialId> materialIds, Consumer<String> progress) {
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("Existing asset ID is required");
        }
        return generate(workspaceId, materialIds, assetId, progress);
    }

    private FileDocumentView generate(WorkspaceId workspaceId, List<MaterialId> materialIds,
            String existingAssetId, Consumer<String> progress) {
        workspaces.getWorkspace(workspaceId);
        if (materialIds == null || materialIds.isEmpty()) {
            throw new QuizForgeException(ErrorCode.NO_MATERIAL_SELECTED, "Select at least one material.");
        }
        if (existingAssetId != null) requireCurrentAsset(workspaceId, existingAssetId);
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
                        "Selected materials are too large for document processing.");
            }
            sources.add(new SourceMaterial(material.originalFileName(), content));
        }
        progress.accept("Calling AI provider");
        String draft;
        try {
            draft = processor.process(new DocumentProcessRequest(sources, providers.resolve()))
                    .candidateContent();
        } catch (AiProviderException error) {
            throw AiProviderErrors.map(error);
        }
        progress.accept("Validating document");
        DocumentValidationResult validation = draftValidator.validate(draft);
        if (!validation.valid()) {
            throw new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_VALIDATION_FAILED,
                    "Generated document failed validation: " + String.join(", ", validation.errors()));
        }
        AssembledKnowledgeDocument assembled;
        try {
            assembled = assembler.assemble(draft, validation.title(),
                    assembler.draftLanguage(draft), existingAssetId);
        } catch (IllegalArgumentException error) {
            throw new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_VALIDATION_FAILED,
                    "Generated document cannot be assembled as v1.", error);
        }
        progress.accept("Saving document file");
        Asset previous = existingAssetId == null ? null : requireCurrentAsset(workspaceId, existingAssetId);
        try (FileDocumentStorage.StagedFile staged = previous == null
                ? files.stageCreate(workspaceId, assembled.title(), assembled.content())
                : files.stageReplace(workspaceId, previous.currentPath(), assembled.content())) {
            staged.publish();
            try {
                progress.accept("Refreshing asset registry");
                Asset registered = scanner.scan(workspaceId).stream()
                        .filter(asset -> asset.assetId().equals(assembled.assetId()))
                        .findFirst().orElseThrow(() -> new QuizForgeException(
                                ErrorCode.STANDARD_DOCUMENT_STORAGE_FAILED,
                                "Generated document was not found in the asset registry."));
                if (!registered.currentPath().equals(staged.currentPath())
                        || !registered.contentId().equals(assembled.contentId())) {
                    throw new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_STORAGE_FAILED,
                            "Registered document does not match the generated file.");
                }
                String saved = files.read(workspaceId, registered.currentPath());
                staged.complete();
                return new FileDocumentView(registered, saved);
            } catch (RuntimeException failure) {
                if (previous == null) {
                    staged.complete();
                    throw new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_STORAGE_FAILED,
                            "Document file was saved at " + staged.currentPath()
                            + ", but registry refresh failed. Rescan the workspace to recover it.",
                            failure);
                }
                try {
                    staged.rollback();
                    scanner.scan(workspaceId);
                } catch (RuntimeException recoveryFailure) {
                    failure.addSuppressed(recoveryFailure);
                }
                throw failure;
            }
        }
    }

    private Asset requireCurrentAsset(WorkspaceId workspaceId, String assetId) {
        return list(workspaceId).stream().filter(asset -> asset.assetId().equals(assetId))
                .findFirst().orElseThrow(() -> new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_NOT_FOUND,
                        "The selected document file was not found."));
    }
}
