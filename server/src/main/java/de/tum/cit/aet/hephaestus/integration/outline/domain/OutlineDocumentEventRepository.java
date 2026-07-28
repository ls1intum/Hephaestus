package de.tum.cit.aet.hephaestus.integration.outline.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the append-only {@link OutlineDocumentEvent} log: insert + workspace-scoped read +
 * the workspace-level bulk erase — deliberately no per-row update or delete path, so the trail
 * cannot be rewritten. Every finder carries the {@code workspace_id} predicate the tenancy
 * {@code StatementInspector} requires; {@link #deleteByWorkspaceId(Long)} is the GDPR erase used by
 * workspace purge and connection revoke ({@code actor_subject} is personal data).
 */
public interface OutlineDocumentEventRepository extends JpaRepository<OutlineDocumentEvent, Long> {
    long deleteByWorkspaceId(Long workspaceId);

    /** One document's event trail, oldest first — the longitudinal per-document read. */
    List<OutlineDocumentEvent> findByWorkspaceIdAndDocumentIdOrderByOccurredAtAsc(Long workspaceId, String documentId);

    long countByWorkspaceId(Long workspaceId);
}
