package io.quizforge.desktop.ui.content;

import io.quizforge.core.question.content.QuestionContent;
import java.util.List;
import java.util.Set;

public record ContentEditResult(boolean saved,QuestionContent content,
        List<StagedContentResource> addedResources,Set<String> removedResourceReferences) {
    public ContentEditResult {
        addedResources=List.copyOf(addedResources);removedResourceReferences=Set.copyOf(removedResourceReferences);
    }
    public static ContentEditResult cancelled() {return new ContentEditResult(false,null,List.of(),Set.of());}
}
