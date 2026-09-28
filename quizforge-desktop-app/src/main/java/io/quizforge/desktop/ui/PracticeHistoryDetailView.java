package io.quizforge.desktop.ui;

import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.core.workspace.WorkspaceId;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** One archived question and one submitted attempt at a time; all navigation stays in memory. */
final class PracticeHistoryDetailView extends BorderPane {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final PracticeHistoryDetail detail;
    private final HistoryQuestionOutlineView outline;
    private final VBox question = new VBox(18);
    private final ScrollPane scroll;
    private final WorkspaceId workspace;
    private final HistorySourceNavigationAdapter sources;
    private HistorySourceListView sourceList;
    private int questionIndex;
    private int attemptIndex;

    PracticeHistoryDetailView(PracticeHistoryDetail detail, Runnable back, WorkspaceId workspace,
            HistorySourceNavigationAdapter sources) {
        this.detail = detail;
        this.workspace = workspace;
        this.sources = sources;
        setId("practice-history-detail");
        getStyleClass().add("history-detail");
        Label title = UiTheme.label(detail.bankTitle() + " · "
                + DATE.format(LocalDateTime.ofInstant(detail.archivedAt(), ZoneId.systemDefault())), "page-title");
        HBox.setHgrow(title, Priority.ALWAYS);
        Button returnButton = UiTheme.button("返回历史记录", "arrow-left", "", back);
        returnButton.setId("history-detail-back");
        HBox heading = new HBox(12, title, returnButton);
        heading.getStyleClass().add("history-heading");
        setTop(heading);
        question.setId("history-detail-question");
        question.getStyleClass().add("history-question");
        scroll = UiTheme.scroll(question);
        scroll.setFitToWidth(true);
        setCenter(scroll);
        outline = new HistoryQuestionOutlineView(detail, this::showQuestion);
        setRight(outline);
        if (detail.questions().isEmpty()) render();
        else showQuestion(0);
    }

    private void showQuestion(int index) {
        if (index < 0 || index >= detail.questions().size()) return;
        questionIndex = index;
        attemptIndex = detail.questions().get(index).attempts().size() - 1;
        render();
    }

    private void showAttempt(int index) {
        if (index < 0 || index >= detail.questions().get(questionIndex).attempts().size()) return;
        attemptIndex = index;
        render();
    }

