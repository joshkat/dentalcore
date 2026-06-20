package com.dentalcore.documents;

import com.dentalcore.documents.internal.repository.DocumentRepository;
import com.dentalcore.documents.internal.service.DocumentBlobCleanupJob;
import com.dentalcore.shared.storage.StoragePort;
import com.dentalcore.shared.storage.StoragePort.StoredBlob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentBlobCleanupJobTest {

    private static final long GRACE_DAYS = 30;

    private StoragePort storage;
    private DocumentRepository documents;
    private DocumentBlobCleanupJob job;

    private final Instant old = Instant.now().minus(Duration.ofDays(GRACE_DAYS + 5));
    private final Instant fresh = Instant.now().minus(Duration.ofDays(1));

    @BeforeEach
    void setUp() {
        storage = mock(StoragePort.class);
        documents = mock(DocumentRepository.class);
        job = new DocumentBlobCleanupJob(storage, documents, GRACE_DAYS);
    }

    @Test
    void keepsBlobsReferencedByLiveOrRecentlyDeletedDocuments() {
        when(documents.findRetainableStorageKeys(any())).thenReturn(Set.of("live.pdf", "recent.pdf"));
        when(storage.listBlobs()).thenReturn(List.of(
                new StoredBlob("live.pdf", old),
                new StoredBlob("recent.pdf", old)));

        job.sweepOrphanedBlobs();

        verify(storage, never()).delete(any());
    }

    @Test
    void removesOrphanedBlobOlderThanGrace() {
        when(documents.findRetainableStorageKeys(any())).thenReturn(Set.of());
        when(storage.listBlobs()).thenReturn(List.of(new StoredBlob("orphan.pdf", old)));

        job.sweepOrphanedBlobs();

        verify(storage).delete("orphan.pdf");
    }

    @Test
    void removesSoftDeletedPastGraceButKeepsItsFreshlyWrittenSibling() {
        // expired soft-delete no longer in the retainable set; the in-flight blob is too new
        when(documents.findRetainableStorageKeys(any())).thenReturn(Set.of());
        when(storage.listBlobs()).thenReturn(List.of(
                new StoredBlob("expired.pdf", old),
                new StoredBlob("inflight.pdf", fresh)));

        job.sweepOrphanedBlobs();

        verify(storage).delete("expired.pdf");
        verify(storage, never()).delete("inflight.pdf");
    }

    @Test
    void keepsFreshOrphanToProtectInFlightWrites() {
        when(documents.findRetainableStorageKeys(any())).thenReturn(Set.of());
        when(storage.listBlobs()).thenReturn(List.of(new StoredBlob("inflight.pdf", fresh)));

        job.sweepOrphanedBlobs();

        verify(storage, never()).delete(any());
    }

    @Test
    void removesOrphanSittingExactlyOnTheGraceBoundary() {
        // A blob whose mtime equals the cutoff is exactly `grace` old: the keep
        // guard (isAfter) only spares strictly-newer blobs, so this is eligible.
        Instant[] cutoff = new Instant[1];
        when(documents.findRetainableStorageKeys(any())).thenAnswer(inv -> {
            cutoff[0] = inv.getArgument(0);
            return Set.of();
        });
        when(storage.listBlobs()).thenAnswer(inv ->
                List.of(new StoredBlob("boundary.pdf", cutoff[0])));

        job.sweepOrphanedBlobs();

        verify(storage).delete("boundary.pdf");
    }

    @Test
    void doesNothingWhenStorageIsEmpty() {
        when(documents.findRetainableStorageKeys(any())).thenReturn(Set.of());
        when(storage.listBlobs()).thenReturn(List.of());

        job.sweepOrphanedBlobs();

        verify(storage, never()).delete(any());
    }

    @Test
    void oneFailedDeleteDoesNotAbortTheRemainingSweep() {
        when(documents.findRetainableStorageKeys(any())).thenReturn(Set.of());
        when(storage.listBlobs()).thenReturn(List.of(
                new StoredBlob("bad.pdf", old),
                new StoredBlob("good.pdf", old)));
        doThrow(new RuntimeException("locked")).when(storage).delete("bad.pdf");

        job.sweepOrphanedBlobs();

        verify(storage).delete("bad.pdf");
        verify(storage).delete("good.pdf"); // sweep continued past the failure
    }
}
