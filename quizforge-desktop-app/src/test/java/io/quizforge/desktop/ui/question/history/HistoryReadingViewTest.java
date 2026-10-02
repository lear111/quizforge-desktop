package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.type.objective.cloze.ClozeQuestionType;
import io.quizforge.core.question.type.objective.reading.*;
import io.quizforge.core.question.type.objective.matching.*;
import io.quizforge.core.question.type.subjective.translation.*;
import io.quizforge.desktop.testing.FxTestRuntime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoryReadingViewTest {
    @BeforeAll static void start() throws Exception { FxTestRuntime.start(); }
    private static void fx(Runnable action) throws Exception {
        var result=new CompletableFuture<Void>();
        Platform.runLater(()->{try{action.run();result.complete(null);}catch(Throwable error){result.completeExceptionally(error);}});
        result.get(20,TimeUnit.SECONDS);
    }
    private static Question reading() {
        var ids=new AtomicInteger();
        var q=new ReadingQuestionType().createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
        return new Question(q.id(),q.type(),List.of(),new TextContent("Archived article"),q.payload(),q.answerSpec(),q.scoreSpec(),
                null,new TextContent("Archived explanation"),List.of());
    }
    private static PracticeHistoryDetail.Question row(Question q, PracticePayload snapshot, List<String> selected,
            PracticeSessionQuestion.State state, PracticePayload draft, boolean twoAttempts) {
        var attempts=new ArrayList<PracticeHistoryDetail.Attempt>();
        if(selected!=null){
            attempts.add(new PracticeHistoryDetail.Attempt(1,QuestionAttempt.Mode.INITIAL,new PracticePayload(selected),
                    QuestionAttempt.Result.INCORRECT,2d,10d,Instant.EPOCH));
            if(twoAttempts)attempts.add(new PracticeHistoryDetail.Attempt(2,QuestionAttempt.Mode.RETRY,new PracticePayload(selected),
                    QuestionAttempt.Result.INCORRECT,2d,10d,Instant.EPOCH));
        }
        return new PracticeHistoryDetail.Question("psq_"+q.id(),q.id(),0,q.type(),"Old plain summary",List.of(),List.of(),"",new PracticePayload(List.of()),
                state,draft,attempts,snapshot);
    }
    private static PracticeHistoryDetail detail(List<PracticeHistoryDetail.Question> questions) {
        return new PracticeHistoryDetail("ps_history","Archived",Instant.EPOCH,Instant.EPOCH,
                new PracticeSummary(questions.size(),1,0,1,questions.size()-1,Optional.of(java.math.BigDecimal.valueOf(2)),Optional.empty()),questions);
    }
    private static Button cell(HistoryQuestionOutlineView outline,int number){return (Button)outline.lookup("#history-question-number-"+number);}

    @Test void frozenReadingAndClozeItemsUseContinuousNumbersAndIndependentStates() throws Exception {
        fx(()->{
            var reading=reading();var items=((ReadingPayload)reading.payload()).items();
            var chosen=List.of(items.get(0).options().get(0).id(),items.get(1).options().get(1).id());
            var readingRow=row(reading,new PracticePayload(Map.of("reading",ReadingQuestionSnapshot.logical(reading))),chosen,
                    PracticeSessionQuestion.State.SUBMITTED,null,false);
            var ids=new AtomicInteger(100);
            var cloze=new ClozeQuestionType().createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
            var blank=((io.quizforge.core.question.type.objective.cloze.ClozePayload)cloze.payload()).blanks().getFirst();
            var clozeRow=row(cloze,new PracticePayload(Map.of("cloze",ClozeQuestionSnapshot.logical(cloze))),null,
                    PracticeSessionQuestion.State.RETRYING,new PracticePayload(List.of(blank.options().get(0).id())),false);
            var archive=detail(List.of(readingRow,clozeRow,readingRow));
            var jump=new AtomicInteger(-1);var item=new AtomicInteger(-1);
            var outline=new HistoryQuestionOutlineView(archive,(index,number)->{jump.set(index);item.set(number);});
            new Scene(outline,260,600);outline.applyCss();outline.layout();outline.refresh(archive,0);
            assertEquals(11,outline.lookupAll(".question-number-cell").size());
            assertTrue(cell(outline,1).getStyleClass().contains("correct"));
            assertTrue(cell(outline,2).getStyleClass().contains("incorrect"));
            assertTrue(cell(outline,3).getStyleClass().contains("unsubmitted"));
            assertTrue(cell(outline,6).getStyleClass().contains("draft"));
            cell(outline,5).fire();assertEquals(0,jump.get());assertEquals(5,item.get());
            cell(outline,6).fire();assertEquals(1,jump.get());assertEquals(1,item.get());
            cell(outline,11).fire();assertEquals(2,jump.get());assertEquals(5,item.get());
        });
    }

    @Test void historyUsesFrozenPresentationAndLogicalFallbackAndKeepsAttemptWhenJumpingWithinArticle() throws Exception {
        fx(()->{
            var q=reading();var payload=(ReadingPayload)q.payload();
            var selected=List.of(payload.items().getFirst().options().getFirst().id());
            var snapshot=ReadingQuestionSnapshot.capture(q,List.of(),QuestionResourceInput.NONE).payload();
            var logical=new LinkedHashMap<>(ReadingQuestionSnapshot.logical(q));
            logical.put("prompt",Map.of("kind","TEXT","text","Later article"));
            var fields=Map.of("readingPresentation",snapshot.value(),"reading",logical);
            var archive=detail(List.of(row(q,new PracticePayload(fields),selected,PracticeSessionQuestion.State.RETRYING,
                    new PracticePayload(List.of(payload.items().get(1).options().get(1).id())),true)));
            var view=new PracticeHistoryDetailView(archive,()->{},null,null);
            new Scene(view,1100,750);view.applyCss();view.layout();
            assertNotNull(view.lookup("#history-reading-card"));
            assertTrue(view.lookupAll(".question-stem").stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label->label.getText().equals("Archived article")));
            assertFalse(view.lookupAll(".question-stem").stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label->label.getText().equals("Later article")));
            assertNull(view.lookup("#history-reading-submit"));assertNull(view.lookup("#history-reading-retry"));
            assertEquals(20,view.lookupAll(".reading-answer-option").size());
            assertTrue(view.lookupAll(".reading-answer-option").stream().map(RadioButton.class::cast).allMatch(RadioButton::isDisabled));
            assertNotNull(view.lookup("#history-reading-draft"));
            assertEquals("得分：2 / 10",((Label)view.lookup("#history-reading-result")).getText());
            assertTrue(view.lookupAll(".essay-section-title").stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label->label.getText().equals("答案与解析")));
            ((Button)view.lookup("#history-previous-attempt")).fire();
            ((Button)view.lookup("#history-question-number-3")).fire();
            assertTrue(((Label)view.lookup("#history-attempt-position")).getText().startsWith("第 1 / 2 次作答"));
            var legacy=detail(List.of(row(q,new PracticePayload(Map.of("reading",ReadingQuestionSnapshot.logical(q))),null,
                    PracticeSessionQuestion.State.DRAFT,new PracticePayload(selected),false)));
            var fallback=new PracticeHistoryDetailView(legacy,()->{},null,null);new Scene(fallback,1100,750);
            fallback.applyCss();fallback.layout();
            assertNotNull(fallback.lookup("#history-reading-card"));
            assertTrue(((RadioButton)fallback.lookup("#history-reading-option-1-0")).isSelected());
            assertNotNull(fallback.lookup("#history-no-attempt"));
        });
    }

    @Test void matchingHistoryKeepsPositionalAnswersFrozenPresentationAndRetryDraft() throws Exception {
        fx(()->{
            var ids=new AtomicInteger();
            var draft=new MatchingQuestionType().createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
            var original=(MatchingPayload)draft.payload();
            var archivedBlanks=original.blanks().stream().map(blank->
                    new MatchingBlank(blank.id(),blank.number(),blank.number()>5)).toList();
            var q=new Question(draft.id(),draft.type(),List.of(),new TextContent("Archived matching article and full A–H options"),
                    new MatchingPayload(archivedBlanks,original.options()),draft.answerSpec(),draft.scoreSpec(),null,new TextContent("Archived matching explanation"),List.of());
            var payload=(MatchingPayload)q.payload();
            var blanks=payload.blanks();var options=payload.options();
            // Repeated letters are preserved and graded by position; hints are not user answers.
            var repeated=Map.of(blanks.get(0).id(),options.get(1).id(),blanks.get(1).id(),options.get(1).id());
            var draftAnswers=Map.of(blanks.get(2).id(),options.get(2).id());
            var logical=new LinkedHashMap<>(MatchingQuestionSnapshot.logical(q));
            logical.put("prompt",Map.of("kind","TEXT","text","Later matching article"));
            logical.put("blanks",original.blanks().stream().map(blank->Map.of("id",blank.id(),"number",blank.number(),"locked",false)).toList());
            var frozen=MatchingQuestionSnapshot.capture(q,List.of(),QuestionResourceInput.NONE).payload();
            var content=new PracticePayload(Map.of("matching",logical,"matchingPresentation",frozen.value()));
            var attempts=List.of(new PracticeHistoryDetail.Attempt(1,QuestionAttempt.Mode.INITIAL,new PracticePayload(repeated),
                    QuestionAttempt.Result.INCORRECT,2d,10d,Instant.EPOCH),
                    new PracticeHistoryDetail.Attempt(2,QuestionAttempt.Mode.RETRY,new PracticePayload(repeated),
                    QuestionAttempt.Result.INCORRECT,2d,10d,Instant.EPOCH));
            var row=new PracticeHistoryDetail.Question("psq_matching",q.id(),0,q.type(),"Old summary",List.of(),List.of(),"",new PracticePayload(List.of()),
                    PracticeSessionQuestion.State.RETRYING,new PracticePayload(draftAnswers),attempts,content);
            var view=new PracticeHistoryDetailView(detail(List.of(row)),()->{},null,null);
            new Scene(view,1100,750);view.applyCss();view.layout();
            assertNotNull(view.lookup("#history-matching-card"));
            assertEquals(5,view.outline().lookupAll(".question-number-cell").size());
            assertNull(cell(view.outline(),6));
            assertNull(view.lookup("#history-matching-submit"));assertNull(view.lookup("#history-matching-retry"));
            assertTrue(((MenuButton)view.lookup("#history-matching-blank-1")).isDisabled());
            assertEquals("1. B",((MenuButton)view.lookup("#history-matching-blank-1")).getText());
            assertEquals("2. B",((MenuButton)view.lookup("#history-matching-blank-2")).getText());
            assertTrue(view.lookupAll(".matching-answer-slot .icon-lock").isEmpty());
            assertNotNull(view.lookup("#history-matching-draft"));
            assertEquals("得分：2 / 10",((Label)view.lookup("#history-matching-result")).getText());
            assertTrue(view.lookupAll(".question-stem").stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label->label.getText().contains("Archived matching article")));
            assertFalse(view.lookupAll(".question-stem").stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label->label.getText().contains("Later matching article")));
            ((Button)view.lookup("#history-previous-attempt")).fire();
            ((Button)view.lookup("#history-question-number-4")).fire();
            assertTrue(((Label)view.lookup("#history-attempt-position")).getText().startsWith("第 1 / 2 次作答"));
            assertTrue(cell(view.outline(),3).getStyleClass().contains("draft"));

            var submitted=new PracticeHistoryDetail.Question("psq_matching",q.id(),0,q.type(),"Old summary",List.of(),List.of(),"",new PracticePayload(List.of()),
                    PracticeSessionQuestion.State.SUBMITTED,null,attempts,content);
            var outline=new HistoryQuestionOutlineView(detail(List.of(submitted)),index->{});
            new Scene(outline,260,600);outline.applyCss();outline.layout();
            assertEquals(5,outline.lookupAll(".question-number-cell").size());
            assertTrue(cell(outline,1).getStyleClass().contains("incorrect"));
            assertTrue(cell(outline,2).getStyleClass().contains("correct"));
            assertTrue(cell(outline,3).getStyleClass().contains("unsubmitted"));

            var fallbackRow=new PracticeHistoryDetail.Question("psq_matching",q.id(),0,q.type(),"Old summary",List.of(),List.of(),"",new PracticePayload(List.of()),
                    PracticeSessionQuestion.State.DRAFT,new PracticePayload(draftAnswers),List.of(),new PracticePayload(Map.of("matching",MatchingQuestionSnapshot.logical(q))));
            var fallback=new PracticeHistoryDetailView(detail(List.of(fallbackRow)),()->{},null,null);
            new Scene(fallback,1100,750);fallback.applyCss();fallback.layout();
            assertNotNull(fallback.lookup("#history-matching-card"));assertNotNull(fallback.lookup("#history-no-attempt"));
            assertTrue(((MenuButton)fallback.lookup("#history-matching-blank-3")).getText().contains("C"));
            assertEquals(5,fallback.outline().lookupAll(".question-number-cell").size());
        });
    }
    @Test void matchingHistoryNumberingSkipsInterspersedHintsAndKeepsActualJumpTargets() throws Exception {
        fx(()->{
            var ids=new AtomicInteger(500);
            var cloze=new ClozeQuestionType().createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
            var matching=new MatchingQuestionType().createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
            var reading=reading();
            var rows=List.of(
                    row(cloze,new PracticePayload(Map.of("cloze",ClozeQuestionSnapshot.logical(cloze))),null,PracticeSessionQuestion.State.UNANSWERED,null,false),
                    row(matching,new PracticePayload(Map.of("matching",MatchingQuestionSnapshot.logical(matching))),null,PracticeSessionQuestion.State.UNANSWERED,null,false),
                    row(reading,new PracticePayload(Map.of("reading",ReadingQuestionSnapshot.logical(reading))),null,PracticeSessionQuestion.State.UNANSWERED,null,false));
            var targets=new ArrayList<List<Integer>>();
            var outline=new HistoryQuestionOutlineView(detail(rows),(index,position)->targets.add(List.of(index,position)));
            new Scene(outline,260,650);outline.applyCss();outline.layout();
            assertEquals(11,outline.lookupAll(".question-number-cell").size());
            for(int number=1;number<=11;number++)assertEquals(Integer.toString(number),cell(outline,number).getText());
            for(int number=2;number<=6;number++)cell(outline,number).fire();
            assertEquals(List.of(List.of(1,2),List.of(1,3),List.of(1,5),List.of(1,7),List.of(1,8)),targets);
            cell(outline,7).fire();assertEquals(List.of(2,1),targets.getLast());
            assertTrue(outline.lookupAll(".question-number-cell.hint").isEmpty());
        });
    }

    @Test void translationHistoryKeepsFrozenSentencesReferencesAndAttemptAnswers() throws Exception {
        var ids=new AtomicInteger(800);
        var original=new TranslationQuestionType().createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
        var payload=(TranslationPayload)original.payload();
        var references=new TranslationAnswerSpec(payload.items().stream().map(item->
                new TranslationAnswerSpec.Answer(item.id(),new TextContent("Frozen reference "+item.number()))).toList());
        byte[] bytes="{\"version\":\"1.0.4\",\"options\":{},\"data\":{\"main\":[{\"value\":\"Frozen analysis\"}]}}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var resource=new io.quizforge.core.question.resource.QBankResource("res_canvas_"+hash,
                io.quizforge.core.question.resource.ResourceKind.DOCUMENT,"application/vnd.quizforge.canvas+json","resources/translation.canvas.json",hash);
        var q=new Question(original.id(),original.type(),List.of(),original.prompt(),payload,references,original.scoreSpec(),null,
                new io.quizforge.core.question.content.DocumentContent(resource.id(),"Frozen analysis"),List.of());
        var frozen=TranslationQuestionSnapshot.capture(q,List.of(resource),r->new java.io.ByteArrayInputStream(bytes)).payload();
        var captured=TranslationQuestionSnapshot.from(frozen);
        assertArrayEquals(bytes,captured.open(resource).readAllBytes());
        var logical=new LinkedHashMap<>(TranslationQuestionSnapshot.logical(q));
        logical.put("prompt",Map.of("kind","TEXT","text","Changed article"));
        logical.put("items",List.of(Map.of("id","item_later","number",1,"text","Later sentence")));
        var first=new TranslationPracticeAnswer(Map.of(payload.items().get(0).id(),new EssayPracticeAnswer("First submitted translation",null))).payload();
        var second=new TranslationPracticeAnswer(Map.of(payload.items().get(1).id(),new EssayPracticeAnswer("Second submitted translation",null))).payload();
        var draft=new TranslationPracticeAnswer(Map.of(payload.items().get(2).id(),new EssayPracticeAnswer("Unsubmitted translation draft",null))).payload();
        var attempts=List.of(new PracticeHistoryDetail.Attempt(1,QuestionAttempt.Mode.INITIAL,first,QuestionAttempt.Result.UNSCORED,null,null,Instant.EPOCH),
                new PracticeHistoryDetail.Attempt(2,QuestionAttempt.Mode.RETRY,second,QuestionAttempt.Result.UNSCORED,null,null,Instant.EPOCH));
        var fields=new PracticePayload(Map.of("translationPresentation",frozen.value(),"translation",logical));
        var row=new PracticeHistoryDetail.Question("psq_translation",q.id(),0,q.type(),"Old summary",List.of(),List.of(),"",new PracticePayload(List.of()),
                PracticeSessionQuestion.State.RETRYING,draft,attempts,fields);
        fx(()->{
            var view=new PracticeHistoryDetailView(detail(List.of(row)),()->{},null,null);
            new Scene(view,1100,750);view.applyCss();view.layout();
            assertNotNull(view.lookup("#history-translation-card"));
            assertEquals(5,view.outline().lookupAll(".question-number-cell").size());
            assertNull(view.lookup("#history-translation-submit"));assertNull(view.lookup("#history-translation-retry"));
            assertNotNull(view.lookup("#history-translation-draft"));
            assertEquals("已提交 · 待评分",((Label)view.lookup("#history-translation-result")).getText());
            assertTrue(labels(view).contains("Second submitted translation"));
            assertTrue(labels(view).contains("Unsubmitted translation draft"));
            assertTrue(labels(view).contains("Frozen reference 1"));
            assertFalse(labels(view).contains("Changed article"));
            ((Button)view.lookup("#history-previous-attempt")).fire();
            ((Button)view.lookup("#history-question-number-4")).fire();
            assertTrue(((Label)view.lookup("#history-attempt-position")).getText().startsWith("第 1 / 2 次作答"));
            assertTrue(labels(view).contains("First submitted translation"));
            assertFalse(labels(view).contains("Second submitted translation"));
            assertTrue(cell(view.outline(),3).getStyleClass().contains("draft"));
            assertTrue(cell(view.outline(),1).getStyleClass().contains("unsubmitted"));

            var submittedRow=new PracticeHistoryDetail.Question("psq_translation",q.id(),0,q.type(),"Old summary",List.of(),List.of(),"",new PracticePayload(List.of()),
                    PracticeSessionQuestion.State.SUBMITTED,null,attempts,fields);
            var outline=new HistoryQuestionOutlineView(detail(List.of(submittedRow)),index->{});
            new Scene(outline,260,600);outline.applyCss();outline.layout();
            assertTrue(cell(outline,2).getStyleClass().contains("unscored"));
            assertTrue(cell(outline,1).getStyleClass().contains("unsubmitted"));
            var logicalOnly=new PracticeHistoryDetail.Question("psq_translation",q.id(),0,q.type(),"Old summary",List.of(),List.of(),"",new PracticePayload(List.of()),
                    PracticeSessionQuestion.State.DRAFT,first,List.of(),new PracticePayload(Map.of("translation",TranslationQuestionSnapshot.logical(original))));
            var fallback=new PracticeHistoryDetailView(detail(List.of(logicalOnly)),()->{},null,null);
            new Scene(fallback,1100,750);fallback.applyCss();fallback.layout();
            assertNotNull(fallback.lookup("#history-no-attempt"));
            assertNotNull(fallback.lookup("#history-translation-card"));
            assertTrue(labels(fallback).contains("First submitted translation"));
            assertNull(fallback.lookup("#history-translation-result"));
        });
    }

    @Test void practiceTranslationOutlineExpandsSentencesAndKeepsPartialStatesAndGlobalJumpNumbers() throws Exception {
        fx(()->{
            var ids=new AtomicInteger(1000);
            var translation=new TranslationQuestionType().createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
            var cloze=new ClozeQuestionType().createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
            var session=new QuestionBankPracticeSession(new io.quizforge.core.question.model.QuestionBank("qb_translation_outline","Translation",List.of(),List.of(translation,cloze),List.of()));
            var items=((TranslationPayload)translation.payload()).items();
            session.assignTranslation(items.get(1).id(),new EssayPracticeAnswer("A partial draft",null));
            var targets=new ArrayList<List<Integer>>();
            var outline=new io.quizforge.desktop.ui.question.shared.QuestionOutlineView(session,index->targets.add(List.of(index,0)));
            outline.setItemJump((index,item)->targets.add(List.of(index,item)));
            new Scene(outline,260,600);outline.applyCss();outline.layout();outline.refresh();
            assertEquals(6,outline.lookupAll(".question-number-cell").size());
            assertTrue(outline.lookup("#question-number-2").getStyleClass().contains("draft"));
            assertTrue(outline.lookup("#question-number-1").getStyleClass().contains("unsubmitted"));
            ((Button)outline.lookup("#question-number-5")).fire();
            assertEquals(List.of(0,5),targets.getLast());
            ((Button)outline.lookup("#question-number-6")).fire();
            assertEquals(List.of(1,0),targets.getLast());
            session.submit();outline.refresh();
            assertTrue(outline.lookup("#question-number-2").getStyleClass().contains("unscored"));
            assertTrue(outline.lookup("#question-number-1").getStyleClass().contains("unsubmitted"));
        });
    }
    private static List<String> labels(javafx.scene.Parent parent){
        return parent.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
    }
}
