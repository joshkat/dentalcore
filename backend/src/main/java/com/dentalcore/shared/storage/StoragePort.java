package com.dentalcore.shared.storage;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * Binary storage abstraction. The local filesystem adapter serves development
 * and single-office deployments; an S3-compatible adapter can replace it
 * without touching callers.
 */
public interface StoragePort {

    /** A stored blob with its last-modified time (maps to S3 key + LastModified). */
    record StoredBlob(String key, Instant lastModified) {
    }

    /** Stores the stream under the given key (keys are caller-generated, opaque). */
    void store(String key, InputStream content);

    /** Opens the stored content; caller closes the stream. */
    InputStream load(String key);

    void delete(String key);

    boolean exists(String key);

    /**
     * Lists every stored blob with its last-modified time. Used by retention
     * sweeps to reconcile on-disk blobs against the document index.
     */
    List<StoredBlob> listBlobs();
}
