package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.PracticeSummary;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;

/** One question at a time; answer state never alters the .qbank file. */
final class QuestionBankPracticeView extends VBox {
    /** Mixed-bank navigation retains original question numbers and author-preview pages. */
    record Navigation(int index, int total, boolean previousDisabled, boolean last,
            Runnable previous, Runnable next) { }
    private final PersistentPracticeRuntime runtime;
    private final QuestionBankPracticeSession session;
    private final Function<List<SourceRef>, QuestionSourceListView> sources;
    private final QuestionOutlineView outline;
    private final Navigation navigation;
    private final Runnable onChanged;
    private QuestionSourceListView sourceRows;
    private BooleanSupplier restartConfirmation = this::confirmRestart;

    QuestionBankPracticeView(PersistentPracticeRuntime runtime,
            Function<List<SourceRef>, QuestionSourceListView> sources) {
        this(runtime, sources, null, null);
    }
    QuestionBankPracticeView(PersistentPracticeRuntime runtime,
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
        outline.refresh();
        sourceRows = null;
        getChildren().clear();
        if (session.finished()) { summary(); return; }
        Question question = session.current();
        int displayIndex = navigation == null ? session.index() : navigation.index();
        int displayTotal = navigation == null ? session.bank().questions().size() : navigation.total();
        var state = session.state();
        QuestionCardView card;
        if (state == QuestionBankPracticeSession.State.SUBMITTED) {
            if (!question.sourceRefs().isEmpty()) sourceRows = sources.apply(question.sourceRefs());
            card = QuestionCardView.result(QuestionPresentationMapper.practiceResult(session),
                    displayIndex, displayTotal, "", sourceRows);
        } else {
            card = QuestionCardView.answering(QuestionPresentationMapper.practice(session), displayIndex,
                    displayTotal, session.selected(), id -> command(() -> runtime.select(id)));
        }
        card.setId("practice-question-card");
        Button submit = UiTheme.button("提交答案", "check", "primary", () -> { });
        submit.getStyleClass().add("practice-submit");
        submit.setId("submit-answer");
        submit.setAccessibleText("提交答案");
        submit.setDisable(state != QuestionBankPracticeSession.State.SELECTED);
        FlowPane actions = new FlowPane(12, 10, submit);
        actions.setId("practice-card-actions");
        actions.getStyleClass().add("practice-card-actions");
        actions.setAlignment(Pos.CENTER_LEFT);
        submit.setOnAction(event -> {
            submit.setDisable(true);
            command(runtime::submit);
        });
        if (state == QuestionBankPracticeSession.State.SUBMITTED) {
            Button retry = UiTheme.button("重新答题", "refresh", "", () -> command(runtime::retry));
            retry.setId("practice-retry");
            actions.getChildren().add(retry);
        }
        card.getChildren().add(actions);
        Button previous = QuestionCardLayout.navigation("arrow-left", "上一题", () -> { });
        previous.setId("previous-question");
        previous.setDisable(navigation == null ? session.index() == 0 : navigation.previousDisabled());
        previous.setOnAction(event -> { if (navigation == null) command(runtime::previous); else navigation.previous().run(); });
        boolean last = navigation == null ? session.index() == session.bank().questions().size() - 1 : navigation.last();
        Button next = QuestionCardLayout.navigation("arrow", last ? "查看本次练习" : "下一题", () -> { });
        next.setId("next-question");
        next.setOnAction(event -> { if (navigation == null) command(runtime::next); else navigation.next().run(); });
        HBox navigation = QuestionCardLayout.row(previous, card, next);
        navigation.setId("practice-navigation");
        getChildren().add(navigation);
    }

    void refreshSources() { if (sourceRows != null) sourceRows.refresh(); }
    void setRestartConfirmation(BooleanSupplier confirmation) { restartConfirmation = confirmation; }

    QuestionOutlineView outline() { return outline; }

    private void jumpToQuestion(int target) {
        command(() -> runtime.goTo(target));
    }

    private void command(Runnable action) {
        try {
            action.run();
            if (onChanged != null) onChanged.run();
            if (session.finished() || !"ESSAY".equals(session.current().type())) render();
        } catch (RuntimeException failure) {
            // The runtime is only hydrated after commit; repaint restores the persisted selection.
            render();
            var error = UiTheme.label("练习状态未保存：" + failure.getMessage(), "incorrect");
            error.setId("practice-error");
            getChildren().add(error);
        }
    }

