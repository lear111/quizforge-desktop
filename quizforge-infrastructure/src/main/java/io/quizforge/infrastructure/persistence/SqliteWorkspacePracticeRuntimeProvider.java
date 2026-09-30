package io.quizforge.infrastructure.persistence;

import io.quizforge.core.question.*;

import io.quizforge.core.port.PracticeRuntimeProvider;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.PracticeSessionService;
import io.quizforge.core.practice.PracticeHistoryService;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

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
        new QuestionBankValidator().validate(bank);
        var choices = bank.questions().stream().filter(QuestionText::supports).toList();
        if (choices.isEmpty()) throw new IllegalArgumentException("This bank has no supported practice questions");
        // Keep the original asset and full-file logical revision. Only supported choice
        // questions enter the existing practice snapshots; essays remain author previews.
        var practiceBank = choices.size() == bank.questions().size() ? bank
                : new QuestionBank(bank.assetId(), bank.title(), bank.schemaVersion(),
                        bank.stimuli(), choices, bank.resources());
        var service = new PracticeSessionService(new SqlitePracticeTransaction(database(workspace)), clock);
        return new PersistentPracticeRuntime(service, practiceBank, codec.contentId(bank));
    }

    @Override public PracticeHistoryService history(WorkspaceId workspace) {
        return new PracticeHistoryService(new SqlitePracticeTransaction(database(workspace)));
    }

    private SqliteDatabase database(WorkspaceId workspace) {
        Path internal = paths.workspaceRoot(workspace).resolve(".quizforge");
        Path file = internal.resolve("quizforge.db");
        if (Files.isSymbolicLink(internal) || Files.isSymbolicLink(file) || !Files.isDirectory(internal))
            throw new IllegalStateException("Workspace practice database location is unavailable");
        return databases.computeIfAbsent(file, SqliteDatabase::new);
    }
}
