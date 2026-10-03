package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.practice.EssayQuestionSnapshot;
import io.quizforge.core.practice.ReadingQuestionSnapshot;
import io.quizforge.core.practice.MatchingQuestionSnapshot;
import io.quizforge.core.practice.MatchingPracticeAnswer;
import io.quizforge.core.practice.TranslationPracticeAnswer;
import io.quizforge.core.practice.TranslationQuestionSnapshot;
import io.quizforge.core.question.type.objective.matching.MatchingPayload;
import io.quizforge.desktop.ui.question.objective.matching.MatchingQuestionCardView;
import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.dev.DevelopmentRefreshable;
import io.quizforge.desktop.ui.question.objective.choice.ChoiceCardView;
import io.quizforge.desktop.ui.question.objective.choice.ChoicePresentationMapper;
import io.quizforge.desktop.ui.question.objective.reading.ReadingQuestionCardView;
import io.quizforge.desktop.ui.question.shared.QuestionCardLayout;
import io.quizforge.desktop.ui.question.source.HistorySourceListView;
import io.quizforge.desktop.ui.question.source.HistorySourceNavigationAdapter;
import io.quizforge.desktop.ui.question.subjective.essay.EssayAnswerPane;
import io.quizforge.desktop.ui.question.subjective.essay.EssayQuestionCardView;
import io.quizforge.desktop.ui.question.subjective.translation.TranslationQuestionCardView;
import io.quizforge.desktop.ui.shared.UiTheme;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** One archived question and one submitted attempt at a time; all navigation stays in memory. */
public final class PracticeHistoryDetailView extends BorderPane implements DevelopmentRefreshable {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final PracticeHistoryDetail detail;
    private final HistoryQuestionOutlineView outline;
    private final VBox question = new VBox(18);
    private final ScrollPane scroll;
    private final VBox headings = new VBox();
    private final VBox attemptControls = new VBox(8);
    private final HistorySurfaceHost surface;
    private final HistoryDraftAdapter drafts;
    private VBox attemptContext;
    private Node fileHeader;
    private final WorkspaceId workspace;
    private final HistorySourceNavigationAdapter sources;
    private HistorySourceListView sourceList;
    private ReadingQuestionCardView readingCard;
    private MatchingQuestionCardView matchingCard;
    private TranslationQuestionCardView translationCard;
    private int questionIndex;
    private int attemptIndex;
    private final io.quizforge.core.question.model.QuestionBank currentBank;
    private final String currentContentId;
    private final io.quizforge.core.port.QuestionResourceInput currentResources;

