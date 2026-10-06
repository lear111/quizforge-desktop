package io.quizforge.desktop.ui.file;

import io.quizforge.desktop.ui.markdown.MarkdownOutline;
import javafx.scene.layout.Region;

/** Lifecycle and navigation used by the file-page router. */
interface FileView {
    default void dispose() { }
    default boolean prepareClose() { return true; }
    default java.util.concurrent.CompletionStage<Boolean> prepareCloseAsync(){return java.util.concurrent.CompletableFuture.completedFuture(prepareClose());}
    default void cancelClose(){}
    default boolean usesAsyncClose(){return false;}
    FilePresentation currentFile();
    FileMode mode();
    boolean hasUnsavedChanges();
    boolean saveUnsavedChanges();
    Region visibleOutline();
    void refreshSourceStatus();
    void refreshBrowseFromDisk();
    boolean jumpTo(MarkdownOutline.Kind kind,String label,int occurrence);
    boolean shouldRefresh();
    void refreshForDevelopment();
}
