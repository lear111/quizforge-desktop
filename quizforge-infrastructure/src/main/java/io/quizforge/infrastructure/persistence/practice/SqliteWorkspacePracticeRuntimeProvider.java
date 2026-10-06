package io.quizforge.infrastructure.persistence.practice;

import io.quizforge.core.port.PracticeRuntimeProvider;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.PracticeHistoryService;
import io.quizforge.core.practice.PracticeSessionService;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankValidator;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteAssetIndexRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Durable practice data is isolated per Workspace; Registry rebuilds cannot delete it. */
public final class SqliteWorkspacePracticeRuntimeProvider implements PracticeRuntimeProvider {
    private final WorkspacePathResolver paths;
    private final QuestionBankFileCodec codec;
    private final Clock clock;
    // Database handles do not retain connections or practice state. Every open reads persisted rows.
    private final Map<Path, SqliteDatabase> databases = new ConcurrentHashMap<>();

    public SqliteWorkspacePracticeRuntimeProvider(WorkspacePathResolver paths, QuestionBankFileCodec codec, Clock clock) {
        this.paths = paths;
        this.codec = codec;
        this.clock = clock;
    }

    @Override public PersistentPracticeRuntime open(WorkspaceId workspace, QuestionBank bank) {
        return open(workspace,bank,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    @Override public PersistentPracticeRuntime open(WorkspaceId workspace,QuestionBank bank,io.quizforge.core.port.QuestionResourceInput resources) {
        new QuestionBankValidator().validate(bank);
        var scan = new io.quizforge.infrastructure.filesystem.workspace.FileSystemWorkspaceAssetScanner(
                paths, new SqliteAssetIndexRepository(paths), clock).scanReadOnly(workspace);
        boolean unique = scan.assets().stream().anyMatch(asset -> asset.assetId().equals(bank.assetId())
                && asset.assetType() == io.quizforge.core.asset.AssetType.QUESTION_BANK);
        if (!unique) throw new IllegalStateException("题库身份不可用或重复，请检查工作区中的题库文件。 "
                + scan.issues().stream().map(issue -> issue.currentPath() + ": " + issue.detail())
                        .collect(java.util.stream.Collectors.joining("; ")));
        // A formal round always contains the entire bank. Never silently omit extension questions.
        for (var question : bank.questions()) {
            var type = QuestionTypes.require(question.type());
            if (!type.supportsPractice(question))
                throw new IllegalArgumentException("This question cannot be practiced yet: " + question.id());
        }
        var service = new PracticeSessionService(new SqlitePracticeTransaction(database(workspace)), clock);
        return new PersistentPracticeRuntime(service, bank, codec.contentId(bank),resources);
    }

    @Override public PracticeHistoryService history(WorkspaceId workspace) {
        return new PracticeHistoryService(new SqlitePracticeTransaction(database(workspace)));
    }

    private SqliteDatabase database(WorkspaceId workspace) {
        Path internal = paths.workspaceRoot(workspace).resolve(".quizforge");
        Path file = internal.resolve("quizforge.db");
        io.quizforge.infrastructure.filesystem.workspace.WorkspacePathGuard.requireInside(paths.workspaceRoot(workspace),file);
        if (Files.isSymbolicLink(internal) || Files.isSymbolicLink(file) || !Files.isDirectory(internal))
            throw new IllegalStateException("Workspace practice database location is unavailable");
        return databases.computeIfAbsent(file, SqliteDatabase::new);
    }
}
