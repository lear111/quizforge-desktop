package io.quizforge.core.document.qdoc;

import java.util.Map;
import java.util.Set;

/** Allowed child kinds and minimum required structural children per template. */
public record DocumentSchema(Set<DocumentNodeType> rootTypes,
        Map<DocumentNodeType, Set<DocumentNodeType>> nodeChildren,
        Map<DocumentNodeType, Set<ContentBlockType>> blockChildren,
        Map<DocumentNodeType, DocumentNodeType> requiredChild) {
    public DocumentSchema {
        rootTypes = Set.copyOf(rootTypes);
        nodeChildren = Map.copyOf(nodeChildren);
        blockChildren = Map.copyOf(blockChildren);
        requiredChild = Map.copyOf(requiredChild);
    }

    public Set<DocumentNodeType> allowedNodes(DocumentNodeType parent) {
        return parent == null ? rootTypes : nodeChildren.getOrDefault(parent, Set.of());
    }

    public Set<ContentBlockType> allowedBlocks(DocumentNodeType parent) {
        return parent == null ? Set.of() : blockChildren.getOrDefault(parent, Set.of());
    }
}
