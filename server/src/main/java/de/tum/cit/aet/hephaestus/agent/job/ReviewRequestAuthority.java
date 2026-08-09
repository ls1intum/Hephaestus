package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
 * <h2>Identity: one person, several SCM rows</h2>
 * <p>An SCM {@link User} row is one vendor identity, and a Hephaestus account may link several of them
 * (ADR 0017). So the question is asked of a <em>set</em> of identities and answered yes if any one of
 * them has standing — otherwise a workspace admin who is an admin under their GitLab login but signed
 * in through GitHub is refused for being two people.
 *
 * <p>Which door was used decides how much of that set is known, and the difference is deliberate. A
 * request authenticated as a Hephaestus account arrives with every linked identity, resolved by
 * {@code CurrentAccountUsers}. A merge-request comment arrives with exactly one — the identity that
 * wrote it — because a comment carries no account, and inferring one from a login would let a matching
 * login under another provider vote on this workspace's spending. The bot path therefore fails closed
 * for a multi-identity admin, and that person can still ask through the front door they are signed in
 * on.
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
        return standingOf(workspaceId, artifact, requester == null ? List.of() : List.of(requester)).isPresent();
    }

    /**
     * Which of these identities gives this person standing to ask, if any does.
     *
     * <p>Returns the identity rather than a boolean because the answer is needed twice: once to allow
     * the request and once to attribute it, and the ledger row must name a person the rule actually
     * accepted. Deriving the attribution separately is how a row comes to name someone who was refused.
     *
     * <p>The order of {@code candidates} decides which identity a request is filed under when more than
     * one qualifies. Callers pass the account's links in their own order and nothing downstream depends
     * on the choice — the hourly allowance counts every identity in the set together.
     */
    public Optional<User> standingOf(long workspaceId, Issue artifact, Collection<User> candidates) {
        return candidates
            .stream()
            .filter(candidate -> candidate != null && candidate.getId() != null)
            .filter(
                candidate -> isActorOn(artifact, candidate.getId()) || isWorkspaceAdmin(workspaceId, candidate.getId())
            )
            .findFirst();
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
