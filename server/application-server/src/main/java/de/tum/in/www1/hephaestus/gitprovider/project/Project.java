package de.tum.in.www1.hephaestus.gitprovider.project;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

/**
 * Represents a GitHub Projects V2 project.
 * <p>
 * Projects can be owned by repositories, organizations, or users.
 * Each project has a unique number within its owner context.
 */
@Entity
@Table(
    name = "project",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_owner_number", columnNames = { "owner_type", "owner_id", "number" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Project extends BaseGitServiceEntity {

    /**
     * The type of entity that owns this project.
     */
    public enum OwnerType {
        REPOSITORY,
        ORGANIZATION,
        USER,
    }

    /**
     * GitHub GraphQL node ID for this project.
     */
    @Column(length = 64)
    private String nodeId;

    /**
     * The type of owner for this project.
     */
    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private OwnerType ownerType;

    /**
     * The ID of the owner entity (repository, organization, or user).
     */
    @Column(nullable = false)
    private Long ownerId;

    /**
     * The project number within the owner context.
     * This is unique per owner (owner_type + owner_id).
     */
    @Column(nullable = false)
    private int number;

    /**
     * The title of the project.
     */
    @Column(length = 256)
    private String title;

    /**
     * A short description of the project.
     */
    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String shortDescription;

    /**
     * The URL to the project on GitHub.
     */
    @Column(length = 512)
    private String url;

    /**
     * Whether the project is closed.
     */
    @Column(nullable = false)
    private boolean closed = false;

    /**
     * When the project was closed, if applicable.
     */
    private Instant closedAt;

    /**
     * Whether the project is publicly visible.
     */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    /**
     * Timestamp of the last successful sync for this project.
     */
    private Instant lastSyncAt;

    /**
     * Pagination cursor for resuming item sync.
     * <p>
     * When item sync is interrupted (rate limit, error, etc.), this cursor
     * allows resuming from where we left off instead of starting over.
     * Cleared when item sync completes successfully.
     */
    @Column(name = "item_sync_cursor", length = 256)
    private String itemSyncCursor;

    /**
     * Timestamp of the last successful item sync for this project.
     * <p>
     * Used for incremental sync - only items updated after this timestamp
     * need to be fetched on subsequent syncs.
     */
    private Instant itemsSyncedAt;

    /**
     * The user who created this project.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    @ToString.Exclude
    private User creator;

    /**
     * Items in this project.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<ProjectItem> items = new HashSet<>();

    /**
     * Custom fields defined for this project.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<ProjectField> fields = new HashSet<>();

    // ==================== Bidirectional Relationship Helpers ====================

    /**
     * Adds an item to this project and maintains bidirectional consistency.
     *
     * @param item the item to add
     */
    public void addItem(ProjectItem item) {
        if (item != null) {
            this.items.add(item);
            item.setProject(this);
        }
    }

    /**
     * Removes an item from this project and maintains bidirectional consistency.
     *
     * @param item the item to remove
     */
    public void removeItem(ProjectItem item) {
        if (item != null) {
            this.items.remove(item);
            item.setProject(null);
        }
    }

    /**
     * Adds a field to this project and maintains bidirectional consistency.
     *
     * @param field the field to add
     */
    public void addField(ProjectField field) {
        if (field != null) {
            this.fields.add(field);
            field.setProject(this);
        }
    }

    /**
     * Removes a field from this project and maintains bidirectional consistency.
     *
     * @param field the field to remove
     */
    public void removeField(ProjectField field) {
        if (field != null) {
            this.fields.remove(field);
            field.setProject(null);
        }
    }
}
