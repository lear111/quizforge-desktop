package io.quizforge.desktop.extension;

import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import java.io.*;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionManagerStartupTest {
    @TempDir Path temporary;

    @Test void ordinaryExternalImportRestartDevelopmentAndMissingTypeFlow() throws Exception {
        startFx();
        var manager = new ExtensionManager();
        var root = temporary.resolve("profile/extensions");
        var outside = Files.createDirectories(temporary.resolve("developer-distribution"));
        for (var slug : List.of("single-choice", "multiple-choice", "true-false")) {
            var manifest = io.quizforge.infrastructure.json.DocumentJson.mapper().readTree(
                    Files.readString(Path.of("../extensions/packages", slug, "manifest.json")));
            var path = Path.of("../extensions/dist", manifest.path("id").asText() + "-" + manifest.path("version").asText() + ".qfext");
            Files.copy(path, outside.resolve(path.getFileName()));
        }
        try {
            fx(() -> manager.initialize(root)).toCompletableFuture().get(120, TimeUnit.SECONDS);
            assertTrue(fx(() -> manager.loaded().isEmpty()));
            assertTrue(fx(() -> manager.diskInstalled().isEmpty()));
            for (var type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE"))
                assertTrue(QuestionTypes.find(type).isEmpty());
            var installed = new ArrayList<io.quizforge.infrastructure.extension.ExtensionPackageStore.InstalledExtension>();
            try (var files = Files.list(outside)) {
                for (var path : files.sorted().toList()) {
                    var review = fx(() -> manager.inspect(path));
                    // No implicit grant, even though all requested permissions are declared by the package.
                    var grants = new LinkedHashMap<String, Set<String>>();
                    review.manifest().types().forEach(type -> grants.put(type.id(), Set.of()));
                    installed.add(fx(() -> manager.install(path, review.sha256(), grants)).toCompletableFuture().get(20, TimeUnit.SECONDS));
                }
            }
            assertEquals(3, installed.size());
            assertTrue(fx(() -> manager.loaded().isEmpty()), "import requires a restart");
            var oldPackage=Path.of("../extensions/dist/quizforge.types.true-false-2.3.0.qfext");
            var oldReview=fx(()->manager.inspect(oldPackage));
            fx(()->manager.install(oldPackage,oldReview.sha256(),Map.of("TRUE_FALSE",Set.of()))).toCompletableFuture().get(20,TimeUnit.SECONDS);
            long initializationStarted=System.nanoTime();
            fx(() -> manager.initialize(root)).toCompletableFuture().get(120, TimeUnit.SECONDS);
            System.out.println("EXTENSION_METADATA_STARTUP_MS="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-initializationStarted));
            assertEquals(3, fx(() -> manager.loaded().size()));
            assertEquals(0,fx(manager::runningRuleRuntimeCount),"Registering current and historical versions must not start rule workers");
            assertEquals(List.of(), fx(manager::failures));
            for (var extension : installed) {
                assertTrue(fx(() -> manager.grantedPermissions(extension).values().stream().allMatch(Set::isEmpty)));
                assertArrayEquals(Files.readAllBytes(outside.resolve(extension.manifest().id() + "-" + extension.manifest().version() + ".qfext")),
                        Files.readAllBytes(extension.directory().resolve(".package.qfext")));
            }
            var questions = fx(() -> List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").stream().map(type ->
                    QuestionTypes.require(type).createDraft(prefix -> prefix + UUID.randomUUID(), List.of())).toList());
            fx(() -> {
                for (var question : questions) {
                    var definition = QuestionTypes.require(question.type());
                    var external = (io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition) definition;
                    var grade = external.invoke("grade", Map.of("question", external.encodeQuestion(question),
                            "answer", Map.of("selectedOptionIds", question.choiceAnswerSpec().correctOptionIds()),
                            "maxScore", question.scoreSpec().defaultMaxScore()));
                    assertEquals(0, question.scoreSpec().defaultMaxScore().compareTo(new java.math.BigDecimal(grade.get("score").toString())));
                    var copy = definition.duplicate(question, prefix -> prefix + UUID.randomUUID());
                    assertNotEquals(question.id(), copy.id());
                    assertTrue(copy.choicePayload().options().stream().noneMatch(option -> question.choicePayload().options().stream().anyMatch(old -> old.id().equals(option.id()))));
                }
                return null;
            });
            assertEquals(3,fx(manager::runningRuleRuntimeCount),"Only the three used current versions should run");
            fx(()->{
                var old=QuestionTypes.requireVersion("TRUE_FALSE","2.3.0");
                var question=questions.stream().filter(q->q.type().equals("TRUE_FALSE")).findFirst().orElseThrow();
                assertNotNull(old.invoke("grade",Map.of("question",old.encodeQuestion(question),"answer",Map.of("selectedOptionIds",question.choiceAnswerSpec().correctOptionIds()),"maxScore",question.scoreSpec().defaultMaxScore())));
                return null;
            });
            assertEquals(4,fx(manager::runningRuleRuntimeCount),"A frozen historical version starts only when explicitly used");
            var codec = new QuestionBankV2Codec();
            var bank = new QuestionBank("qb_external", "External flow", List.of(), questions, List.of());
            var saved = codec.write(bank);
            assertEquals(bank, codec.parse(saved));

            var source = temporary.resolve("developer-source");
            var template = Path.of("../extensions/packages/true-false");
            try (var paths = Files.walk(template)) {
                for (var path : paths.toList()) {
                    var target = source.resolve(template.relativize(path));
                    if (Files.isDirectory(path)) Files.createDirectories(target); else Files.copy(path, target);
                }
            }
            var styles = source.resolve("style.css");
            Files.writeString(styles, "\n/* external-live-preview-marker */\n", StandardOpenOption.APPEND);
            assertFalse(fx(manager::currentPackagesJson).contains("external-live-preview-marker"), "source folders are not watched automatically");
            fx(() -> manager.loadDevelopmentDirectory(source)).toCompletableFuture().get(30, TimeUnit.SECONDS);
            assertTrue(fx(manager::currentPackagesJson).contains("external-live-preview-marker"));
            var judgment = installed.stream().filter(item -> item.manifest().id().endsWith("true-false")).findFirst().orElseThrow();
            var judgmentPackage = outside.resolve(judgment.manifest().id() + "-" + judgment.manifest().version() + ".qfext");
            assertArrayEquals(Files.readAllBytes(judgmentPackage), Files.readAllBytes(judgment.directory().resolve(".package.qfext")));
            fx(() -> { manager.stopDevelopment(); return null; });
            assertFalse(fx(manager::currentPackagesJson).contains("external-live-preview-marker"));

            // A changed package cannot silently replace an installed release or elevate its grants.
            var changed = outside.resolve("changed.qfext");
            Files.write(changed, withChangedStyles(Files.readAllBytes(judgmentPackage)));
            var review = fx(() -> manager.inspect(changed));
            assertThrows(ExecutionException.class, () -> fx(() -> manager.install(changed, review.sha256(), Map.of("TRUE_FALSE", Set.of("answer.write")))).toCompletableFuture().get());
            assertEquals(Set.of(), fx(() -> manager.grantedPermissions(judgment).get("TRUE_FALSE")));

            fx(() -> manager.initialize(temporary.resolve("another-empty-profile/extensions"))).toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertTrue(fx(() -> manager.loaded().isEmpty()));
            for (var question : questions) assertTrue(QuestionTypes.find(question.type()).isEmpty());
            assertEquals(bank, codec.parse(saved), "missing extensions preserve question data");
        } finally { fx(() -> { manager.close(); return null; }); }
    }

    private static byte[] withChangedStyles(byte[] original) throws Exception {
        var result = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(new ByteArrayInputStream(original), StandardCharsets.UTF_8);
             var output = new ZipOutputStream(result, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName())); output.write(input.readAllBytes());
                if (entry.getName().equals("style.css")) output.write("\n/* changed package */\n".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return result.toByteArray();
    }
    private static void startFx() throws Exception {
        var started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); }
        catch (IllegalStateException running) { Platform.runLater(started::countDown); }
        assertTrue(started.await(20, TimeUnit.SECONDS));
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        // A single test action invokes three independent workers; each retains its 45 s cold-start budget.
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(160, TimeUnit.SECONDS);
    }
}
