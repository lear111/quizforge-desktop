package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.port.MaterialFileStorage;
import io.quizforge.core.port.StoredMaterialFile;
import io.quizforge.core.workspace.WorkspaceId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class LocalMaterialFileStorage implements MaterialFileStorage {
    private final WorkspacePathResolver paths;

    public LocalMaterialFileStorage(WorkspacePathResolver paths) {
        this.paths = paths;
    }

    @Override
    public StoredMaterialFile store(WorkspaceId workspaceId, MaterialId materialId,
            String sourceFile, long maxBytes) {
        Path source;
        try {
            source = Path.of(sourceFile).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new QuizForgeException(ErrorCode.MATERIAL_READ_FAILED, "Source file path is invalid.", e);
        }
        String originalName = source.getFileName().toString();
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".md") && !lowerName.endsWith(".markdown")) {
            throw new QuizForgeException(ErrorCode.UNSUPPORTED_MATERIAL_FORMAT,
                    "Only .md and .markdown files can be imported.");
        }
        if (!Files.isRegularFile(source)) {
            throw new QuizForgeException(ErrorCode.MATERIAL_READ_FAILED,
                    "Source file does not exist or is not a regular file.");
        }
        byte[] contents = readSource(source, maxBytes);
        Path target = paths.materialPath(workspaceId, materialId);
        try {
            Files.write(target, contents, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED,
                    "Could not save the material copy.", e);
        }
        return new StoredMaterialFile(originalName, materialId + ".md", contents.length);
    }

    private byte[] readSource(Path source, long maxBytes) {
        try (InputStream input = Files.newInputStream(source);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            long size = 0;
            int count;
            while ((count = input.read(chunk)) != -1) {
                size += count;
                if (size > maxBytes) {
                    throw new QuizForgeException(ErrorCode.MATERIAL_TOO_LARGE,
                            "Material exceeds the configured size limit.");
                }
                output.write(chunk, 0, count);
            }
            if (size == 0) {
                throw new QuizForgeException(ErrorCode.EMPTY_MATERIAL, "Material file is empty.");
            }
            byte[] contents = output.toByteArray();
            decodeUtf8(contents);
            return contents;
        } catch (IOException e) {
            throw new QuizForgeException(ErrorCode.MATERIAL_READ_FAILED,
                    "Could not read the source material.", e);
        }
    }

    @Override
    public String read(Material material) {
        try {
            return decodeUtf8(Files.readAllBytes(paths.materialPath(material)));
        } catch (IOException e) {
            throw new QuizForgeException(ErrorCode.MATERIAL_READ_FAILED,
                    "Could not read the stored material.", e);
        }
    }

    @Override
    public void delete(Material material) {
        try {
            Files.deleteIfExists(paths.materialPath(material));
        } catch (IOException e) {
            throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED,
                    "Could not delete the stored material.", e);
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new QuizForgeException(ErrorCode.MATERIAL_READ_FAILED,
                    "Material must contain valid UTF-8 text.", e);
        }
    }
}
