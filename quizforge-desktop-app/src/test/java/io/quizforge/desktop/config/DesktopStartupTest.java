package io.quizforge.desktop.config;

import io.quizforge.core.workspace.service.WorkspaceService;
import io.quizforge.desktop.ui.shell.DesktopView;
import java.nio.file.Path;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class DesktopStartupTest {
    @TempDir Path temporary;

    @Test void productionCompositionOpensWithOwnedDataAndPackagedResources() throws Exception {
        String previous=System.getProperty("quizforge.dataDir");
        System.setProperty("quizforge.dataDir",temporary.resolve("app-data").toString());
        try(var context=new AnnotationConfigApplicationContext(DesktopConfiguration.class)) {
            context.getBean(WorkspaceService.class).createWorkspace("Startup smoke");
            var started=new CountDownLatch(1);
            try{Platform.startup(()->{Platform.setImplicitExit(false);started.countDown();});}
            catch(IllegalStateException running){Platform.runLater(started::countDown);}
            assertTrue(started.await(20,TimeUnit.SECONDS));
            var task=new FutureTask<Void>(()->{
                var stage=new Stage();stage.initStyle(StageStyle.UNDECORATED);stage.setOpacity(0);
                try{
                    var scene=context.getBean(DesktopView.class).createScene(stage);stage.setScene(scene);stage.show();
                    scene.getRoot().applyCss();scene.getRoot().layout();
                    assertNotNull(scene.lookup("#workspace-file-tree"));
                    assertNotNull(scene.lookup("#window-close"));
                    assertEquals(2,scene.getStylesheets().size());
                    assertNotNull(DesktopView.class.getResource("/editor/canvas/canvas-editor.html"));
                    assertNull(scene.getOnKeyPressed());
                }finally{stage.close();}
                return null;
            });Platform.runLater(task);task.get(40,TimeUnit.SECONDS);
        }finally{
            if(previous==null)System.clearProperty("quizforge.dataDir");else System.setProperty("quizforge.dataDir",previous);
        }
    }
}
