package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.asset.WorkspaceScanResult;
import io.quizforge.core.document.registered.MarkdownBlockType;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.infrastructure.filesystem.FileDocumentNodeLookup;
import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentV1;
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
        var previous = loaded.questions().getFirst();
        var sourceRef = previous.sourceRefs().getFirst();
        var node = new QuestionBankFile.SourceRef(sourceRef.documentAssetId(),
                sourceRef.documentContentId(), sourceRef.nodeId(),
                sourceRef.documentTitle(), sourceRef.sectionTitle());
        var upgraded = new QuestionBankFile(loaded.format(), "1.1", loaded.id(), loaded.title(),
                loaded.sourceDocuments(), List.of(new QuestionBankFile.Entry(previous.id(), previous.type(),
                        previous.stem(), previous.analysis(), List.of(node), previous.data())));
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

    @Test void legacyMarkdownSectionStillResolvesByItsNodeId() {
        String markdown = "---\nquizforge_format: \"study-document\"\nschema_version: \"1.0\"\n"
                + "quizforge_id: \"doc_legacy\"\ntitle: \"Legacy\"\nlanguage: \"zh-CN\"\n---\n"
                + "# Legacy\n\n## Chapter\n<!-- qf:id=chapter_old -->\n\n"
                + "### Section\n<!-- qf:id=section_old -->\n\nBody\n";
        String revision = new StandardKnowledgeDocumentV1().parseIfStandard(markdown)
                .orElseThrow().contentId();
        var legacy = banks.parse(banks.write(bank("1.0", "doc_legacy", revision, "section_old")));
        var source = new AtomicReference<>(markdown);
        var assets = new AtomicReference<>(List.of(asset("doc_legacy", revision, "old.md", "1.0")));
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                resolver(source, assets).resolveRefs(workspace, legacy).getFirst().status());
    }

    @Test void namedAnchorSchema12RoundTripsWithoutLegacyFieldsAndResolvesOccurrences() {
        var markdown = new RegisteredMarkdownCodec();
        String text = "---\nquizforge:\n  format: document\n  version: 1\n  assetId: doc_anchor\n---\n"
                + "# H\n\n<!-- qf:anchor=shared -->\nFirst.\n\n"
                + "<!-- qf:anchor=shared -->\nSecond.\n";
        var document = markdown.parseIfRegistered(text, "notes/source.md").orElseThrow();
        var source = new AtomicReference<>(text);
        var assets = new AtomicReference<>(List.of(asset(document.documentAssetId(), document.contentId(),
                "notes/source.md", "1")));
        var first = QuestionBankFile.SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "shared", 1, document.title(), "First");
        var second = QuestionBankFile.SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "shared", 2, document.title(), "Second");
        var bank = bank("1.2", document.documentAssetId(), document.contentId(), List.of(first, second));
        String written = banks.write(bank);
        assertTrue(written.contains("\"anchorName\""));
        assertTrue(written.contains("\"occurrence\""));
        assertFalse(written.contains("\"nodeId\""));
        assertFalse(written.contains("\"sectionId\""));
        var reread = banks.parse(written);
        assertEquals(written, banks.write(reread));
        assertEquals(List.of(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                QuestionBankReferenceResolver.Status.EXACT_MATCH), resolver(source, assets)
                .resolveRefs(workspace, reread).stream().map(item -> item.status()).toList());
        var missingName = bank("1.2", document.documentAssetId(), document.contentId(),
                List.of(QuestionBankFile.SourceRef.anchor(document.documentAssetId(), document.contentId(),
                        "absent", 1, document.title(), "")));
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_ANCHOR,
                resolver(source, assets).resolveRefs(workspace, missingName).getFirst().status());
        var missingOccurrence = bank("1.2", document.documentAssetId(), document.contentId(),
                List.of(QuestionBankFile.SourceRef.anchor(document.documentAssetId(), document.contentId(),
                        "shared", 3, document.title(), "")));
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_ANCHOR,
                resolver(source, assets).resolveRefs(workspace, missingOccurrence).getFirst().status());
        assets.set(List.of());
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_DOCUMENT,
                resolver(source, assets).resolveRefs(workspace, reread).getFirst().status());
        source.set(text.replace("Second.", "Changed."));
        var changed = markdown.parseIfRegistered(source.get(), "notes/source.md").orElseThrow();
        assets.set(List.of(asset(document.documentAssetId(), changed.contentId(), "notes/source.md", "1")));
        assertEquals(QuestionBankReferenceResolver.Status.DIFFERENT_REVISION,
                resolver(source, assets).resolveRefs(workspace, reread).getFirst().status());
    }

    @Test void orphanAnchorIsReportedWithoutGuessingAnotherBlock() {
        var markdown = new RegisteredMarkdownCodec();
        String text = "---\nquizforge:\n  format: document\n  version: 1\n"
                + "  assetId: doc_orphan\n---\n# H\n\n<!-- qf:anchor=lost -->\n";
        var document = markdown.parseIfRegistered(text, "orphan.md").orElseThrow();
        assertTrue(document.anchors().getFirst().orphan());
        var source = new AtomicReference<>(text);
        var assets = new AtomicReference<>(List.of(asset(document.documentAssetId(),
                document.contentId(), "orphan.md", "1")));
        var bank = bank("1.2", document.documentAssetId(), document.contentId(),
                List.of(QuestionBankFile.SourceRef.anchor(document.documentAssetId(),
                        document.contentId(), "lost", 1, document.title(), "lost")));
        assertEquals(QuestionBankReferenceResolver.Status.ORPHAN_ANCHOR,
                resolver(source, assets).resolveRefs(workspace, bank).getFirst().status());
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
