package com.dentalcore.infrastructure.storage;

import com.dentalcore.shared.storage.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class LocalFileSystemStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemStorageAdapter.class);
    /** Keys are generated UUID-based names — never user input — but verify anyway. */
    private static final Pattern SAFE_KEY = Pattern.compile("[a-zA-Z0-9._-]{1,255}");

    private final Path root;

    public LocalFileSystemStorageAdapter(
            @Value("${dentalcore.storage.local-path:./data/documents}") String localPath) {
        this.root = Path.of(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create document storage at " + root, e);
        }
        log.info("Document storage: {}", root);
    }

    @Override
    public void store(String key, InputStream content) {
        try {
            Files.copy(content, resolve(key), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store " + key, e);
        }
    }

    @Override
    public InputStream load(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public List<StoredBlob> listBlobs() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(this::toStoredBlob)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list document storage at " + root, e);
        }
    }

    private StoredBlob toStoredBlob(Path path) {
        try {
            return new StoredBlob(
                    path.getFileName().toString(),
                    Files.getLastModifiedTime(path).toInstant());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stat " + path, e);
        }
    }

    private Path resolve(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Illegal storage key");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Illegal storage key");
        }
        return resolved;
    }
}