    private void summary() {
        PracticeSummary summary = runtime.summary();
        int percent = summary.totalCount() == 0 ? 0
                : (int) Math.round(100.0 * summary.correctCount() / summary.totalCount());
        Label percentage = UiTheme.label(summary.totalCount() == 0 ? "—" : percent + "%",
                "practice-summary-percentage");
        percentage.setId("summary-percentage");

        VBox legend = new VBox(14,
                summaryStatus("正确", summary.correctCount(), "correct", "summary-correct-count"),
                summaryStatus("错误", summary.incorrectCount(), "incorrect", "summary-incorrect-count"),
                summaryStatus("未作答", summary.unfinishedCount(), "unanswered", "summary-unanswered-count"));
        legend.getStyleClass().add("practice-summary-status-list");
        if(summary.unscoredCount()>0)legend.getChildren().add(summaryStatus("未评分",summary.unscoredCount(),"unanswered","summary-unscored-count"));
        FlowPane results = new FlowPane(28, 20, summaryRing(summary, percentage), legend);
        results.getStyleClass().add("practice-summary-results");
        results.setMinWidth(0);
        results.setAlignment(Pos.CENTER);
        results.setColumnHalignment(HPos.CENTER);
        results.setRowValignment(VPos.CENTER);

        var title = UiTheme.label("本次练习", "practice-question-type");
        title.setWrapText(false);
        var total = UiTheme.label("共 " + summary.totalCount() + " 题", "muted");
        total.setId("summary-total-count");
        total.setWrapText(false);
        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox heading = new HBox(12, title, titleSpacer, total);
        heading.setAlignment(Pos.CENTER_LEFT);

        VBox page = new VBox(heading, results);
        page.setId("practice-summary");
        page.getStyleClass().addAll("practice-question-card", "practice-summary-card");
        page.setMinWidth(0);
        page.setMaxHeight(Region.USE_PREF_SIZE);
        Button previous = QuestionCardLayout.navigation("arrow-left", "回到上一题", () -> command(runtime::previous));
        previous.setId("summary-previous");
        Button restart = UiTheme.button("重新练习", "refresh", "primary", () -> {
            if (restartConfirmation.getAsBoolean()) command(runtime::restart);
        });
        restart.setId("practice-restart");
        restart.getStyleClass().add("practice-restart");
        restart.setAccessibleText("重新练习");
        FlowPane actions = new FlowPane(12, 10, restart);
        actions.setId("practice-summary-actions");
        actions.getStyleClass().add("practice-card-actions");
        actions.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().add(actions);
        Region nextSpace = new Region();
        nextSpace.getStyleClass().add("practice-navigation-spacer");
        nextSpace.setVisible(false);
        HBox navigation = QuestionCardLayout.row(previous, page, nextSpace);
        navigation.setId("practice-summary-navigation");
        getChildren().add(navigation);
    }

    private StackPane summaryRing(PracticeSummary summary, Label percentage) {
        double center = 66;
        double radius = 52;
        Pane segments = new Pane();
        segments.setMinSize(132, 132);
        segments.setPrefSize(132, 132);
        segments.setMaxSize(132, 132);
        segments.getStyleClass().add("practice-summary-segments");
        Circle track = new Circle(center, center, radius);
        track.getStyleClass().add("practice-summary-track");
        segments.getChildren().add(track);
        if (summary.totalCount() > 0) {
            double start = 90;
            start = addSummarySegment(segments, start, summary.correctCount(), summary.totalCount(), "correct");
            start = addSummarySegment(segments, start, summary.incorrectCount(), summary.totalCount(), "incorrect");
            addSummarySegment(segments, start, summary.unfinishedCount()+summary.unscoredCount(), summary.totalCount(), "unanswered");
        }
        StackPane ring = new StackPane(segments, percentage);
        ring.setMinSize(132, 132);
        ring.setPrefSize(132, 132);
        ring.setMaxSize(132, 132);
        ring.getStyleClass().add("practice-summary-ring");
        return ring;
    }

    private double addSummarySegment(Pane segments, double start, int count, int total, String state) {
        if (count <= 0) return start;
        double length = 360.0 * count / total;
        Arc arc = new Arc(66, 66, 52, 52, start, -length);
        arc.setType(ArcType.OPEN);
        arc.setFill(Color.TRANSPARENT);
        arc.setStrokeLineCap(StrokeLineCap.BUTT);
        arc.getStyleClass().addAll("practice-summary-segment", state);
        segments.getChildren().add(arc);
        return start - length;
    }

    private HBox summaryStatus(String label, int count, String state, String countId) {
        Circle dot = new Circle(4);
        dot.getStyleClass().add("practice-summary-dot");
        Label name = UiTheme.label(label, "practice-summary-status-name");
        Label value = UiTheme.label(Integer.toString(count), "practice-summary-status-count");
        value.setId(countId);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(9, dot, name, spacer, value);
        row.getStyleClass().addAll("practice-summary-status", state);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
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

}
