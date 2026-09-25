package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.FormalDocumentReader;
import io.quizforge.core.port.FileDocumentStorage;
import io.quizforge.core.question.SourceDocumentSnapshot;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.IncludeSourceSpans;

/** Extracts selectable sections only after the shared formal v1 validator succeeds. */
public final class FormalMarkdownDocumentReader implements FormalDocumentReader {
    private static final Pattern ID = Pattern.compile("(?s)\\s*<!--\\s*qf:id=([A-Za-z0-9_-]+)\\s*-->\\s*");
    private static final Parser MARKDOWN = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private final FileDocumentStorage files;
    private final StandardKnowledgeDocumentV1 contract = new StandardKnowledgeDocumentV1();

    public FormalMarkdownDocumentReader(FileDocumentStorage files) { this.files = files; }

    @Override
    public SourceDocumentSnapshot read(WorkspaceId workspaceId, String relativePath) {
        String source = files.read(workspaceId, relativePath).replace("\r\n", "\n").replace('\r', '\n');
        StandardKnowledgeDocumentV1.Parsed parsed = contract.parseIfStandard(source)
                .orElseThrow(() -> new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_INVALID,
                        "File is not a formal study document."));
        int frontEnd = source.indexOf("\n---\n", 4);
        String body = source.substring(frontEnd + 5);
        String[] lines = body.split("\n", -1);
        Node root = MARKDOWN.parse(body);
        List<SourceDocumentSnapshot.Chapter> chapters = new ArrayList<>();
        String chapterId = null;
        String chapterTitle = null;
        List<SourceDocumentSnapshot.Section> sections = new ArrayList<>();
        String sectionId = null;
        String sectionTitle = null;
        int sectionStart = -1;
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (!(node instanceof Heading heading)) continue;
            int line = heading.getSourceSpans().getFirst().getLineIndex();
            if (sectionId != null) {
                sections.add(new SourceDocumentSnapshot.Section(sectionId, sectionTitle,
                        String.join("\n", java.util.Arrays.copyOfRange(lines, sectionStart, line)).trim()));
                sectionId = null;
            }
            if (heading.getLevel() == 2) {
                if (chapterId != null) chapters.add(new SourceDocumentSnapshot.Chapter(chapterId, chapterTitle, sections));
                sections = new ArrayList<>();
                chapterId = idAfter(heading);
                chapterTitle = title(heading);
            } else if (heading.getLevel() == 3) {
                sectionId = idAfter(heading);
                sectionTitle = title(heading);
                Node idNode = heading.getNext();
                sectionStart = idNode.getSourceSpans().getLast().getLineIndex() + 1;
            }
        }
        if (sectionId != null) sections.add(new SourceDocumentSnapshot.Section(sectionId, sectionTitle,
                String.join("\n", java.util.Arrays.copyOfRange(lines, sectionStart, lines.length)).trim()));
        if (chapterId != null) chapters.add(new SourceDocumentSnapshot.Chapter(chapterId, chapterTitle, sections));
        return new SourceDocumentSnapshot(parsed.assetId(), parsed.contentId(), parsed.title(), chapters);
    }

    private String idAfter(Heading heading) {
        Matcher matcher = ID.matcher(((HtmlBlock) heading.getNext()).getLiteral());
        if (!matcher.matches()) throw new IllegalArgumentException("Missing heading ID");
        return matcher.group(1);
    }

    private String title(Node node) {
        StringBuilder out = new StringBuilder();
        append(node, out);
        return out.toString().trim();
    }

    private void append(Node node, StringBuilder out) {
        if (node instanceof Text value) out.append(value.getLiteral());
        else if (node instanceof Code value) out.append(value.getLiteral());
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) append(child, out);
    }
}
