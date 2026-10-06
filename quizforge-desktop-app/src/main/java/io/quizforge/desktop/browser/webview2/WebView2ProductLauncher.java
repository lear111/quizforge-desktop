package io.quizforge.desktop.browser.webview2;

import io.quizforge.core.practice.*;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/** Real product host in the real transparent-window style, with an isolated test database. */
public final class WebView2ProductLauncher extends Application {
    private PracticeSurfaceHost host;
    @Override public void start(Stage stage)throws Exception{
        stage.initStyle(StageStyle.TRANSPARENT);stage.setTitle("QuizForge 正式练习后端验证 · "+UUID.randomUUID());
        var root=new BorderPane(new Label("正在准备题型…"));root.setStyle("-fx-background-color: white;");stage.setScene(new Scene(root,1100,780));stage.show();
        var directory=Files.createDirectories(Path.of("target/webview2-product-acceptance",UUID.randomUUID().toString()).toAbsolutePath());
        ExternalExtensionAcceptance.initialize(ExtensionManager.getDefault(), directory.resolve("extensions"), getParameters().getRaw()).whenComplete((initialized,loadFailure)->{
            if(loadFailure!=null){loadFailure.printStackTrace();if(getParameters().getRaw().contains("--verify"))Platform.runLater(stage::hide);}
        }).thenRun(()->CompletableFuture.supplyAsync(()->{
            try{
                var questions=new ArrayList<io.quizforge.core.question.model.Question>();
                var types=getParameters().getRaw().contains("--true-false")?List.of("TRUE_FALSE"):List.of("SINGLE_CHOICE","MULTIPLE_CHOICE","SINGLE_CHOICE");
                for(var type:types)questions.add(QuestionTypes.require(type).createDraft(prefix->prefix+UUID.randomUUID(),List.of()));
                var bank=new QuestionBank("qb_"+UUID.randomUUID(),"正式练习后端验证",List.of(),questions,List.of());var codec=new QuestionBankV2Codec();
                Files.writeString(directory.resolve("question-bank.json"),codec.write(bank));
                return new PersistentPracticeRuntime(new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(directory.resolve("practice.db"))),Clock.systemUTC()),bank,codec.contentId(bank));
            }catch(Exception failure){throw new CompletionException(failure);}
        }).whenComplete((runtime,failure)->Platform.runLater(()->{
            if(failure!=null){failure.printStackTrace();root.setCenter(new Label(failure.toString()));if(getParameters().getRaw().contains("--verify"))stage.hide();return;}
            host=new PracticeSurfaceHost(runtime,()->{host.showQuestion();host.refreshChrome();},()->host.navigate(()->{runtime.previous();host.showQuestion();}),()->host.navigate(()->{runtime.next();host.showQuestion();}));
            var close=new Button("关闭");close.setOnAction(event->stage.fireEvent(new WindowEvent(stage,WindowEvent.WINDOW_CLOSE_REQUEST)));
            var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);var header=new HBox(12,new Label("正式练习组件 · 单选 / 多选"),spacer,host.toggleButton(),close);header.setStyle("-fx-padding:8;-fx-alignment:center-left;");root.setTop(header);root.setCenter(host);host.showQuestion();
            stage.setOnCloseRequest(event->{event.consume();host.prepareCloseAsync().whenComplete((ok,error)->Platform.runLater(()->{if(error==null&&Boolean.TRUE.equals(ok)){host.destroy();stage.hide();}}));});
            host.ready().whenComplete((v,error)->Platform.runLater(()->{
                if(error!=null){System.err.println("PRODUCT_BACKEND_FAILED "+error);if(getParameters().getRaw().contains("--verify"))stage.hide();return;}
                if(getParameters().getRaw().contains("--verify")) {
                    if(getParameters().getRaw().contains("--true-false"))WebView2Verification.runTrueFalse(host,stage,directory);
                    else WebView2Verification.runProduct(host,stage,directory,root,()->host.navigate(()->{runtime.next();host.showQuestion();}),()->host.navigate(()->{runtime.previous();host.showQuestion();}));
                }
            }));
        })));
    }
    @Override public void stop(){ExtensionManager.getDefault().close();}
}
