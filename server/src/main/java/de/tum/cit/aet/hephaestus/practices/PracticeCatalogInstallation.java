package de.tum.cit.aet.hephaestus.practices;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Proof that a workspace has already been given the catalog, so a later boot does not re-create
 * practices an administrator has since edited or deleted.
 *
 * <p>{@code provenanceLinkedAt} records the second, separate question: whether this workspace's
 * copies have been matched back to the curated entries they came from. A workspace seeded by this
 * version is linked as it is created; one seeded before the catalog existed is linked by
 * {@link de.tum.cit.aet.hephaestus.practices.curated.CatalogProvenanceBackfill} on the next boot.
 */
@Entity
@Table(name = "practice_catalog_installation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeCatalogInstallation {

    @Id
    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "installed_at", nullable = false, updatable = false)
    private Instant installedAt;

    @Column(name = "provenance_linked_at")
    private @Nullable Instant provenanceLinkedAt;

    public PracticeCatalogInstallation(Long workspaceId, Instant installedAt, @Nullable Instant provenanceLinkedAt) {
        this.workspaceId = workspaceId;
        this.installedAt = installedAt;
        this.provenanceLinkedAt = provenanceLinkedAt;
    }

    public void markProvenanceLinked(Instant now) {
        this.provenanceLinkedAt = now;
    }
}