    private void render() {
        outline.refresh(detail, questionIndex);
        question.getChildren().clear();
        if (detail.questions().isEmpty()) {
            question.getChildren().add(UiTheme.label("本轮没有题目", "muted"));
            return;
        }
        var row = detail.questions().get(questionIndex);
        Label position = UiTheme.label("第 " + (questionIndex + 1) + " / " + detail.questions().size() + " 题", "muted");
        position.setId("history-question-position");
        question.getChildren().addAll(position, UiTheme.label(row.stem(), "question-stem"));
        boolean unfinished = row.finalState() != PracticeSessionQuestion.State.SUBMITTED;
        Label finalState = UiTheme.label(unfinished ? "本轮最终状态：未完成" : "本轮最终状态：已提交", "muted");
        finalState.setId("history-final-state");
        question.getChildren().add(finalState);

        PracticeHistoryDetail.Attempt attempt = attemptIndex < 0 ? null : row.attempts().get(attemptIndex);
        Set<String> selected = attempt == null ? Set.of() : optionIds(attempt.answer());
        VBox options = new VBox(8);
        options.setId("history-options");
        for (int index = 0; index < row.options().size(); index++) {
            var option = row.options().get(index);
            Label label = UiTheme.label((char) ('A' + index) + "   " + option.content(), "history-option");
            label.setId("history-option-" + index);
            if (selected.contains(option.id())) label.getStyleClass().add("selected");
            if (row.correctOptionIds().contains(option.id())) label.getStyleClass().add("correct-option");
            options.getChildren().add(label);
        }
        question.getChildren().add(options);

        if (row.draftAnswer() != null) {
            Label draft = UiTheme.label("未提交选择：" + labels(row, optionIds(row.draftAnswer())), "history-draft");
            draft.setId("history-draft");
            question.getChildren().add(draft);
        }
        if (attempt == null) {
            Label noAttempt = UiTheme.label("本轮未提交", "muted");
            noAttempt.setId("history-no-attempt");
            question.getChildren().add(noAttempt);
        } else {
            Label attemptHeader = UiTheme.label("第 " + (attemptIndex + 1) + " / " + row.attempts().size()
                    + " 次作答 · " + mode(attempt.mode()), "history-attempt-header");
            attemptHeader.setId("history-attempt-position");
            Label result = UiTheme.label(result(attempt.result()), "history-attempt-result");
            result.setId("history-attempt-result");
            result.getStyleClass().add(switch (attempt.result()) {
                case CORRECT -> "correct";
                case INCORRECT -> "incorrect";
                case UNSCORED -> "unscored";
            });
            question.getChildren().addAll(attemptHeader, result,
                    UiTheme.label("你的答案：" + labels(row, selected), "history-answer"));
            Button previousAttempt = new Button("↑ 上一次作答");
            previousAttempt.setId("history-previous-attempt");
            previousAttempt.setOnAction(event -> showAttempt(attemptIndex - 1));
            previousAttempt.setDisable(attemptIndex == 0);
            Button nextAttempt = new Button("↓ 下一次作答");
            nextAttempt.setId("history-next-attempt");
            nextAttempt.setOnAction(event -> showAttempt(attemptIndex + 1));
            nextAttempt.setDisable(attemptIndex == row.attempts().size() - 1);
            HBox attempts = new HBox(16, previousAttempt, nextAttempt);
            attempts.getStyleClass().add("history-attempt-navigation");
            question.getChildren().add(attempts);
        }
        question.getChildren().add(UiTheme.label("正确答案：" + labels(row, Set.copyOf(row.correctOptionIds())),
                "history-answer"));
        if (!row.analysis().isBlank()) question.getChildren().add(UiTheme.label(row.analysis(), "preview-paragraph"));
        sourceList = new HistorySourceListView(workspace, row.sourceRefs(), sources);
        question.getChildren().add(sourceList);

        Button previous = UiTheme.button("上一题", "arrow-left", "", () -> showQuestion(questionIndex - 1));
        previous.setId("history-previous-question");
        previous.setDisable(questionIndex == 0);
        Button next = UiTheme.button("下一题", "arrow", "", () -> showQuestion(questionIndex + 1));
        next.setId("history-next-question");
        next.setDisable(questionIndex == detail.questions().size() - 1);
        HBox navigation = new HBox(52, previous, next);
        navigation.setId("history-question-navigation");
        navigation.setAlignment(Pos.CENTER);
        question.getChildren().add(navigation);
        scroll.setVvalue(0);
    }

    void refreshSources() { if (sourceList != null) sourceList.refresh(); }

    private static Set<String> optionIds(PracticePayload payload) {
        if (!(payload.value() instanceof List<?> values)) throw new IllegalStateException("Invalid history choice answer");
        Set<String> ids = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String id)) throw new IllegalStateException("Invalid history choice answer");
            ids.add(id);
        }
        return Set.copyOf(ids);
    }

    private static String labels(PracticeHistoryDetail.Question question, Set<String> ids) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < question.options().size(); index++) {
            if (!ids.contains(question.options().get(index).id())) continue;
            if (!text.isEmpty()) text.append("、");
            text.append((char) ('A' + index));
        }
        return text.isEmpty() ? "—" : text.toString();
    }

    private static String mode(QuestionAttempt.Mode mode) {
        return switch (mode) {
            case INITIAL -> "首次作答";
            case RETRY -> "重新答题";
            case REVISION -> "修改答案";
        };
    }

    private static String result(QuestionAttempt.Result result) {
        return switch (result) {
            case CORRECT -> "正确";
            case INCORRECT -> "错误";
            case UNSCORED -> "未评分";
        };
    }
}
