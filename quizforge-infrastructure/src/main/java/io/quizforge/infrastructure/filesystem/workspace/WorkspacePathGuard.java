package io.quizforge.infrastructure.filesystem.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Checks real paths as well as symbolic links; Windows junctions are not symbolic links. */
public final class WorkspacePathGuard {
    private WorkspacePathGuard() { }

    public static Path requireInside(Path root, Path target) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot)) throw new IllegalArgumentException("Path escapes storage root");
        try {
            Path realRoot = normalizedRoot.toRealPath();
            Path part = normalizedRoot;
            for (Path segment : normalizedRoot.relativize(normalized)) {
                part = part.resolve(segment);
                if (Files.isSymbolicLink(part)) throw new IllegalArgumentException("Linked paths cannot be accessed");
                if (Files.exists(part, LinkOption.NOFOLLOW_LINKS) && !part.toRealPath().startsWith(realRoot))
                    throw new IllegalArgumentException("Linked path escapes storage root");
            }
            return normalized;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not verify storage path", failure);
        }
    }
}
