package io.quizforge.infrastructure.filesystem.qbank;

/** Limits refer to uncompressed bytes, including entries not listed in the manifest. */
public record PackageLimits(int maxEntries, long manifestBytes, long bankBytes,
        long resourceBytes, long totalBytes) {
    public static final PackageLimits DEFAULT = new PackageLimits(10_000,
            4L * 1024 * 1024, 64L * 1024 * 1024, 256L * 1024 * 1024, 1024L * 1024 * 1024);
    public PackageLimits {
        if (maxEntries < 2 || manifestBytes < 1 || bankBytes < 1 || resourceBytes < 1 || totalBytes < 1)
            throw new IllegalArgumentException("Package limits must be positive and allow both JSON entries");
    }
    /** Bound ZIP's central directory before ZipFile allocates it; JSON/resource limits alone cannot do this. */
    public long centralDirectoryBytes() { return Math.min(64L * 1024 * 1024, maxEntries * 65_536L); }
}
