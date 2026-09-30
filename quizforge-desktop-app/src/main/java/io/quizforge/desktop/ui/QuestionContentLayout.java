package io.quizforge.desktop.ui;

import java.util.Map;

/** Shared authoring/preview layout semantics; the editor receives these values at load. */
final class QuestionContentLayout {
    static final int QUESTION_CONTENT_WIDTH=672;
    static final String FONT_FAMILY="Arial";
    static final int FONT_SIZE=16;
    static final int LINE_HEIGHT=25;
    static final int PARAGRAPH_SPACING=12;
    private QuestionContentLayout() { }
    static String editorConfig() {
        return RichContentEditorAdapter.json(Map.of("width",QUESTION_CONTENT_WIDTH,"fontFamily",FONT_FAMILY,
                "fontSize",FONT_SIZE,"lineHeight",LINE_HEIGHT,"paragraphSpacing",PARAGRAPH_SPACING));
    }
    static String previewStyle() {
        return "-fx-font-family:'"+FONT_FAMILY+"';-fx-font-size:"+FONT_SIZE+"px;-fx-line-spacing:"+
                Math.max(0,LINE_HEIGHT-FONT_SIZE-3)+"px;";
    }
}
