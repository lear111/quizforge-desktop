package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Filesystem-backed tree and file inspection. The Registry never determines tree membership. */
public final class LocalWorkspaceFileCatalog implements WorkspaceFileCatalog {
    private static final Pattern DECLARED_STANDARD = Pattern.compile(
            "(?m)^\\s*quizforge_format\\s*:\\s*[\"']?study-document(?:[\"']|\\s|$)");
    private static final Pattern DECLARED_REGISTERED = Pattern.compile("(?m)^quizforge\\s*:");
    private final WorkspacePathResolver paths;
    private final StandardKnowledgeDocumentV1 documents = new StandardKnowledgeDocumentV1();
    private final RegisteredMarkdownCodec registeredMarkdown = new RegisteredMarkdownCodec();
    private final QuestionBankFileCodec banks;

    public LocalWorkspaceFileCatalog(WorkspacePathResolver paths, QuestionBankFileCodec banks) {
        this.paths = paths;
        this.banks = banks;
    }

    @Override
    public List<WorkspaceFileEntry> list(WorkspaceId workspaceId) {
        Path root = paths.workspaceRoot(workspaceId);
        List<WorkspaceFileEntry> entries = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (directory.equals(root)) return FileVisitResult.CONTINUE;
                    if (directory.getFileName().toString().equals(".quizforge")) return FileVisitResult.SKIP_SUBTREE;
                    String relative = relative(root, directory);
                    entries.add(new WorkspaceFileEntry(relative, directory.getFileName().toString(),
                            WorkspaceFileKind.DIRECTORY, null, null, null, null));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && supported(file)) entries.add(describe(root, file));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException error) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException error) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not list workspace files.", error);
        }
        return List.copyOf(entries);
    }

    @Override
    public WorkspaceFileEntry inspect(WorkspaceId workspaceId, String relativePath) {
        Path root = paths.workspaceRoot(workspaceId);
        Path file = checked(root, relativePath);
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
            return new WorkspaceFileEntry(relativePath, file.getFileName().toString(),
                    WorkspaceFileKind.DIRECTORY, null, null, null, null);
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Selected workspace file no longer exists.");
        }
        return describe(root, file);
    }

    @Override
    public String readText(WorkspaceId workspaceId, String relativePath) {
        Path file = checked(paths.workspaceRoot(workspaceId), relativePath);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Selected workspace file is not a regular file.");
        }
        try { return Files.readString(file, StandardCharsets.UTF_8); }
        catch (IOException error) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not read workspace file.", error);
        }
    }

    private WorkspaceFileEntry describe(Path root, Path file) {
        String relative = relative(root, file);
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) {
            String source;
            try { source = Files.readString(file, StandardCharsets.UTF_8); }
            catch (IOException error) {
                return new WorkspaceFileEntry(relative, name, WorkspaceFileKind.MARKDOWN,
                        null, null, null, "Could not read Markdown: " + error.getMessage());
            }
            boolean declared = declaresStandard(source) || declaresRegistered(source);
            try {
                var registered = registeredMarkdown.parseIfRegistered(source, relative);
                if (registered.isPresent()) {
                    var document = registered.get();
                    return new WorkspaceFileEntry(relative, name, WorkspaceFileKind.STANDARD_DOCUMENT,
                            document.documentAssetId(), document.contentId(), document.title(), null);
                }
                var parsed = documents.parseIfStandard(source);
                if (parsed.isPresent()) {
                    var asset = parsed.get();
                    return new WorkspaceFileEntry(relative, name, WorkspaceFileKind.STANDARD_DOCUMENT,
                            asset.assetId(), asset.contentId(), asset.title(), null);
                }
                return new WorkspaceFileEntry(relative, name, WorkspaceFileKind.MARKDOWN,
                        null, null, null, null);
            } catch (RuntimeException error) {
                return new WorkspaceFileEntry(relative, name,
                        declared ? WorkspaceFileKind.INVALID_STANDARD_DOCUMENT : WorkspaceFileKind.MARKDOWN,
                        null, null, null, declared ? error.getMessage() : null);
            }
        }
        if (lower.endsWith(".qbank")) {
            try {
                var bank = banks.parse(Files.readString(file, StandardCharsets.UTF_8));
                return new WorkspaceFileEntry(relative, name, WorkspaceFileKind.QUESTION_BANK,
                        bank.id(), banks.contentId(bank), bank.title(), null);
            } catch (IOException | RuntimeException error) {
                return new WorkspaceFileEntry(relative, name, WorkspaceFileKind.INVALID_QUESTION_BANK,
                        null, null, null, error.getMessage());
            }
        }
        return other(root, file, null);
    }

    private boolean declaresStandard(String source) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---\n")) return false;
        int end = normalized.indexOf("\n---\n", 4);
        String frontMatter = normalized.substring(4, end < 0
                ? Math.min(normalized.length(), 64 * 1024) : Math.min(end, 64 * 1024));
        return DECLARED_STANDARD.matcher(frontMatter).find();
    }

    private boolean declaresRegistered(String source) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---\n")) return false;
        int end = normalized.indexOf("\n---\n", 4);
        String frontMatter = normalized.substring(4, end < 0
                ? Math.min(normalized.length(), 64 * 1024) : Math.min(end, 64 * 1024));
        return DECLARED_REGISTERED.matcher(frontMatter).find();
    }

    private WorkspaceFileEntry other(Path root, Path file, String issue) {
        return new WorkspaceFileEntry(relative(root, file), file.getFileName().toString(),
                WorkspaceFileKind.OTHER, null, null, null, issue);
    }

    private boolean supported(Path file) {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".qbank");
    }

    private Path checked(Path root, String relativePath) {
        new WorkspaceFileEntry(relativePath, Path.of(relativePath).getFileName().toString(),
                WorkspaceFileKind.OTHER, null, null, null, null);
        if (relativePath.equals(".quizforge") || relativePath.startsWith(".quizforge/")
                || relativePath.contains("/.quizforge/")) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Internal workspace files are hidden.");
        }
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Path escapes workspace");
        Path part = root;
        for (Path segment : root.relativize(file)) {
            part = part.resolve(segment);
            if (Files.isSymbolicLink(part)) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Linked workspace paths cannot be opened.");
            }
        }
        return file;
    }

    private String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