    public PracticeHistoryDetailView(PracticeHistoryDetail detail, Runnable back, WorkspaceId workspace,
            HistorySourceNavigationAdapter sources) {
        this(detail,back,workspace,sources,null,null,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    public PracticeHistoryDetailView(PracticeHistoryDetail detail,Runnable back,WorkspaceId workspace,
            HistorySourceNavigationAdapter sources,io.quizforge.core.question.model.QuestionBank currentBank,String currentContentId,
            io.quizforge.core.port.QuestionResourceInput currentResources){
        this(detail,back,workspace,sources,currentBank,currentContentId,currentResources,null);
    }
    public PracticeHistoryDetailView(PracticeHistoryDetail detail,Runnable back,WorkspaceId workspace,
            HistorySourceNavigationAdapter sources,io.quizforge.core.question.model.QuestionBank currentBank,String currentContentId,
            io.quizforge.core.port.QuestionResourceInput currentResources,HistoryDraftAdapter drafts){
        this.drafts=drafts;
        this.currentBank=currentBank;this.currentContentId=currentContentId;this.currentResources=currentResources;
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
        title.setMaxWidth(Double.MAX_VALUE);title.setWrapText(false);
        HBox heading = new HBox(12, returnButton, title);
        heading.getStyleClass().add("history-heading");
        headings.getChildren().add(heading);
        question.setId("history-detail-question");
        QuestionCardLayout.configure(question);
        scroll = QuestionCardLayout.scroll(question);
        scroll.setId("history-question-scroll");
        outline = new HistoryQuestionOutlineView(detail, (index, item) -> showQuestion(index, item));
        surface=new HistorySurfaceHost(scroll,()->showQuestion(questionIndex-1),()->showQuestion(questionIndex+1),
                ()->questionIndex>0,()->questionIndex<detail.questions().size()-1);
        surface.onModeChanged(this::placeAttemptControls);
        heading.getChildren().add(surface.toggleButton());
        BorderPane readerColumn = new BorderPane(surface);
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

    private void showQuestion(int index, int itemNumber) {
        if (index < 0 || index >= detail.questions().size()) return;
        if (index != questionIndex) showQuestion(index);
        else render();
        if (matchingCard != null && itemNumber > 0) {
            var target = matchingCard;
            javafx.application.Platform.runLater(() -> {
                if (matchingCard == target) target.focusBlank(itemNumber);
            });
        }
        if (readingCard != null && itemNumber > 0) {
            var target = readingCard;
            javafx.application.Platform.runLater(() -> {
                if (readingCard == target) target.focusItem(itemNumber);
            });
        }
        if (translationCard != null && itemNumber > 0) {
            var target = translationCard;
            javafx.application.Platform.runLater(() -> {
                if (translationCard == target) target.focusItem(itemNumber);
            });
        }
    }

    private void showAttempt(int index) {
        if (index < 0 || index >= detail.questions().get(questionIndex).attempts().size()) return;
        attemptIndex = index;
        render();
    }

    private void render() {
        attemptControls.getChildren().clear();
        outline.refresh(detail, questionIndex);
        question.getChildren().clear();
        sourceList = null;
        readingCard = null;
        matchingCard = null;
        translationCard = null;
        if (detail.questions().isEmpty()) {
            surface.select(null);
            attemptContext=null;placeAttemptControls();
            question.getChildren().add(UiTheme.label("本轮没有题目", "muted"));
            return;
        }
        var row = detail.questions().get(questionIndex);
        try{surface.select(drafts==null || attemptIndex<0?null:drafts.load(row,row.attempts().get(attemptIndex)));}
        catch(RuntimeException failure){surface.select(new HistoryDraftAdapter.Replay(
                io.quizforge.core.practice.HistoryDraftReplay.unavailable("历史草稿暂时无法读取，请返回结果查看。"),null));}
        boolean essay = QuestionTypes.isEssay(row.questionType());
        boolean cloze = QuestionTypes.isCloze(row.questionType());
        boolean reading = QuestionTypes.isReading(row.questionType());
        boolean matching = QuestionTypes.isMatching(row.questionType());
        boolean translation = QuestionTypes.isTranslation(row.questionType());
        var content = ChoicePresentationMapper.history(row);
        VBox context = new VBox(8);
        attemptContext=context;
        context.getStyleClass().add("history-question-context");
        boolean unfinished = row.finalState() != PracticeSessionQuestion.State.SUBMITTED;
        Label finalState = UiTheme.label(unfinished ? "本轮最终状态：未完成" : "本轮最终状态：已完成", "muted");
        finalState.setId("history-final-state");
        context.getChildren().add(finalState);

        if (row.draftAnswer() != null) {
            Label draft = UiTheme.label(essay || cloze || reading || matching || translation ? "未提交草稿" : "未提交选择：" + content.answerLabels(
                    ChoicePresentationMapper.answerIds(row.draftAnswer())), "history-draft");
            draft.setId("history-draft");
            context.getChildren().add(draft);
        }
        sourceList = new HistorySourceListView(workspace, row.sourceRefs(), sources);
        VBox card;
        if (attemptIndex < 0) {
            Label noAttempt = UiTheme.label("本轮未提交", "muted");
            noAttempt.setId("history-no-attempt");
            context.getChildren().add(noAttempt);
            card = translation?translationCard(row,row.draftAnswer(),false):matching?matchingCard(row,row.draftAnswer(),false):reading?readingCard(row,row.draftAnswer(),false):cloze?clozeCard(row,row.draftAnswer(),false):essay?essayCard(row,row.draftAnswer(),false):ChoiceCardView.readOnly(content, questionIndex, detail.questions().size(), "history-",
                    row.draftAnswer() == null ? Set.of() : ChoicePresentationMapper.answerIds(row.draftAnswer()), sourceList);
        } else {
            var attempt = row.attempts().get(attemptIndex);
            Label attemptHeader = UiTheme.label("第 " + (attemptIndex + 1) + " / " + row.attempts().size()
                    + " 次作答 · " + mode(attempt.mode()), "history-attempt-header");
            attemptHeader.setId("history-attempt-position");
            attemptControls.getChildren().add(attemptHeader);
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
            attemptControls.getChildren().add(attempts);
            if(translation){
                card=translationCard(row,attempt.answer(),true);
            }else if(matching){
                card=matchingCard(row,attempt.answer(),true);
            }else if(reading){
                card=readingCard(row,attempt.answer(),true);
            }else if(cloze){
                card=clozeCard(row,attempt.answer(),true);
            }else if(essay){
                card=essayCard(row,attempt.answer(),true);
            }else{
                var choice = ChoiceCardView.result(ChoicePresentationMapper.historyResult(row, attempt),
                        questionIndex, detail.questions().size(), "history-", sourceList);
                choice.resultLabel().setId("history-attempt-result");card=choice;
            }
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
        placeAttemptControls();
        scroll.setVvalue(0);
    }

    public void refreshSources() { if (sourceList != null) sourceList.refresh(); }
    private VBox translationCard(PracticeHistoryDetail.Question row,PracticePayload answer,boolean submitted){
        var fields=QuestionContentData.map(row.contentSnapshot().value());
        var stored=fields.get("translationPresentation");
        if(!(stored instanceof java.util.Map<?,?>))stored=fields.get("translation");
        var snapshot=TranslationQuestionSnapshot.from(new PracticePayload(stored));
        var selected=TranslationPracticeAnswer.from(answer).answers();
        translationCard=new TranslationQuestionCardView(snapshot.question(),questionIndex,detail.questions().size(),
                snapshot.resources(),snapshot::open,"history-",()->selected,()->submitted,null,null,null,null);
        if(row.draftAnswer()!=null && submitted){
            var draftAnswers=TranslationPracticeAnswer.from(row.draftAnswer()).answers();
            var draft=new VBox(12);draft.setId("history-translation-draft");
            var payload=(io.quizforge.core.question.type.subjective.translation.TranslationPayload)snapshot.question().payload();
            for(var item:payload.items()){
                var saved=draftAnswers.get(item.id());
                if(saved==null || saved.empty())continue;
                draft.getChildren().addAll(UiTheme.label(item.number()+".","essay-section-title"),
                        EssayAnswerPane.renderSavedAnswer(saved.payload(),"history-translation-draft-"+item.number()+"-"));
            }
            translationCard.getChildren().addAll(UiTheme.label("未提交草稿","essay-section-title"),draft);
        }
        if(row.sourceRefs().value() instanceof java.util.List<?> refs && !refs.isEmpty())translationCard.getChildren().add(sourceList);
        return translationCard;
    }
    private VBox matchingCard(PracticeHistoryDetail.Question row,PracticePayload answer,boolean submitted){
        var fields=QuestionContentData.map(row.contentSnapshot().value());
        var stored=fields.get("matchingPresentation");
        if(!(stored instanceof java.util.Map<?,?>))stored=fields.get("matching");
        var snapshot=MatchingQuestionSnapshot.from(new PracticePayload(stored));
        var selected=MatchingPracticeAnswer.from(answer).assignments();
        matchingCard=new MatchingQuestionCardView(snapshot.question(),questionIndex,detail.questions().size(),
                snapshot.resources(),snapshot::open,"history-",()->selected,()->submitted,null,null,null,null);
        if(row.draftAnswer()!=null && submitted){
            var draftSelected=MatchingPracticeAnswer.from(row.draftAnswer()).assignments();
            var payload=(MatchingPayload)snapshot.question().payload();
            var draft=new VBox(8);draft.setId("history-matching-draft");
            for(var blank:payload.blanks()){
                if(blank.locked())continue;
                var chosen=draftSelected.get(blank.id());
                payload.options().stream().filter(option->option.id().equals(chosen)).findFirst().ifPresent(option->
                        draft.getChildren().add(UiTheme.label(blank.number()+". "+option.label(),"history-draft")));
            }
            matchingCard.getChildren().addAll(UiTheme.label("未提交草稿","essay-section-title"),draft);
        }
        if(row.sourceRefs().value() instanceof java.util.List<?> refs && !refs.isEmpty())matchingCard.getChildren().add(sourceList);
        return matchingCard;
    }

    private VBox readingCard(PracticeHistoryDetail.Question row,PracticePayload answer,boolean submitted){
        var fields=QuestionContentData.map(row.contentSnapshot().value());
        var stored=fields.get("readingPresentation");
        if(!(stored instanceof java.util.Map<?,?>)){
            var fallback=new java.util.LinkedHashMap<String,Object>();
            QuestionContentData.map(fields.get("reading")).forEach((key,value)->fallback.put((String)key,value));
            fallback.putIfAbsent("resources",java.util.List.of());fallback.putIfAbsent("resourceData",java.util.Map.of());
            stored=fallback;
        }
        var snapshot=ReadingQuestionSnapshot.from(new PracticePayload(stored));
        var selected=answer==null?Set.<String>of():ChoicePresentationMapper.answerIds(answer);
        readingCard=new ReadingQuestionCardView(snapshot.question(),questionIndex,detail.questions().size(),
                snapshot.resources(),snapshot::open,"history-",()->selected,()->submitted,null,null,null,null);
        if(row.draftAnswer()!=null && submitted){
            var draftSelected=ChoicePresentationMapper.answerIds(row.draftAnswer());
            var draft=new VBox(8);draft.setId("history-reading-draft");
            for(var item:((io.quizforge.core.question.type.objective.reading.ReadingPayload)snapshot.question().payload()).items()){
                for(int option=0;option<item.options().size();option++){
                    var choice=item.options().get(option);
                    if(draftSelected.contains(choice.id()))draft.getChildren().add(UiTheme.label(item.number()+". "+(char)('A'+option)+". "
                            +QuestionContentData.plainText(choice.content()),"history-draft"));
                }
            }
            readingCard.getChildren().addAll(UiTheme.label("未提交草稿","essay-section-title"),draft);
        }
        if(row.sourceRefs().value() instanceof java.util.List<?> refs && !refs.isEmpty())readingCard.getChildren().add(sourceList);
        return readingCard;
    }

    private VBox clozeCard(PracticeHistoryDetail.Question row,PracticePayload answer,boolean submitted){
        var fields=QuestionContentData.map(row.contentSnapshot().value());
        var stored=fields.get("clozePresentation");
        if(!(stored instanceof java.util.Map<?,?>)){
            var fallback=new java.util.LinkedHashMap<String,Object>();
            QuestionContentData.map(fields.get("cloze")).forEach((key,value)->fallback.put((String)key,value));
            fallback.putIfAbsent("resources",java.util.List.of());fallback.putIfAbsent("resourceData",java.util.Map.of());stored=fallback;
        }
        var snapshot=io.quizforge.core.practice.ClozeQuestionSnapshot.from(new PracticePayload(stored));
        var selected=answer==null?Set.<String>of():ChoicePresentationMapper.answerIds(answer);
        var card=new io.quizforge.desktop.ui.question.objective.cloze.ClozeQuestionCardView(snapshot.question(),questionIndex,detail.questions().size(),
                snapshot.resources(),snapshot::open,"history-",()->selected,()->submitted,null,null,null,null);
        if(row.sourceRefs().value() instanceof java.util.List<?> refs && !refs.isEmpty())card.getChildren().add(sourceList);
        return card;
    }

    private VBox essayCard(PracticeHistoryDetail.Question row,PracticePayload answer,boolean submitted){
        boolean sameBank=currentBank!=null && currentContentId!=null && currentContentId.equals(detail.bankContentId());
        var fields=row.contentSnapshot()==null?java.util.Map.of():QuestionContentData.map(row.contentSnapshot().value());
        EssayQuestionSnapshot snapshot;
        if(fields.get("essayPresentation") instanceof java.util.Map<?,?> stored){
            snapshot=EssayQuestionSnapshot.from(new PracticePayload(stored));
        }else{
            var original=sameBank?currentBank.questions().stream().filter(q->q.id().equals(row.questionId()) && QuestionTypes.isEssay(q.type())).findFirst().orElse(null):null;
            if(original!=null)snapshot=EssayQuestionSnapshot.capture(original,currentBank.resources(),io.quizforge.core.port.QuestionResourceInput.NONE);
            else{
                var prompt=fields.containsKey("prompt")?QuestionContentData.decode(fields.get("prompt")):new TextContent(row.stem());
                var reference=fields.get("referenceAnswer") instanceof java.util.Map<?,?> ref && ref.containsKey("kind")?QuestionContentData.decode(ref):null;
                snapshot=new EssayQuestionSnapshot(prompt,reference,row.analysis().isBlank()?null:new TextContent(row.analysis()),
                        null,null,java.util.List.of(),java.util.Map.of());
            }
        }
        var owned=snapshot;
        io.quizforge.core.port.QuestionResourceInput resources=resource->{
            var stream=owned.open(resource);return stream==null && sameBank?currentResources.open(resource):stream;
        };
        var prompt=availableContent(snapshot.prompt(),snapshot,sameBank);
        var card=EssayQuestionCardView.card(prompt,snapshot.maxScore(),questionIndex,detail.questions().size(),"history-",snapshot.resources(),resources);
        javafx.scene.Node response=answer==null?UiTheme.label("本轮未作答","essay-answer-empty"):
                EssayAnswerPane.renderSavedAnswer(answer,"history-essay-answer-");
        var box=new VBox(14,UiTheme.label("作答","essay-section-title"),response);box.setId("history-essay-answer-box");
        box.getStyleClass().add("essay-answer-box");box.setMinWidth(0);card.getChildren().add(box);
        var feedback=new VBox(12);
        if(submitted){var state=UiTheme.label("已提交 · 未评分","muted");state.setId("history-attempt-result");feedback.getChildren().add(state);}
        var reference=availableContent(snapshot.reference(),snapshot,sameBank);
        var analysis=availableContent(snapshot.analysis(),snapshot,sameBank);
        if(reference!=null || analysis!=null)feedback.getChildren().add(EssayQuestionCardView.reference(reference,analysis,snapshot.resources(),resources,"history-reference-"));
        if(snapshot.guidance()!=null && !snapshot.guidance().isBlank())feedback.getChildren().addAll(UiTheme.label("评分细则","essay-section-title"),
                UiTheme.label(snapshot.guidance(),"authoring-essay-text"));
        if(!feedback.getChildren().isEmpty())card.getChildren().add(feedback);
        if(row.draftAnswer()!=null && submitted)card.getChildren().addAll(UiTheme.label("未提交草稿","essay-section-title"),
                EssayAnswerPane.renderSavedAnswer(row.draftAnswer(),"history-essay-draft-"));
        if(row.sourceRefs().value() instanceof java.util.List<?> refs && !refs.isEmpty())card.getChildren().add(sourceList);
        return card;
    }

    private static QuestionContent availableContent(QuestionContent content,EssayQuestionSnapshot snapshot,boolean sameBank){
        if(content==null)return null;
        // Older archives did not own their resources. Never substitute a later revision.
        return QuestionContentData.resourceIds(content).stream().allMatch(id->snapshot.resourceData().containsKey(id) || sameBank)
                ?content:new TextContent(QuestionContentData.plainText(content));
    }

    public HistoryQuestionOutlineView outline() { return outline; }
    public HistorySurfaceHost surface(){return surface;}
    public void destroy(){surface.destroy();setHeader(null);}

    public void setHeader(Node header) {
        if (fileHeader != null) headings.getChildren().remove(fileHeader);
        fileHeader=header;
        if (header != null) headings.getChildren().addFirst(header);
    }

    /** Preserve the original RESULT layout; keep Attempt controls reachable above the Draft viewport. */
    private void placeAttemptControls(){
        if(attemptControls.getParent() instanceof javafx.scene.layout.Pane parent)parent.getChildren().remove(attemptControls);
        if(attemptControls.getChildren().isEmpty())return;
        if(surface.mode()==HistorySurfaceMode.DRAFT)headings.getChildren().add(attemptControls);
        else if(attemptContext!=null)attemptContext.getChildren().add(attemptControls);
    }

    private static String mode(QuestionAttempt.Mode mode) {
        return switch (mode) {
            case INITIAL -> "首次作答";
            case RETRY -> "重新答题";
            case REVISION -> "修改答案";
        };
    }

    @Override public void refreshForDevelopment(){render();}
}
