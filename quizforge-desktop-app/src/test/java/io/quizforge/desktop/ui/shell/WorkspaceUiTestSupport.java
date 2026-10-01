package io.quizforge.desktop.ui.shell;

import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.workspace.model.WorkspaceFileEntry;
import io.quizforge.desktop.ui.content.document.canvas.CanvasEditorTestDriver;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.infrastructure.filesystem.markdown.RegisteredMarkdownCodec;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

abstract class WorkspaceUiTestSupport {
    @TempDir Path temp;
    protected ShellFixture fixture;
    protected MainWorkspaceView shell;
    protected Stage stage;

    @BeforeAll static void startFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(started::countDown); }
        assertTrue(started.await(20, TimeUnit.SECONDS));
    }

    @BeforeEach void setup() throws Exception {
        fixture = new ShellFixture(temp);
        fx(() -> {
            stage = new Stage();
            shell = fixture.shell(stage);
            Scene scene = new Scene(shell, 1100, 740);
            UiTheme.apply(scene);
            stage.setScene(scene);
            stage.setOpacity(0);
            stage.show();
        }, 120); // Native JavaFX window initialization can be slow on a busy Windows desktop.
    }

    @AfterEach void close() throws Exception {
        fx(() -> {
            if (shell != null) shell.tabs().closeAll();
            if (stage != null) { stage.setScene(null); stage.close(); }
            pulse(100); // Finish queued WebView detach/release callbacks before TempDir cleanup.
        });
        fx(() -> { });
        if (fixture != null) fixture.close();
    }

    protected void assertHeaderGapUnderActiveTab() {
        var tab = shell.tabs().activeTabNode();
        var left = shell.lookup("#workspace-header-seam");
        var right = shell.lookup("#workspace-header-seam-right");
        assertEquals(tab.localToScene(0, 0).getX(),
                left.localToScene(left.getBoundsInLocal().getWidth(), 0).getX(), 0.5);
        assertEquals(tab.localToScene(tab.getBoundsInLocal().getWidth(), 0).getX(),
                right.localToScene(0, 0).getX(), 0.5);
    }

    protected Node divider(SplitPane pane) {
        return pane.getChildrenUnmodifiable().stream()
                .filter(child -> child.getStyleClass().contains("split-pane-divider"))
                .findFirst().orElseThrow();
    }

    protected static javafx.scene.input.MouseEvent mouseMoved() {
        return mouseEvent(javafx.scene.input.MouseEvent.MOUSE_MOVED);
    }

    protected static javafx.scene.input.MouseEvent mouseEvent(
            javafx.event.EventType<? extends javafx.scene.input.MouseEvent> type) {
        return new javafx.scene.input.MouseEvent(type,
                0, 0, 0, 0, javafx.scene.input.MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null);
    }

    protected static void answerDialog(String label) {
        DialogPane pane = currentDialog();
        ButtonType choice = pane.getButtonTypes().stream()
                .filter(type -> label.equals(type.getText())).findFirst().orElseThrow();
        ((Button) pane.lookupButton(choice)).fire();
    }

    protected static DialogPane currentDialog() {
        return javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing)
                .map(window -> window.getScene() == null ? null : window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .findFirst().orElseThrow();
    }

    protected List<String> outlineNumbers(String type) {
        var section = (javafx.scene.layout.VBox) shell.lookup("#question-outline-" + type);
        var numbers = (javafx.scene.layout.FlowPane) section.getChildren().get(1);
        return numbers.getChildren().stream().map(node -> ((Button) node).getText()).toList();
    }

    protected String outlineBank(String... types) throws Exception {
        var template = new QuestionBankV2Codec().parse(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            var original = template.questions().stream().filter(question -> question.type().equals(type)).findFirst().orElseThrow();
            List<ChoiceOption> options = new ArrayList<>();
            List<String> correct = new ArrayList<>();
            for (int j = 0; j < original.choicePayload().options().size(); j++) {
                var old = original.choicePayload().options().get(j);
                String id = "opt_outline_" + i + "_" + j;
                options.add(new ChoiceOption(id, old.content()));
                if (original.choiceAnswerSpec().correctOptionIds().contains(old.id())) correct.add(id);
            }
            questions.add(Question.choice("q_outline_" + i, types[i], original.prompt(), original.analysis(), original.sourceRefs(), new ChoicePayload(options), new ChoiceAnswerSpec(correct)));
        }
        String path = "题库/Outline.qbank";
        fixture.write(path, new QuestionBankV2Codec().write(new QuestionBank("qb_outline", "Outline practice", "2.0", List.of(), questions, List.of())));
        return path;
    }

    protected record NavigationBank(String documentPath, String bankPath, String markdown,
            QuestionBank bank, String json) { }

    protected NavigationBank navigationBank(boolean multiple) throws Exception {
        String documentPath = "Java/SourceNav.md", bankPath = "题库/SourceNav.qbank";
        StringBuilder text = new StringBuilder("# Source navigation\n\n<!-- qf:anchor=定义 -->\nFirst definition.\n\n");
        for (int i = 0; i < 65; i++) text.append("Paragraph ").append(i).append(" for scrolling.\n\n");
        text.append("<!-- qf:anchor=定义 -->\nSecond definition.\n\n<!-- qf:anchor=孤立 -->\n");
        var prepared = new RegisteredMarkdownCodec().prepareRegistration(text.toString(), documentPath);
        var document = prepared.document();
        var first = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "定义", 1, "Historical document title", "Historical section title");
        var second = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "定义", 2, "Historical document title", "Historical section title");
        var missing = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "不存在", 1, "Historical document title", "Historical section title");
        var orphan = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "孤立", 1, "Historical document title", "Historical section title");
        var question = Question.choice("q_navigation", "SINGLE_CHOICE", new TextContent("Test question"), new TextContent("Analysis"), multiple ? List.of(first, second, missing, orphan) : List.of(second), new ChoicePayload(List.of(new ChoiceOption("opt_nav_a", new TextContent("Correct")),
                        new ChoiceOption("opt_nav_b", new TextContent("Incorrect")))), new ChoiceAnswerSpec(List.of("opt_nav_a")));
        var bank = new QuestionBank("qb_navigation", "Navigation bank", "2.0", List.of(), List.of(question), List.of());
        String json = new QuestionBankV2Codec().write(bank);
        fixture.write(documentPath, prepared.source()); fixture.write(bankPath, json);
        return new NavigationBank(documentPath, bankPath, prepared.source(), bank, json);
    }

    protected String archiveSourceHistory(NavigationBank sample) throws Exception {
        var first = sample.bank().questions().getFirst();
        var second = Question.choice("q_history_second", first.type(), new TextContent("Archived second question"), first.analysis(), first.sourceRefs(), new ChoicePayload(List.of(
                        new ChoiceOption("opt_second_a", new TextContent("Correct")),
                        new ChoiceOption("opt_second_b", new TextContent("Incorrect")))), new ChoiceAnswerSpec(List.of("opt_second_a")));
        var bank = new QuestionBank(sample.bank().assetId(), sample.bank().title(), "2.0", List.of(), List.of(first, second), List.of());
        fixture.write(sample.bankPath(), new QuestionBankV2Codec().write(bank));
        var runtime = fixture.context.getBean(io.quizforge.core.port.PracticeRuntimeProvider.class).open(fixture.alpha.id(), bank);
        for (int i = 0; i < 2; i++) {
            String prefix = i == 0 ? "opt_nav_" : "opt_second_";
            runtime.select(prefix + "b"); runtime.submit(); runtime.retry();
            runtime.select(prefix + "a"); runtime.submit();
            if (i == 0) runtime.next();
        }
        String id = runtime.sessionId();
        new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb()).archive(id, java.time.Instant.now());
        return id;
    }

    protected void openSourceHistory(NavigationBank sample, String archived) {
        shell.tabs().openPinned(fixture.alpha.id(), sample.bankPath());
        button("qbank-history-entry").fire(); shell.applyCss(); shell.layout();
        click(shell.lookup("#history-card-" + archived), 1); shell.applyCss(); shell.layout();
        assertNotNull(shell.lookup("#practice-history-detail"));
    }

    protected List<String> practiceRows() throws Exception {
        List<String> rows = new ArrayList<>();
        try (var connection = practiceDb().openConnection(); var statement = connection.createStatement()) {
            for (String table : List.of("practice_session", "practice_session_question", "question_attempt")) {
                try (var result = statement.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    int columns = result.getMetaData().getColumnCount();
                    while (result.next()) {
                        StringBuilder row = new StringBuilder(table);
                        for (int i = 1; i <= columns; i++) row.append('|').append(result.getString(i));
                        rows.add(row.toString());
                    }
                }
            }
        }
        return rows;
    }

    protected void forbidPracticeWrites() throws Exception {
        try (var connection = practiceDb().openConnection(); var statement = connection.createStatement()) {
            for (String table : List.of("practice_session", "practice_session_question", "question_attempt"))
                for (String operation : List.of("INSERT", "UPDATE", "DELETE")) statement.execute(
                        "CREATE TRIGGER no_" + table + "_" + operation + " BEFORE " + operation + " ON " + table
                                + " BEGIN SELECT RAISE(ABORT, 'History navigation must be read-only'); END");
        }
    }

    protected io.quizforge.infrastructure.persistence.SqliteDatabase practiceDb() {
        return new io.quizforge.infrastructure.persistence.SqliteDatabase(fixture.alphaRoot.resolve(".quizforge/quizforge.db"));
    }
    protected io.quizforge.core.practice.PracticeSession practiceDbSession() {
        return new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                .findActiveByQuestionBankAssetId("qb_java").orElseThrow();
    }

    protected TreeCell<?> fileCell(String path) {
        shell.applyCss(); shell.layout();
        return shell.sidebar().tree().lookupAll(".tree-cell").stream().filter(TreeCell.class::isInstance)
                .map(TreeCell.class::cast).filter(row -> row.getItem() instanceof WorkspaceFileEntry entry
                        && entry.relativePath().equals(path)).findFirst().orElseThrow();
    }

    protected TreeCell<?> folderCell(String path) {
        shell.applyCss(); shell.layout();
        return shell.sidebar().tree().lookupAll(".tree-cell").stream().filter(TreeCell.class::isInstance)
                .map(TreeCell.class::cast).filter(row -> row.getItem() instanceof WorkspaceFileEntry entry
                        && entry.relativePath().equals(path)).findFirst().orElseThrow();
    }

    protected static void click(Node target, int count) {
        for (var type : List.of(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                javafx.scene.input.MouseEvent.MOUSE_RELEASED, javafx.scene.input.MouseEvent.MOUSE_CLICKED)) {
            target.fireEvent(new javafx.scene.input.MouseEvent(type, 8, 8, 8, 8,
                    javafx.scene.input.MouseButton.PRIMARY, count, false, false, false, false,
                    type == javafx.scene.input.MouseEvent.MOUSE_PRESSED, false, false, false, false, true, null));
        }
    }

    protected Node previewLink(String href) {
        return shell.lookupAll(".prose-internal-link").stream()
                .filter(node -> href.equals(node.getProperties().get("quizforge.linkHref")))
                .findFirst().orElseThrow();
    }

    protected static void pulse(int millis) {
        Object token = new Object();
        var timer = new javafx.animation.PauseTransition(javafx.util.Duration.millis(millis));
        timer.setOnFinished(event -> Platform.exitNestedEventLoop(token, null));
        timer.play();
        Platform.enterNestedEventLoop(token);
    }

    protected void open(String path) { shell.selectPath(path); shell.applyCss(); shell.layout(); }
    protected Button button(String id) { shell.applyCss(); shell.layout(); return (Button) shell.lookup("#" + id); }

    protected static List<String> paths(TreeItem<WorkspaceFileEntry> root) {
        List<String> paths = new ArrayList<>();
        if (root.getValue() != null) paths.add(root.getValue().relativePath());
        root.getChildren().forEach(child -> paths.addAll(paths(child)));
        return paths;
    }

    static String text(Node node) {
        if (node == null) return "";
        if (node instanceof javafx.scene.text.Text span) return span.getText();
        if (node instanceof ScrollPane scroll) return text(scroll.getContent());
        if (node instanceof SplitPane split) return split.getItems().stream()
                .map(WorkspaceUiTestSupport::text).collect(java.util.stream.Collectors.joining("\n"));
        StringBuilder out = new StringBuilder(node instanceof Labeled label ? label.getText() + "\n" : "");
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> out.append(text(child)));
        return out.toString();
    }

    protected void editPrompt(Consumer<CanvasEditorTestDriver> action,boolean save) {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Platform.runLater(()->{
            Stage dialog=null;
            try {
                dialog=Window.getWindows().stream().filter(w->w instanceof Stage stage && stage.isShowing()
                        && "编辑题干".equals(stage.getTitle())).map(w->(Stage)w).findFirst().orElseThrow();
                assertEquals(javafx.stage.Modality.WINDOW_MODAL,dialog.getModality());
                var window=CanvasEditorTestDriver.from(dialog);
                assertNotNull(window);for(int i=0;i<160 && !window.bridge().ready();i++)pulse(50);
                assertTrue(window.bridge().ready(),"Canvas Editor WebView must load");action.accept(window);
                var button=(Button)dialog.getScene().lookup(save?"#canvas-editor-save":"#canvas-editor-back");
                assertNotNull(button);button.fire();
            }catch(Throwable error){failure.set(error);if(dialog!=null)dialog.close();}
        });
        button("essay-edit-prompt").fire();
        if(failure.get()!=null)throw new AssertionError("Generic editor window interaction failed",failure.get());
    }

    protected Path localImage(String format) throws Exception {Path path=temp.resolve("local-image."+format);Files.write(path,io.quizforge.infrastructure.testing.EssayTestBanks.image(format));return path;}
    protected QuestionBank readEssay(){return new io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader().read(fixture.alphaRoot.resolve("题库/Essay.qbank"));}
    protected QuestionBank prepareEssayImage() throws Exception {
        var original=io.quizforge.infrastructure.testing.EssayTestBanks.bank();Path local=localImage("png");
        var image=new io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter().read(local);var model=new QuestionBankEditorModel(original);
        model.addResource(image.resource());model.setPrompt(0,io.quizforge.infrastructure.testing.EssayTestBanks.prompt(image.resource().id()));
        Path file=fixture.alphaRoot.resolve("题库/Essay.qbank");Files.createDirectories(file.getParent());
        return new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(file,model.bank(),r->image.open());
    }
    protected static <T extends Node> List<T> nodes(Node root,Class<T> type) {
        var found=new ArrayList<T>();if(type.isInstance(root))found.add(type.cast(root));
        if(root instanceof ScrollPane pane)found.addAll(nodes(pane.getContent(),type));
        else if(root instanceof Parent parent)parent.getChildrenUnmodifiable().forEach(child->found.addAll(nodes(child,type)));
        return found;
    }
    protected static void fx(CheckedRunnable action) throws Exception {
        fx(action, 40);
    }

    protected static void fx(CheckedRunnable action, int timeoutSeconds) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    protected interface CheckedRunnable { void run() throws Exception; }

    protected String nativePrompt(QuestionBank bank,String path) throws Exception {
        var content=(DocumentContent)bank.questions().getFirst().prompt();
        var resource=bank.resources().stream().filter(item->item.id().equals(content.resourceId())).findFirst().orElseThrow();
        try(var file=new io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader().open(fixture.alphaRoot.resolve(path));
                var input=file.open(resource)){return CanvasEditorTestDriver.readDocument(input);}
    }
}
