package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.document.qdoc.ContentBlock;
import io.quizforge.core.document.qdoc.ContentBlockType;
import io.quizforge.core.document.qdoc.DocumentElement;
import io.quizforge.core.document.qdoc.DocumentNode;
import io.quizforge.core.port.FileDocumentStorage;
import io.quizforge.core.port.FormalDocumentReader;
import io.quizforge.core.question.SourceDocumentSnapshot;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads QDoc revisions for QuestionBank generation; keeps legacy Markdown readable. */
public final class QDocFormalDocumentReader implements FormalDocumentReader {
    private final FileDocumentStorage files;
    private final FormalMarkdownDocumentReader legacy;
    private final QDocV1Codec codec = new QDocV1Codec();

    public QDocFormalDocumentReader(FileDocumentStorage files) {
        this.files = files;
        this.legacy = new FormalMarkdownDocumentReader(files);
    }

    @Override
    public SourceDocumentSnapshot read(WorkspaceId workspaceId, String relativePath) {
        if (!relativePath.toLowerCase(Locale.ROOT).endsWith(".qdoc")) {
            return legacy.read(workspaceId, relativePath);
        }
        var document = codec.parse(files.read(workspaceId, relativePath));
        List<SourceDocumentSnapshot.Chapter> chapters = new ArrayList<>();
        for (DocumentNode chapter : document.content()) {
            List<SourceDocumentSnapshot.Section> sections = new ArrayList<>();
            for (DocumentElement element : chapter.children()) {
                DocumentNode section = (DocumentNode) element;
                StringBuilder body = new StringBuilder();
                List<SourceDocumentSnapshot.Subsection> subsections = new ArrayList<>();
                for (DocumentElement child : section.children()) {
                    if (child instanceof ContentBlock block) append(body, block);
                    else if (child instanceof DocumentNode subsection) {
                        body.append("SUBSECTION\nsubsectionId: ").append(subsection.id())
                                .append("\ntitle: ").append(subsection.title()).append('\n');
                        StringBuilder subsectionBody = new StringBuilder();
                        for (DocumentElement item : subsection.children()) {
                            append(body, (ContentBlock) item);
                            append(subsectionBody, (ContentBlock) item);
                        }
                        subsections.add(new SourceDocumentSnapshot.Subsection(subsection.id(),
                                subsection.title(), subsectionBody.toString().trim()));
                    }
                }
                sections.add(new SourceDocumentSnapshot.Section(section.id(), section.title(),
                        body.toString().trim(), subsections));
            }
            chapters.add(new SourceDocumentSnapshot.Chapter(chapter.id(), chapter.title(), sections));
        }
        return new SourceDocumentSnapshot(document.id(), codec.contentId(document), document.title(), chapters);
    }

    private void append(StringBuilder target, ContentBlock block) {
        if (block.type() == ContentBlockType.BULLET_LIST || block.type() == ContentBlockType.ORDERED_LIST) {
            for (int i = 0; i < block.items().size(); i++) {
                target.append(block.type() == ContentBlockType.BULLET_LIST ? "- " : (i + 1) + ". ")
                        .append(block.items().get(i)).append('\n');
            }
        } else if (block.type() == ContentBlockType.CODE_BLOCK) {
            target.append("```").append(block.language() == null ? "" : block.language())
                    .append('\n').append(block.text()).append("\n```\n");
        } else {
            target.append(block.type() == ContentBlockType.QUOTE ? "> " : "")
                    .append(block.text()).append('\n');
        }
    }
}
