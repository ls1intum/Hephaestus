package de.tum.in.www1.hephaestus.gitprovider.installation;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "installation_repository")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class InstallationRepositoryLink {

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("installationId")
    @JoinColumn(
        name = "installation_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_installation_repository_installation")
    )
    @ToString.Exclude
    private Installation installation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("repositoryId")
    @JoinColumn(
        name = "repository_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_installation_repository_repository")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private Repository repository;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "installation_id")
        private Long installationId;

        @Column(name = "repository_id")
        private Long repositoryId;

        public Id(Long installationId, Long repositoryId) {
            this.installationId = installationId;
            this.repositoryId = repositoryId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id other)) {
                return false;
            }
            return (
                Objects.equals(installationId, other.installationId) && Objects.equals(repositoryId, other.repositoryId)
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(installationId, repositoryId);
        }
    }
    /*
     * Supported webhook fields/relationships (installation + installation_repositories payloads):
     * Fields:
     * - repositories[].id → composite key component (`installation_id`, `repository_id`)
     * - webhook timestamps (installation.updated_at / action-specific) → `linkedAt`, `removedAt`, `lastSyncedAt`
     * - action semantics (added/removed/deleted) → `active`
     * Relationships:
     * - installation.repositories[] ↔ `installation`
     * - repository metadata resolved via RepositorySyncService ↔ `repository`
     *
     * Ignored although available without extra fetch:
     * - repositories[].full_name / name (persisted on Repository entity)
     * - repositories[].node_id (tracked on Repository)
     *
     * Desired but missing in hub4j 2.0-rc.5 (GitHub REST provides them):
     * - Repository-level permissions granted to the installation (`permissions` block on installation_repositories)
     * - Selection reason / updated_by metadata from REST `repository_selection_user`
     *
     * Requires extra REST/GraphQL fetch (out of scope):
     * - `GET /installation/repositories` pagination to reconcile drift outside webhook windows.
     */
}
