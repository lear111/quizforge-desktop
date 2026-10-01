package io.quizforge.core.asset;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetTest {
    @Test void acceptsWorkspaceRelativePathOnly() {
        Asset asset = new Asset("doc_123", AssetType.REGISTERED_MARKDOWN,
                "documents/java/study.md", "Java");
        assertEquals("documents/java/study.md", asset.currentPath());
        assertThrows(IllegalArgumentException.class, () -> new Asset("doc_123",
                AssetType.REGISTERED_MARKDOWN, "C:/private/study.md", "Java"));
        assertThrows(IllegalArgumentException.class, () -> new Asset("doc_123",
                AssetType.REGISTERED_MARKDOWN, "../study.md", "Java"));
        assertThrows(IllegalArgumentException.class, () -> new Asset("doc_123",
                AssetType.REGISTERED_MARKDOWN, "/tmp/study.md", "Java"));
    }

    @Test void revisionPrefixMatchesAssetType() {
        String hash = "a".repeat(64);
        assertEquals("qfb:v2:" + hash, new Asset("qb_one", AssetType.QUESTION_BANK,
                "banks/one.qbank", "One", "qfb:v2:" + hash, "1.0").contentId());
        assertThrows(IllegalArgumentException.class, () -> new Asset("qb_one", AssetType.QUESTION_BANK,
                "banks/one.qbank", "One", "qfd:v1:" + hash, "1.0"));
    }
}
