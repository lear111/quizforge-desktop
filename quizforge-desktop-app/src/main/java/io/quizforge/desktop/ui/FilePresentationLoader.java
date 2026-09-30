package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quizforge.infrastructure.filesystem.RegisteredMarkdownCodec;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.workspace.*;
import java.util.HashSet;
import java.util.Set;

/** Read-only draft presentation. Formal classification, validation and indexing stay in Step 4. */
final class FilePresentationLoader {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final WorkspaceFileService files;
    private final WorkspaceFileCatalog catalog;
    private final SafeMarkdownPreview markdown = new SafeMarkdownPreview();
    private final RegisteredMarkdownCodec registeredMarkdown = new RegisteredMarkdownCodec();

    FilePresentationLoader(WorkspaceFileService files, WorkspaceFileCatalog catalog) {
        this.files = files;
        this.catalog = catalog;
    }

    FilePresentation load(WorkspaceId workspace, String path) {
        OpenedWorkspaceFile file = files.open(workspace, path);
        var kind = file.entry().kind();
        if (kind == WorkspaceFileKind.STANDARD_DOCUMENT) {
            var registered = registeredMarkdown.parseIfRegistered(file.sourceText(), path);
            return new FilePresentation(file, emptyMarkdown(file.sourceText()), false,
                    registered.orElse(null));
        }
        if (kind == WorkspaceFileKind.QUESTION_BANK) return new FilePresentation(file, file.questionBank().questions().isEmpty(), false);
        if (kind == WorkspaceFileKind.INVALID_STANDARD_DOCUMENT || kind == WorkspaceFileKind.INVALID_QUESTION_BANK) {
            try {
                FilePresentation draft = kind == WorkspaceFileKind.INVALID_STANDARD_DOCUMENT
                        ? documentDraft(file, catalog.readText(workspace, path))
                        : bankDraft(file, catalog.readBank(workspace, path));
                if (draft != null) return draft;
            } catch (Exception invalidDraft) {
                // Keep the original invalid-file presentation and its authoritative error.
            }
        }
        return new FilePresentation(file, false, false);
    }
    io.quizforge.core.port.QuestionResourceInput resources(WorkspaceId workspace,String path) {
        return resource->catalog.openResource(workspace,path,resource);
    }

    private FilePresentation documentDraft(OpenedWorkspaceFile original, String source) throws Exception {
        String issue = original.entry().issue();
        if (!("EMPTY_SECTION".equals(issue) || "INVALID_DOCUMENT_STRUCTURE".equals(issue))) return null;
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        int end = normalized.indexOf("\n---\n", 4);
        if (!normalized.startsWith("---\n") || end < 0 || !emptyMarkdown(normalized)) return null;
        JsonNode front = YAML.readTree(normalized.substring(4, end));
        if (!"study-document".equals(front.path("quizforge_format").asText())
                || !"1.0".equals(front.path("schema_version").asText())
                || !front.path("quizforge_id").asText().matches("doc_[A-Za-z0-9_-]+")
                || front.path("title").asText().isBlank() || front.path("language").asText().isBlank()) return null;
        return draft(original, source, WorkspaceFileKind.STANDARD_DOCUMENT,
                front.path("quizforge_id").asText(), front.path("title").asText(), null);
    }

    private FilePresentation bankDraft(OpenedWorkspaceFile original, QuestionBank bank) {
        new QuestionBankValidator().validateEmptyDraft(bank);
        return draft(original, null, WorkspaceFileKind.QUESTION_BANK, bank.assetId(), bank.title(), bank);
    }

    private FilePresentation draft(OpenedWorkspaceFile original, String source, WorkspaceFileKind kind,
            String id, String title, QuestionBank bank) {
        var old = original.entry();
        var entry = new WorkspaceFileEntry(old.relativePath(), old.name(), kind, id, null, title, old.issue());
        return new FilePresentation(new OpenedWorkspaceFile(entry, source, bank, bank == null ? null
                : new io.quizforge.infrastructure.filesystem.QuestionBankV2Codec().contentId(bank)), true, true);
    }

    private boolean emptyMarkdown(String source) {
        return markdown.project(source).stream().noneMatch(block -> !block.style().startsWith("preview-heading-"));
    }
}
