package io.quizforge.desktop.ui;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.function.Supplier;

/** Executes current-location links within the active workspace, without revising source references. */
final class WorkspaceNavigationService {
    enum Result { OPENED, MISSING_ASSET, MISSING_TARGET, EDIT_MODE, UNAVAILABLE_FILE }

    private final AssetIndexRepository index;
    private final Supplier<WorkspaceId> workspace;
    private final WorkspaceTabManager tabs;

    WorkspaceNavigationService(AssetIndexRepository index, Supplier<WorkspaceId> workspace,
            WorkspaceTabManager tabs) {
        this.index = index;
        this.workspace = workspace;
        this.tabs = tabs;
    }

    Result navigate(QuizForgeNavigationLink link) {
        WorkspaceId current = workspace.get();
        if (current == null) return Result.MISSING_ASSET;
        Asset asset = index.findById(current, link.assetId()).orElse(null);
        if (asset == null) return Result.MISSING_ASSET;
        WorkspaceTab tab = tabs.openPinned(current, asset.currentPath());
        if (tab == null) return Result.UNAVAILABLE_FILE;
        if (link.target() instanceof QuizForgeNavigationLink.AssetTarget) return Result.OPENED;
        if (tab.pane().mode() == FileMode.EDIT) return Result.EDIT_MODE;
        if (link.target() instanceof QuizForgeNavigationLink.HeadingTarget heading)
            return tab.pane().jumpTo(MarkdownOutline.Kind.HEADING,
                    heading.headingText(), heading.occurrence()) ? Result.OPENED : Result.MISSING_TARGET;
        QuizForgeNavigationLink.AnchorTarget anchor = (QuizForgeNavigationLink.AnchorTarget) link.target();
        return tab.pane().jumpTo(MarkdownOutline.Kind.ANCHOR,
                anchor.anchorName(), anchor.occurrence()) ? Result.OPENED : Result.MISSING_TARGET;
    }
}
