package io.quizforge.desktop.browser.webview2;

import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.desktop.ui.question.editor.QuestionBankEditorView;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.nio.file.*;
import java.util.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.*;

/** Formal editor component with isolated packages/data and the product's transparent window style. */
public final class WebView2EditorLauncher extends Application {
    private QuestionBankEditorView editor;
    @Override public void start(Stage stage)throws Exception {
        stage.initStyle(StageStyle.TRANSPARENT);stage.setTitle("QuizForge 编辑后端验证 · "+UUID.randomUUID());
        var root=new BorderPane(new Label("正在准备题型…"));root.setStyle("-fx-background-color:white;");
        stage.setScene(new Scene(root,1000,680));stage.show();
        Path directory=Files.createDirectories(Path.of("target/webview2-editor-acceptance",UUID.randomUUID().toString()).toAbsolutePath());
        ExternalExtensionAcceptance.initialize(ExtensionManager.getDefault(), directory.resolve("extensions"), getParameters().getRaw()).whenComplete((v,error)->Platform.runLater(()->{
            try {
                if(error!=null)throw new IllegalStateException(error);
                var questions=new ArrayList<io.quizforge.core.question.model.Question>();
                var types=getParameters().getRaw().contains("--true-false")?List.of("TRUE_FALSE"):List.of("SINGLE_CHOICE","MULTIPLE_CHOICE","SINGLE_CHOICE");
                for(String type:types)questions.add(QuestionTypes.require(type).createDraft(prefix->prefix+UUID.randomUUID(),List.of()));
                var bank=new QuestionBank("qb_"+UUID.randomUUID(),"正式编辑验证",List.of(),questions,List.of());
                editor=new QuestionBankEditorView(bank,WorkspaceId.newId(),null,null,edited->{
                    try{Files.writeString(directory.resolve("saved-bank.json"),new QuestionBankV2Codec().write(edited));}
                    catch(Exception failure){throw new IllegalStateException(failure);}
                });
                root.setCenter(editor);
                stage.setOnCloseRequest(event->{event.consume();editor.prepareCloseAsync().whenComplete((ignored,failure)->Platform.runLater(()->{if(failure==null){editor.destroy();stage.hide();}}));});
                editor.ready().whenComplete((ignored,failure)->Platform.runLater(()->{
                    if(failure!=null){System.err.println("EDITOR_BACKEND_FAILED "+failure);editor.destroy();stage.hide();return;}
                    if(getParameters().getRaw().contains("--verify"))WebView2Verification.runEditor(editor,stage,directory,root);
                }));
            }catch(Exception failure){failure.printStackTrace();stage.hide();}
        }));
    }
    @Override public void stop(){ExtensionManager.getDefault().close();}
}
