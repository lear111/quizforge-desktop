package io.quizforge.desktop.ui.content.document.canvas;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** Native lifecycle, image and clipboard API exposed to the editor page. */
public final class CanvasEditorImageHost {
    private final Runnable chooseImage;
    private final Runnable editorReady;
    private final Consumer<String> errors;
    private final Supplier<String> clipboardReader;
    private final BiPredicate<String,String> clipboardWriter;
    private java.util.function.DoubleConsumer heightListener;
    private Runnable changeListener;
    private java.util.function.BiConsumer<Integer,String> clozeListener;

    public CanvasEditorImageHost(Runnable chooseImage) {
        this(chooseImage, () -> {});
    }

    CanvasEditorImageHost(Runnable chooseImage, Runnable editorReady) {
        this(chooseImage,editorReady,ignored->{},null,null);
    }
    CanvasEditorImageHost(Runnable chooseImage,Runnable editorReady,Consumer<String> errors) {
        this(chooseImage,editorReady,errors,null,null);
    }
    CanvasEditorImageHost(Runnable chooseImage,Runnable editorReady,Consumer<String> errors,
            Supplier<String> reader,BiPredicate<String,String> writer) {
        this.chooseImage = Objects.requireNonNull(chooseImage);
        this.editorReady = Objects.requireNonNull(editorReady);
        this.errors=Objects.requireNonNull(errors);clipboardReader=reader;clipboardWriter=writer;
    }

    public void chooseImage() {
        Platform.runLater(chooseImage);
    }

    public void editorReady() { Platform.runLater(editorReady); }

    /** Called only by an explicit editor copy/paste action on the FX thread. */
    public boolean writeClipboard(String text,String html) {
        if(clipboardWriter!=null)return clipboardWriter.test(text,html);
        var content=new ClipboardContent();content.putString(text==null?"":text);
        if(html!=null && !html.isBlank())content.putHtml(html);
        return Clipboard.getSystemClipboard().setContent(content);
    }
    public String readClipboard() {
        if(clipboardReader!=null)return clipboardReader.get();
        var clipboard=Clipboard.getSystemClipboard();
        var data=new java.util.LinkedHashMap<String,Object>();
        data.put("text",clipboard.hasString()?clipboard.getString():"");
        data.put("html",clipboard.hasHtml()?clipboard.getHtml():"");
        if(clipboard.hasImage())data.put("image",CanvasClipboardImage.read(clipboard.getImage()));
        return ContentJson.write(data);
    }
    public void clipboardError(String message) { if(errors!=null)errors.accept(message); }
    void onHeight(java.util.function.DoubleConsumer listener) { heightListener=listener; }
    public void contentHeight(double height) { if(heightListener!=null && Double.isFinite(height))heightListener.accept(height); }
    void onChange(Runnable listener) { changeListener=listener; }
    public void contentChanged() { if(changeListener!=null)changeListener.run(); }
    void onCloze(java.util.function.BiConsumer<Integer,String> listener){clozeListener=listener;}
    public void clozeSelected(int number,String optionId){
        var listener=clozeListener;
        if(listener!=null)Platform.runLater(()->{if(clozeListener==listener)listener.accept(number,optionId);});
    }
}
