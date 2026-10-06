package io.quizforge.desktop.ui.question.source;

import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Each source has independent availability; refresh never replaces the surrounding editor/session. */
public final class QuestionSourceListView extends VBox {
    private final List<SourceRef> refs;
    private final WorkspaceId workspace;
    private final QuestionSourceNavigationAdapter sources;
    private final BiConsumer<Integer, HBox> editActions;

    public QuestionSourceListView(List<SourceRef> refs, WorkspaceId workspace,
            QuestionSourceNavigationAdapter sources, BiConsumer<Integer, HBox> editActions) {
        this.refs = List.copyOf(refs);
        this.workspace = workspace;
        this.sources = sources;
        this.editActions = editActions;
        getStyleClass().add("question-sources");
        refresh();
    }

    public void refresh() {
        getChildren().clear();
        setVisible(!refs.isEmpty());
        setManaged(!refs.isEmpty());
        if (refs.isEmpty()) return;
        List<QuestionSourceNavigationAdapter.Source> states = sources.inspect(workspace, refs);
        for (int i = 0; i < states.size(); i++) {
            var source = states.get(i);
            HBox row = QuestionSourceVisuals.row(new QuestionSourceVisuals.Presentation(source.label(),
                    source.message(), source.status(), source.navigable(), () -> sources.open(workspace, source.ref())),
                    "qbank-source-" + i);
            if (editActions != null) editActions.accept(i, row);
            getChildren().add(row);
        }
    }
    public List<java.util.Map<String,Object>> pageSources(){return sources.inspect(workspace,refs).stream().map(source->java.util.Map.<String,Object>of("label",source.label(),"message",source.message(),"navigable",source.navigable())).toList();}
    public void openSource(int index){sources.open(workspace,refs.get(index));}
}
