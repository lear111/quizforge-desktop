package io.quizforge.core.material;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.MaterialFileStorage;
import io.quizforge.core.port.MaterialRepository;
import io.quizforge.core.port.StoredMaterialFile;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import java.time.Clock;
import java.util.List;

public final class MaterialService {
    private final WorkspaceService workspaces;
    private final MaterialRepository repository;
    private final MaterialFileStorage storage;
    private final Clock clock;
    private final long maxBytes;

    public MaterialService(WorkspaceService workspaces, MaterialRepository repository,
            MaterialFileStorage storage, Clock clock, long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Maximum material size must be positive");
        }
        this.workspaces = workspaces;
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
        this.maxBytes = maxBytes;
    }

    public Material importMaterial(WorkspaceId workspaceId, String sourceFile) {
        workspaces.getWorkspace(workspaceId);
        MaterialId materialId = MaterialId.newId();
        StoredMaterialFile stored = storage.store(workspaceId, materialId, sourceFile, maxBytes);
        Material material = new Material(materialId, workspaceId, stored.originalFileName(),
                stored.storedFileName(), stored.fileSize(), MaterialStatus.IMPORTED, clock.instant());
        try {
            repository.save(material);
        } catch (RuntimeException failure) {
            try {
                storage.delete(material);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return material;
    }

    public List<Material> listMaterials(WorkspaceId workspaceId) {
        workspaces.getWorkspace(workspaceId);
        return repository.listByWorkspace(workspaceId);
    }

    public String readMaterial(MaterialId materialId) {
        return storage.read(find(materialId));
    }

    public void deleteMaterial(MaterialId materialId) {
        Material material = find(materialId);
        repository.delete(materialId);
        try {
            storage.delete(material);
        } catch (RuntimeException failure) {
            try {
                repository.save(material);
            } catch (RuntimeException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            throw failure;
        }
    }

    private Material find(MaterialId id) {
        return repository.findById(id).orElseThrow(() ->
                new QuizForgeException(ErrorCode.MATERIAL_NOT_FOUND, "Material was not found."));
    }
}
