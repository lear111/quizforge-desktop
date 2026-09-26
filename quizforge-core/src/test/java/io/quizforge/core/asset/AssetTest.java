package io.quizforge.core.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AssetTest {
    @Test void acceptsWorkspaceRelativePathOnly() {
        Asset asset = new Asset("doc_123", AssetType.STANDARD_DOCUMENT,
                "documents/java/study.md", "Java");
        assertEquals("documents/java/study.md", asset.currentPath());
        assertThrows(IllegalArgumentException.class, () -> new Asset("doc_123",
                AssetType.STANDARD_DOCUMENT, "C:/private/study.md", "Java"));
        assertThrows(IllegalArgumentException.class, () -> new Asset("doc_123",
                AssetType.STANDARD_DOCUMENT, "../study.md", "Java"));
        assertThrows(IllegalArgumentException.class, () -> new Asset("doc_123",
                AssetType.STANDARD_DOCUMENT, "/tmp/study.md", "Java"));
    }

    @Test void revisionPrefixMatchesAssetType() {
        String hash = "a".repeat(64);
        assertEquals("qfb:v1:" + hash, new Asset("qb_one", AssetType.QUESTION_BANK,
                "banks/one.qbank", "One", "qfb:v1:" + hash, "1.0").contentId());
        assertThrows(IllegalArgumentException.class, () -> new Asset("qb_one", AssetType.QUESTION_BANK,
                "banks/one.qbank", "One", "qfd:v1:" + hash, "1.0"));
    }
}
