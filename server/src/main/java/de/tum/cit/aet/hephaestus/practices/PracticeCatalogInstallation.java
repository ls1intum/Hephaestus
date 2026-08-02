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
