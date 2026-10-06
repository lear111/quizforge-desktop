package io.quizforge.desktop.browser.webview2;

import io.quizforge.core.practice.*;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.desktop.ui.question.history.*;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.*;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.*;

/** Actual history UI and archived SQLite records, entirely under a fresh acceptance directory. */
public final class WebView2HistoryLauncher extends Application {
    private PracticeHistoryDetailView historyView;
    private record Fixture(QuestionBank bank,PracticeHistoryService history,PracticeHistoryDetail detail){}
    @Override public void start(Stage stage)throws Exception {
        stage.initStyle(StageStyle.TRANSPARENT);stage.setTitle("QuizForge 历史后端验证 · "+UUID.randomUUID());
        var root=new BorderPane(new Label("正在准备历史记录…"));root.setStyle("-fx-background-color:white;");
        var scene=new Scene(root,1250,820);UiTheme.apply(scene);stage.setScene(scene);stage.show();
        var directory=Files.createDirectories(Path.of("target/webview2-history-acceptance",UUID.randomUUID().toString()).toAbsolutePath());
        ExternalExtensionAcceptance.initialize(ExtensionManager.getDefault(), directory.resolve("extensions"), getParameters().getRaw()).thenRun(()->CompletableFuture.supplyAsync(()->fixture(directory)).whenComplete((fixture,error)->Platform.runLater(()->{
            if(error!=null){error.printStackTrace();stage.hide();return;}
            historyView=new PracticeHistoryDetailView(fixture.detail(),()->{historyView.destroy();stage.hide();},WorkspaceId.newId(),null,fixture.bank(),fixture.detail().bankContentId(),io.quizforge.core.port.QuestionResourceInput.NONE,new HistoryDraftAdapter(fixture.history(),fixture.bank().assetId(),fixture.detail()));
            root.setCenter(historyView);
            stage.setOnCloseRequest(event->{historyView.destroy();});
            historyView.surface().learningSurface().ready().whenComplete((v,failure)->Platform.runLater(()->{
                if(failure!=null){failure.printStackTrace();historyView.destroy();stage.hide();return;}
                if(getParameters().getRaw().contains("--verify"))WebView2Verification.runHistory(historyView,fixture.detail(),stage,directory,root);
            }));
        })));
    }
    private static Fixture fixture(Path directory){
        try{
            var questions=new ArrayList<io.quizforge.core.question.model.Question>();
            for(var type:List.of("SINGLE_CHOICE","MULTIPLE_CHOICE","SINGLE_CHOICE"))questions.add(QuestionTypes.require(type).createDraft(prefix->prefix+UUID.randomUUID(),List.of()));
            var bank=new QuestionBank("qb_"+UUID.randomUUID(),"历史回放验证",List.of(),questions,List.of());
            var codec=new QuestionBankV2Codec();Files.writeString(directory.resolve("question-bank.json"),codec.write(bank));
            var transaction=new SqlitePracticeTransaction(new SqliteDatabase(directory.resolve("practice.db")));
            var runtime=new PersistentPracticeRuntime(new PracticeSessionService(transaction,Clock.systemUTC()),bank,codec.contentId(bank));
            runtime.saveActiveDraftCanvas(document("first"));
            runtime.saveChoiceDraft(Set.of(bank.questions().getFirst().choicePayload().options().get(1).id()));runtime.submit();
            runtime.retry();runtime.saveActiveDraftCanvas(document("second"));
            runtime.saveChoiceDraft(Set.of(bank.questions().getFirst().choicePayload().options().getFirst().id()));runtime.submit();
            runtime.next();runtime.saveActiveDraftCanvas(document("pending"));
            runtime.saveChoiceDraft(Set.of(bank.questions().get(1).choicePayload().options().getFirst().id()));
            String session=runtime.sessionId();runtime.restart();
            var history=new PracticeHistoryService(transaction);
            return new Fixture(bank,history,history.loadArchivedSessionDetail(bank.assetId(),session));
        }catch(Exception failure){throw new CompletionException(failure);}
    }
    private static DraftCanvasDocument document(String name){
        return new DraftCanvasDocument("1.0","1",new DraftCanvasDocument.Viewport(0,0,1),new DraftCanvasDocument.QuestionCard(120,70,720),
            List.of(new DraftCanvasDocument.Stroke("ink-"+name,"PEN","#7660ab",2,List.of(new DraftCanvasDocument.Point(200,450,.5),new DraftCanvasDocument.Point(500,480,.8)))),
            List.of(new DraftCanvasDocument.TextAnnotation("text-"+name,200,540,260,20,"#34313b",name+" 归档便记")),new DraftCanvasDocument.Paper("#fff7dc",DraftCanvasDocument.PaperPattern.LINES));
    }
    @Override public void stop(){ExtensionManager.getDefault().close();}
}
