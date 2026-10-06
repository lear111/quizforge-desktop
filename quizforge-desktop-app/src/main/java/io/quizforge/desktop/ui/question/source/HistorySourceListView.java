package io.quizforge.desktop.ui.question.source;

import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.scene.layout.VBox;

/** Source-only refresh preserves the surrounding archived question, attempt and scroll position. */
public final class HistorySourceListView extends VBox {
    private final WorkspaceId workspace;
    private final PracticePayload snapshot;
    private final HistorySourceNavigationAdapter sources;

    public HistorySourceListView(WorkspaceId workspace, PracticePayload snapshot, HistorySourceNavigationAdapter sources) {
        this.workspace = workspace;
        this.snapshot = snapshot;
        this.sources = sources;
        setId("history-sources");
        getStyleClass().add("question-sources");
        refresh();
    }

    public void refresh() {
        getChildren().clear();
        try {
            var states = sources.inspect(workspace, snapshot);
            setVisible(!states.isEmpty());
            setManaged(!states.isEmpty());
            for (int i = 0; i < states.size(); i++) {
                var source = states.get(i);
                getChildren().add(QuestionSourceVisuals.row(new QuestionSourceVisuals.Presentation(source.label(),
                        source.message(), source.status(), source.navigable(), () -> sources.open(workspace, source.ref())),
                        "history-source-" + i));
            }
        } catch (RuntimeException error) {
            setVisible(true);
            setManaged(true);
            getChildren().add(UiTheme.label("来源暂不可读取", "question-source-status"));
        }
    }
    public java.util.List<java.util.Map<String,Object>> pageSources(){return sources.inspect(workspace,snapshot).stream().map(source->java.util.Map.<String,Object>of("label",source.label(),"message",source.message(),"navigable",source.navigable())).toList();}
    public void openSource(int index){sources.open(workspace,sources.inspect(workspace,snapshot).get(index).ref());}
}
