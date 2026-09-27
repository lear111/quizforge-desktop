package io.quizforge.infrastructure.filesystem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.asset.WorkspaceScanIssue;
import io.quizforge.core.asset.WorkspaceScanResult;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceId;
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
    private static final ObjectMapper JSON = new ObjectMapper();
    private final StandardKnowledgeDocumentV1 documents = new StandardKnowledgeDocumentV1();
    private final RegisteredMarkdownCodec registeredMarkdown = new RegisteredMarkdownCodec();
    private final QDocV1Codec qdocs = new QDocV1Codec();
    private final QuestionBankV1Codec banks = new QuestionBankV1Codec();

    private final WorkspacePathResolver paths;
    private final AssetIndexRepository index;
    private final Clock clock;
    private final boolean legacyMarkdown;

    public FileSystemWorkspaceAssetScanner(WorkspacePathResolver paths,
            AssetIndexRepository index, Clock clock) {
        this(paths, index, clock, true);
    }

    public FileSystemWorkspaceAssetScanner(WorkspacePathResolver paths,
            AssetIndexRepository index, Clock clock, boolean legacyMarkdown) {
        this.paths = paths;
        this.index = index;
        this.clock = clock;
        this.legacyMarkdown = legacyMarkdown;
    }

    @Override
    public WorkspaceScanResult scanWithReport(WorkspaceId workspaceId) {
        Path root = paths.workspaceRoot(workspaceId);
        List<Path> candidatePaths = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    return !directory.equals(root) && directory.getFileName().toString().equals(".quizforge")
                            ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
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
        List<WorkspaceScanIssue> issues = new ArrayList<>();
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
            } catch (IOException | IllegalArgumentException error) {
                issues.add(new WorkspaceScanIssue("INVALID_ASSET_FILE", relative, error.getMessage()));
            }
        }
        duplicates.forEach(found::remove);
        List<Asset> assets = found.values().stream()
                .sorted((one, two) -> one.currentPath().compareTo(two.currentPath())).toList();
        index.synchronize(workspaceId, assets, clock.instant());
        return new WorkspaceScanResult(assets, issues);
    }

    private Optional<Asset> recognize(Path root, Path file) throws IOException {
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".qdoc")) {
            var document = qdocs.parse(Files.readString(file, StandardCharsets.UTF_8));
            return Optional.of(new Asset(document.id(), AssetType.STANDARD_DOCUMENT,
                    relative(root, file), document.title(), qdocs.contentId(document), document.schemaVersion()));
        }
        if (lower.endsWith(".md")) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String relative = relative(root, file);
            var registered = registeredMarkdown.parseIfRegistered(source, relative);
            if (registered.isPresent()) {
                var document = registered.get();
                return Optional.of(new Asset(document.documentAssetId(), AssetType.STANDARD_DOCUMENT,
                        relative, document.title(), document.contentId(), "1"));
            }
            // Legacy formal Markdown remains discoverable until its compatibility path is retired.
            return legacyMarkdown ? standardDocument(root, file) : Optional.empty();
        }
        if (lower.endsWith(".qbank")) {
            return questionBank(root, file);
        }
        return Optional.empty();
    }

    private Optional<Asset> standardDocument(Path root, Path file) throws IOException {
        return documents.parseIfStandard(Files.readString(file, StandardCharsets.UTF_8))
                .map(document -> new Asset(document.assetId(), AssetType.STANDARD_DOCUMENT,
                        relative(root, file), document.title(), document.contentId(),
                        document.schemaVersion()));
    }

    private Optional<Asset> questionBank(Path root, Path file) throws IOException {
        String format = "";
        String id = "";
        String title = "";
        String version = "";
        try (JsonParser parser = JSON.createParser(file.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) return Optional.empty();
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (token == null || token != JsonToken.FIELD_NAME) return Optional.empty();
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if (value == null) return Optional.empty();
                String text = value == JsonToken.VALUE_STRING ? parser.getText() : "";
                switch (field) {
                    case "format" -> format = text;
                    case "id" -> id = text;
                    case "title" -> title = text;
                    case "schemaVersion" -> version = text;
                    default -> { }
                }
                parser.skipChildren();
            }
            if (parser.nextToken() != null) throw new IllegalArgumentException("Trailing QuestionBank JSON");
        }
        if (!"quizforge-question-bank".equals(format)) return Optional.empty();
        if (!("1.0".equals(version) || "1.1".equals(version))
                || !id.matches("qb_[A-Za-z0-9_-]+") || title.isBlank()) {
            throw new IllegalArgumentException("Invalid QuestionBank metadata");
        }
        String revision;
        try { revision = banks.contentId(banks.parse(Files.readString(file, StandardCharsets.UTF_8))); }
        catch (RuntimeException invalidDraft) { revision = null; }
        return Optional.of(new Asset(id, AssetType.QUESTION_BANK, relative(root, file),
                title, revision, version));
    }

    private String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
