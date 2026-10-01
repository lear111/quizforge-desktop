package io.quizforge.infrastructure.filesystem.workspace;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.asset.WorkspaceScanIssue;
import io.quizforge.core.asset.WorkspaceScanResult;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.infrastructure.filesystem.markdown.LegacyMarkdownCodec;
import io.quizforge.infrastructure.filesystem.markdown.RegisteredMarkdownCodec;
import io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Recursively discovers file-backed assets. A damaged asset is skipped, not indexed. */
public final class FileSystemWorkspaceAssetScanner implements WorkspaceAssetScanner {
    private final LegacyMarkdownCodec documents = new LegacyMarkdownCodec();
    private final RegisteredMarkdownCodec registeredMarkdown = new RegisteredMarkdownCodec();
    private final QuestionBankV2Codec banks = new QuestionBankV2Codec();

    private final WorkspacePathResolver paths;
    private final AssetIndexRepository index;
    private final Clock clock;

    public FileSystemWorkspaceAssetScanner(WorkspacePathResolver paths,
            AssetIndexRepository index, Clock clock) {
        this.paths = paths;
        this.index = index;
        this.clock = clock;
    }

    @Override
    public WorkspaceScanResult scanWithReport(WorkspaceId workspaceId) {
        return discover(workspaceId,true);
    }

    public WorkspaceScanResult scanReadOnly(WorkspaceId workspaceId) {
        return discover(workspaceId,false);
    }

    private WorkspaceScanResult discover(WorkspaceId workspaceId, boolean updateIndex) {
        Path root = paths.workspaceRoot(workspaceId);
        List<Path> candidatePaths = new ArrayList<>();
        List<WorkspaceScanIssue> issues = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    try { WorkspacePathGuard.requireInside(root, directory); }
                    catch (RuntimeException denied) {
                        issues.add(new WorkspaceScanIssue("UNAVAILABLE_PATH", relative(root,directory), denied.getMessage()));
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return !directory.equals(root) && directory.getFileName().toString().equals(".quizforge")
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    try { WorkspacePathGuard.requireInside(root, file); }
                    catch (RuntimeException denied) {
                        issues.add(new WorkspaceScanIssue("UNAVAILABLE_PATH", relative(root,file), denied.getMessage()));
                        return FileVisitResult.CONTINUE;
                    }
                    if (attributes.isRegularFile()) candidatePaths.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException error) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not scan workspace files.", error);
        }
        candidatePaths.sort((one, two) -> root.relativize(one).toString()
                .compareTo(root.relativize(two).toString()));
        Map<String, Asset> found = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (Path file : candidatePaths) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            try {
                Optional<Asset> maybeAsset = recognize(root, file);
                if (maybeAsset.isPresent()) {
                    Asset asset = maybeAsset.get();
                    if (found.putIfAbsent(asset.assetId(), asset) != null) {
                        duplicates.add(asset.assetId());
                        issues.add(new WorkspaceScanIssue("DUPLICATE_ASSET_ID", relative,
                                "Asset ID " + asset.assetId() + " also occurs at "
                                + found.get(asset.assetId()).currentPath()));
                    }
                }
            } catch (IOException | IllegalArgumentException | QuizForgeException error) {
                issues.add(new WorkspaceScanIssue("INVALID_ASSET_FILE", relative, error.getMessage()));
            }
        }
        duplicates.forEach(found::remove);
        List<Asset> assets = found.values().stream()
                .sorted((one, two) -> one.currentPath().compareTo(two.currentPath())).toList();
        if(updateIndex) index.synchronize(workspaceId, assets, clock.instant());
        return new WorkspaceScanResult(assets, issues);
    }

    private Optional<Asset> recognize(Path root, Path file) throws IOException {
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String relative = relative(root, file);
            var registered = registeredMarkdown.parseIfRegistered(source, relative);
            if (registered.isPresent()) {
                var document = registered.get();
                return Optional.of(new Asset(document.documentAssetId(), AssetType.REGISTERED_MARKDOWN,
                        relative, document.title(), document.contentId(), "1"));
            }
            return standardDocument(root, file);
        }
        if (lower.endsWith(".qbank")) {
            return questionBank(root, file);
        }
        return Optional.empty();
    }

    private Optional<Asset> standardDocument(Path root, Path file) throws IOException {
        return documents.parseLegacy(Files.readString(file, StandardCharsets.UTF_8))
                .map(document -> new Asset(document.assetId(), AssetType.REGISTERED_MARKDOWN,
                        relative(root, file), document.title(), document.contentId(),
                        document.schemaVersion()));
    }

    private Optional<Asset> questionBank(Path root, Path file) throws IOException {
        var bank = new QBankPackageReader().inspect(file);
        String revision = banks.contentId(bank);
        return Optional.of(new Asset(bank.assetId(), AssetType.QUESTION_BANK, relative(root, file),
                bank.title(), revision, bank.schemaVersion()));
    }

    private String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
