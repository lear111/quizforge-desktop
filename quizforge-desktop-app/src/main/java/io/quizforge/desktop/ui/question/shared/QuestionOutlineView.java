package io.quizforge.desktop.ui.question.shared;

import io.quizforge.core.practice.QuestionBankPracticeSession;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.ui.shared.UiTheme;
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

/** A view of the existing session; numbered cells never own answer or submission state. */
public final class QuestionOutlineView extends VBox {
    private final QuestionBankPracticeSession session;
    private final String cellPrefix;
    private final Map<Integer, Button> cells = new LinkedHashMap<>();
    private final Map<String, Integer> practiceIndexes = new LinkedHashMap<>();
    private final VBox groups = new VBox();
    private List<Question> questions;
    private IntConsumer jump;

    public QuestionOutlineView(QuestionBankPracticeSession session, IntConsumer jump) {
        this(session, jump, "question-number-");
    }

    public QuestionOutlineView(QuestionBankPracticeSession session, IntConsumer jump, String cellPrefix) {
        this.session = session;
        this.cellPrefix = cellPrefix;
        this.questions = session.bank().questions();
        this.jump = jump;
        for (int i = 0; i < questions.size(); i++) practiceIndexes.put(questions.get(i).id(), i);
        setId("question-outline");
        getStyleClass().add("question-outline");
        groups.setMinWidth(0);
        groups.getStyleClass().add("question-outline-groups");
        rebuild();
        ScrollPane scroll = UiTheme.scroll(groups);
        scroll.setId("question-outline-scroll");
        scroll.getStyleClass().add("question-outline-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(UiTheme.label("题目大纲", "question-outline-title"), scroll);
    }

    private void rebuild() {
        groups.getChildren().clear();
        cells.clear();
        Map<String, List<Integer>> byType = new LinkedHashMap<>();
        for (int i = 0; i < questions.size(); i++)
            byType.computeIfAbsent(questions.get(i).type(), ignored -> new ArrayList<>()).add(i);
        byType.forEach((type, indexes) -> {
            String title = QuestionTypeCatalog.label(type);
            FlowPane numbers = new FlowPane();
            numbers.setMinWidth(0);
            numbers.getStyleClass().add("question-outline-numbers");
            for (int index : indexes) {
                Button cell = new Button(Integer.toString(index + 1));
                cell.setId(cellPrefix + (index + 1));
                cell.getStyleClass().add("question-number-cell");
                cell.setOnAction(event -> this.jump.accept(index));
                cells.put(index, cell);
                numbers.getChildren().add(cell);
            }
            VBox section = new VBox(UiTheme.label(title, "question-outline-section-title"), numbers);
            section.setId("question-outline-" + type.toLowerCase(java.util.Locale.ROOT));
            section.getStyleClass().add("question-outline-section");
            groups.getChildren().add(section);
        });
    }

    public int currentIndex() { return session.index(); }

    public void showEditor(QuestionBank bank, int selected, IntConsumer editorJump) {
        List<Question> edited = bank.questions();
        boolean changed = questions.size() != edited.size();
        for (int i = 0; !changed && i < edited.size(); i++)
            changed = !questions.get(i).id().equals(edited.get(i).id())
                    || !questions.get(i).type().equals(edited.get(i).type());
        questions = edited;
        jump = editorJump;
        if (changed) rebuild();
        refreshCells(selected);
    }

    public void refresh() {
        refreshCells(session.finished() ? -1 : session.index());
    }

    private void refreshCells(int selected) {
        setVisible(true);
        setManaged(true);
        cells.forEach((index, cell) -> {
            Integer practiceIndex = practiceIndexes.get(questions.get(index).id());
            String state = practiceIndex != null && session.state(practiceIndex) == QuestionBankPracticeSession.State.SUBMITTED
                    ? QuestionTypes.isEssay(questions.get(index).type()) ? "unscored"
                            : session.correct(practiceIndex) ? "correct" : "incorrect"
                    : practiceIndex != null && session.state(practiceIndex) == QuestionBankPracticeSession.State.SELECTED
                            ? "draft" : "unsubmitted";
            boolean current = index == selected;
            cell.getStyleClass().removeAll("unsubmitted", "draft", "correct", "incorrect", "unscored", "current");
            cell.getStyleClass().add(state);
            if (current) cell.getStyleClass().add("current");
            String description = "第 " + (index + 1) + " 题 · " + switch (state) {
                case "correct" -> "回答正确";
                case "incorrect" -> "回答错误";
                case "unscored" -> "已提交，未评分";
                case "draft" -> "草稿已暂存，未提交";
                default -> "未提交";
            } + (current ? " · 当前题目" : "");
            cell.setAccessibleText(description);
            cell.setTooltip(new Tooltip(description));
        });
    }
}
