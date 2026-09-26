package io.quizforge.core.document.qdoc;

import java.util.Map;
import java.util.Set;

public record DocumentTemplate(String id, String version, DocumentSchema schema) {
    public static final DocumentTemplate GENERAL_KNOWLEDGE = new DocumentTemplate(
            "quizforge-general-knowledge", "1.0",
            new DocumentSchema(Set.of(DocumentNodeType.CHAPTER),
                    Map.of(DocumentNodeType.CHAPTER, Set.of(DocumentNodeType.SECTION),
                            DocumentNodeType.SECTION, Set.of(DocumentNodeType.SUBSECTION),
                            DocumentNodeType.SUBSECTION, Set.of()),
                    Map.of(DocumentNodeType.CHAPTER, Set.of(ContentBlockType.PARAGRAPH),
                            DocumentNodeType.SECTION, Set.of(ContentBlockType.PARAGRAPH,
                                    ContentBlockType.BULLET_LIST, ContentBlockType.ORDERED_LIST,
                                    ContentBlockType.CODE_BLOCK, ContentBlockType.QUOTE),
                            DocumentNodeType.SUBSECTION, Set.of(ContentBlockType.PARAGRAPH,
                                    ContentBlockType.BULLET_LIST, ContentBlockType.ORDERED_LIST,
                                    ContentBlockType.CODE_BLOCK, ContentBlockType.QUOTE)),
                    Map.of(DocumentNodeType.CHAPTER, DocumentNodeType.SECTION)));

    public QDocDocument.TemplateRef reference() { return new QDocDocument.TemplateRef(id, version); }
}
