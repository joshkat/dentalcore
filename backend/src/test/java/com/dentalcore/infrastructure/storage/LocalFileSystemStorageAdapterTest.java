package com.dentalcore.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileSystemStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalFileSystemStorageAdapter adapter() {
        return new LocalFileSystemStorageAdapter(tempDir.toString());
    }

    @Test
    void storeLoadDeleteRoundTrip() throws Exception {
        LocalFileSystemStorageAdapter storage = adapter();
        byte[] payload = "x-ray bytes".getBytes(StandardCharsets.UTF_8);

        storage.store("abc-123.pdf", new ByteArrayInputStream(payload));
        assertThat(storage.exists("abc-123.pdf")).isTrue();

        try (InputStream in = storage.load("abc-123.pdf")) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }

        storage.delete("abc-123.pdf");
        assertThat(storage.exists("abc-123.pdf")).isFalse();
    }

    @Test
    void pathTraversalKeysAreRejected() {
        LocalFileSystemStorageAdapter storage = adapter();
        for (String bad : new String[]{"../escape.txt", "a/b.txt", "..", "a\\b", ""}) {
            assertThatThrownBy(() -> storage.store(bad, new ByteArrayInputStream(new byte[]{1})))
                    .as("key '%s'", bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
