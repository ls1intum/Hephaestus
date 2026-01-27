package de.tum.in.www1.hephaestus.gitprovider.label;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
    name = "label",
    uniqueConstraints = @UniqueConstraint(name = "uq_label_repository_name", columnNames = { "repository_id", "name" })
)
@Getter
@Setter
@NoArgsConstructor
public class Label {

    @Id
    protected Long id;

    /**
     * When this label was created on GitHub.
     * Nullable because GitHub's GraphQL API returns this as optional.
     */
    private Instant createdAt;

    /**
     * When this label was last updated on GitHub.
     * Nullable because GitHub's GraphQL API returns this as optional.
     */
    private Instant updatedAt;

    @NonNull
    private String name;

    private String description;

    // 6-character hex code, without the leading #, identifying the color
    @NonNull
    private String color;

    @ManyToMany(mappedBy = "labels")
    @ToString.Exclude
    private Set<Issue> issues = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "repository_id")
    @ToString.Exclude
    private Repository repository;

    /**
     * Timestamp of the last successful sync for this label from the Git provider.
     * <p>
     * This is ETL infrastructure used by the sync engine to track when this label
     * was last synchronized via GraphQL. Used to implement sync cooldown logic
     * and detect stale data.
     */
    private Instant lastSyncAt;

    /**
     * Removes this label from all referencing issues (required before deletion).
     */
    public void removeAllIssues() {
        this.issues.forEach(issue -> issue.getLabels().remove(this));
        this.issues.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Label label = (Label) o;
        return id != null && Objects.equals(id, label.id);
    }

    @Override
    public int hashCode() {
        // Use a constant hashCode to ensure consistency across entity state transitions
        // (transient -> managed -> detached). The ID may be null before persistence.
        return getClass().hashCode();
    }
    // Ignored GitHub properties:
    // - default
}
