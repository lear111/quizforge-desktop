package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankPracticeSession;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.PracticeSummary;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** One question at a time; answer state never alters the .qbank file. */
final class QuestionBankPracticeView extends VBox {
    private final PersistentPracticeRuntime runtime;
    private final QuestionBankPracticeSession session;
    private final Function<List<QuestionBankFile.SourceRef>, QuestionSourceListView> sources;
    private final QuestionOutlineView outline;
    private QuestionSourceListView sourceRows;
    private BooleanSupplier restartConfirmation = this::confirmRestart;

    QuestionBankPracticeView(PersistentPracticeRuntime runtime,
            Function<List<QuestionBankFile.SourceRef>, QuestionSourceListView> sources) {
        this.runtime = runtime;
        session = runtime.session();
        this.sources = sources;
        outline = new QuestionOutlineView(session, this::jumpToQuestion);
        setId("question-practice");
        getStyleClass().add("practice-view");
        setSpacing(20);
        setMaxWidth(760);
        setMinWidth(0);
        render();
    }

    private void render() {
        outline.refresh();
        sourceRows = null;
        getChildren().clear();
        getChildren().add(UiTheme.label(session.bank().title(), "section-title"));
        if (session.finished()) { summary(); return; }
        QuestionBankFile.Entry question = session.current();
        var state = session.state();
        var position = UiTheme.label("Question " + (session.index() + 1) + " / "
                + session.bank().questions().size(), "muted");
        position.setId("question-position");
        ProgressBar progress = new ProgressBar((session.index() + 1.0) / session.bank().questions().size());
        progress.setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(position, progress, UiTheme.label(question.stem(), "question-stem"));
        Button submit = UiTheme.iconButton("check", "确认答案", () -> { });
        submit.getStyleClass().add("practice-submit");
        submit.setId("submit-answer");
        submit.setDisable(state != QuestionBankPracticeSession.State.SELECTED);
        ToggleGroup group = new ToggleGroup();
        VBox options = new VBox(8);
        boolean single = "SINGLE_CHOICE".equals(question.type());
        for (int i = 0; i < question.data().options().size(); i++) {
            var option = question.data().options().get(i);
            String label = (char) ('A' + i) + "   " + option.content();
            if (single) {
                RadioButton choice = new RadioButton(label);
                choice.setToggleGroup(group);
                choice.setSelected(session.selected().contains(option.id()));
                choice.setOnAction(event -> command(() -> runtime.select(option.id())));
                choice.setWrapText(true);
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.setDisable(state == QuestionBankPracticeSession.State.SUBMITTED);
                choice.setId("option-" + i);
                options.getChildren().add(choice);
            } else {
                CheckBox choice = new CheckBox(label);
                choice.setSelected(session.selected().contains(option.id()));
                choice.setOnAction(event -> {
                    command(() -> runtime.select(option.id()));
                });
                choice.setWrapText(true);
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.setDisable(state == QuestionBankPracticeSession.State.SUBMITTED);
                choice.setId("option-" + i);
                options.getChildren().add(choice);
            }
        }
        getChildren().add(options);
        submit.setOnAction(event -> {
            submit.setDisable(true);
            command(runtime::submit);
        });
        if (state == QuestionBankPracticeSession.State.SUBMITTED) {
            VBox feedback = new VBox(12, UiTheme.label(session.correct() ? "回答正确" : "回答错误",
                    session.correct() ? "answer" : "incorrect"),
                    UiTheme.label("正确答案：" + answerLabels(question), "field-label"),
                    UiTheme.label(question.analysis(), "preview-paragraph"));
            feedback.setId("answer-feedback");
            feedback.getStyleClass().add("practice-feedback");
            if (!question.sourceRefs().isEmpty()) {
                sourceRows = sources.apply(question.sourceRefs());
                feedback.getChildren().addAll(UiTheme.label("来源", "editor-caption"), sourceRows);
            }
            getChildren().add(feedback);
            Button retry = UiTheme.button("重新答题", "refresh", "", () -> command(runtime::retry));
            retry.setId("practice-retry");
            getChildren().add(retry);
        }
        Button previous = UiTheme.iconButton("arrow-left", "上一题", () -> { });
        previous.setId("previous-question");
        previous.setDisable(session.index() == 0);
        previous.setOnAction(event -> command(runtime::previous));
        boolean last = session.index() == session.bank().questions().size() - 1;
        Button next = UiTheme.iconButton("arrow", last ? "查看本次练习" : "下一题", () -> { });
        next.setId("next-question");
        next.setOnAction(event -> command(runtime::next));
        HBox navigation = new HBox(52, previous, submit, next);
        navigation.setId("practice-navigation");
        navigation.getStyleClass().add("practice-navigation");
        navigation.setAlignment(Pos.CENTER);
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
        PracticeSummary summary = runtime.summary();
        VBox page = new VBox(12, UiTheme.label("本次练习", "section-title"),
                UiTheme.label("正确率：" + (summary.accuracyPercent().isPresent()
                        ? summary.accuracyPercent().getAsInt() + "%" : "—"), "practice-result-score"),
                UiTheme.label("已提交：" + summary.submittedCount() + " / " + summary.totalCount(), "preview-paragraph"),
                UiTheme.label("正确：" + summary.correctCount(), "preview-paragraph"),
                UiTheme.label("错误：" + summary.incorrectCount(), "preview-paragraph"),
                UiTheme.label("未完成：" + summary.unfinishedCount(), "preview-paragraph"));
        page.setId("practice-summary");
        Button previous = UiTheme.button("上一题", "arrow-left", "", () -> command(runtime::previous));
        previous.setId("summary-previous");
        Button restart = UiTheme.button("重新练习", "refresh", "primary-button", () -> {
            if (restartConfirmation.getAsBoolean()) command(runtime::restart);
        });
        restart.setId("practice-restart");
        page.getChildren().addAll(previous, restart);
        getChildren().add(page);
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

    private String answerLabels(QuestionBankFile.Entry question) {
        StringBuilder labels = new StringBuilder();
        for (int i = 0; i < question.data().options().size(); i++) {
            if (!question.data().correctOptionIds().contains(question.data().options().get(i).id())) continue;
            if (!labels.isEmpty()) labels.append(", ");
            labels.append((char) ('A' + i));
        }
        return labels.toString();
    }
}
