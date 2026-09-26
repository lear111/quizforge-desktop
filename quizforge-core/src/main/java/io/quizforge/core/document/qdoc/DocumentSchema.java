package io.quizforge.core.document.qdoc;

import java.util.Map;
import java.util.Set;

/** Allowed child kinds and minimum required structural children per template. */
public record DocumentSchema(Set<DocumentNodeType> rootTypes,
        Map<DocumentNodeType, Set<DocumentNodeType>> nodeChildren,
        Set<DocumentNodeType> blockParents,
        Map<DocumentNodeType, DocumentNodeType> requiredChild) {
    public DocumentSchema {
        rootTypes = Set.copyOf(rootTypes);
        nodeChildren = Map.copyOf(nodeChildren);
        blockParents = Set.copyOf(blockParents);
        requiredChild = Map.copyOf(requiredChild);
    }
}
