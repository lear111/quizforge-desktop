package io.quizforge.desktop.ui.workspace;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.ui.file.FileMode;
import io.quizforge.desktop.ui.markdown.MarkdownOutline;
import java.util.function.Supplier;

/** Executes current-location links within the active workspace, without revising source references. */
public final class WorkspaceNavigationService {
    public enum Result { OPENED, MISSING_ASSET, MISSING_TARGET, EDIT_MODE, UNAVAILABLE_FILE }

    private final AssetIndexRepository index;
    private final Supplier<WorkspaceId> workspace;
    private final WorkspaceTabManager tabs;

    public WorkspaceNavigationService(AssetIndexRepository index, Supplier<WorkspaceId> workspace,
            WorkspaceTabManager tabs) {
        this.index = index;
        this.workspace = workspace;
        this.tabs = tabs;
    }

    public Result navigate(QuizForgeNavigationLink link) {
        WorkspaceId current = workspace.get();
        if (current == null) return Result.MISSING_ASSET;
        Asset asset = index.findById(current, link.assetId()).orElse(null);
        if (asset == null) return Result.MISSING_ASSET;
        boolean alreadyOpen = tabs.findOpenTab(asset.currentPath()) != null;
        WorkspaceTab tab = tabs.openPinned(current, asset.currentPath());
        if (tab == null) return Result.UNAVAILABLE_FILE;
        if (alreadyOpen) tab.pane().refreshBrowseFromDisk();
        if (tab.pane().currentFile() == null) return Result.UNAVAILABLE_FILE;
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
