package io.quizforge.desktop.ui.question.shared;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.desktop.ui.content.StagedContentResource;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Explicit collaboration contract for type fields inside one bank editor session. */
public record QuestionEditorContext(QuestionBankEditorModel model,int index,VBox body,HBox navigation,Region spacer,
        Map<String,StagedContentResource> imported,List<Runnable> validators,VBox errors,
        QuestionResourceInput resources,Supplier<Window> owner,Runnable refresh) { }
