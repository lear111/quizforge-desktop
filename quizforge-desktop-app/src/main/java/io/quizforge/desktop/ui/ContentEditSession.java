package io.quizforge.desktop.ui;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.*;
import io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Draft resources remain private until Save returns a result to the owning editor. */
final class ContentEditSession {
    private final QuestionContent original;
    private final List<QBankResource> existing;
    private final QuestionResourceInput input;
    private final Map<String,QBankImageImporter.ImportedImage> staged=new LinkedHashMap<>();
    ContentEditSession(QuestionContent original,List<QBankResource> existing,QuestionResourceInput input) {
        this.original=Objects.requireNonNull(original);this.existing=List.copyOf(existing);this.input=input;
    }
    QBankResource stage(Path path) {
        var image=new QBankImageImporter().read(path);staged.put(image.resource().id(),image);return image.resource();
    }
    List<QBankResource> resources() {
        var all=new ArrayList<>(existing);staged.values().forEach(i->all.add(i.resource()));return List.copyOf(all);
    }
    InputStream open(QBankResource resource) throws IOException {
        var image=staged.get(resource.id());return image==null?input.open(resource):image.open();
    }
    ContentEditResult save(QuestionContent content) {
        var used=QuestionContentData.imageIds(content);
        var known=new HashSet<String>();resources().forEach(r->known.add(r.id()));
        if(!known.containsAll(used))throw new IllegalArgumentException("编辑内容引用未知图片资源");
        var added=staged.values().stream().filter(i->used.contains(i.resource().id())).toList();
        var removed=new HashSet<>(QuestionContentData.imageIds(original));removed.removeAll(used);
        return new ContentEditResult(true,content,added,removed);
    }
    ContentEditResult cancel() {staged.clear();return ContentEditResult.cancelled();}
}
