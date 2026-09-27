package io.quizforge.infrastructure.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.WorkspaceFileOperations;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceFileType;
import io.quizforge.core.workspace.WorkspaceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/** Workspace-relative filesystem actions; no operation follows links or touches .quizforge. */
public final class LocalWorkspaceFileOperations implements WorkspaceFileOperations {
    private final WorkspacePathResolver paths;
    private final QuestionBankV1Codec banks = new QuestionBankV1Codec();
    private final ObjectMapper json = new ObjectMapper();

    public LocalWorkspaceFileOperations(WorkspacePathResolver paths) { this.paths = paths; }

    @Override public String createFolder(WorkspaceId workspace, String parentPath, String name) {
        Path root = paths.workspaceRoot(workspace);
        Path parent = parent(root, parentPath);
        Path target = parent.resolve(name(name));
        try { Files.createDirectory(target); }
        catch (IOException error) { throw failure("create folder", error); }
        return relative(root, target);
    }

    @Override public String createFile(WorkspaceId workspace, String parentPath, String name,
            WorkspaceFileType type) {
        if (type == null) throw new IllegalArgumentException("File type is required");
        Path root = paths.workspaceRoot(workspace);
        Path parent = parent(root, parentPath);
        String fileName = name(name);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(type.extension())) fileName += type.extension();
        fileName = name(fileName);
        Path target = parent.resolve(fileName);
        String title = fileName.substring(0, fileName.length() - type.extension().length());
        String content = switch (type) {
            case MARKDOWN -> "";
            case QUESTION_BANK -> emptyQuestionBank(title);
        };
        try { Files.writeString(target, content, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE); }
        catch (IOException error) { throw failure("create file", error); }
        return relative(root, target);
    }

    @Override public String rename(WorkspaceId workspace, String relativePath, String newName) {
        Path root = paths.workspaceRoot(workspace);
        Path source = existing(root, relativePath);
        String candidate = name(newName);
        if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            String old = source.getFileName().toString().toLowerCase(Locale.ROOT);
            String next = candidate.toLowerCase(Locale.ROOT);
            boolean sameType = List.of(WorkspaceFileType.values()).stream().anyMatch(type ->
                    old.endsWith(type.extension()) && next.endsWith(type.extension()));
            if (!sameType) throw new IllegalArgumentException("Keep the file's .md or .qbank extension");
        }
        Path target = source.resolveSibling(candidate);
        if (source.equals(target)) return relativePath;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("A file or folder with that name already exists");
        try { Files.move(source, target); }
        catch (IOException error) { throw failure("rename file or folder", error); }
        return relative(root, target);
    }

    @Override public void delete(WorkspaceId workspace, String relativePath) {
        Path root = paths.workspaceRoot(workspace);
        Path target = existing(root, relativePath);
        try {
            try (Stream<Path> descendants = Files.walk(target)) {
                if (descendants.anyMatch(path -> path.getFileName().toString().equalsIgnoreCase(".quizforge")))
                    throw new IllegalArgumentException("Internal workspace state cannot be deleted");
            }
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs)
                        throws IOException {
                    if (directory.getFileName().toString().equalsIgnoreCase(".quizforge"))
                        throw new IOException("Internal workspace state cannot be deleted");
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path directory, IOException error)
                        throws IOException {
                    if (error != null) throw error;
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException error) { throw failure("delete file or folder", error); }
    }

    @Override public Path absolutePath(WorkspaceId workspace, String relativePath) {
        return existing(paths.workspaceRoot(workspace), relativePath);
    }

    private Path parent(Path root, String relativePath) {
        Path result = relativePath == null || relativePath.isEmpty() ? root : existing(root, relativePath);
        if (!Files.isDirectory(result, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Choose a folder for the new item");
        return result;
    }

    private Path existing(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("Path is required");
        new WorkspaceFileEntry(relativePath, Path.of(relativePath).getFileName().toString(),
                WorkspaceFileKind.OTHER, null, null, null, null);
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Path escapes workspace");
        Path part = root;
        for (Path segment : root.relativize(target)) {
            if (segment.toString().equalsIgnoreCase(".quizforge"))
                throw new IllegalArgumentException("Internal workspace state cannot be changed");
            part = part.resolve(segment);
            if (Files.isSymbolicLink(part)) throw new IllegalArgumentException("Linked paths cannot be changed");
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("File or folder no longer exists");
        return target;
    }

    private String name(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip())
                || value.equals(".") || value.equals("..") || value.endsWith("."))
            throw new IllegalArgumentException("Invalid file or folder name");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || "<>:\"/\\|?*".indexOf(character) >= 0)
                throw new IllegalArgumentException("Invalid file or folder name");
        }
        String stem = value.split("\\.", 2)[0];
        if (stem.matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")
                || value.equalsIgnoreCase(".quizforge"))
            throw new IllegalArgumentException("Reserved file or folder name");
        return value;
    }

    private String emptyQuestionBank(String title) {
        var root = json.createObjectNode();
        root.put("format", "quizforge-question-bank");
        root.put("schemaVersion", "1.2");
        root.put("id", "qb_" + UUID.randomUUID().toString().replace("-", ""));
        root.put("title", title);
        root.putArray("sourceDocuments");
        root.putArray("questions");
        try {
            String content = json.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
            banks.parseEmptyDraft(content);
            return content;
        } catch (IOException error) { throw new IllegalStateException("Could not create QuestionBank draft", error); }
    }

    private String relative(Path root, Path target) {
        return root.relativize(target).toString().replace('\\', '/');
    }

    private QuizForgeException failure(String action, IOException cause) {
        return new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED, "Could not " + action + ".", cause);
    }
}
