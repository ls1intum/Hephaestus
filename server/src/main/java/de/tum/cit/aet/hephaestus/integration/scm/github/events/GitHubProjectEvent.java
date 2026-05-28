package de.tum.cit.aet.hephaestus.integration.scm.github.events;

import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * Sealed domain events for GitHub Projects V2 — the project, item, and
 * status-update lifecycles. Lives in the GitHub-vendor package because GitLab
 * has no equivalent concept; the {@code IntegrationCoreVendorNeutralityTest}
 * arch test forbids GitHub vendor types in {@code integration.core.events}.
 *
 * <p>Each sub-hierarchy is independently sealed so {@code @TransactionalEventListener}
 * subscribers can listen to the umbrella ({@code ProjectEvent}) and pattern-match
 * exhaustively on the variants.
 */
public final class GitHubProjectEvent {

    private GitHubProjectEvent() {}

    public sealed interface ProjectEvent
        permits ProjectCreated, ProjectUpdated, ProjectClosed, ProjectReopened, ProjectDeleted {
        @Nullable
        GitHubProjectEventPayload.ProjectData project();

        EventContext context();
    }

    public record ProjectCreated(GitHubProjectEventPayload.ProjectData project, EventContext context)
        implements ProjectEvent {}

    public record ProjectUpdated(
        GitHubProjectEventPayload.ProjectData project,
        Set<String> changedFields,
        EventContext context
    )
        implements ProjectEvent {}

    public record ProjectClosed(GitHubProjectEventPayload.ProjectData project, EventContext context)
        implements ProjectEvent {}

    public record ProjectReopened(GitHubProjectEventPayload.ProjectData project, EventContext context)
        implements ProjectEvent {}

    /** Deleted event - entity no longer exists, only ID and title available. */
    public record ProjectDeleted(Long projectId, String projectTitle, EventContext context) implements ProjectEvent {
        @Override
        public GitHubProjectEventPayload.ProjectData project() {
            return null;
        }
    }

    public sealed interface ProjectItemEvent
        permits
            ProjectItemCreated,
            ProjectItemUpdated,
            ProjectItemArchived,
            ProjectItemRestored,
            ProjectItemDeleted,
            ProjectItemConverted,
            ProjectItemReordered {
        @Nullable
        GitHubProjectEventPayload.ProjectItemData item();

        Long projectId();

        EventContext context();
    }

    public record ProjectItemCreated(
        GitHubProjectEventPayload.ProjectItemData item,
        Long projectId,
        EventContext context
    )
        implements ProjectItemEvent {}

    public record ProjectItemUpdated(
        GitHubProjectEventPayload.ProjectItemData item,
        Long projectId,
        Set<String> changedFields,
        EventContext context
    )
        implements ProjectItemEvent {}

    public record ProjectItemArchived(
        GitHubProjectEventPayload.ProjectItemData item,
        Long projectId,
        EventContext context
    )
        implements ProjectItemEvent {}

    public record ProjectItemRestored(
        GitHubProjectEventPayload.ProjectItemData item,
        Long projectId,
        EventContext context
    )
        implements ProjectItemEvent {}

    /** Deleted event - entity no longer exists, only ID available. */
    public record ProjectItemDeleted(Long itemId, Long projectId, EventContext context) implements ProjectItemEvent {
        @Override
        public GitHubProjectEventPayload.ProjectItemData item() {
            return null;
        }
    }

    /** Converted event - draft issue converted to a real issue. */
    public record ProjectItemConverted(
        GitHubProjectEventPayload.ProjectItemData item,
        Long projectId,
        EventContext context
    )
        implements ProjectItemEvent {}

    /** Reordered event - item position changed in project view. */
    public record ProjectItemReordered(
        GitHubProjectEventPayload.ProjectItemData item,
        Long projectId,
        EventContext context
    )
        implements ProjectItemEvent {}

    public sealed interface ProjectStatusUpdateEvent
        permits ProjectStatusUpdateCreated, ProjectStatusUpdateUpdated, ProjectStatusUpdateDeleted {
        @Nullable
        GitHubProjectEventPayload.ProjectStatusUpdateData statusUpdate();

        Long projectId();

        EventContext context();
    }

    public record ProjectStatusUpdateCreated(
        GitHubProjectEventPayload.ProjectStatusUpdateData statusUpdate,
        Long projectId,
        EventContext context
    )
        implements ProjectStatusUpdateEvent {}

    public record ProjectStatusUpdateUpdated(
        GitHubProjectEventPayload.ProjectStatusUpdateData statusUpdate,
        Long projectId,
        EventContext context
    )
        implements ProjectStatusUpdateEvent {}

    public record ProjectStatusUpdateDeleted(Long statusUpdateId, Long projectId, EventContext context)
        implements ProjectStatusUpdateEvent {
        @Override
        public GitHubProjectEventPayload.ProjectStatusUpdateData statusUpdate() {
            return null;
        }
    }
}
