package io.quizforge.core.document.registered;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import java.util.List;

/** A registered Markdown asset; source ranges are runtime positions, never file identity. */
public record RegisteredMarkdownDocument(String documentAssetId, String contentId,
        String relativePath, String title, List<AddressableMarkdownBlock> addressableBlocks,
        int unaddressedBlockCount, List<NamedMarkdownAnchor> anchors, List<String> anchorErrors) {
    public RegisteredMarkdownDocument(String documentAssetId, String contentId, String relativePath,
            String title, List<AddressableMarkdownBlock> addressableBlocks, int unaddressedBlockCount) {
        this(documentAssetId, contentId, relativePath, title, addressableBlocks,
                unaddressedBlockCount, List.of(), List.of());
    }
    public RegisteredMarkdownDocument {
        new Asset(documentAssetId, AssetType.STANDARD_DOCUMENT, relativePath, title,
                contentId, "1");
        addressableBlocks = List.copyOf(addressableBlocks);
        anchors = List.copyOf(anchors);
        anchorErrors = List.copyOf(anchorErrors);
        if (unaddressedBlockCount < 0) throw new IllegalArgumentException("Invalid unaddressed block count");
    }
}
