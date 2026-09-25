package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionBankFile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** A transient practice session: one question at a time, no persisted records. */
final class QuestionBankPracticeView extends VBox {
    private final QuestionBankFile bank;
    private final Map<Integer, Set<String>> selections = new HashMap<>();
    private final Set<Integer> submitted = new HashSet<>();
    private int index;

    QuestionBankPracticeView(QuestionBankFile bank) {
        this.bank = bank;
        setId("question-practice");
        getStyleClass().add("practice-view");
        setSpacing(20);
        setMaxWidth(760);
        render();
    }

    private void render() {
        getChildren().clear();
        if (bank.questions().isEmpty()) {
            getChildren().add(UiTheme.quietState("该题库暂无题目", "开始编辑，或使用 AI 生成"));
            return;
        }
        var question = bank.questions().get(index);
        Set<String> selected = selections.computeIfAbsent(index, ignored -> new HashSet<>());
        boolean answered = submitted.contains(index);
        var position = UiTheme.label("Question " + (index + 1) + " / " + bank.questions().size(), "muted");
        position.setId("question-position");
        ProgressBar progress = new ProgressBar((index + 1.0) / bank.questions().size());
        progress.setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(position, progress, UiTheme.label(question.stem(), "question-stem"));
        Button submit = new Button("提交答案");
        submit.setId("submit-answer");
        submit.setDisable(selected.isEmpty() || answered);
        ToggleGroup group = new ToggleGroup();
        boolean single = "SINGLE_CHOICE".equals(question.type());
        VBox options = new VBox(8);
        for (int i = 0; i < question.data().options().size(); i++) {
            var option = question.data().options().get(i);
            String text = (char) ('A' + i) + "   " + option.content();
            if (single) {
                RadioButton choice = new RadioButton(text);
                choice.setToggleGroup(group);
                choice.setSelected(selected.contains(option.id()));
                choice.setOnAction(event -> { selected.clear(); selected.add(option.id()); submit.setDisable(false); });
                choice.setWrapText(true);
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.setDisable(answered);
                choice.setId("option-" + i);
                options.getChildren().add(choice);
            } else {
                CheckBox choice = new CheckBox(text);
                choice.setSelected(selected.contains(option.id()));
                choice.setOnAction(event -> {
                    if (choice.isSelected()) selected.add(option.id()); else selected.remove(option.id());
                    submit.setDisable(selected.isEmpty());
                });
                choice.setWrapText(true);
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.setDisable(answered);
                choice.setId("option-" + i);
                options.getChildren().add(choice);
            }
        }
        getChildren().add(options);
        submit.setOnAction(event -> { submitted.add(index); render(); });
        getChildren().add(submit);
        if (answered) {
            boolean correct = selected.equals(new HashSet<>(question.data().correctOptionIds()));
            VBox feedback = new VBox(12, UiTheme.label(correct ? "回答正确" : "回答错误", correct ? "answer" : "incorrect"),
                    UiTheme.label("正确答案：" + answerLabels(question), "field-label"),
                    UiTheme.label(question.analysis(), "preview-paragraph"));
            feedback.setId("answer-feedback");
            feedback.getStyleClass().add("practice-feedback");
            for (var ref : question.sourceRefs()) {
                feedback.getChildren().add(UiTheme.label("来源：" + ref.documentTitle() + " / " + ref.sectionTitle(), "muted"));
            }
            getChildren().add(feedback);
        }
        Button previous = new Button("上一题");
        previous.setId("previous-question");
        previous.setDisable(index == 0);
        previous.setOnAction(event -> { index--; render(); });
        Button next = new Button("下一题");
        next.setId("next-question");
        next.setDisable(index == bank.questions().size() - 1);
        next.setOnAction(event -> { index++; render(); });
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox navigation = new HBox(previous, space, next);
        navigation.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(navigation);
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
