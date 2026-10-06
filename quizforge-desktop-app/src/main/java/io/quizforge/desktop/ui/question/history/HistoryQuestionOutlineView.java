package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.MatchingPracticeAnswer;
import io.quizforge.core.practice.TranslationPracticeAnswer;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.ui.question.shared.QuestionTypeCatalog;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Read-only numbered outline, based only on archived question snapshots. */
final class HistoryQuestionOutlineView extends VBox {
    private record Cell(int questionIndex, int itemNumber, Set<String> optionIds,
            Set<String> correctOptionIds, String blankId, String matchingCorrectOptionId, boolean locked, Button button) { }
    private final List<Cell> cells = new ArrayList<>();

    HistoryQuestionOutlineView(PracticeHistoryDetail detail, IntConsumer jump) {
        this(detail, (index, item) -> jump.accept(index));
    }

    HistoryQuestionOutlineView(PracticeHistoryDetail detail, BiConsumer<Integer,Integer> jump) {
        setId("history-question-outline");
        getStyleClass().add("question-outline");
        VBox groups = new VBox();
        groups.setMinWidth(0);
        groups.getStyleClass().add("question-outline-groups");
        List<List<Integer>> consecutiveGroups = new ArrayList<>();
        for (int index = 0; index < detail.questions().size(); index++) {
            if (index == 0 || !detail.questions().get(index).questionType()
                    .equals(detail.questions().get(index - 1).questionType()))
                consecutiveGroups.add(new ArrayList<>());
            consecutiveGroups.getLast().add(index);
        }
        int number = 1;
        for (var indexes : consecutiveGroups) {
            String type = detail.questions().get(indexes.getFirst()).questionType();
            String title = QuestionTypeCatalog.label(type);
            FlowPane numbers = new FlowPane();
            numbers.setMinWidth(0);
            numbers.getStyleClass().add("question-outline-numbers");
            for (int index : indexes) {
                var row = detail.questions().get(index);
                var snapshot = row.contentSnapshot() == null ? java.util.Map.<String,Object>of()
                        : QuestionContentData.map(row.contentSnapshot().value());
                boolean reading = QuestionTypes.isReading(type), cloze = QuestionTypes.isCloze(type), matching = QuestionTypes.isMatching(type), translation=QuestionTypes.isTranslation(type);
                String key = translation ? "translation" : matching ? "matching" : reading ? "reading" : "cloze";
                var stored = snapshot.get(key + "Presentation");
                if (!(stored instanceof java.util.Map<?,?>)) stored = snapshot.get(key);
                var data = stored instanceof java.util.Map<?,?> ? QuestionContentData.map(stored) : java.util.Map.<String,Object>of();
                boolean extension = snapshot.get("extensionPresentation") instanceof java.util.Map<?,?>;
                if (extension) data = QuestionContentData.map(snapshot.get("extensionPresentation"));
                var parts = data.get(reading || translation ? "items" : "blanks");
                if (extension) parts = data.get("targets");
                var items = (extension || reading || cloze || matching || translation) && parts instanceof List<?> entries && !entries.isEmpty() ? entries : List.of(java.util.Map.of());
                for (var value : items) {
                    var item = QuestionContentData.map(value);
                    if (matching && Boolean.TRUE.equals(item.get("locked"))) continue;
                    if (extension && (Boolean.TRUE.equals(item.get("locked")) || Boolean.FALSE.equals(item.get("gradable")))) continue;
                    int itemNumber = item.get("number") instanceof Number n ? n.intValue() : 0;
                    Set<String> options = item.get("options") instanceof List<?> entries
                            ? entries.stream().map(QuestionContentData::map).map(o -> (String)o.get("id")).collect(java.util.stream.Collectors.toSet()) : Set.of();
                    var correct = new java.util.HashSet<>(row.correctOptionIds());
                    if(data.get("answers") instanceof List<?> answers)for(var answer : answers){
                        var fields=QuestionContentData.map(answer);
                        if(fields.get("correctOptionId") instanceof String id)correct.add(id);
                    }
                    Button cell = new Button(Integer.toString(number));
                    cell.setId("history-question-number-" + number++);
                    cell.getStyleClass().add("question-number-cell");
                    cell.setOnAction(event -> jump.accept(index, itemNumber));
                    String blankId = matching || translation ? (String)item.get("id") : null;
                    String matchingCorrect = null;
                    if(matching && data.get("answers") instanceof List<?> answers)for(var answer : answers){
                        var fields=QuestionContentData.map(answer);
                        if(java.util.Objects.equals(blankId,fields.get("blankId")))matchingCorrect=(String)fields.get("correctOptionId");
                    }
                    cells.add(new Cell(index, itemNumber, options, Set.copyOf(correct), blankId, matchingCorrect, matching && Boolean.TRUE.equals(item.get("locked")), cell));
                    numbers.getChildren().add(cell);
                }
            }
            VBox section = new VBox(UiTheme.label(title, "question-outline-section-title"), numbers);
            section.getStyleClass().add("question-outline-section");
            groups.getChildren().add(section);
        }
        ScrollPane scroll = UiTheme.scroll(groups);
        scroll.setId("history-question-outline-scroll");
        scroll.getStyleClass().add("question-outline-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(UiTheme.label("题目大纲", "question-outline-title"), scroll);
        refresh(detail, 0);
    }

    void refresh(PracticeHistoryDetail detail, int currentIndex) {
        var row=detail.questions().isEmpty()?null:detail.questions().get(currentIndex);
        refresh(detail,currentIndex,row!=null && row.finalState()==PracticeSessionQuestion.State.SUBMITTED?row.attempts().size()-1:-1);
    }

    void refresh(PracticeHistoryDetail detail, int currentIndex,int selectedAttempt) {
        cells.forEach(entry -> {
            int index=entry.questionIndex();
            Button cell=entry.button();
            var question = detail.questions().get(index);
            String state = "unsubmitted";
            int attemptIndex=index==currentIndex?selectedAttempt:question.finalState()==PracticeSessionQuestion.State.SUBMITTED?question.attempts().size()-1:-1;
            var attempt=attemptIndex>=0 && attemptIndex<question.attempts().size()?question.attempts().get(attemptIndex):null;
            boolean submitted=attempt!=null;
            if(entry.locked())state="hint";
            else if(entry.itemNumber()>0){
                PracticePayload answer=submitted?attempt.answer():question.draftAnswer();
                if(question.contentSnapshot()!=null && QuestionContentData.map(question.contentSnapshot().value()).containsKey("extensionPresentation")) {
                    state = submitted ? attempt.result().name().toLowerCase(java.util.Locale.ROOT)
                            : answer == null ? "unsubmitted" : "draft";
                }else if(QuestionTypes.isTranslation(question.questionType())){
                    var chosen=TranslationPracticeAnswer.from(answer).answers().get(entry.blankId());
                    if(chosen!=null && !chosen.empty())state=submitted?"unscored":"draft";
                }else if(QuestionTypes.isMatching(question.questionType())){
                    var chosen=MatchingPracticeAnswer.from(answer).assignments().get(entry.blankId());
                    if(chosen!=null)state=submitted?chosen.equals(entry.matchingCorrectOptionId())?"correct":"incorrect":"draft";
                }else{
                    var selected=answer==null?Set.<String>of():LegacyChoiceAnswerDecoder.answerIds(answer);
                    var chosen=entry.optionIds().stream().filter(selected::contains).findFirst();
                    if(chosen.isPresent())state=submitted?entry.correctOptionIds().contains(chosen.get())?"correct":"incorrect":"draft";
                }
            }else if (submitted) {
                state = switch (attempt.result()) {
                    case CORRECT -> "correct";
                    case INCORRECT -> "incorrect";
                    case UNSCORED -> "unscored";
                };
            }
            cell.getStyleClass().removeAll("unsubmitted", "draft", "correct", "incorrect", "unscored", "hint", "current");
            cell.getStyleClass().add(state);
            if (index == currentIndex) cell.getStyleClass().add("current");
            String description = "第 " + cell.getText() + " 题"
                    + (entry.itemNumber()>0 ? " · " + (QuestionTypes.isTranslation(question.questionType())?"翻译第 ":QuestionTypes.isMatching(question.questionType())?"匹配第 ":QuestionTypes.isReading(question.questionType())?"阅读第 ":"完形第 ") +entry.itemNumber()+" 小题" : "")
                    + " · " + switch (state) {
                case "correct" -> "回答正确";
                case "incorrect" -> "回答错误";
                case "unscored" -> "已提交，未评分";
                case "draft" -> "草稿已暂存，未提交";
                case "hint" -> "题目已给出，不计分";
                default -> "未完成";
            } + (index == currentIndex ? " · 当前题目" : "");
            cell.setAccessibleText(description);
            cell.setTooltip(new Tooltip(description));
        });
    }
}
