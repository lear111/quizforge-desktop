package io.quizforge.extensions.document.standardmd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quizforge.extension.document.DocumentValidationResult;
import io.quizforge.extension.document.DocumentValidator;
import io.quizforge.extension.document.DocumentStructureParser;
import io.quizforge.extension.document.StandardDocumentStructure;
import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.IncludeSourceSpans;

public final class StandardMarkdownV1Validator implements DocumentValidator, DocumentStructureParser {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private final Parser parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();

    @Override
    public StandardDocumentStructure parse(String candidateContent) {
        DocumentValidationResult validation = validate(candidateContent);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Invalid standard document: " + String.join(", ", validation.errors()));
        }
        String content = candidateContent.replace("\r\n", "\n");
        int frontEnd = content.indexOf("\n---\n", 4);
        if (frontEnd < 0) {
            frontEnd = content.length() - 4;
        }
        String body = content.substring(Math.min(content.length(), frontEnd + 5));
        int[] starts = lineStarts(body);
        Node root = parser.parse(body);
        List<StandardDocumentStructure.Chapter> chapters = new ArrayList<>();
        List<StandardDocumentStructure.Section> sections = new ArrayList<>();
        String chapterTitle = null;
        String chapterId = null;
        String sectionTitle = null;
        String sectionId = null;
        int chapterStart = 0;
        int sectionStart = 0;
        int chapterNumber = 0;
        int sectionNumber = 0;
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (!(node instanceof Heading heading) || heading.getLevel() < 2 || heading.getLevel() > 3) {
                continue;
            }
            int start = starts[heading.getSourceSpans().getFirst().getLineIndex()];
            if (sectionTitle != null) {
                sections.add(new StandardDocumentStructure.Section(sectionId, sectionTitle,
                        body.substring(sectionStart, start).stripTrailing()));
                sectionTitle = null;
            }
            if (heading.getLevel() == 2) {
                if (chapterTitle != null) {
                    chapters.add(new StandardDocumentStructure.Chapter(chapterId, chapterTitle,
                            body.substring(chapterStart, start).stripTrailing(), sections));
                    sections = new ArrayList<>();
                }
                chapterNumber++;
                sectionNumber = 0;
                chapterId = "chapter-" + chapterNumber;
                chapterTitle = textOf(heading).trim();
                chapterStart = start;
            } else {
                sectionNumber++;
                sectionId = chapterId + "-section-" + sectionNumber;
                sectionTitle = textOf(heading).trim();
                sectionStart = start;
            }
        }
        if (sectionTitle != null) {
            sections.add(new StandardDocumentStructure.Section(sectionId, sectionTitle,
                    body.substring(sectionStart).stripTrailing()));
        }
        if (chapterTitle != null) {
            chapters.add(new StandardDocumentStructure.Chapter(chapterId, chapterTitle,
                    body.substring(chapterStart).stripTrailing(), sections));
        }
        return new StandardDocumentStructure(validation.title(), content, chapters);
    }

    private int[] lineStarts(String body) {
        int count = 1;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '\n') count++;
        }
        int[] starts = new int[count];
        int line = 1;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '\n' && line < count) starts[line++] = i + 1;
        }
        return starts;
    }

    @Override
    public String formatId() {
        return StandardMarkdownV1Processor.FORMAT_ID;
    }

    @Override
    public String formatVersion() {
        return StandardMarkdownV1Processor.FORMAT_VERSION;
    }

    @Override
    public DocumentValidationResult validate(String candidateContent) {
        List<String> errors = new ArrayList<>();
        if (candidateContent == null || !candidateContent.startsWith("---\n")
                && !candidateContent.startsWith("---\r\n")) {
            return new DocumentValidationResult("", List.of("MISSING_FRONT_MATTER"));
        }
        String content = candidateContent.replace("\r\n", "\n");
        int end = content.indexOf("\n---\n", 4);
        if (end < 0 && content.endsWith("\n---")) {
            end = content.length() - 4;
        }
        if (end < 0) {
            return new DocumentValidationResult("", List.of("MISSING_FRONT_MATTER"));
        }
        String title = "";
        try {
            JsonNode frontMatter = YAML.readTree(content.substring(4, end));
            if (frontMatter == null || !frontMatter.isObject()) {
                errors.add("INVALID_FRONT_MATTER");
            } else {
                if (!"1.0".equals(value(frontMatter, "quizforge_version"))) {
                    errors.add("INVALID_SCHEMA_VERSION");
                }
                title = value(frontMatter, "title").trim();
                if (title.isEmpty()) {
                    errors.add("MISSING_TITLE");
                }
                if (value(frontMatter, "language").trim().isEmpty()) {
                    errors.add("MISSING_LANGUAGE");
                }
            }
        } catch (Exception e) {
            errors.add("INVALID_FRONT_MATTER");
        }

        String body = content.substring(Math.min(content.length(), end + 5));
        Node root = parser.parse(body);
        int h1Count = 0;
        int h2Count = 0;
        boolean chapterHasSection = false;
        boolean inChapter = false;
        boolean inSection = false;
        boolean sectionHasBody = false;
        String h1Title = "";
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                if (inSection && !sectionHasBody) {
                    errors.add("EMPTY_SECTION");
                }
                inSection = false;
                sectionHasBody = false;
                int level = heading.getLevel();
                if (level == 1) {
                    h1Count++;
                    if (h1Count == 1) {
                        h1Title = textOf(heading).trim();
                    }
                    if (inChapter && !chapterHasSection) {
                        errors.add("CHAPTER_WITHOUT_SECTION");
                    }
                    inChapter = false;
                } else if (level == 2) {
                    if (inChapter && !chapterHasSection) {
                        errors.add("CHAPTER_WITHOUT_SECTION");
                    }
                    if (h1Count != 1) {
                        errors.add("INVALID_HEADING_LEVEL");
                    }
                    h2Count++;
                    inChapter = true;
                    chapterHasSection = false;
                } else if (level == 3) {
                    if (!inChapter || h1Count != 1) {
                        errors.add("INVALID_HEADING_LEVEL");
                    } else {
                        chapterHasSection = true;
                    }
                    inSection = true;
                } else {
                    errors.add("INVALID_HEADING_LEVEL");
                }
            } else if (inSection && hasContent(node)) {
                sectionHasBody = true;
            }
        }
        if (inSection && !sectionHasBody) {
            errors.add("EMPTY_SECTION");
        }
        if (inChapter && !chapterHasSection) {
            errors.add("CHAPTER_WITHOUT_SECTION");
        }
        if (h1Count == 0) {
            errors.add("MISSING_H1");
        } else if (h1Count > 1) {
            errors.add("MULTIPLE_H1");
        }
        if (h2Count == 0) {
            errors.add("MISSING_CHAPTER");
        }
        if (!title.isEmpty() && h1Count == 1 && !title.equals(h1Title)) {
            errors.add("TITLE_MISMATCH");
        }
        return new DocumentValidationResult(title, errors);
    }

    private String value(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isValueNode() && !value.isNull() ? value.asText() : "";
    }

    private boolean hasContent(Node node) {
        if (node instanceof FencedCodeBlock block) {
            return !block.getLiteral().isBlank();
        }
        if (node instanceof IndentedCodeBlock block) {
            return !block.getLiteral().isBlank();
        }
        if (node instanceof HtmlBlock block) {
            return !block.getLiteral().isBlank();
        }
        return !textOf(node).isBlank();
    }

    private String textOf(Node node) {
        StringBuilder result = new StringBuilder();
        appendText(node, result);
        return result.toString();
    }

    private void appendText(Node node, StringBuilder result) {
        if (node instanceof Text text) {
            result.append(text.getLiteral());
        } else if (node instanceof Code code) {
            result.append(code.getLiteral());
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            appendText(child, result);
        }
    }
}
