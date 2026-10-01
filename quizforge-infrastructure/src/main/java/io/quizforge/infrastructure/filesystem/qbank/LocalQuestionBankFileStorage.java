package io.quizforge.infrastructure.filesystem.qbank;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.QuestionBankFileStorage;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.SafeFilePublication;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathGuard;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Staged publication and rollback follow the existing document-file storage policy. */
public final class LocalQuestionBankFileStorage implements QuestionBankFileStorage {
    private final WorkspacePathResolver paths;

    public LocalQuestionBankFileStorage(QuizForgeDataDirectory directory) {
        this(new WorkspacePathResolver(directory));
    }

    public LocalQuestionBankFileStorage(WorkspacePathResolver paths) {
        this.paths = paths;
    }

    @Override
    public StagedFile stageReplace(WorkspaceId workspaceId, String relativePath, QuestionBank bank) {
        return stageReplace(workspaceId,relativePath,bank,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    @Override public StagedFile stageReplace(WorkspaceId workspaceId, String relativePath, QuestionBank bank,
            io.quizforge.core.port.QuestionResourceInput resources) {
        Path root = paths.workspaceRoot(workspaceId);
        Path target = checked(root, relativePath);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw failure("replace missing QuestionBank", null);
        }
        try { return stage(target.getParent(), target, root, bank, resources); }
        catch (IOException error) { throw failure("stage QuestionBank replacement", error); }
    }

    @Override
    public QuestionBank read(WorkspaceId workspaceId, String relativePath) {
        Path target = checked(paths.workspaceRoot(workspaceId), relativePath);
        if (Files.isSymbolicLink(target)) throw failure("read linked QuestionBank", null);
        return new QBankPackageReader().read(target);
    }

    private StagedFile stage(Path folder, Path target, Path root, QuestionBank bank,
            io.quizforge.core.port.QuestionResourceInput resources) throws IOException {
        Path candidate = Files.createTempFile(folder, ".qf-bank-", ".tmp");
        try {
            try (var existing = new QBankPackageReader().open(target)) {
                    new QBankPackageWriter().write(candidate, bank, resource -> {
                        var supplied=resources.open(resource);
                        return supplied==null ? existing.open(resource) : supplied;
                    });
            }
            return new SafeFilePublication(candidate, target, root, root.relativize(target).toString().replace('\\', '/'), true, ErrorCode.QUESTION_BANK_STORAGE_FAILED);
        } catch (RuntimeException error) {
            try { Files.deleteIfExists(candidate); }
            catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    private Path checked(Path root, String relativePath) {
        new Asset("qb_storage", AssetType.QUESTION_BANK, relativePath, "QuestionBank");
        if (relativePath.equals(".quizforge") || relativePath.startsWith(".quizforge/")) {
            throw failure("access internal QuestionBank path", null);
        }
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) throw failure("access QuestionBank outside workspace", null);
        Path part = root;
        for (Path segment : root.relativize(target.getParent())) {
            part = part.resolve(segment);
            if (Files.isSymbolicLink(part)) throw failure("access linked QuestionBank directory", null);
        }
        return WorkspacePathGuard.requireInside(root, target);
    }

    private static QuizForgeException failure(String action, Throwable cause) {
        return new QuizForgeException(ErrorCode.QUESTION_BANK_STORAGE_FAILED,
                "Could not " + action + ".", cause);
    }

}
