package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipRepository;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Who may ask for a review of a given artifact, right now.
 *
 * <p>A review is not a read. It spends the workspace's LLM budget, and — the part that makes this an
 * authorization question rather than a rate-limiting one — its feedback is delivered to the artifact's
 * <em>author</em>, not to whoever asked. So an unchecked request path lets anyone who can reach the
 * artifact aim coaching at a colleague, repeatedly, and the colleague sees only that Hephaestus has
 * opinions about their work today.
 *
 * <p>The rule is therefore about standing on the artifact, not about reaching it: the requester must be
 * someone the review is <em>about</em> — its author or one of its assignees — or a workspace admin, who
 * already decides what this workspace reviews and pays for it. Everyone else is refused, including
 * ordinary workspace members: membership says you may see the work, not that you may commission
 * coaching about it for somebody else.
 *
 * <p>Shared by every front door onto a hand-requested review — the SCM bot command and the REST
 * endpoint — because a second copy of this rule is a second thing to keep true, and the copy that is
 * forgotten is the hole.
 *
 * <h2>Identity, and where this deliberately fails closed</h2>
 * <p>The requester is an SCM {@link User} row: one vendor identity, not the Hephaestus account behind
 * it. A person who is a workspace admin under a <em>different</em> linked identity than the one they
 * commented with is refused. That is the safe direction of the error, and the requester can still ask
 * through the front door they are authenticated on.
 */
@Component
public class ReviewRequestAuthority {

    private static final Set<WorkspaceRole> ADMIN_ROLES = Set.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN);

    private final WorkspaceMembershipRepository memberships;

    public ReviewRequestAuthority(WorkspaceMembershipRepository memberships) {
        this.memberships = memberships;
    }

    /**
     * Whether this person may occasion a review of this artifact in this workspace.
     *
     * @param artifact the artifact, with its author and assignees already fetched — a lazy collection
     *     read here would decide the question on whatever the session happened to have loaded
     * @param requester the SCM identity that asked, or {@code null} when the request could not be
     *     attributed to one at all, which is itself a refusal: an unattributable ask cannot be shown to
     *     be an authorized one
     */
    public boolean mayRequest(long workspaceId, Issue artifact, @Nullable User requester) {
        if (requester == null || requester.getId() == null) {
            return false;
        }
        return isActorOn(artifact, requester.getId()) || isWorkspaceAdmin(workspaceId, requester.getId());
    }

    /** The people a review of this artifact is about: whoever wrote it and whoever it is assigned to. */
    private boolean isActorOn(Issue artifact, Long requesterId) {
        User author = artifact.getAuthor();
        if (author != null && requesterId.equals(author.getId())) {
            return true;
        }
        Set<User> assignees = artifact.getAssignees();
        return assignees != null && assignees.stream().anyMatch(a -> requesterId.equals(a.getId()));
    }

    private boolean isWorkspaceAdmin(long workspaceId, Long requesterId) {
        return memberships
            .findByWorkspace_IdAndUser_Id(workspaceId, requesterId)
            .map(membership -> ADMIN_ROLES.contains(membership.getRole()))
            .orElse(false);
    }
}
