package io.quizforge.desktop.poc.sharedpractice;

import io.quizforge.core.practice.*;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.desktop.ui.question.history.HistoryDraftAdapter;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SharedRendererContentBoundaryTest {
 @TempDir Path directory;
 static QuestionBank bank(String type){return switch(type){case "READING"->new SharedReadingAdapterTest().bank();case "CLOZE"->new SharedClozeAdapterTest().bank();case "MATCHING"->new SharedMatchingAdapterTest().bank();case "TRANSLATION"->new SharedTranslationAdapterTest().bank();default->new SharedEssayAdapterTest().bank();};}
 @ParameterizedTest @ValueSource(strings={"READING","CLOZE","MATCHING","TRANSLATION","ESSAY"})
 void unsupportedStructuredContentCannotBecomePlainText(String type){
   var bank=bank(type);var db=new SqliteDatabase(directory.resolve("practice.db"));var service=new PracticeSessionService(new SqlitePracticeTransaction(db),Clock.systemUTC());
   var state=service.openOrCreateActiveSession(bank,new QuestionBankV2Codec().contentId(bank));var snapshot=state.questions().getFirst().sessionQuestion().snapshot();
   var metadata=new LinkedHashMap<String,Object>();((Map<?,?>)snapshot.correctAnswer().value()).forEach((key,value)->metadata.put((String)key,value));
   String family=type.toLowerCase(Locale.ROOT);
   var logical=new LinkedHashMap<String,Object>();
   ((Map<?,?>)metadata.get(type.equals("ESSAY")?"essayPresentation":family)).forEach((key,value)->logical.put((String)key,value));
   logical.put("prompt",Map.of("kind","RICH","document",Map.of("blocks",List.of())));
   metadata.put(type.equals("ESSAY")?"essayPresentation":family,logical);
   var fields=metadata;
   var failure=assertThrows(IllegalArgumentException.class,()->SharedPracticeViewModel.project(type,new SharedPracticeViewModel.Text("TEXT",snapshot.stem()),snapshot.options(),fields,List.of(),List.of(),false,null));
   assertTrue(failure.getMessage().contains("Unsupported content"));
 }
 @ParameterizedTest @ValueSource(strings={"READING","CLOZE","MATCHING","TRANSLATION","ESSAY"})
 void attemptWithoutSnapshotKeepsResultAndNeverInventsBlankDraft(String type){
   var bank=bank(type);var db=new SqliteDatabase(directory.resolve("practice.db"));var tx=new SqlitePracticeTransaction(db);var service=new PracticeSessionService(tx,Clock.systemUTC());var revision=new QuestionBankV2Codec().contentId(bank);var adapter=new SharedPracticeAdapter(service,service.openOrCreateActiveSession(bank,revision));
   switch(type){case "READING","CLOZE"->adapter.answerChanged(Set.of("opt_1_a"));case "MATCHING"->adapter.assignmentsChanged(Map.of("blank_m_2","opt_m_b"));case "TRANSLATION"->adapter.textAnswersChanged(Map.of("item_t_1","text"));default->adapter.essayChanged("Essay");}
   var result=adapter.submit().question().result();assertNotNull(result);new SqlitePracticeSessionRepository(db).archive(adapter.snapshot().session().id(),Instant.now());
   var history=new PracticeHistoryService(tx);var detail=history.loadArchivedSessionDetail(bank.assetId(),adapter.snapshot().session().id());var row=detail.questions().getFirst();assertEquals(result.attemptId(),row.attempts().getFirst().attemptId());
   var replay=new HistoryDraftAdapter(history,bank.assetId(),detail).load(row,row.attempts().getFirst());assertEquals(HistoryDraftReplay.Status.MISSING,replay.draft().status());assertNull(replay.card());
 }
}
