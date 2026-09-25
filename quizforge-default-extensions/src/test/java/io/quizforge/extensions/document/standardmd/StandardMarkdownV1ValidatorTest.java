package io.quizforge.extensions.document.standardmd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.extension.document.DocumentValidationResult;
import org.junit.jupiter.api.Test;

class StandardMarkdownV1ValidatorTest {
    private final StandardMarkdownV1Validator validator = new StandardMarkdownV1Validator();
    private static final String VALID = """
            ---
            quizforge_version: "1.0"
            title: "Java 集合"
            language: "zh-CN"
            ---

            # Java 集合

            ## 1. Collection

            ### 1.1 概念

            正文，含 **强调** 和 `code`。
            """;

    @Test void validDocumentPasses() { assertTrue(validator.validate(VALID).valid()); }
    @Test void missingFrontMatter() { assertError("# Java 集合\n", "MISSING_FRONT_MATTER"); }
    @Test void wrongVersion() { assertError(VALID.replace("\"1.0\"", "\"2.0\""), "INVALID_SCHEMA_VERSION"); }
    @Test void emptyTitle() { assertError(VALID.replace("title: \"Java 集合\"", "title: \" \""), "MISSING_TITLE"); }
    @Test void multipleH1() { assertError(VALID + "\n# 另一个标题\n", "MULTIPLE_H1"); }
    @Test void titleMismatch() { assertError(VALID.replace("# Java 集合", "# 不同标题"), "TITLE_MISMATCH"); }
    @Test void missingChapter() { assertError(VALID.replace("## 1. Collection", "正文"), "MISSING_CHAPTER"); }
    @Test void chapterWithoutSection() { assertError(VALID + "\n## 空章节\n", "CHAPTER_WITHOUT_SECTION"); }
    @Test void emptySection() { assertError(VALID + "\n### 空节\n", "EMPTY_SECTION"); }
    @Test void invalidHeadingLevel() { assertError(VALID + "\n#### 四级标题\n", "INVALID_HEADING_LEVEL"); }

    @Test
    void codeBlockHeadingsAreNotStructural() {
        String candidate = VALID + "\n```markdown\n# fake\n#### fake\n```\n";
        assertTrue(validator.validate(candidate).valid());
    }

    @Test
    void codeOnlySectionIsValid() {
        String candidate = VALID.replace("正文，含 **强调** 和 `code`。", "```java\nint x = 1;\n```");
        assertTrue(validator.validate(candidate).valid());
    }

    private void assertError(String candidate, String expected) {
        DocumentValidationResult result = validator.validate(candidate);
        assertFalse(result.valid());
        assertTrue(result.errors().contains(expected), () -> result.errors().toString());
    }
}
