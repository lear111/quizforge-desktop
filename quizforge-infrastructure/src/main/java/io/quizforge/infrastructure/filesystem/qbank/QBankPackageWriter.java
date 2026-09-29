package io.quizforge.infrastructure.filesystem.qbank;

import io.quizforge.core.*;
import io.quizforge.core.question.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import static io.quizforge.infrastructure.filesystem.qbank.PackageJson.*;

/** File-first publication: bounded resource spools, closed verified ZIP, then atomic replacement. */
public final class QBankPackageWriter {
    private final PackageLimits limits;
    public QBankPackageWriter() { this(PackageLimits.DEFAULT); }
    public QBankPackageWriter(PackageLimits limits) { this.limits = Objects.requireNonNull(limits); }
    public QuestionBank write(Path target, QuestionBank bank) { return write(target, bank, ResourceContentProvider.NONE); }

    /** Returns the logical bank containing hashes calculated from the supplied bytes. */
    public QuestionBank write(Path target, QuestionBank bank, ResourceContentProvider provider) {
        // Validate domain and package paths before touching the target or opening a provider.
        CODEC.write(bank);
        if (bank.resources().size() > limits.maxEntries() - 2) limit("Too many resources");
        Set<String> paths = new HashSet<>();
        for (QBankResource resource : bank.resources()) {
            resourcePath(resource.locator());
            if (!paths.add(resource.locator())) throw error(ErrorCode.INVALID_MANIFEST, "Duplicate resource path");
        }
        Path temporary = null;
        List<Path> spools = new ArrayList<>();
        Throwable failure = null;
        try {
            Path folder = target.toAbsolutePath().getParent();
            temporary = Files.createTempFile(folder, ".qf-package-", ".tmp");
            Map<String,Path> content = new HashMap<>();
            List<QBankResource> resources = new ArrayList<>();
            long total = 0;
            for (QBankResource resource : bank.resources()) {
                Path spool = Files.createTempFile(folder, ".qf-resource-", ".tmp");
                spools.add(spool);
                MessageDigest hash = QBankPackageReader.sha256();
                long bytes = 0;
                InputStream supplied;
                try { supplied = provider.open(resource); }
                catch (IOException e) { throw new QuizForgeException(ErrorCode.MISSING_RESOURCE, "No bytes for " + resource.id(), e); }
                try (InputStream input = supplied; OutputStream output = Files.newOutputStream(spool)) {
                    if (input == null) throw error(ErrorCode.MISSING_RESOURCE, resource.id());
                    byte[] buffer = new byte[8192];
                    for (int count; (count = readResource(input, buffer, resource.id())) != -1;) {
                        if (count > limits.resourceBytes() - bytes || count > limits.totalBytes() - total)
                            limit("Resource uncompressed size");
                        bytes += count; total += count;
                        hash.update(buffer, 0, count); output.write(buffer, 0, count);
                    }
                }
                content.put(resource.locator(), spool);
                resources.add(new QBankResource(resource.id(), resource.kind(), resource.mediaType(), resource.locator(), HexFormat.of().formatHex(hash.digest())));
            }
            QuestionBank normalized = new QuestionBank(bank.assetId(), bank.title(), bank.schemaVersion(), bank.stimuli(), bank.questions(), resources);
            byte[] manifest = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest(normalized));
            byte[] body = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(body(normalized));
            if (manifest.length > limits.manifestBytes() || body.length > limits.bankBytes()
                    || manifest.length > limits.totalBytes() - total
                    || body.length > limits.totalBytes() - total - manifest.length) limit("JSON or total uncompressed size");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                entry(zip, "manifest.json"); zip.write(manifest); zip.closeEntry();
                entry(zip, "bank.json"); zip.write(body); zip.closeEntry();
                for (String path : new TreeSet<>(content.keySet())) {
                    entry(zip, path); Files.copy(content.get(path), zip); zip.closeEntry();
                }
            }
            QuestionBank verified = new QBankPackageReader(limits).read(temporary);
            if (!normalized.equals(verified) || !CODEC.contentId(normalized).equals(CODEC.contentId(verified)))
                throw error(ErrorCode.INVALID_PACKAGE, "Written logical content did not round-trip");
            // Cleanup can fail too; finish it before publication so an error never changes the target.
            for (Path spool : spools) Files.delete(spool);
            spools.clear();
            // No nonatomic fallback: unsupported atomic replacement leaves the old target intact.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            return verified;
        } catch (IOException e) {
            failure = e;
            throw new QuizForgeException(ErrorCode.QUESTION_BANK_STORAGE_FAILED, "Could not atomically write QBank package", e);
        } catch (RuntimeException e) { failure = e; throw e; }
        finally {
            if (temporary != null) spools.add(temporary);
            for (Path spool : spools) try { Files.deleteIfExists(spool); }
            catch (IOException e) {
                if (failure != null) failure.addSuppressed(e);
                else throw new QuizForgeException(ErrorCode.QUESTION_BANK_STORAGE_FAILED, "Could not remove package temporary file", e);
            }
        }
    }
    private void entry(ZipOutputStream zip, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry);
    }
    private int readResource(InputStream input, byte[] buffer, String id) {
        try { return input.read(buffer); }
        catch (IOException e) { throw new QuizForgeException(ErrorCode.MISSING_RESOURCE, "Could not read bytes for " + id, e); }
    }
    private void limit(String detail) { throw error(ErrorCode.PACKAGE_LIMIT_EXCEEDED, detail); }
}
