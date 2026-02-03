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
 * <p>
 * <h2>Polymorphic Ownership Design</h2>
 * <p>
 * This entity uses a polymorphic ownership pattern with {@link #ownerType} and
 * {@link #ownerId} instead of traditional foreign key relationships. This design
 * mirrors GitHub's flexible project ownership model where a single project entity
 * can belong to different types of owners.
 * <p>
 * <b>Trade-offs:</b>
 * <ul>
 *   <li><b>Flexibility:</b> Single table design supports all owner types without
 *       complex inheritance hierarchies or table-per-owner-type patterns</li>
 *   <li><b>No DB-level FK:</b> Database cannot enforce referential integrity because
 *       {@code ownerId} may reference organization, repository, or user tables</li>
 *   <li><b>Application-level integrity:</b> Cascade deletes are handled by
 *       {@link ProjectIntegrityService} when owner entities are deleted</li>
 * </ul>
 * <p>
 * <b>Integrity Enforcement:</b>
 * <ul>
 *   <li>Organization deletion: {@code GitHubOrganizationProcessor.delete()} cascades to projects</li>
 *   <li>Repository deletion: {@code GitHubRepositoryMessageHandler} cascades to projects</li>
 *   <li>User deletion: {@code ProjectIntegrityService.cascadeDeleteProjectsForUser()}</li>
 *   <li>Orphan detection: {@code ProjectIntegrityService.findOrphanedProjects()}</li>
 * </ul>
 *
 * @see ProjectIntegrityService
 * @see Project.OwnerType
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
     * <p>
     * GitHub Projects V2 can be owned by three types of entities, each with different
     * characteristics and support levels in Hephaestus:
     * <p>
     * <h3>Support Matrix</h3>
     * <table border="1">
     *   <tr><th>Owner Type</th><th>Webhook Support</th><th>Sync Support</th><th>Notes</th></tr>
     *   <tr>
     *     <td><b>ORGANIZATION</b></td>
     *     <td>Full</td>
     *     <td>Full</td>
     *     <td>Primary use case. Scope resolved via organization login.</td>
     *   </tr>
     *   <tr>
     *     <td><b>REPOSITORY</b></td>
     *     <td>Full</td>
     *     <td>Not implemented</td>
     *     <td>Scope resolved via repository fullName. Sync requires implementation.</td>
     *   </tr>
     *   <tr>
     *     <td><b>USER</b></td>
     *     <td>Detected but skipped</td>
     *     <td>Not implemented</td>
     *     <td>User projects aren't associated with monitored workspaces.</td>
     *   </tr>
     * </table>
     * <p>
     * <h3>Webhook Detection</h3>
     * <p>
     * Owner type is determined from webhook payload fields:
     * <ol>
     *   <li>If {@code organization} field is present → ORGANIZATION</li>
     *   <li>Else if {@code repository} field is present → REPOSITORY</li>
     *   <li>Else → USER (only {@code sender} is present)</li>
     * </ol>
     *
     * @see GitHubProjectMessageHandler for webhook handling
     * @see GitHubProjectSyncService for sync (currently ORGANIZATION only)
     */
    public enum OwnerType {
        /**
         * Project owned by a GitHub repository.
         * <p>
         * Repository-level projects are linked to a specific repository and
         * visible to repository collaborators. Webhook support is available
         * but sync is not yet implemented.
         */
        REPOSITORY,

        /**
         * Project owned by a GitHub organization.
         * <p>
         * Organization-level projects are the primary use case. They are
         * visible to organization members and have full webhook and sync support.
         */
        ORGANIZATION,

        /**
         * Project owned by a GitHub user.
         * <p>
         * User-level projects are personal projects that are not associated
         * with any organization or repository. These are detected in webhooks
         * but currently skipped since they aren't linked to monitored workspaces.
         */
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
     * <p>
     * <b>Note:</b> This field does not have a database-level foreign key constraint
     * because it references different tables depending on {@link #ownerType}.
     * Referential integrity is maintained at the application level via
     * {@link ProjectIntegrityService}.
     *
     * @see ProjectIntegrityService#cascadeDeleteProjectsForOrganization(Long)
     * @see ProjectIntegrityService#cascadeDeleteProjectsForRepository(Long)
     * @see ProjectIntegrityService#cascadeDeleteProjectsForUser(Long)
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
     * The project's README content (markdown).
     */
    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String readme;

    /**
     * Whether this project is a template.
     */
    @Column(nullable = false)
    private boolean template = false;

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
     * Pagination cursor for resuming field sync.
     * <p>
     * When field sync is interrupted (rate limit, error, etc.), this cursor
     * allows resuming from where we left off instead of starting over.
     * Cleared when field sync completes successfully.
     */
    @Column(name = "field_sync_cursor", length = 256)
    private String fieldSyncCursor;

    /**
     * Timestamp of the last successful field sync for this project.
     * <p>
     * Used to determine if fields need to be re-synced. Fields rarely change,
     * so this timestamp helps skip unnecessary field sync operations.
     */
    private Instant fieldsSyncedAt;

    /**
     * Pagination cursor for resuming status update sync.
     * <p>
     * When status update sync is interrupted (rate limit, error, etc.), this cursor
     * allows resuming from where we left off instead of starting over.
     * Cleared when status update sync completes successfully.
     */
    @Column(name = "status_update_sync_cursor", length = 256)
    private String statusUpdateSyncCursor;

    /**
     * Timestamp of the last successful status update sync for this project.
     * <p>
     * Used for incremental sync - only status updates created after this timestamp
     * need to be fetched on subsequent syncs.
     */
    private Instant statusUpdatesSyncedAt;

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

    /**
     * Status updates for this project.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<ProjectStatusUpdate> statusUpdates = new HashSet<>();

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

    /**
     * Adds a status update to this project and maintains bidirectional consistency.
     *
     * @param statusUpdate the status update to add
     */
    public void addStatusUpdate(ProjectStatusUpdate statusUpdate) {
        if (statusUpdate != null) {
            this.statusUpdates.add(statusUpdate);
            statusUpdate.setProject(this);
        }
    }

    /**
     * Removes a status update from this project and maintains bidirectional consistency.
     *
     * @param statusUpdate the status update to remove
     */
    public void removeStatusUpdate(ProjectStatusUpdate statusUpdate) {
        if (statusUpdate != null) {
            this.statusUpdates.remove(statusUpdate);
            statusUpdate.setProject(null);
        }
    }
}
