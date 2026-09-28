package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankPracticeSession;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import java.util.List;
import java.util.function.Function;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
        if (session.finished()) { result(); return; }
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
        }
        Button previous = UiTheme.iconButton("arrow-left", "上一题", () -> { });
        previous.setId("previous-question");
        previous.setDisable(session.index() == 0);
        previous.setOnAction(event -> command(runtime::previous));
        boolean last = session.index() == session.bank().questions().size() - 1;
        Button next = UiTheme.iconButton(last ? "finish" : "arrow", last ? "完成练习" : "下一题", () -> { });
        next.setId("next-question");
        next.setDisable(session.index() == session.bank().questions().size() - 1
                && !session.canFinish());
        next.setOnAction(event -> command(runtime::next));
        HBox navigation = new HBox(52, previous, submit, next);
        navigation.setId("practice-navigation");
        navigation.getStyleClass().add("practice-navigation");
        navigation.setAlignment(Pos.CENTER);
        getChildren().add(navigation);
    }

    void refreshSources() { if (sourceRows != null) sourceRows.refresh(); }

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

    private void result() {
        var result = session.result();
        VBox page = new VBox(12, UiTheme.label("本次练习完成", "section-title"),
                UiTheme.label(result.correct() + " / " + result.total(), "practice-result-score"),
                UiTheme.label("正确率：" + result.accuracyPercent() + "%", "preview-paragraph"),
                UiTheme.label("正确：" + result.correct(), "preview-paragraph"),
                UiTheme.label("错误：" + result.incorrect(), "preview-paragraph"));
        page.setId("practice-result");
        Button restart = UiTheme.button("重新开始", "refresh", "primary-button", () -> { });
        restart.setId("practice-restart");
        restart.setDisable(true);
        restart.setTooltip(new javafx.scene.control.Tooltip("持久化练习的重新开始将在后续步骤提供"));
        page.getChildren().add(restart);
        getChildren().add(page);
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
