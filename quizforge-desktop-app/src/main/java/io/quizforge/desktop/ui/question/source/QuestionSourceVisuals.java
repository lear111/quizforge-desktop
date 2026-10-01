package io.quizforge.desktop.ui.question.source;

import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Source visuals consume display values and an action, never a live or archived SourceRef. */
final class QuestionSourceVisuals {
    record Presentation(String label, String message, QuestionBankReferenceResolver.Status status,
            boolean navigable, Runnable open) { }
    private QuestionSourceVisuals() { }

    static HBox row(Presentation source, String id) {
        Button link = new Button(source.label());
        link.setId(id);
        link.setMaxWidth(Double.MAX_VALUE);
        link.setWrapText(true);
        link.getStyleClass().add("question-source-link");
        link.setDisable(!source.navigable());
        link.getProperties().put("quizforge.sourceStatus", source.status());
        if (source.navigable()) link.setOnAction(event -> source.open().run());
        if (!source.message().isEmpty()) link.setTooltip(new Tooltip(source.message()));
        HBox.setHgrow(link, Priority.ALWAYS);
        HBox row = new HBox(link);
        row.getStyleClass().add("question-source-row");
        if (!source.message().isEmpty()) row.getChildren().add(UiTheme.label(source.message(), source.status()
                == QuestionBankReferenceResolver.Status.DIFFERENT_REVISION
                ? "question-source-warning" : "question-source-status"));
        return row;
    }
}
