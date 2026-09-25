package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceFileKind;
import javafx.geometry.Bounds;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

final class AssetDetailsPopover {
    private final Popup popup = new Popup();
    private final VBox body = new VBox(12);
    private final javafx.scene.control.ScrollPane scroll = UiTheme.scroll(body);

    AssetDetailsPopover() {
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        body.setId("asset-details-popover");
        body.getStyleClass().add("asset-details");
        body.getStylesheets().add(UiTheme.stylesheet());
        body.setPrefWidth(340);
        body.setMaxWidth(340);
        scroll.getStylesheets().add(UiTheme.stylesheet());
        scroll.setPrefViewportWidth(340);
        popup.getContent().add(scroll);
    }

    void show(Button anchor, FilePresentation file, WorkspaceId workspace, QuestionBankReferenceResolver references) {
        if (popup.isShowing()) { hide(); return; }
        body.getChildren().clear();
        var entry = file.file().entry();
        body.getChildren().add(UiTheme.label(entry.name(), "field-label"));
        row("Type", file.kind() == WorkspaceFileKind.QUESTION_BANK ? "Question Bank" : "Standard Document");
        row("Asset ID", entry.assetId());
        if (file.kind() == WorkspaceFileKind.STANDARD_DOCUMENT) row("Content Revision", entry.contentId());
        row("Path", entry.relativePath());
        row("Status", file.draft() ? "空草稿 · 未通过正式格式校验" : "Valid");
        if (file.kind() == WorkspaceFileKind.QUESTION_BANK) {
            var bank = file.file().questionBank();
            row("Questions", Integer.toString(bank.questions().size()));
            row("References", bank.sourceDocuments().size() + " documents");
            try {
                var resolved = references.resolve(workspace, bank);
                if (resolved.isEmpty()) row("Reference Status", "No references");
                for (var resolution : resolved) {
                    String status = switch (resolution.status()) {
                        case EXACT_MATCH -> "Exact";
                        case EXACT_CONTENT_MATCH -> "Exact content";
                        case DIFFERENT_REVISION -> "Changed";
                        case MISSING -> "Missing";
                    };
                    row("Reference Status · " + resolution.source().title(), status + (resolution.ambiguous() ? " · ambiguous" : ""));
                }
            } catch (RuntimeException error) { row("Reference Status", "Unavailable · refresh the workspace"); }
        }
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        scroll.setPrefViewportHeight(Math.min(560, body.getChildren().size() * 59 + 20));
        if (bounds != null) popup.show(anchor.getScene().getWindow(), bounds.getMaxX() - 340, bounds.getMaxY() + 6);
    }

    void showFailure(Button anchor) {
        hide();
        body.getChildren().setAll(UiTheme.label("无法读取最新文件信息，请刷新工作区。", "muted"));
        scroll.setPrefViewportHeight(90);
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds != null) popup.show(anchor.getScene().getWindow(), bounds.getMaxX() - 340, bounds.getMaxY() + 6);
    }

    private void row(String title, String value) {
        body.getChildren().add(new VBox(3, UiTheme.label(title, "detail-key"),
                UiTheme.label(value == null || value.isBlank() ? "—" : value, "detail-value")));
    }

    void hide() { popup.hide(); }
    boolean isShowing() { return popup.isShowing(); }
    VBox content() { return body; }
}
