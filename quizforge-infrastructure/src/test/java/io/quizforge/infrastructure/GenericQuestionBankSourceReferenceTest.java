package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.asset.WorkspaceScanResult;
import io.quizforge.core.document.qdoc.ContentBlock;
import io.quizforge.core.document.qdoc.ContentBlockType;
import io.quizforge.core.document.qdoc.DocumentNode;
import io.quizforge.core.document.qdoc.DocumentNodeType;
import io.quizforge.core.document.qdoc.DocumentTemplate;
import io.quizforge.core.document.qdoc.QDocDocument;
import io.quizforge.core.document.registered.MarkdownBlockType;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.infrastructure.filesystem.FileDocumentNodeLookup;
import io.quizforge.infrastructure.filesystem.QDocV1Codec;
import io.quizforge.infrastructure.filesystem.QuestionBankV1Codec;
import io.quizforge.infrastructure.filesystem.RegisteredMarkdownCodec;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GenericQuestionBankSourceReferenceTest {
    private final QuestionBankV1Codec banks = new QuestionBankV1Codec();
    private final WorkspaceId workspace = WorkspaceId.newId();

    @Test void legacySectionIdIsMappedToDomainNodeIdAndNewSavesUseOnlyNodeId() {
        String revision = "qfd:v1:" + "a".repeat(64);
        var old = bank("1.0", "doc_legacy", revision, "section_old");
        String legacyJson = banks.write(old);
        assertTrue(legacyJson.contains("\"sectionId\""));
        assertFalse(legacyJson.contains("\"nodeId\""));
        var loaded = banks.parse(legacyJson);
        assertEquals("section_old", loaded.questions().getFirst().sourceRefs().getFirst().nodeId());
        var upgraded = new QuestionBankFile(loaded.format(), "1.1", loaded.id(), loaded.title(),
                loaded.sourceDocuments(), loaded.questions());
        String newJson = banks.write(upgraded);
        assertTrue(newJson.contains("\"nodeId\""));
        assertFalse(newJson.contains("\"sectionId\""));
        assertEquals(upgraded, banks.parse(newJson));
        assertThrows(RuntimeException.class, () -> banks.parse(newJson.replace("nodeId", "sectionId")));
    }

    @Test void registeredMarkdownResolvesHeadingParagraphListAndCodeAndPinsRevision() {
        var markdown = new RegisteredMarkdownCodec();
        var prepared = markdown.prepare("# Heading\n\nParagraph.\n\n- one\n- two\n\n```java\nint x = 1;\n```\n",
                "notes/source.md");
        var document = prepared.document();
        assertEquals(List.of(MarkdownBlockType.HEADING, MarkdownBlockType.PARAGRAPH,
                MarkdownBlockType.LIST, MarkdownBlockType.FENCED_CODE),
                document.addressableBlocks().stream().map(block -> block.blockType()).toList());
        var refs = document.addressableBlocks().stream().map(block ->
                new QuestionBankFile.SourceRef(document.documentAssetId(), document.contentId(),
                        block.nodeId(), document.title(), block.displayText())).toList();
        var bank = bank("1.1", document.documentAssetId(), document.contentId(), refs);
        banks.validate(bank);
        var source = new AtomicReference<>(prepared.source());
        var assets = new AtomicReference<>(List.of(asset(document.documentAssetId(), document.contentId(),
                "notes/source.md", "1")));
        var resolver = resolver(source, assets);
        assertEquals(List.of(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                QuestionBankReferenceResolver.Status.EXACT_MATCH,
                QuestionBankReferenceResolver.Status.EXACT_MATCH,
                QuestionBankReferenceResolver.Status.EXACT_MATCH),
                resolver.resolveRefs(workspace, bank).stream().map(item -> item.status()).toList());

        var missingNode = bank("1.1", document.documentAssetId(), document.contentId(), "node_missing");
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_NODE,
                resolver.resolveRefs(workspace, missingNode).getFirst().status());
        assets.set(List.of());
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_DOCUMENT,
                resolver.resolveRefs(workspace, bank).getFirst().status());

        source.set(prepared.source().replace("Paragraph.", "Changed paragraph."));
        String changedRevision = markdown.parseIfRegistered(source.get(), "notes/source.md")
                .orElseThrow().contentId();
        assets.set(List.of(asset(document.documentAssetId(), changedRevision, "notes/source.md", "1")));
        assertEquals(QuestionBankReferenceResolver.Status.DIFFERENT_REVISION,
                resolver.resolveRefs(workspace, bank).getFirst().status());
        assertEquals(document.contentId(), bank.questions().getFirst().sourceRefs().getFirst()
                .documentContentId());
        assertEquals(refs.getFirst().nodeId(), markdown.parseIfRegistered(source.get(), "notes/source.md")
                .orElseThrow().addressableBlocks().getFirst().nodeId());
    }

    @Test void legacyQDocSectionStillResolvesByItsNodeId() {
        var section = new DocumentNode("section_old", DocumentNodeType.SECTION, "Section",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, "Body")));
        var chapter = new DocumentNode("chapter_old", DocumentNodeType.CHAPTER, "Chapter",
                List.of(section));
        var document = new QDocDocument("quizforge-document", "1.0", "doc_legacy",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Legacy", "zh-CN", List.of(chapter));
        var qdocs = new QDocV1Codec();
        String revision = qdocs.contentId(document);
        var legacy = banks.parse(banks.write(bank("1.0", document.id(), revision, section.id())));
        var source = new AtomicReference<>(qdocs.write(document));
        var assets = new AtomicReference<>(List.of(asset(document.id(), revision, "old.qdoc", "1.0")));
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                resolver(source, assets).resolveRefs(workspace, legacy).getFirst().status());
    }

    private QuestionBankReferenceResolver resolver(AtomicReference<String> source,
            AtomicReference<List<Asset>> assets) {
        WorkspaceAssetScanner scanner = id -> new WorkspaceScanResult(assets.get(), List.of());
        WorkspaceFileCatalog files = new WorkspaceFileCatalog() {
            @Override public List<WorkspaceFileEntry> list(WorkspaceId id) { return List.of(); }
            @Override public WorkspaceFileEntry inspect(WorkspaceId id, String path) {
                throw new UnsupportedOperationException();
            }
            @Override public String readText(WorkspaceId id, String path) { return source.get(); }
        };
        return new QuestionBankReferenceResolver(scanner, new FileDocumentNodeLookup(files));
    }

    private Asset asset(String id, String revision, String path, String version) {
        return new Asset(id, AssetType.STANDARD_DOCUMENT, path, "Document", revision, version);
    }

    private QuestionBankFile bank(String version, String id, String revision, String nodeId) {
        return bank(version, id, revision, List.of(new QuestionBankFile.SourceRef(id, revision,
                nodeId, "Document", "Node")));
    }

    private QuestionBankFile bank(String version, String id, String revision,
            List<QuestionBankFile.SourceRef> refs) {
        var data = new QuestionBankFile.Data(List.of(new QuestionBankFile.Option("opt_a", "A"),
                new QuestionBankFile.Option("opt_b", "B")), List.of("opt_a"));
        var question = new QuestionBankFile.Entry("q_one", "SINGLE_CHOICE", "Stem", "Analysis", refs, data);
        return new QuestionBankFile("quizforge-question-bank", version, "qb_one", "Bank",
                List.of(new QuestionBankFile.SourceDocument(id, revision, "Document")), List.of(question));
    }
}
