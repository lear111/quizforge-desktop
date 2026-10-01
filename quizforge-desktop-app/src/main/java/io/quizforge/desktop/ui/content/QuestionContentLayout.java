package io.quizforge.desktop.ui.content;

/** Shared authoring/preview layout semantics; the editor receives these values at load. */
public final class QuestionContentLayout {
    public static final int QUESTION_CONTENT_WIDTH=672;
    public static final String FONT_FAMILY="Arial";
    public static final int FONT_SIZE=16;
    public static final int LINE_HEIGHT=25;
    public static final int PARAGRAPH_SPACING=12;
    private QuestionContentLayout() { }
    public static String previewStyle() {
        return "-fx-font-family:'"+FONT_FAMILY+"';-fx-font-size:"+FONT_SIZE+"px;-fx-line-spacing:"+
                Math.max(0,LINE_HEIGHT-FONT_SIZE-3)+"px;";
    }
}
