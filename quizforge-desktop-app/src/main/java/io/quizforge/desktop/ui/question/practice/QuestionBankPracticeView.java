package io.quizforge.desktop.ui.question.practice;

import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.QuestionBankPracticeSession;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.dev.DevelopmentRefreshable;


import io.quizforge.desktop.ui.question.shared.QuestionCardLayout;
import io.quizforge.desktop.ui.question.shared.QuestionOutlineView;
import io.quizforge.desktop.ui.question.source.QuestionSourceListView;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** One question at a time; answer state never alters the .qbank file. */
public final class QuestionBankPracticeView extends VBox implements DevelopmentRefreshable {
    /** Mixed-bank navigation retains original question numbers and author-preview pages. */
    public record Navigation(int index, int total, boolean previousDisabled, boolean last,
            Runnable previous, Runnable next) { }
    private final PersistentPracticeRuntime runtime;
    private final QuestionBankPracticeSession session;
    private final Function<List<SourceRef>, QuestionSourceListView> sources;
    private final QuestionOutlineView outline;
    private final Navigation navigation;
    private final Runnable onChanged;
    private QuestionSourceListView sourceRows;
    private BooleanSupplier restartConfirmation = this::confirmRestart;
    private PracticeSurfaceHost surface;
    public void setSurfaceHost(PracticeSurfaceHost surface) { this.surface=surface; }
    public void refreshPracticeState() { render(); }
    private void navigate(Runnable action) { if(surface==null)action.run();else surface.navigate(action); }
    public void navigateFromSurface(Runnable action) { navigate(action); }

    public QuestionBankPracticeView(PersistentPracticeRuntime runtime,
            Function<List<SourceRef>, QuestionSourceListView> sources) {
        this(runtime, sources, null, null);
    }
    public QuestionBankPracticeView(PersistentPracticeRuntime runtime,
            Function<List<SourceRef>, QuestionSourceListView> sources, Navigation navigation, Runnable onChanged) {
        this.runtime = runtime;
        this.navigation = navigation;
        this.onChanged = onChanged;
        session = runtime.session();
        this.sources = sources;
        outline = new QuestionOutlineView(session, this::jumpToQuestion);
        setId("question-practice");
        getStyleClass().add("practice-view");
        QuestionCardLayout.configure(this);
        render();
    }

    private void render() {
        if(surface!=null)surface.refreshChrome();
        outline.refresh();
        sourceRows = null;
        getChildren().clear();
        if (session.finished()) { summary(); return; }
        if (surface != null) {
            surface.showQuestion();
            return;
        }
        var shared = new MixedQuestionPracticeView(session.bank(),
                io.quizforge.core.port.QuestionResourceInput.NONE, () -> runtime, sources);
        VBox.setVgrow(shared, Priority.ALWAYS);
        getChildren().add(shared);
    }
    public void refreshSources() { if (sourceRows != null) sourceRows.refresh(); }
    public void setRestartConfirmation(BooleanSupplier confirmation) { restartConfirmation = confirmation; }

    public QuestionOutlineView outline() { return outline; }

    private void jumpToQuestion(int target) {
        navigate(() -> command(() -> runtime.goTo(target)));
    }

    private void command(Runnable action) {
        try {
            action.run();
            if (onChanged != null) onChanged.run();
            render();
        } catch (RuntimeException failure) {
            // The runtime is only hydrated after commit; repaint restores the persisted selection.
            render();
            var error = UiTheme.label("练习状态未保存：" + failure.getMessage(), "incorrect");
            error.setId("practice-error");
            getChildren().add(error);
        }
    }

    private void summary() {
        VBox page = new io.quizforge.desktop.ui.question.shared.PracticeSummaryCard(runtime.summary(),"本次练习","practice-summary","summary");
        Button previous = QuestionCardLayout.navigation("arrow-left", "回到上一题", () -> navigate(() -> command(runtime::previous)));
        previous.setId("summary-previous");
        Button restart = UiTheme.button("重新练习", "refresh", "primary", () -> {
            if (restartConfirmation.getAsBoolean()) navigate(() -> command(runtime::restart));
        });
        restart.setId("practice-restart");
        restart.getStyleClass().add("practice-restart");
        restart.setAccessibleText("重新练习");
        FlowPane actions = new FlowPane(12, 10, restart);
        actions.setId("practice-summary-actions");
        actions.getStyleClass().add("practice-card-actions");
        actions.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().add(actions);
        if (surface != null && surface.unified()) {
            setAlignment(Pos.CENTER);
            getChildren().add(page);
            return;
        }
        Region nextSpace = new Region();
        nextSpace.getStyleClass().add("practice-navigation-spacer");
        nextSpace.setVisible(false);
        HBox navigation = QuestionCardLayout.row(previous, page, nextSpace);
        navigation.setId("practice-summary-navigation");
        getChildren().add(navigation);
    }

    private boolean confirmRestart() {
        ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType accept = new ButtonType("重新练习", ButtonBar.ButtonData.OK_DONE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "当前练习将保存到历史记录，并从第一题重新开始。", cancel, accept);
        confirm.setHeaderText("重新练习？");
        if (getScene() != null) confirm.initOwner(getScene().getWindow());
        UiTheme.apply(confirm);
        return confirm.showAndWait().orElse(cancel) == accept;
    }

    @Override public void refreshForDevelopment(){render();}
}
