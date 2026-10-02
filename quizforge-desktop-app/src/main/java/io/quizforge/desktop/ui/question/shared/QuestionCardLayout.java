package io.quizforge.desktop.ui.question.shared;

import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shared reading geometry and navigation visuals; every caller keeps its own actions. */
public final class QuestionCardLayout {
    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");
    private QuestionCardLayout() { }

    public static boolean confirmSubmission(Node owner,int unanswered){
        var cancel=new ButtonType("继续作答",ButtonBar.ButtonData.CANCEL_CLOSE);
        var accept=new ButtonType("提交",ButtonBar.ButtonData.OK_DONE);
        var dialog=new Alert(Alert.AlertType.CONFIRMATION,
                "提交后本次作答将锁定，若要修改需要重新答题。",cancel,accept);
        dialog.setTitle("确认提交");
        dialog.setHeaderText(unanswered>0?"还有 "+unanswered+" 道小题未作答，确定提交吗？":"确认提交这道题的答案？");
        dialog.getDialogPane().setId("question-submit-confirmation");
        if(owner.getScene()!=null)dialog.initOwner(owner.getScene().getWindow());
        UiTheme.apply(dialog);
        ((Button)dialog.getDialogPane().lookupButton(accept)).setDefaultButton(false);
        ((Button)dialog.getDialogPane().lookupButton(cancel)).setDefaultButton(true);
        return dialog.showAndWait().orElse(cancel)==accept;
    }

    public static void configure(VBox reader) {
        reader.getStyleClass().add("question-reader");
        reader.setMinWidth(0);
        reader.setMaxHeight(Region.USE_PREF_SIZE);
        reader.widthProperty().addListener((ignored, before, width) ->
                reader.pseudoClassStateChanged(COMPACT, width.doubleValue() < 520));
    }

    public static HBox row(Node previous, VBox card, Node next) {
        HBox.setHgrow(card, Priority.ALWAYS);
        HBox row = new NavigationRow(previous, card, next);
        row.getStyleClass().add("question-navigation");
        row.setMinWidth(0);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    /** Pin navigation to the visible reader, while the card remains in the scroll content. */
    private static final class NavigationRow extends HBox {
        private final Node previous;
        private final Node next;
        private ScrollPane scroll;
        private final ChangeListener<Bounds> viewportChanged = (o, before, after) -> positionNavigation();

        NavigationRow(Node previous, VBox card, Node next) {
            super(14, previous, card, next);
            this.previous = previous;this.next = next;
            localToSceneTransformProperty().addListener((o, before, after) -> positionNavigation());
            sceneProperty().addListener((o, before, after) -> {
                if (after == null) observeScroll(null);
                else javafx.application.Platform.runLater(this::positionNavigation);
            });
        }

        @Override protected void layoutChildren() {
            super.layoutChildren();
            positionNavigation();
        }

        private void observeScroll(ScrollPane current) {
            if (scroll == current) return;
            if (scroll != null) scroll.viewportBoundsProperty().removeListener(viewportChanged);
            scroll = current;
            if (scroll != null) scroll.viewportBoundsProperty().addListener(viewportChanged);
        }

        private void positionNavigation() {
            Node viewport = null;ScrollPane current = null;
            for (Node parent = getParent();parent != null;parent = parent.getParent()) {
                if (parent.getStyleClass().contains("viewport")) viewport = parent;
                if (parent instanceof ScrollPane pane) { current = pane;break; }
            }
            observeScroll(getScene() == null ? null : current);
            if (scroll == null || viewport == null) {
                previous.setTranslateY(0);next.setTranslateY(0);return;
            }
            double centerY = sceneToLocal(viewport.localToScene(0,
                    viewport.getLayoutBounds().getHeight() / 2)).getY();
            pin(previous, centerY);pin(next, centerY);
        }

        private void pin(Node node, double centerY) {
            if (node instanceof Button && node.getStyleClass().contains("question-navigation-button"))
                node.setTranslateY(centerY - node.getLayoutY() - node.getLayoutBounds().getMinY()
                        - node.getLayoutBounds().getHeight() / 2);
        }
    }

    public static Button navigation(String icon, String description, Runnable action) {
        Button button = UiTheme.iconButton(icon, description, action);
        button.getStyleClass().add("question-navigation-button");
        button.visibleProperty().bind(button.disableProperty().not());
        return button;
    }

    public static ScrollPane scroll(VBox content) {
        StackPane aligned = new StackPane(content);
        aligned.setMinWidth(0);
        aligned.setAlignment(Pos.CENTER);
        ScrollPane scroll = UiTheme.scroll(aligned);
        scroll.setMinWidth(0);
        aligned.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> scroll.getViewportBounds().getHeight(), scroll.viewportBoundsProperty()));
        return scroll;
    }
}
