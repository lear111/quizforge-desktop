package io.quizforge.core.port;

import io.quizforge.core.document.AssembledKnowledgeDocument;

public interface KnowledgeDocumentAssembler {
    String draftLanguage(String draft);

    AssembledKnowledgeDocument assemble(String draft, String title, String language,
            String existingAssetId);
}
