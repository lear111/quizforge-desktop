package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Each source has independent availability; refresh never replaces the surrounding editor/session. */
final class QuestionSourceListView extends VBox {
    private final List<QuestionBankFile.SourceRef> refs;
    private final WorkspaceId workspace;
    private final QuestionSourceNavigationAdapter sources;
    private final BiConsumer<Integer, HBox> editActions;

    QuestionSourceListView(List<QuestionBankFile.SourceRef> refs, WorkspaceId workspace,
            QuestionSourceNavigationAdapter sources, BiConsumer<Integer, HBox> editActions) {
        this.refs = List.copyOf(refs);
        this.workspace = workspace;
        this.sources = sources;
        this.editActions = editActions;
        getStyleClass().add("question-sources");
        refresh();
    }

    void refresh() {
        getChildren().clear();
        if (refs.isEmpty()) return;
        List<QuestionSourceNavigationAdapter.Source> states = sources.inspect(workspace, refs);
        for (int i = 0; i < states.size(); i++) {
            var source = states.get(i);
            Button link = new Button(source.label());
            link.setId("qbank-source-" + i);
            link.setMaxWidth(Double.MAX_VALUE);
            link.setWrapText(true);
            link.getStyleClass().add("question-source-link");
            link.setDisable(!source.navigable());
            link.getProperties().put("quizforge.sourceStatus", source.status());
            link.setOnAction(event -> sources.open(workspace, source.ref()));
            if (!source.message().isEmpty()) link.setTooltip(new Tooltip(source.message()));
            HBox.setHgrow(link, Priority.ALWAYS);
            HBox row = new HBox(link);
            row.getStyleClass().add("question-source-row");
            if (!source.message().isEmpty()) {
                var state = UiTheme.label(source.message(), source.status()
                        == QuestionBankReferenceResolver.Status.DIFFERENT_REVISION
                        ? "question-source-warning" : "question-source-status");
                row.getChildren().add(state);
            }
            if (editActions != null) editActions.accept(i, row);
            getChildren().add(row);
        }
    }
}
