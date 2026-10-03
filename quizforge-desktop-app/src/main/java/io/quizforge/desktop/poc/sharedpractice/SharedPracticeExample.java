package io.quizforge.desktop.poc.sharedpractice;

import io.quizforge.core.practice.ActivePracticeSnapshot;
import io.quizforge.core.practice.PracticeSession;
import io.quizforge.core.practice.PracticeSessionService;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;

/** Read-only real QBank input with an explicit isolated practice database; no Workspace access. */
public final class SharedPracticeExample {
    private SharedPracticeExample() { }

    public static Context open(Path qbank, Path databaseFile) {
        QuestionBank bank = new QBankPackageReader().read(qbank);
        String contentId = new QuestionBankV2Codec().contentId(bank);
        // Formal Flyway migration and Practice transactions include active/frozen Draft storage.
        var service = new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(databaseFile)), Clock.systemUTC());
        var snapshot = service.openOrCreateActiveSession(bank, contentId);
        String currentId = snapshot.session().currentQuestionId();
        var current = snapshot.questions().stream().filter(row -> row.sessionQuestion().questionId().equals(currentId))
                .filter(row -> QuestionTypes.isSingleChoice(row.sessionQuestion().snapshot().questionType())).findFirst();
        String choiceId = current.isPresent() ? current.get().sessionQuestion().questionId()
                : bank.questions().stream().filter(question -> QuestionTypes.isSingleChoice(question.type()))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("QBank has no SINGLE_CHOICE question")).id();
        if (!choiceId.equals(currentId) || snapshot.session().currentView() != PracticeSession.View.QUESTION)
            snapshot = service.updateCurrentQuestion(snapshot.session().id(), contentId, choiceId);
        return new Context(bank, service, new SharedPracticeAdapter(service, snapshot), null);
    }

    public static Context openTemporary(Path qbank) throws IOException {
        Path directory = Files.createTempDirectory("quizforge-shared-practice-");
        try {
            var context = open(qbank, directory.resolve("practice.db"));
            return new Context(context.bank(), context.service(), context.adapter(), directory);
        } catch (RuntimeException failure) {
            try { removeOwnedDirectory(directory); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static final class Context implements AutoCloseable {
        private final QuestionBank bank;
        private final PracticeSessionService service;
        private final SharedPracticeAdapter adapter;
        private final Path ownedDirectory;
        private boolean closed;

        private Context(QuestionBank bank, PracticeSessionService service, SharedPracticeAdapter adapter, Path ownedDirectory) {
            this.bank = bank;
            this.service = service;
            this.adapter = adapter;
            this.ownedDirectory = ownedDirectory;
        }
        public QuestionBank bank() { return bank; }
        public PracticeSessionService service() { return service; }
        public SharedPracticeAdapter adapter() { return adapter; }
        public ActivePracticeSnapshot snapshot() { return adapter.snapshot(); }
        @Override public synchronized void close() throws IOException {
            if (closed) return;
            // Every transaction and package closes its connection/file internally.
            if (ownedDirectory != null) removeOwnedDirectory(ownedDirectory);
            closed = true;
        }
    }

    private static void removeOwnedDirectory(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Path temporaryRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if (!root.getParent().equals(temporaryRoot) || !root.getFileName().toString().startsWith("quizforge-shared-practice-"))
            throw new IOException("Refusing cleanup outside this POC's temporary directory");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.toAbsolutePath().normalize().startsWith(root)) throw new IOException("Unsafe cleanup path");
                Files.deleteIfExists(path);
            }
        }
    }
}
