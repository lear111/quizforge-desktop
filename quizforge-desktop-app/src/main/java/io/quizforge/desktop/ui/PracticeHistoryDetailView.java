package io.quizforge.desktop.ui;

import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.core.workspace.WorkspaceId;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** One archived question and one submitted attempt at a time; all navigation stays in memory. */
final class PracticeHistoryDetailView extends BorderPane {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final PracticeHistoryDetail detail;
    private final HistoryQuestionOutlineView outline;
    private final VBox question = new VBox(18);
    private final ScrollPane scroll;
    private final VBox headings = new VBox();
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
        returnButton.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        title.setMinWidth(0);
        HBox heading = new HBox(12, returnButton, title);
        heading.getStyleClass().add("history-heading");
        headings.getChildren().add(heading);
        question.setId("history-detail-question");
        QuestionCardLayout.configure(question);
        scroll = QuestionCardLayout.scroll(question);
        scroll.setId("history-question-scroll");
        outline = new HistoryQuestionOutlineView(detail, this::showQuestion);
        BorderPane readerColumn = new BorderPane(scroll);
        readerColumn.setMinWidth(320);
        readerColumn.setTop(headings);
        SplitPane layout = new SplitPane(readerColumn, outline);
        layout.getStyleClass().add("history-browse-layout");
        layout.setMinWidth(0);
        SplitPane.setResizableWithParent(outline, false);
        layout.widthProperty().addListener(new javafx.beans.value.ChangeListener<Number>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends Number> value,
                    Number before, Number width) {
                if (width.doubleValue() <= 500) return;
                layout.setDividerPositions((width.doubleValue() - 260) / width.doubleValue());
                layout.widthProperty().removeListener(this);
            }
        });
        setCenter(layout);
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
        sourceList = null;
        if (detail.questions().isEmpty()) {
            question.getChildren().add(UiTheme.label("本轮没有题目", "muted"));
            return;
        }
        var row = detail.questions().get(questionIndex);
        var content = QuestionPresentationMapper.history(row);
        VBox context = new VBox(8);
        context.getStyleClass().add("history-question-context");
        boolean unfinished = row.finalState() != PracticeSessionQuestion.State.SUBMITTED;
        Label finalState = UiTheme.label(unfinished ? "本轮最终状态：未完成" : "本轮最终状态：已完成", "muted");
        finalState.setId("history-final-state");
        context.getChildren().add(finalState);

        if (row.draftAnswer() != null) {
            Label draft = UiTheme.label("未提交选择：" + content.answerLabels(
                    QuestionPresentationMapper.answerIds(row.draftAnswer())), "history-draft");
            draft.setId("history-draft");
            context.getChildren().add(draft);
        }
        sourceList = new HistorySourceListView(workspace, row.sourceRefs(), sources);
        QuestionCardView card;
        if (attemptIndex < 0) {
            Label noAttempt = UiTheme.label("本轮未提交", "muted");
            noAttempt.setId("history-no-attempt");
            context.getChildren().add(noAttempt);
            card = QuestionCardView.readOnly(content, questionIndex, detail.questions().size(), "history-",
                    row.draftAnswer() == null ? Set.of() : QuestionPresentationMapper.answerIds(row.draftAnswer()), sourceList);
        } else {
            var attempt = row.attempts().get(attemptIndex);
            Label attemptHeader = UiTheme.label("第 " + (attemptIndex + 1) + " / " + row.attempts().size()
                    + " 次作答 · " + mode(attempt.mode()), "history-attempt-header");
            attemptHeader.setId("history-attempt-position");
            context.getChildren().add(attemptHeader);
            Button previousAttempt = new Button("↑ 上一次作答");
            previousAttempt.setId("history-previous-attempt");
            previousAttempt.setOnAction(event -> showAttempt(attemptIndex - 1));
            previousAttempt.setDisable(attemptIndex == 0);
            Button nextAttempt = new Button("↓ 下一次作答");
            nextAttempt.setId("history-next-attempt");
            nextAttempt.setOnAction(event -> showAttempt(attemptIndex + 1));
            nextAttempt.setDisable(attemptIndex == row.attempts().size() - 1);
            FlowPane attempts = new FlowPane(16, 8, previousAttempt, nextAttempt);
            attempts.getStyleClass().add("history-attempt-navigation");
            context.getChildren().add(attempts);
            card = QuestionCardView.result(QuestionPresentationMapper.historyResult(row, attempt),
                    questionIndex, detail.questions().size(), "history-", sourceList);
            card.resultLabel().setId("history-attempt-result");
        }
        Button previous = QuestionCardLayout.navigation("arrow-left", "上一题", () -> showQuestion(questionIndex - 1));
        previous.setId("history-previous-question");
        previous.setDisable(questionIndex == 0);
        Button next = QuestionCardLayout.navigation("arrow", "下一题", () -> showQuestion(questionIndex + 1));
        next.setId("history-next-question");
        next.setDisable(questionIndex == detail.questions().size() - 1);
        HBox navigation = QuestionCardLayout.row(previous, card, next);
        navigation.setId("history-question-navigation");
        question.getChildren().addAll(context, navigation);
        scroll.setVvalue(0);
    }

    void refreshSources() { if (sourceList != null) sourceList.refresh(); }

    HistoryQuestionOutlineView outline() { return outline; }

    void setHeader(Node header) {
        if (headings.getChildren().size() > 1) headings.getChildren().removeFirst();
        if (header != null) headings.getChildren().addFirst(header);
    }

    private static String mode(QuestionAttempt.Mode mode) {
        return switch (mode) {
            case INITIAL -> "首次作答";
            case RETRY -> "重新答题";
            case REVISION -> "修改答案";
        };
    }

}
