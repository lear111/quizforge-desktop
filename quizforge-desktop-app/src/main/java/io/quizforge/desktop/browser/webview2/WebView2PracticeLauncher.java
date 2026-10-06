package io.quizforge.desktop.browser.webview2;

import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.PracticeSessionService;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Opt-in real single-choice + shared whiteboard validation; never reads the user's Workspace. */
public final class WebView2PracticeLauncher extends Application {
    private WebView2Browser browser;
    private WebView2PracticeSession session;
    private Stage stage;
    private Label status;
    private Button previous,next,toggle;
    private boolean navigating,closing;
    private int index;
    private String mode="PRACTICE";
    private long started,changed;
    public static void main(String[] args){launch(args);}
    @Override public void start(Stage stage) throws Exception {
        this.stage=stage;
        var directory=Files.createDirectories(Path.of("target/webview2-acceptance",UUID.randomUUID().toString()).toAbsolutePath());
        stage.setTitle("QuizForge WebView2 验证 · "+UUID.randomUUID());
        var area=new Pane();area.setMinSize(0,0);
        previous=new Button("上一题");next=new Button("下一题");toggle=new Button("草稿");status=new Label("正在准备单选拓展…");
        previous.setOnAction(event->navigate(index-1));next.setOnAction(event->navigate(index+1));
        toggle.setOnAction(event->{if(browser!=null&&!navigating){navigating=true;buttons();browser.post(Map.of("kind","mode","mode",mode.equals("PRACTICE")?"DRAFT":"PRACTICE"));}});
        var spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);
        var editor=new Button("编辑验证");editor.setOnAction(event->{if(browser!=null&&!navigating)browser.evaluate("window.webview2Verification.showEditor()");});
        var header=new HBox(12,previous,next,status,spacer,editor,toggle);header.setStyle("-fx-padding: 8; -fx-alignment: center-left;");
        var root=new BorderPane(area,header,null,null,null);stage.setScene(new Scene(root,1100,780));stage.show();buttons();
        if(getParameters().getRaw().contains("--verify"))CompletableFuture.delayedExecutor(180,TimeUnit.SECONDS).execute(()->{
            if(!WebView2Verification.passed)Platform.runLater(()->{System.err.println("WEBVIEW2_VERIFY_TIMEOUT");release();stage.close();});
        });
        stage.setOnCloseRequest(event->{if(browser!=null&&!closing){event.consume();closing=true;navigating=true;buttons();browser.post(Map.of("kind","close"));}});
        ExternalExtensionAcceptance.initialize(ExtensionManager.getDefault(), directory.resolve("extensions"), getParameters().getRaw()).thenRun(()->{
            var bundle=ExtensionManager.getDefault().packageForType("SINGLE_CHOICE");
            CompletableFuture.supplyAsync(()->{
                try {
                    if(bundle.isEmpty())throw new IllegalStateException("单选拓展初始化失败："+ExtensionManager.getDefault().failures());
                    var definition=QuestionTypes.require("SINGLE_CHOICE");var questions=new ArrayList<io.quizforge.core.question.model.Question>();
                    for(int n=0;n<3;n++)questions.add(definition.createDraft(prefix->prefix+UUID.randomUUID(),List.of()));
                    var bank=new QuestionBank("qb_webview2_"+UUID.randomUUID(),"WebView2 单选验证",List.of(),questions,List.of());
                    var codec=new QuestionBankV2Codec();Files.writeString(directory.resolve("question-bank.json"),codec.write(bank));
                    var service=new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(directory.resolve("practice.db"))),Clock.systemUTC());
                    return new PersistentPracticeRuntime(service,bank,codec.contentId(bank));
                }catch(Exception failure){throw new CompletionException(failure);}
            }).whenComplete((runtime,failure)->Platform.runLater(()->{
                if(!stage.isShowing())return;
                if(failure!=null){status.setText("初始化失败："+failure.getMessage());return;}
                try {
                    started=System.nanoTime();
                    var connection=new CompletableFuture<WebView2PracticeSession>();
                    browser=new WebView2Browser(stage,area,directory,message->connection.thenAccept(current->current.receive(message)));
                    session=new WebView2PracticeSession(runtime,List.of(bundle),browser::post,message->Platform.runLater(()->{
                        switch(message.path("kind").asText()){
                            case "rendered" -> {index=message.path("index").asInt();navigating=false;buttons();
                                long base=changed==0?started:changed;double ms=(System.nanoTime()-base)/1_000_000d;
                                status.setText("第 "+(index+1)+" / 3 题 · "+String.format(Locale.ROOT,"%.0f ms",ms));
                                System.out.printf(Locale.ROOT,"WEBVIEW2_RENDER index=%d ms=%.1f%n",index,ms);
                                if(getParameters().getRaw().contains("--verify")&&index==0&&changed==0)WebView2Verification.run(browser,session,stage,area,directory,()->navigate(1),()->navigate(0));
                            }
                            case "mode-ready" -> {mode=message.path("mode").asText();navigating=false;toggle.setText(mode.equals("DRAFT")?"返回练习":"草稿");buttons();}
                            case "state-changed","preferences" -> { }
                            case "close-ready" -> {release();stage.close();}
                            default -> {status.setText("验证错误："+message);System.err.println("WEBVIEW2_ERROR "+message);navigating=false;buttons();}
                        }
                    }));
                    connection.complete(session);
                }catch(Exception error){status.setText(error.getMessage());}
            }));
        });
    }
    private void navigate(int target){if(browser==null||navigating||target<0||target>=3)return;navigating=true;changed=System.nanoTime();buttons();browser.post(Map.of("kind","navigate","index",target));}
    private void buttons(){previous.setDisable(browser==null||navigating||index==0);next.setDisable(browser==null||navigating||index==2);toggle.setDisable(browser==null||navigating);}
    private void release(){if(session!=null){session.close();session=null;}if(browser!=null){browser.close();browser=null;}}
    @Override public void stop(){release();ExtensionManager.getDefault().close();}
}
