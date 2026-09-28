package io.quizforge.desktop.ui;

import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.WorkspaceId;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Source-only refresh preserves the surrounding archived question, attempt and scroll position. */
final class HistorySourceListView extends VBox {
    private final WorkspaceId workspace;
    private final PracticePayload snapshot;
    private final HistorySourceNavigationAdapter sources;

    HistorySourceListView(WorkspaceId workspace, PracticePayload snapshot, HistorySourceNavigationAdapter sources) {
        this.workspace = workspace;
        this.snapshot = snapshot;
        this.sources = sources;
        setId("history-sources");
        getStyleClass().add("history-source-section");
        refresh();
    }

    void refresh() {
        getChildren().clear();
        getChildren().add(UiTheme.label("来源", "section-title"));
        try {
            var states = sources.inspect(workspace, snapshot);
            setVisible(!states.isEmpty());
            setManaged(!states.isEmpty());
            for (int i = 0; i < states.size(); i++) {
                var source = states.get(i);
                VBox item = new VBox();
                item.getStyleClass().add("history-source-item");
                javafx.scene.control.Labeled name;
                if (source.navigable()) {
                    Button link = new Button(source.label());
                    link.getStyleClass().add("history-source-link");
                    link.setOnAction(event -> sources.open(workspace, source.ref()));
                    name = link;
                } else {
                    Label unavailable = UiTheme.label(source.label(), "history-source-disabled");
                    unavailable.setDisable(true);
                    name = unavailable;
                }
                name.setWrapText(true);
                name.setId("history-source-" + i);
                name.getProperties().put("quizforge.sourceStatus", source.status());
                item.getChildren().add(name);
                if (!source.message().isEmpty()) item.getChildren().add(UiTheme.label(source.message(),
                        source.status() == QuestionBankReferenceResolver.Status.DIFFERENT_REVISION
                                ? "history-source-warning" : "history-source-status"));
                getChildren().add(item);
            }
        } catch (RuntimeException error) {
            setVisible(true);
            setManaged(true);
            getChildren().add(UiTheme.label("来源暂不可读取", "history-source-status"));
        }
    }
}
