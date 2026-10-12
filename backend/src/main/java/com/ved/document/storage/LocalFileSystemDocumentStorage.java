package com.ved.document.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.ved.document.DocumentProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Stores files under {@code ved.documents.storage-dir} as {@code yyyy/MM/dd/<uuid>.<ext>}.
 */
@Component
class LocalFileSystemDocumentStorage implements DocumentStorage {

    private final Path root;

    LocalFileSystemDocumentStorage(DocumentProperties properties) {
        this.root = properties.storageDir().toAbsolutePath().normalize();
    }

    @Override
    public String store(InputStream content, String extension) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String key = "%d/%02d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                UUID.randomUUID(), extension);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new StorageException("Failed to store file", e);
        }
    }

    @Override
    public Resource load(String key) {
        return new FileSystemResource(resolve(key));
    }

    @Override
    public String copy(String key) {
        int dot = key.lastIndexOf('.');
        try (InputStream in = Files.newInputStream(resolve(key))) {
            return store(in, dot < 0 ? "bin" : key.substring(dot + 1));
        } catch (IOException e) {
            throw new StorageException("Failed to copy file " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Failed to delete file " + key, e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes storage root: " + key);
        }
        return path;
    }
}
