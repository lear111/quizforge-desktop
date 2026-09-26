package io.quizforge.desktop.ui;

import io.quizforge.core.document.qdoc.ContentBlock;
import io.quizforge.core.document.qdoc.ContentBlockType;
import io.quizforge.core.document.qdoc.DocumentElement;
import io.quizforge.core.document.qdoc.DocumentNode;
import io.quizforge.core.document.qdoc.QDocDocument;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Read-only rendering of structured content. Numbering is presentation, never file data. */
final class QDocDocumentView {
    Node view(QDocDocument document) {
        VBox page = new VBox(12);
        page.setId("qdoc-browse-view");
        page.getStyleClass().add("markdown-preview");
        page.setMaxWidth(820);
        page.getChildren().add(UiTheme.label(document.title(), "preview-heading-1"));
        int chapterNumber = 0;
        for (DocumentNode chapter : document.content()) {
            chapterNumber++;
            page.getChildren().add(UiTheme.label(chapterNumber + " " + chapter.title(), "preview-heading-2"));
            int sectionNumber = 0;
            for (DocumentElement element : chapter.children()) {
                if (element instanceof ContentBlock block) { show(page, block); continue; }
                DocumentNode section = (DocumentNode) element;
                sectionNumber++;
                page.getChildren().add(UiTheme.label(chapterNumber + "." + sectionNumber + " "
                        + section.title(), "preview-heading-3"));
                int subsectionNumber = 0;
                for (DocumentElement child : section.children()) {
                    if (child instanceof ContentBlock block) show(page, block);
                    else if (child instanceof DocumentNode subsection) {
                        subsectionNumber++;
                        page.getChildren().add(UiTheme.label(chapterNumber + "." + sectionNumber + "."
                                + subsectionNumber + " " + subsection.title(), "preview-heading-3"));
                        for (DocumentElement item : subsection.children()) show(page, (ContentBlock) item);
                    }
                }
            }
        }
        StackPane centered = new StackPane(page);
        centered.setAlignment(Pos.TOP_CENTER);
        return UiTheme.scroll(centered);
    }

    private void show(VBox page, ContentBlock block) {
        if (block.type() == ContentBlockType.BULLET_LIST || block.type() == ContentBlockType.ORDERED_LIST) {
            for (int i = 0; i < block.items().size(); i++) {
                String marker = block.type() == ContentBlockType.BULLET_LIST ? "• " : (i + 1) + ". ";
                page.getChildren().add(UiTheme.label(marker + block.items().get(i), "preview-paragraph"));
            }
        } else {
            String value = block.type() == ContentBlockType.QUOTE ? "❝ " + block.text() : block.text();
            page.getChildren().add(UiTheme.label(value,
                    block.type() == ContentBlockType.CODE_BLOCK ? "preview-code" : "preview-paragraph"));
        }
    }
}
