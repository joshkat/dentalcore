package com.dentalcore.shared.storage;

import java.io.InputStream;

/**
 * Binary storage abstraction. The local filesystem adapter serves development
 * and single-office deployments; an S3-compatible adapter can replace it
 * without touching callers.
 */
public interface StoragePort {

    /** Stores the stream under the given key (keys are caller-generated, opaque). */
    void store(String key, InputStream content);

    /** Opens the stored content; caller closes the stream. */
    InputStream load(String key);

    void delete(String key);

    boolean exists(String key);
}
