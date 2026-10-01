package io.quizforge.desktop.ui.question.objective.choice;

import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.objective.choice.QuestionText;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.shared.EditorUi;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Choice-specific fields; session/save/source coordination stays in the owning editor. */
public final class ChoiceEditorFields {
    private ChoiceEditorFields() { }
    public static void render(QuestionEditorContext context) {
        var model=context.model();int index=context.index();var body=context.body();
        var question=model.bank().questions().get(index);
        TextArea stem = area(QuestionText.prompt(question), "qbank-question-stem", 3);
        stem.textProperty().addListener((obs, old, value) -> model.setStem(index, value));
        stem.setPromptText("输入题干…");
        body.getChildren().addAll(UiTheme.label("题干", "editor-caption"), stem,
                UiTheme.label("选项 · 勾选正确答案", "editor-caption"));
        ToggleGroup correctGroup = new ToggleGroup();
        VBox options = new VBox(8);
        for (int i = 0; i < question.choicePayload().options().size(); i++) {
            final int optionIndex = i;
            var option = question.choicePayload().options().get(i);
            TextField content = new TextField(QuestionText.option(option));
            content.setId("qbank-option-" + i);
            content.textProperty().addListener((obs, old, value) -> model.setOptionContent(index, optionIndex, value));
            HBox.setHgrow(content, Priority.ALWAYS);
            javafx.scene.control.ButtonBase correct;
            if (QuestionTypes.isSingleChoice(question.type())) {
                RadioButton radio = new RadioButton();
                radio.setToggleGroup(correctGroup);
                radio.setSelected(question.choiceAnswerSpec().correctOptionIds().contains(option.id()));
                radio.setOnAction(event -> model.setCorrect(index, option.id(), true));
                correct = radio;
            } else {
                CheckBox check = new CheckBox();
                check.setSelected(question.choiceAnswerSpec().correctOptionIds().contains(option.id()));
                check.setOnAction(event -> model.setCorrect(index, option.id(), check.isSelected()));
                correct = check;
            }
            correct.setId("qbank-correct-" + i);
            correct.setAccessibleText("将选项 " + (char) ('A' + i) + " 设为正确答案");
            Button remove = UiTheme.iconButton("close", "删除选项", () -> { });
            remove.setId("qbank-remove-option-" + i);
            remove.setOnAction(event -> { model.deleteOption(index, optionIndex); context.refresh().run(); });
            HBox row = new HBox(10, correct, UiTheme.label(String.valueOf((char) ('A' + i)), "muted"), content, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            content.setMinWidth(40);
            options.getChildren().add(row);
        }
        Button addOption = UiTheme.button("添加选项", "plus", "text-action", () -> { });
        addOption.setId("qbank-add-option");
        addOption.setOnAction(event -> { model.addOption(index); context.refresh().run(); });
        options.getChildren().add(addOption);
        body.getChildren().add(options);
            TextArea analysis = area(QuestionText.analysis(question), "qbank-analysis", 4);
            analysis.textProperty().addListener((obs, old, value) -> model.setAnalysis(index, value));
            body.getChildren().addAll(UiTheme.label("解析", "editor-caption"), analysis);
    }
    private static TextArea area(String value,String id,int rows){return EditorUi.content(value,id,false);}
}
