package io.quizforge.desktop.poc.sharedpractice;
import io.quizforge.core.practice.*;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.type.subjective.translation.*;
import io.quizforge.desktop.ui.question.history.HistoryDraftAdapter;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.*;
import java.nio.file.Path;import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
class SharedTranslationAdapterTest {
 @TempDir Path directory;
 QuestionBank bank(){var items=List.of(new TranslationItem("item_t_1",1,"First sentence."),new TranslationItem("item_t_2",2,"Second sentence."));
 var q=new Question("q_translation","TRANSLATION",List.of(),new TextContent("Article {{First sentence.}}\n{{Second sentence.}}"),new TranslationPayload(items),new TranslationAnswerSpec(items.stream().map(i->new TranslationAnswerSpec.Answer(i.id(),new TextContent("Reference "+i.number()))).toList()),new ScoreSpec(java.math.BigDecimal.valueOf(2)),null,new TextContent("Frozen analysis"),List.of());
 return new QuestionBank("qb_translation_contract","Translation contract","2.0",List.of(),List.of(q),List.of());}
 @Test void textRoundTripPartialUnscoredRetryAndFrozenHistory(){
 var db=new SqliteDatabase(directory.resolve("practice.db"));var tx=new SqlitePracticeTransaction(db);var service=new PracticeSessionService(tx,Clock.systemUTC());var bank=bank();var revision=new QuestionBankV2Codec().contentId(bank);var adapter=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
 String parent=adapter.viewModel().question().sessionQuestionId();assertEquals("TRANSLATION",adapter.viewModel().question().type());assertFalse(adapter.viewModelJson().contains("Reference 1"));
 adapter.textAnswersChanged(Map.of("item_t_1","First\nanswer 中文"));var reopened=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));assertEquals(parent,reopened.viewModel().question().sessionQuestionId());assertEquals("First\nanswer 中文",((SharedPracticeViewModel.TranslationPresentation)reopened.viewModel().question().presentation()).items().getFirst().answer().text());
 assertThrows(IllegalArgumentException.class,()->adapter.textAnswersChanged(Map.of("unknown","text")));
 var firstDraft=DraftCanvasDocument.createEmpty();adapter.saveDraft(firstDraft);var first=adapter.submit().question().result();assertEquals("UNSCORED",first.status());assertNull(first.score());assertEquals(4,first.maxScore());
 var snapshots=new SqliteAttemptDraftSnapshotRepository(db);var frozen=snapshots.find(first.attemptId()).orElseThrow();adapter.retry();assertTrue(((SharedPracticeViewModel.TranslationPresentation)adapter.viewModel().question().presentation()).items().stream().allMatch(i->i.answer().text().isEmpty()));
 adapter.textAnswersChanged(Map.of("item_t_1","Changed","item_t_2","Second"));var secondDraft=new DraftCanvasDocument("1.0","1",new DraftCanvasDocument.Viewport(32,-19,1.5),new DraftCanvasDocument.QuestionCard(120,70,720),List.of());adapter.saveDraft(secondDraft);var second=adapter.submit().question().result();assertEquals("UNSCORED",second.status());assertNull(second.score());assertEquals("RETRY",second.attemptMode());assertEquals(frozen,snapshots.find(first.attemptId()).orElseThrow());
 new SqlitePracticeSessionRepository(db).archive(adapter.snapshot().session().id(),Instant.now());var history=new PracticeHistoryService(tx);var detail=history.loadArchivedSessionDetail(bank.assetId(),adapter.snapshot().session().id());var projection=new HistoryDraftAdapter(history,bank.assetId(),detail);var row=detail.questions().getFirst();var a=projection.load(row,row.attempts().getFirst());var b=projection.load(row,row.attempts().getLast());assertNotNull(a.card());assertEquals(first.attemptId(),a.card().question().result().attemptId());assertEquals(second.attemptId(),b.card().question().result().attemptId());assertEquals(firstDraft,a.draft().document());assertEquals(secondDraft,b.draft().document());assertEquals("Reference 1",((SharedPracticeViewModel.TranslationPresentation)a.card().question().presentation()).items().getFirst().reference().text());assertEquals(frozen,snapshots.find(first.attemptId()).orElseThrow());
 }
}
