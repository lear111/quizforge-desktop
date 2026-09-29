package io.quizforge.desktop.ui;

import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticeSessionQuestion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Read-only numbered outline, based only on archived question snapshots. */
final class HistoryQuestionOutlineView extends VBox {
    private final Map<Integer, Button> cells = new LinkedHashMap<>();

    HistoryQuestionOutlineView(PracticeHistoryDetail detail, IntConsumer jump) {
        setId("history-question-outline");
        getStyleClass().add("question-outline");
        VBox groups = new VBox();
        groups.setMinWidth(0);
        groups.getStyleClass().add("question-outline-groups");
        Map<String, List<Integer>> byType = new LinkedHashMap<>();
        for (int index = 0; index < detail.questions().size(); index++)
            byType.computeIfAbsent(detail.questions().get(index).questionType(), ignored -> new ArrayList<>()).add(index);
        byType.forEach((type, indexes) -> {
            String title = switch (type) {
                case "SINGLE_CHOICE" -> "单选题";
                case "MULTIPLE_CHOICE" -> "多选题";
                default -> throw new IllegalArgumentException("Unsupported history question type: " + type);
            };
            FlowPane numbers = new FlowPane();
            numbers.setMinWidth(0);
            numbers.getStyleClass().add("question-outline-numbers");
            for (int index : indexes) {
                Button cell = new Button(Integer.toString(index + 1));
                cell.setId("history-question-number-" + (index + 1));
                cell.getStyleClass().add("question-number-cell");
                cell.setOnAction(event -> jump.accept(index));
                cells.put(index, cell);
                numbers.getChildren().add(cell);
            }
            VBox section = new VBox(UiTheme.label(title, "question-outline-section-title"), numbers);
            section.getStyleClass().add("question-outline-section");
            groups.getChildren().add(section);
        });
        ScrollPane scroll = UiTheme.scroll(groups);
        scroll.setId("history-question-outline-scroll");
        scroll.getStyleClass().add("question-outline-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(UiTheme.label("题目大纲", "question-outline-title"), scroll);
        refresh(detail, 0);
    }

    void refresh(PracticeHistoryDetail detail, int currentIndex) {
        cells.forEach((index, cell) -> {
            var question = detail.questions().get(index);
            String state = "unsubmitted";
            if (question.finalState() == PracticeSessionQuestion.State.SUBMITTED && !question.attempts().isEmpty()) {
                state = switch (question.attempts().getLast().result()) {
                    case CORRECT -> "correct";
                    case INCORRECT -> "incorrect";
                    case UNSCORED -> "unsubmitted";
                };
            }
            cell.getStyleClass().removeAll("unsubmitted", "correct", "incorrect", "current");
            cell.getStyleClass().add(state);
            if (index == currentIndex) cell.getStyleClass().add("current");
            String description = "第 " + (index + 1) + " 题 · " + switch (state) {
                case "correct" -> "回答正确";
                case "incorrect" -> "回答错误";
                default -> "未完成";
            } + (index == currentIndex ? " · 当前题目" : "");
            cell.setAccessibleText(description);
            cell.setTooltip(new Tooltip(description));
        });
    }
}
