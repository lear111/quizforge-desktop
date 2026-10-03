package io.quizforge.desktop.ui.question.shared;

import io.quizforge.core.practice.QuestionBankPracticeSession;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.objective.cloze.ClozeAnswerSpec;
import io.quizforge.core.question.type.objective.cloze.ClozePayload;
import io.quizforge.core.question.type.objective.reading.ReadingPayload;
import io.quizforge.core.question.type.objective.reading.ReadingAnswerSpec;
import io.quizforge.core.question.type.objective.matching.MatchingPayload;
import io.quizforge.core.question.type.objective.matching.MatchingAnswerSpec;
import io.quizforge.core.question.type.subjective.translation.TranslationPayload;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** A view of the existing session; numbered cells never own answer or submission state. */
public final class QuestionOutlineView extends VBox {
    private final QuestionBankPracticeSession session;
    private final String cellPrefix;
    private record Cell(int questionIndex, int blankIndex, Button button) { }
    private final List<Cell> cells = new ArrayList<>();
    private final Map<String, Integer> practiceIndexes = new LinkedHashMap<>();
    private final VBox groups = new VBox();
    private List<Question> questions;
    private IntConsumer jump;
    private java.util.function.BiConsumer<Integer,Integer> itemJump;
    private java.util.function.BiConsumer<Integer,Integer> moveAction;
    private String draggedQuestionId;
    private Button dragSource;
    private double pressedX, pressedY, pointerX, pointerY;
    private boolean moving;
    private int dropIndex = -1;
    private boolean dropAfter;
    private ScrollPane scroll;
    private javafx.scene.Cursor previousCursor;
    private final javafx.animation.AnimationTimer dragScroll = new javafx.animation.AnimationTimer() {
        private long previous;
        @Override public void handle(long now) {
            if (now-previous < 80_000_000L || !moving) return;
            previous = now;
            var point = scroll.sceneToLocal(pointerX,pointerY);
            if (point.getX()<0 || point.getX()>scroll.getWidth()) return;
            double step = point.getY()<32 ? -0.035 : point.getY()>scroll.getHeight()-32 ? 0.035 : 0;
            if (step != 0) {
                scroll.setVvalue(Math.max(scroll.getVmin(),Math.min(scroll.getVmax(),scroll.getVvalue()+step)));
                scroll.layout();updateMoveTarget();
            }
        }
    };

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
        scroll = UiTheme.scroll(groups);
        scroll.setId("question-outline-scroll");
        scroll.getStyleClass().add("question-outline-scroll");
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,event -> {
            if (moving && event.getCode()==javafx.scene.input.KeyCode.ESCAPE) {finishMove();event.consume();}
        });
        sceneProperty().addListener((o,before,after)->{if(after==null)finishMove();});
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(UiTheme.label("题目大纲", "question-outline-title"), scroll);
    }

    private void rebuild() {
        groups.getChildren().clear();
        cells.clear();
        List<List<Integer>> consecutiveGroups = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            if (i == 0 || !questions.get(i).type().equals(questions.get(i - 1).type()))
                consecutiveGroups.add(new ArrayList<>());
            consecutiveGroups.getLast().add(i);
        }
        var seenTypes = new HashSet<String>();
        int number = 1;
        for (var indexes : consecutiveGroups) {
            String type = questions.get(indexes.getFirst()).type();
            String title = QuestionTypeCatalog.label(type);
            FlowPane numbers = new FlowPane();
            numbers.setMinWidth(0);
            numbers.getStyleClass().add("question-outline-numbers");
            for (int index : indexes) {
                for (int blank : cellIndexes(questions.get(index))) {
                    Button cell = new Button(Integer.toString(number));
                    cell.setId(cellPrefix + number++);
                    cell.getStyleClass().add("question-number-cell");
                    int itemNumber=blank+1;
                    cell.setOnAction(event -> {
                        if((QuestionTypes.isReading(questions.get(index).type()) || QuestionTypes.isCloze(questions.get(index).type()) || QuestionTypes.isMatching(questions.get(index).type()) || QuestionTypes.isTranslation(questions.get(index).type())) && itemJump!=null)itemJump.accept(index,itemNumber);
                        else this.jump.accept(index);
                    });
                    cells.add(new Cell(index, blank, cell));
                    numbers.getChildren().add(cell);
                }
            }
            VBox section = new VBox(UiTheme.label(title, "question-outline-section-title"), numbers);
            section.setId("question-outline-" + type.toLowerCase(java.util.Locale.ROOT)
                    + (seenTypes.add(type) ? "" : "-" + (indexes.getFirst() + 1)));
            section.getStyleClass().add("question-outline-section");
            groups.getChildren().add(section);
        }
        cells.forEach(this::configureMove);
    }

    /** Live practice and editors provide their own save actions; archived outlines stay read-only. */
    public void setMoveAction(java.util.function.BiConsumer<Integer,Integer> action) {
        moveAction = action;
        finishMove();
        cells.forEach(this::configureMove);
    }

    private void configureMove(Cell entry) {
        Button cell = entry.button();
        if (moveAction == null) {
            cell.setContextMenu(null);
            return;
        }
        // Resolve stable parent IDs at gesture time: every subquestion moves the same complete card.
        String questionId = questions.get(entry.questionIndex()).id();
        var up = new MenuItem("上移整张题卡");up.setId("outline-move-up");
        var down = new MenuItem("下移整张题卡");down.setId("outline-move-down");
        up.setDisable(entry.questionIndex() == 0);down.setDisable(entry.questionIndex() == questions.size()-1);
        up.setOnAction(event -> moveAdjacent(questionId, -1));
        down.setOnAction(event -> moveAdjacent(questionId, 1));
        cell.setContextMenu(new ContextMenu(up, down));
        if (cell.getProperties().putIfAbsent("outline-move-gesture",Boolean.TRUE)==null) {
            // Capture before ButtonBehavior consumes mouse events. Mouse capture keeps dragging
            // on the pressed cell, so resolve the destination from scene coordinates.
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED,event -> {
                if(moveAction==null || event.getButton()!=MouseButton.PRIMARY || questions.size()<2)return;
                finishMove();dragSource=cell;draggedQuestionId=questionId;
                pressedX=pointerX=event.getSceneX();pressedY=pointerY=event.getSceneY();
            });
            cell.addEventFilter(MouseEvent.MOUSE_DRAGGED,event -> {
                if(moveAction==null || dragSource!=cell || !event.isPrimaryButtonDown())return;
                pointerX=event.getSceneX();pointerY=event.getSceneY();event.setDragDetect(false);
                if(!moving && Math.hypot(pointerX-pressedX,pointerY-pressedY)<5)return;
                if(!moving) {
                    moving=true;cell.disarm();previousCursor=getScene().getCursor();
                    getScene().setCursor(javafx.scene.Cursor.MOVE);dragScroll.start();
                    cells.stream().filter(c -> questions.get(c.questionIndex()).id().equals(draggedQuestionId))
                            .forEach(c -> c.button().getStyleClass().add("reorder-source"));
                }
                updateMoveTarget();event.consume();
            });
            cell.addEventFilter(MouseEvent.MOUSE_RELEASED,event -> {
                if(dragSource!=cell)return;
                if(!moving) {finishMove();return;}
                pointerX=event.getSceneX();pointerY=event.getSceneY();updateMoveTarget();
                int from=questionIndex(draggedQuestionId);
                int boundary=dropIndex+(dropAfter?1:0);
                int to=boundary-(from<boundary?1:0);
                boolean accepted=dropIndex>=0 && from>=0 && from!=dropIndex && from!=to;
                finishMove();event.consume();
                if(accepted && moveAction!=null)moveAction.accept(from,to);
            });
        }
    }

    private void updateMoveTarget() {
        dropIndex=-1;clearMoveTargets();
        var point=scroll.sceneToLocal(pointerX,pointerY);
        if(!scroll.getBoundsInLocal().contains(point))return;
        for(var entry:cells) {
            var local=entry.button().sceneToLocal(pointerX,pointerY);
            if(entry.button().getBoundsInLocal().contains(local)) {
                if(!questions.get(entry.questionIndex()).id().equals(draggedQuestionId)) {
                    dropIndex=entry.questionIndex();dropAfter=local.getX()>=entry.button().getWidth()/2;
                    showMoveTarget(dropIndex,dropAfter);
                }
                return;
            }
        }
    }
    private void finishMove() {
        dragScroll.stop();
        if(moving && getScene()!=null)getScene().setCursor(previousCursor);
        if(moving && dragSource!=null)dragSource.disarm();
        moving=false;dragSource=null;draggedQuestionId=null;dropIndex=-1;clearMoveStyles();
    }
    private int questionIndex(String id) {
        for (int i=0;i<questions.size();i++) if (questions.get(i).id().equals(id)) return i;
        return -1;
    }
    private void moveAdjacent(String id, int direction) {
        int from = questionIndex(id), to = from + direction;
        if (moveAction != null && from >= 0 && to >= 0 && to < questions.size()) moveAction.accept(from, to);
    }
    private void showMoveTarget(int index, boolean after) {
        clearMoveTargets();
        var target = cells.stream().filter(c -> c.questionIndex() == index).toList();
        target.forEach(c -> c.button().getStyleClass().add("reorder-target"));
        if (!target.isEmpty()) (after ? target.getLast() : target.getFirst()).button().getStyleClass()
                .add(after ? "reorder-after" : "reorder-before");
    }
    private void clearMoveTargets() {
        cells.forEach(c -> c.button().getStyleClass().removeAll("reorder-target","reorder-before","reorder-after"));
    }
    private void clearMoveStyles() {
        clearMoveTargets();cells.forEach(c -> c.button().getStyleClass().remove("reorder-source"));
    }

    private static int cellCount(Question question) {
        if(QuestionTypes.isTranslation(question.type()))return ((TranslationPayload)question.payload()).items().size();
        if(QuestionTypes.isMatching(question.type()))return ((MatchingPayload)question.payload()).blanks().size();
        if(QuestionTypes.isReading(question.type()))return ((ReadingPayload)question.payload()).items().size();
        return QuestionTypes.isCloze(question.type()) ? ((ClozePayload) question.payload()).blanks().size() : 1;
    }

    private static List<Integer> cellIndexes(Question question) {
        return java.util.stream.IntStream.range(0, cellCount(question))
                .filter(index -> !QuestionTypes.isMatching(question.type())
                        || !((MatchingPayload) question.payload()).blanks().get(index).locked())
                .boxed().toList();
    }

    public void setItemJump(java.util.function.BiConsumer<Integer,Integer> action) { itemJump=action; }

    public int currentIndex() { return session.index(); }

    public void showEditor(QuestionBank bank, int selected, IntConsumer editorJump) {
        List<Question> edited = bank.questions();
        boolean changed = questions.size() != edited.size();
        for (int i = 0; !changed && i < edited.size(); i++)
            changed = !questions.get(i).id().equals(edited.get(i).id())
                    || !questions.get(i).type().equals(edited.get(i).type())
                    || !cellIndexes(questions.get(i)).equals(cellIndexes(edited.get(i)));
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
        cells.forEach(entry -> {
            int index = entry.questionIndex();
            Button cell = entry.button();
            Integer practiceIndex = practiceIndexes.get(questions.get(index).id());
            String state = QuestionTypes.isTranslation(questions.get(index).type())
                    ? translationState(questions.get(index), entry.blankIndex(), practiceIndex)
                    : QuestionTypes.isMatching(questions.get(index).type())
                    ? matchingState(questions.get(index), entry.blankIndex(), practiceIndex)
                    : QuestionTypes.isReading(questions.get(index).type())
                    ? readingState(questions.get(index), entry.blankIndex(), practiceIndex)
                    : QuestionTypes.isCloze(questions.get(index).type())
                    ? clozeState(questions.get(index), entry.blankIndex(), practiceIndex)
                    : practiceIndex != null && session.state(practiceIndex) == QuestionBankPracticeSession.State.SUBMITTED
                    ? QuestionTypes.isEssay(questions.get(index).type()) ? "unscored"
                            : session.correct(practiceIndex) ? "correct" : "incorrect"
                    : practiceIndex != null && session.state(practiceIndex) == QuestionBankPracticeSession.State.SELECTED
                            ? "draft" : "unsubmitted";
            boolean current = index == selected;
            cell.getStyleClass().removeAll("unsubmitted", "draft", "correct", "incorrect", "unscored", "hint", "current");
            cell.getStyleClass().add(state);
            if (current) cell.getStyleClass().add("current");
            String description = "第 " + cell.getText() + " 题"
                    + (QuestionTypes.isCloze(questions.get(index).type()) ? " · 完形第 "
                            + ((ClozePayload) questions.get(index).payload()).blanks().get(entry.blankIndex()).number() + " 空" : "")
                    + (QuestionTypes.isReading(questions.get(index).type()) ? " · 阅读第 "
                            + ((ReadingPayload)questions.get(index).payload()).items().get(entry.blankIndex()).number()+" 小题" : "")
                    + (QuestionTypes.isMatching(questions.get(index).type()) ? " · 匹配第 "
                            + ((MatchingPayload)questions.get(index).payload()).blanks().get(entry.blankIndex()).number()+" 空" : "")
                    + (QuestionTypes.isTranslation(questions.get(index).type()) ? " · 翻译第 "
                            + ((TranslationPayload)questions.get(index).payload()).items().get(entry.blankIndex()).number()+" 句" : "")
                    + " · " + switch (state) {
                case "correct" -> "回答正确";
                case "incorrect" -> "回答错误";
                case "unscored" -> "已提交，未评分";
                case "draft" -> "草稿已暂存，未提交";
                case "hint" -> "题目已给出，不计分";
                default -> "未提交";
            } + (current ? " · 当前题目" : "")
                    + (moveAction != null ? " · 拖动可移动整张题卡，左半侧插入前方，右半侧插入后方" : "");
            cell.setAccessibleText(description);
            cell.setTooltip(new Tooltip(description));
        });
    }

    private String clozeState(Question question, int blankIndex, Integer practiceIndex) {
        if (practiceIndex == null) return "unsubmitted";
        var blank = ((ClozePayload) question.payload()).blanks().get(blankIndex);
        var selection = session.selected(practiceIndex);
        var chosen = blank.options().stream().filter(option -> selection.contains(option.id())).findFirst();
        if (chosen.isEmpty()) return "unsubmitted";
        if (session.state(practiceIndex) != QuestionBankPracticeSession.State.SUBMITTED) return "draft";
        return ((ClozeAnswerSpec) question.answerSpec()).correctOptionIds().contains(chosen.get().id()) ? "correct" : "incorrect";
    }
    private String matchingState(Question question,int blankIndex,Integer practiceIndex) {
        var blank=((MatchingPayload)question.payload()).blanks().get(blankIndex);
        if(blank.locked())return "hint";
        if(practiceIndex==null)return "unsubmitted";
        var chosen=session.matchingAnswers(practiceIndex).get(blank.id());
        if(chosen==null)return "unsubmitted";
        if(session.state(practiceIndex)!=QuestionBankPracticeSession.State.SUBMITTED)return "draft";
        return chosen.equals(((MatchingAnswerSpec)question.answerSpec()).assignments().get(blank.id()))?"correct":"incorrect";
    }
    private String readingState(Question question,int itemIndex,Integer practiceIndex) {
        if(practiceIndex==null)return "unsubmitted";
        var item=((ReadingPayload)question.payload()).items().get(itemIndex);var selection=session.selected(practiceIndex);
        var chosen=item.options().stream().filter(option->selection.contains(option.id())).findFirst();
        if(chosen.isEmpty())return "unsubmitted";
        if(session.state(practiceIndex)!=QuestionBankPracticeSession.State.SUBMITTED)return "draft";
        return ((ReadingAnswerSpec)question.answerSpec()).correctOptionIds().contains(chosen.get().id())?"correct":"incorrect";
    }
    private String translationState(Question question,int itemIndex,Integer practiceIndex) {
        if(practiceIndex==null)return "unsubmitted";
        var item=((TranslationPayload)question.payload()).items().get(itemIndex);
        var answer=session.translationAnswers(practiceIndex).get(item.id());
        if(answer==null || answer.empty())return "unsubmitted";
        return session.state(practiceIndex)==QuestionBankPracticeSession.State.SUBMITTED?"unscored":"draft";
    }
}
