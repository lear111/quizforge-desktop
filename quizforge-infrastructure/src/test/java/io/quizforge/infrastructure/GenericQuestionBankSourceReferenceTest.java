package io.quizforge.infrastructure;

import io.quizforge.core.question.*;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.asset.WorkspaceScanResult;
import io.quizforge.core.document.registered.MarkdownBlockType;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.infrastructure.filesystem.FileDocumentNodeLookup;
import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentV1;
import io.quizforge.infrastructure.filesystem.QuestionBankV2Codec;
import io.quizforge.infrastructure.filesystem.RegisteredMarkdownCodec;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GenericQuestionBankSourceReferenceTest {
    private final QuestionBankV2Codec banks = new QuestionBankV2Codec();
    private final WorkspaceId workspace = WorkspaceId.newId();

    @Test void namedSourceRefV2RoundTripsWithoutLegacyFields() {
        var bank = bank("doc_source", "qfd:v2:" + "a".repeat(64), "definition");
        String json = banks.write(bank);
        assertFalse(json.contains("nodeId"));
        assertFalse(json.contains("sectionId"));
        assertEquals(bank, banks.parse(json));
        assertThrows(RuntimeException.class, () -> banks.parse(json.replace("anchorName", "nodeId")));
    }

    @Test void registeredMarkdownResolvesHeadingParagraphListAndCodeAndPinsRevision() {
        var markdown = new RegisteredMarkdownCodec();
        var prepared = markdown.prepare("<!-- qf:anchor=heading -->\n# Heading\n\n<!-- qf:anchor=paragraph -->\nParagraph.\n\n<!-- qf:anchor=list -->\n- one\n- two\n\n<!-- qf:anchor=code -->\n```java\nint x = 1;\n```\n",
                "notes/source.md");
        var document = prepared.document();
        assertEquals(List.of(MarkdownBlockType.HEADING, MarkdownBlockType.PARAGRAPH,
                MarkdownBlockType.LIST, MarkdownBlockType.FENCED_CODE),
                document.addressableBlocks().stream().map(block -> block.blockType()).toList());
        var refs = document.anchors().stream().map(anchor ->
                SourceRef.anchor(document.documentAssetId(), document.contentId(), anchor.name(), anchor.occurrence(), document.title(), anchor.name())).toList();
        var bank = bank(document.documentAssetId(), document.contentId(), refs);
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

        var missingNode = bank(document.documentAssetId(), document.contentId(), "node_missing");
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_ANCHOR,
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
        assertEquals(refs.getFirst().anchorName(), markdown.parseIfRegistered(source.get(), "notes/source.md")
                .orElseThrow().anchors().getFirst().name());
    }

    @Test void standardMarkdownStillResolvesSourceIdentity() {
        String markdown = "---\nquizforge_format: \"study-document\"\nschema_version: \"1.0\"\n"
                + "quizforge_id: \"doc_legacy\"\ntitle: \"Legacy\"\nlanguage: \"zh-CN\"\n---\n"
                + "# Legacy\n\n## Chapter\n<!-- qf:id=chapter_old -->\n\n"
                + "<!-- qf:anchor=section_old -->\n### Section\n<!-- qf:id=section_old -->\n\nBody\n";
        String revision = new StandardKnowledgeDocumentV1().parseIfStandard(markdown)
                .orElseThrow().contentId();
        var legacy = banks.parse(banks.write(bank("doc_legacy", revision, "section_old")));
        var source = new AtomicReference<>(markdown);
        var assets = new AtomicReference<>(List.of(asset("doc_legacy", revision, "old.md", "1.0")));
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                resolver(source, assets).resolveCurrentRefs(workspace, legacy.questions().getFirst().sourceRefs()).getFirst().status());
    }

    @Test void namedAnchorV2RoundTripsWithoutLegacyFieldsAndResolvesOccurrences() {
        var markdown = new RegisteredMarkdownCodec();
        String text = "---\nquizforge:\n  format: document\n  version: 1\n  assetId: doc_anchor\n---\n"
                + "# H\n\n<!-- qf:anchor=shared -->\nFirst.\n\n"
                + "<!-- qf:anchor=shared -->\nSecond.\n";
        var document = markdown.parseIfRegistered(text, "notes/source.md").orElseThrow();
        var source = new AtomicReference<>(text);
        var assets = new AtomicReference<>(List.of(asset(document.documentAssetId(), document.contentId(),
                "notes/source.md", "1")));
        var first = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "shared", 1, document.title(), "First");
        var second = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "shared", 2, document.title(), "Second");
        var bank = bank(document.documentAssetId(), document.contentId(), List.of(first, second));
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
        var missingName = bank(document.documentAssetId(), document.contentId(),
                List.of(SourceRef.anchor(document.documentAssetId(), document.contentId(),
                        "absent", 1, document.title(), "")));
        assertEquals(QuestionBankReferenceResolver.Status.MISSING_ANCHOR,
                resolver(source, assets).resolveRefs(workspace, missingName).getFirst().status());
        var missingOccurrence = bank(document.documentAssetId(), document.contentId(),
                List.of(SourceRef.anchor(document.documentAssetId(), document.contentId(),
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
        var bank = bank(document.documentAssetId(), document.contentId(),
                List.of(SourceRef.anchor(document.documentAssetId(),
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
            @Override public QuestionBank readBank(WorkspaceId id, String path) {
                throw new UnsupportedOperationException();
            }
            @Override public String readText(WorkspaceId id, String path) { return source.get(); }
        };
        return new QuestionBankReferenceResolver(scanner, new FileDocumentNodeLookup(files));
    }

    private Asset asset(String id, String revision, String path, String version) {
        return new Asset(id, AssetType.STANDARD_DOCUMENT, path, "Document", revision, version);
    }

    private QuestionBank bank(String id, String revision, String nodeId) {
        return bank(id, revision, List.of(SourceRef.anchor(id, revision, nodeId, 1, "Document", "Node")));
    }

    private QuestionBank bank(String id, String revision,
            List<SourceRef> refs) {
        var payload = new ChoicePayload(List.of(new ChoiceOption("opt_a", new TextContent("A")),
                new ChoiceOption("opt_b", new TextContent("B"))));
        var question = Question.choice("q_one", "SINGLE_CHOICE", new TextContent("Stem"), new TextContent("Analysis"), refs, payload,
                new ChoiceAnswerSpec(List.of("opt_a")));
        return new QuestionBank("qb_one", "Bank", "2.0", List.of(), List.of(question), List.of());
    }
}
