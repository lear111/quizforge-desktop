package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import java.util.HashMap;

/** Checks the current Canvas conversion without coupling authoring to the retired editor. */
public final class ContentEditingSupport {
    private ContentEditingSupport() { }
    public static boolean supports(QuestionContent content) {
        if(content instanceof DocumentContent) return true;
        try {
            var images=new HashMap<String,CanvasEditorAdapter.ImageData>();
            QuestionContentData.resourceIds(content).forEach(id->images.put(id,new CanvasEditorAdapter.ImageData("data:image/png;base64,AA==",1,1)));
            CanvasEditorAdapter.toCanvasJson(content,images);
            return true;
        } catch (IllegalArgumentException failure) { return false; }
    }
}
