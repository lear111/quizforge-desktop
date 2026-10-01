package io.quizforge.infrastructure.filesystem.markdown;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.FileDocumentStorage;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.SafeFilePublication;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathGuard;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class LocalMarkdownFileStorage implements FileDocumentStorage {
    private final WorkspacePathResolver paths;

    public LocalMarkdownFileStorage(QuizForgeDataDirectory directory) {
        this(new WorkspacePathResolver(directory));
    }

    public LocalMarkdownFileStorage(WorkspacePathResolver paths) {
        this.paths = paths;
    }

    @Override
    public FileDocumentStorage.StagedFile stageReplace(WorkspaceId workspaceId,
            String currentPath, String content) {
        Path root = paths.workspaceRoot(workspaceId);
        Path target = assetPath(root, currentPath);
        if (!currentPath.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
            throw failure("replace a document with a different file format", null);
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw failure("replace missing document", null);
        }
        try {
            return stageAsset(target.getParent(), target, root, content);
        } catch (IOException error) {
            throw failure("stage document replacement", error);
        }
    }

    @Override
    public String read(WorkspaceId workspaceId, String currentPath) {
        Path target = assetPath(paths.workspaceRoot(workspaceId), currentPath);
        if (Files.isSymbolicLink(target)) throw failure("read document", null);
        try { return Files.readString(target, StandardCharsets.UTF_8); }
        catch (IOException error) { throw failure("read document", error); }
    }

    private SafeFilePublication stageAsset(Path folder, Path target, Path root, String content) throws IOException {
        Path candidate = Files.createTempFile(folder, ".qf-document-", ".tmp");
        try {
            Files.writeString(candidate, content, StandardCharsets.UTF_8);
            String relative = root.relativize(target).toString().replace('\\', '/');
            return new SafeFilePublication(candidate, target, root, relative, true, ErrorCode.REGISTERED_MARKDOWN_STORAGE_FAILED);
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(candidate);
            throw error;
        }
    }

    private Path assetPath(Path root, String currentPath) {
        new Asset("doc_storage", AssetType.REGISTERED_MARKDOWN, currentPath, "Document");
        if (currentPath.equals(".quizforge") || currentPath.startsWith(".quizforge/")) {
            throw failure("access internal document path", null);
        }
        Path target = root.resolve(currentPath).normalize();
        if (!target.startsWith(root)) throw failure("access document outside workspace", null);
        Path part = root;
        for (Path segment : root.relativize(target.getParent())) {
            part = part.resolve(segment);
            if (Files.isSymbolicLink(part)) throw failure("access linked document directory", null);
        }
        return WorkspacePathGuard.requireInside(root, target);
    }

    private static QuizForgeException failure(String action, Throwable cause) {
        return new QuizForgeException(ErrorCode.REGISTERED_MARKDOWN_STORAGE_FAILED,
                "Could not " + action + ".", cause);
    }

}
