package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.document.registered.RegisteredMarkdownDocument;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

/** Adds a source anchor on demand, registering the user-owned .md only on first use. */
public final class MarkdownDocumentRegistrationService implements MarkdownDocumentRegistration {
    @FunctionalInterface
    interface Publisher {
        void replace(Path candidate, Path target) throws IOException;
    }

    private final WorkspacePathResolver paths;
    private final WorkspaceAssetScanner scanner;
    private final Publisher publisher;
    private final RegisteredMarkdownCodec codec = new RegisteredMarkdownCodec();

    public MarkdownDocumentRegistrationService(WorkspacePathResolver paths, WorkspaceAssetScanner scanner) {
        this(paths, scanner, MarkdownDocumentRegistrationService::move);
    }

    MarkdownDocumentRegistrationService(WorkspacePathResolver paths, WorkspaceAssetScanner scanner,
            Publisher publisher) {
        this.paths = paths;
        this.scanner = scanner;
        this.publisher = publisher;
    }

    @Override
    public Optional<RegisteredMarkdownDocument> inspect(WorkspaceId workspaceId, String relativePath) {
        try {
            return codec.parseIfRegistered(Files.readString(checkedFile(workspaceId, relativePath),
                    StandardCharsets.UTF_8), relativePath);
        } catch (IOException error) {
            throw failure("inspect Markdown document", error);
        }
    }

    @Override
    public NamedMarkdownAnchor createAnchor(WorkspaceId workspaceId, String relativePath,
            String expectedSource, int bodyLine, int bodyColumn, String anchorName) {
        Path file = checkedFile(workspaceId, relativePath);
        try {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            if (!original.equals(expectedSource)) throw new IllegalStateException("Markdown changed externally");
            if (original.startsWith("\uFEFF")) throw new IllegalArgumentException("Markdown BOM is unsupported");
            var prepared = codec.prepareAnchor(original, relativePath, bodyLine, bodyColumn, anchorName);
            if (original.equals(prepared.source())) refresh(workspaceId, prepared.document());
            else publishAndRefresh(workspaceId, file, original,
                    new RegisteredMarkdownCodec.Prepared(prepared.source(), prepared.document()));
            return prepared.anchor();
        } catch (IOException error) {
            throw failure("create Markdown source anchor", error);
        }
    }

    private void publishAndRefresh(WorkspaceId workspaceId, Path file, String original,
            RegisteredMarkdownCodec.Prepared prepared) throws IOException {
        Path candidate = Files.createTempFile(file.getParent(), ".qf-register-", ".tmp");
        Path backup = Files.createTempFile(file.getParent(), ".qf-original-", ".tmp");
        boolean published = false;
        try {
            Files.writeString(candidate, prepared.source(), StandardCharsets.UTF_8);
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            if (!original.equals(Files.readString(file, StandardCharsets.UTF_8))) {
                throw new IllegalStateException("Markdown changed during registration");
            }
            publisher.replace(candidate, file);
            published = true;
            if (!prepared.source().equals(Files.readString(file, StandardCharsets.UTF_8))) {
                throw new IllegalStateException("Registered Markdown differs from prepared content");
            }
            refresh(workspaceId, prepared.document());
        } catch (IOException | RuntimeException error) {
            if (published) {
                try {
                    move(backup, file);
                    scanner.scanWithReport(workspaceId);
                } catch (IOException | RuntimeException recovery) {
                    error.addSuppressed(recovery);
                }
            }
            throw error;
        } finally {
            deleteTemporary(candidate);
            deleteTemporary(backup);
        }
    }

    private void refresh(WorkspaceId workspaceId, RegisteredMarkdownDocument expected) {
        var result = scanner.scanWithReport(workspaceId);
        boolean present = result.assets().stream().anyMatch(asset ->
                asset.assetType() == AssetType.STANDARD_DOCUMENT
                && asset.assetId().equals(expected.documentAssetId())
                && asset.contentId().equals(expected.contentId())
                && asset.currentPath().equals(expected.relativePath()));
        if (!present) throw new IllegalStateException("Registered Markdown was not indexed");
    }

    private Path checkedFile(WorkspaceId workspaceId, String relativePath) {
        if (relativePath == null || !relativePath.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new IllegalArgumentException("Only .md files can be registered");
        }
        new WorkspaceFileEntry(relativePath, Path.of(relativePath).getFileName().toString(),
                WorkspaceFileKind.MARKDOWN, null, null, null, null);
        Path root = paths.workspaceRoot(workspaceId);
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Path escapes Workspace");
        Path part = root;
        for (Path segment : root.relativize(file)) {
            if (segment.toString().equalsIgnoreCase(".quizforge")) {
                throw new IllegalArgumentException("Internal Workspace path cannot be registered");
            }
            part = part.resolve(segment);
            if (Files.isSymbolicLink(part)) throw new IllegalArgumentException("Linked Markdown cannot be registered");
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Markdown file does not exist");
        }
        return file;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTemporary(Path file) {
        try { Files.deleteIfExists(file); }
        catch (IOException ignored) {
            // A leftover temporary file never replaces the registered Markdown.
        }
    }

    private QuizForgeException failure(String action, IOException error) {
        return new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_STORAGE_FAILED,
                "Could not " + action + ".", error);
    }
}
