package de.tum.cit.aet.hephaestus.integration.outline.refs;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Value-only Outline document reference.
 *
 * <p>NOT yet an {@code @Entity}. Per plan v4 D33 the persisted {@code outline_document}
 * table ships with the Outline epic (#1203) once the framework needs to track per-document
 * sync watermarks. For #1198 the value record lets framework code reference documents
 * (e.g. {@code FeedbackPost.target_external_id}) without storing content or implying a
 * persistence boundary that hasn't been designed yet.
 *
 * @param workspaceId         Hephaestus workspace id this ref is scoped to
 * @param workspaceExternalId Outline team id ({@code team.id}) — disambiguates multi-workspace
 *                            installs once a Hephaestus workspace connects multiple Outline
 *                            teams
 * @param documentId          Outline document id (e.g. {@code "doc_…"})
 * @param lastRevisionId      optional latest known revision id; used for ETag-style staleness
 *                            checks once the sync path lands
 */
public record OutlineDocumentRef(
    long workspaceId,
    @NonNull String workspaceExternalId,
    @NonNull String documentId,
    @Nullable String lastRevisionId
) {
}
