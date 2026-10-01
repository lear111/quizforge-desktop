package io.quizforge.desktop.ui.question.objective.choice;

import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.ui.question.shared.QuestionTypeCatalog;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Question-type rendering only. No session commands, history navigation or persistence. */
public final class ChoiceCardView extends VBox {
    public enum Rendering { ANSWERING, RESULT, READ_ONLY }

    private final ChoicePresentation question;
    private final String prefix;
    private final Label position;
    private ChoiceResultPresentation result;
    private Label resultLabel;

    private ChoiceCardView(ChoicePresentation question, int index, int total,
            String prefix, Rendering rendering) {
        this.question = question;
        this.prefix = prefix;
        setId(prefix + "question-card");
        getStyleClass().addAll("question-card", "shared-question-card");
        getProperties().put("quizforge.questionRendering", rendering);
        setMinWidth(0);
        setMaxHeight(Region.USE_PREF_SIZE);
        String typeName = QuestionTypeCatalog.label(question.type());
        Label type = UiTheme.label(typeName, "question-type-badge");
        type.setWrapText(false);
        position = UiTheme.label("第 " + (index + 1) + " / " + total + " 题", "question-progress");
        position.setId(prefix + "question-position");
        position.setWrapText(false);
        position.setMinWidth(0);
        position.setTooltip(new Tooltip(position.getText()));
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox heading = new HBox(12, type, space, position);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label stem = UiTheme.label(question.stem(), "question-stem");
        stem.setMaxWidth(Double.MAX_VALUE);
        getChildren().addAll(heading, stem);
    }

    public static ChoiceCardView answering(ChoicePresentation question, int index, int total,
            Set<String> selected, Consumer<String> select) {
        var card = new ChoiceCardView(question, index, total, "", Rendering.ANSWERING);
        card.options(selected, true, select, false);
        return card;
    }

    public static ChoiceCardView result(ChoiceResultPresentation result, int index, int total,
            String prefix, Node sources) {
        var card = new ChoiceCardView(result.question(), index, total, prefix, Rendering.RESULT);
        card.result = result;
        card.options(result.userAnswer(), false, ignored -> { }, result.showCorrectAnswer());
        VBox feedback = card.feedback();
        String state = result.result().name().toLowerCase(java.util.Locale.ROOT);
        card.resultLabel = UiTheme.label(switch (result.result()) {
            case CORRECT -> "回答正确";
            case INCORRECT -> "回答错误";
            case UNSCORED -> "未评分";
        }, "question-result-" + state);
        card.resultLabel.setId(prefix + "question-result");
        feedback.getChildren().addAll(card.resultLabel,
                UiTheme.label("你的答案：" + card.question.answerLabels(result.userAnswer()), "question-user-answer"));
        card.details(feedback, result.showCorrectAnswer(), result.showAnalysis(), result.showSources() ? sources : null);
        card.getChildren().add(feedback);
        return card;
    }

    /** Content/draft preview without creating a result or a fictitious Attempt. */
    public static ChoiceCardView readOnly(ChoicePresentation question, int index, int total,
            String prefix, Set<String> draft, Node sources) {
        var card = new ChoiceCardView(question, index, total, prefix, Rendering.READ_ONLY);
        card.options(draft, false, ignored -> { }, false);
        VBox details = card.feedback();
        card.details(details, true, true, sources);
        card.getChildren().add(details);
        return card;
    }

    private void options(Set<String> selected, boolean interactive, Consumer<String> select,
            boolean showCorrectAnswer) {
        VBox options = new VBox(8);
        options.setId(prefix + "options");
        options.getStyleClass().add("question-options");
        ToggleGroup group = new ToggleGroup();
        for (int index = 0; index < question.options().size(); index++) {
            var option = question.options().get(index);
            String text = (char) ('A' + index) + "   " + option.content();
            boolean chosen = selected.contains(option.id());
            if (!io.quizforge.core.question.type.QuestionTypes.isChoice(question.type()))
                throw new IllegalArgumentException("Unsupported question type: " + question.type());
            ButtonBase choice;
            if (io.quizforge.core.question.type.QuestionTypes.isSingleChoice(question.type())) {
                RadioButton radio = new RadioButton(text);
                radio.setToggleGroup(group);
                radio.setSelected(chosen);
                choice = radio;
            } else {
                CheckBox check = new CheckBox(text);
                check.setSelected(chosen);
                choice = check;
            }
            choice.setId(prefix + "option-" + index);
            choice.setWrapText(true);
            choice.setMaxWidth(Double.MAX_VALUE);
            choice.setDisable(!interactive);
            choice.getStyleClass().add("question-option");
            String description = text;
            if (chosen) {
                choice.getStyleClass().add("selected");
                description += " · 你的选择";
            }
            if (showCorrectAnswer) {
                if (question.correctAnswer().contains(option.id())) {
                    choice.getStyleClass().add("correct-option");
                    description += " · 正确答案";
                } else if (chosen) {
                    choice.getStyleClass().add("incorrect-option");
                    description += " · 错误选择";
                }
            }
            choice.setAccessibleText(description);
            choice.setTooltip(new Tooltip(description));
            if (interactive) choice.setOnAction(event -> select.accept(option.id()));
            options.getChildren().add(choice);
        }
        getChildren().add(options);
    }

    private VBox feedback() {
        VBox box = new VBox(12);
        box.setId(prefix + "answer-feedback");
        box.getStyleClass().add("question-result");
        return box;
    }

    private void details(VBox box, boolean correctAnswer, boolean analysis, Node sources) {
        if (correctAnswer && !QuestionTypes.isEssay(question.type())) box.getChildren().add(UiTheme.label(
                "正确答案：" + question.answerLabels(question.correctAnswer()), "field-label"));
        if (analysis && !question.analysis().isBlank()) {
            Label explanation = UiTheme.label(question.analysis(), "question-analysis");
            explanation.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(explanation);
        }
        if (sources != null) {
            Label title = UiTheme.label("来源", "editor-caption");
            title.managedProperty().bind(sources.managedProperty());
            title.visibleProperty().bind(sources.visibleProperty());
            box.getChildren().addAll(title, sources);
        }
    }

    public ChoicePresentation presentation() { return question; }
    public Optional<ChoiceResultPresentation> resultPresentation() { return Optional.ofNullable(result); }
    public Label resultLabel() { return resultLabel; }
    public Label position() { return position; }
}
