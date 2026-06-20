package com.dentalcore.documents.internal.service;

import com.dentalcore.documents.internal.repository.DocumentRepository;
import com.dentalcore.shared.storage.StoragePort;
import com.dentalcore.shared.storage.StoragePort.StoredBlob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Reclaims on-disk blobs that no live {@link com.dentalcore.documents.internal.entity.Document}
 * references. Soft-deleting a document keeps its binary "for recovery/audit"
 * (see {@link DocumentService#delete}), and rolled-back ingests can leave a
 * blob with no committed row at all — both accumulate forever otherwise.
 *
 * <p>A blob is removed only when it is unreferenced (or its row was soft-deleted
 * past the grace window) AND its file is older than the grace window. The
 * second guard protects a blob that a still-open transaction has written but not
 * yet committed, and gives failed-ingest orphans a recovery window. (This
 * assumes the filesystem's last-modified clock and the app clock are within a
 * skew far smaller than the grace window — true for local and typical mounted
 * storage.)
 *
 * <p>Each removal is logged by key: a swept blob is unrecoverable and has no
 * live document row to attach an audit event to, so the log is the trail.
 */
@Component
public class DocumentBlobCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DocumentBlobCleanupJob.class);

    private final StoragePort storage;
    private final DocumentRepository documents;
    private final Duration grace;

    public DocumentBlobCleanupJob(
            StoragePort storage,
            DocumentRepository documents,
            @Value("${dentalcore.documents.blob-retention-grace-days:30}") long graceDays) {
        this.storage = storage;
        this.documents = documents;
        this.grace = Duration.ofDays(graceDays);
    }

    @Scheduled(cron = "${dentalcore.documents.cleanup-cron:0 0 4 * * *}")
    public void sweepOrphanedBlobs() {
        Instant cutoff = Instant.now().minus(grace);
        Set<String> keep = documents.findRetainableStorageKeys(cutoff);

        int removed = 0;
        int failed = 0;
        for (StoredBlob blob : storage.listBlobs()) {
            if (keep.contains(blob.key())) {
                continue; // live document, or soft-deleted within the grace window
            }
            if (blob.lastModified().isAfter(cutoff)) {
                continue; // too new to be a stale orphan (in-flight write / just deleted)
            }
            try {
                storage.delete(blob.key());
                removed++;
                log.info("Document blob sweep: removed orphaned blob {}", blob.key());
            } catch (RuntimeException e) {
                // one unreadable/locked blob must not abort the rest of the sweep
                failed++;
                log.warn("Document blob sweep: failed to remove blob {}", blob.key(), e);
            }
        }

        if (removed > 0 || failed > 0) {
            log.info("Document blob sweep: removed {} orphaned blob(s), {} failed", removed, failed);
        }
    }
}
