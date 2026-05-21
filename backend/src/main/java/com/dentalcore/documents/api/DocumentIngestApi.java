package com.dentalcore.documents.api;

import java.util.UUID;

/**
 * Public interface of the documents module for other modules that generate
 * PDFs (signed forms, statements) and want them filed in the patient's
 * Documents tab.
 */
public interface DocumentIngestApi {

    /**
     * Stores a generated PDF as a patient document and returns the new
     * document id. {@code category} must be one of the document categories
     * (e.g. CONSENT, OTHER).
     */
    UUID storePdf(UUID patientId, String title, String category, byte[] pdf,
                  UUID uploadedByUserId);
}
