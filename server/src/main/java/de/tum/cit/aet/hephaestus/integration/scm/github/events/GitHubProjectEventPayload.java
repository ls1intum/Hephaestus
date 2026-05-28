package de.tum.cit.aet.hephaestus.integration.scm.github.events;

import de.tum.cit.aet.hephaestus.integration.scm.github.project.Project;
import de.tum.cit.aet.hephaestus.integration.scm.github.project.ProjectItem;
import de.tum.cit.aet.hephaestus.integration.scm.github.project.ProjectStatusUpdate;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Async-safe payload DTOs mirroring the GitHub Projects V2 GraphQL surface
 * ({@code Project}, {@code ProjectItem}, {@code ProjectStatusUpdate}). GitHub-only
 * — GitLab has no equivalent product.
 */
public final class GitHubProjectEventPayload {

    private GitHubProjectEventPayload() {}

    /**
     * Immutable snapshot of a Project for event handling.
     *
     * @param actorId The user who performed the action (e.g., closed, reopened, updated the project).
     *                This is distinct from creatorId which is the original project creator.
     *                For webhook events, this comes from the sender field.
     *                For sync operations, this may be null.
     */
    public record ProjectData(
        @NonNull Long id,
        int number,
        @NonNull String title,
        @Nullable String shortDescription,
        boolean closed,
        boolean isPublic,
        @NonNull String url,
        @NonNull Project.OwnerType ownerType,
        @NonNull Long ownerId,
        @Nullable Long creatorId,
        @Nullable Long actorId,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable Instant closedAt
    ) {
        /**
         * Creates ProjectData from a Project entity with no actor specified.
         * Use this for sync operations where the actor is unknown.
         */
        public static ProjectData from(Project project) {
            return from(project, null);
        }

        /**
         * Creates ProjectData from a Project entity with the specified actor.
         * Use this for webhook events where the sender is known.
         *
         * @param project the project entity
         * @param actorId the ID of the user who performed the action (from webhook sender)
         */
        public static ProjectData from(Project project, @Nullable Long actorId) {
            return new ProjectData(
                project.getId(),
                project.getNumber(),
                project.getTitle() != null ? project.getTitle() : "Untitled",
                project.getShortDescription(),
                project.isClosed(),
                project.isPublic(),
                project.getUrl() != null ? project.getUrl() : "",
                project.getOwnerType(),
                project.getOwnerId(),
                project.getCreator() != null ? project.getCreator().getId() : null,
                actorId,
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getClosedAt()
            );
        }
    }

    /**
     * Immutable snapshot of a ProjectItem for event handling.
     *
     * @param actorId The user who performed the action (e.g., created, archived, moved the item).
     *                For webhook events, this comes from the sender field.
     *                For sync operations, this may be null.
     */
    public record ProjectItemData(
        @NonNull Long id,
        @NonNull String nodeId,
        @NonNull Long projectId,
        @NonNull ProjectItem.ContentType contentType,
        @Nullable Long issueId,
        boolean archived,
        @Nullable Long actorId,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt
    ) {
        /**
         * Creates ProjectItemData from a ProjectItem entity with no actor specified.
         * Use this for sync operations where the actor is unknown.
         */
        public static ProjectItemData from(ProjectItem item) {
            return from(item, null);
        }

        /**
         * Creates ProjectItemData from a ProjectItem entity with the specified actor.
         * Use this for webhook events where the sender is known.
         *
         * @param item the project item entity
         * @param actorId the ID of the user who performed the action (from webhook sender)
         */
        public static ProjectItemData from(ProjectItem item, @Nullable Long actorId) {
            return new ProjectItemData(
                item.getId(),
                item.getNodeId() != null ? item.getNodeId() : "",
                item.getProject().getId(),
                item.getContentType(),
                item.getIssue() != null ? item.getIssue().getId() : null,
                item.isArchived(),
                actorId,
                item.getCreatedAt(),
                item.getUpdatedAt()
            );
        }
    }

    /**
     * Immutable snapshot of a ProjectStatusUpdate for event handling.
     */
    public record ProjectStatusUpdateData(
        @NonNull Long id,
        @NonNull String nodeId,
        @NonNull Long projectId,
        @Nullable String body,
        @Nullable LocalDate startDate,
        @Nullable LocalDate targetDate,
        @Nullable ProjectStatusUpdate.Status status,
        @Nullable Long creatorId,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt
    ) {
        public static ProjectStatusUpdateData from(ProjectStatusUpdate update) {
            return new ProjectStatusUpdateData(
                update.getId(),
                update.getNodeId() != null ? update.getNodeId() : "",
                update.getProject().getId(),
                update.getBody(),
                update.getStartDate(),
                update.getTargetDate(),
                update.getStatus(),
                update.getCreator() != null ? update.getCreator().getId() : null,
                update.getCreatedAt(),
                update.getUpdatedAt()
            );
        }
    }
}
