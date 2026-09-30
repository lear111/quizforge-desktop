package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionContent;
import java.util.List;
import java.util.Set;

record ContentEditResult(boolean saved,QuestionContent content,
        List<StagedContentResource> addedResources,Set<String> removedResourceReferences) {
    ContentEditResult {
        addedResources=List.copyOf(addedResources);removedResourceReferences=Set.copyOf(removedResourceReferences);
    }
    static ContentEditResult cancelled() {return new ContentEditResult(false,null,List.of(),Set.of());}
}
