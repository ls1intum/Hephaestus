package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.CurrentAccountUsers;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The front door a developer uses to ask for a review of a piece of work, now.
 *
 * <p>Thin by design: {@link ManualReviewRequests} owns the whole sequence — standing, rate limits,
 * ledger, gate, submission — because the merge-request bot command has to perform the identical one.
 *
 * <h2>Why a refusal is a 200</h2>
 * <p>Almost every reason a requested review does not run is a condition of the workspace the asker can
 * neither see nor fix: an exhausted budget, a practice at Off, a cooldown. Answering those with a 4xx
 * would tell the person the button is broken, when in fact the ask was understood and something
 * nameable stopped it. The body carries the reason and the sentence explaining it, and the web app
 * prints that sentence verbatim. The exceptions are the two things that really are wrong with the
 * request: an artifact this workspace does not monitor (404) and a caller with no standing on it (403).
 *
 * <h2>Membership is checked, and is not sufficient</h2>
 * <p>{@code isMember()} is here because {@code WorkspaceContextFilter} admits an anonymous caller on a
 * publicly viewable workspace, and a spend button must not be one of the things public visibility
 * grants. It is only the outer fence; {@link ReviewRequestAuthority} owns the rule that decides standing.
 */
@WorkspaceScopedController
@RequestMapping("/practices/review-requests")
@Tag(name = "Practice review requests", description = "Asking for a review of a piece of work by hand")
@RequiredArgsConstructor
@Validated
public class PracticeReviewRequestController {

    private final ManualReviewRequests manualReviewRequests;
    private final ReviewableArtifactLoader artifactLoader;
    private final CurrentAccountUsers currentAccountUsers;
    private final WorkspaceContextResolver workspaceContextResolver;

    @PostMapping
    @PreAuthorize("@workspaceSecure.isMember()")
    @Operation(
        summary = "Ask for a review of a piece of work now",
        description = "Answers 200 both when a review starts and when one deliberately does not; the " +
            "body says which, and why. 403 only when the caller has no standing on the artifact.",
        operationId = "requestPracticeReview"
    )
    @ApiResponse(
        responseCode = "200",
        description = "The ask was understood: a review is running, or the body names what stopped it",
        content = @Content(schema = @Schema(implementation = ReviewRequestOutcomeDTO.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "The artifact kind is not one that can be asked for",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @ApiResponse(
        responseCode = "403",
        description = "The caller is neither the work's author or assignee nor a workspace admin",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @ApiResponse(
        responseCode = "404",
        description = "No such artifact in this workspace",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    public ResponseEntity<ReviewRequestOutcomeDTO> requestReview(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody CreateReviewRequestDTO request
    ) {
        Workspace workspace = workspaceContextResolver.requireWorkspace(workspaceContext);
        // Every identity of the account, not the one this session signed in with: workspace membership
        // is keyed on the SCM user, so a person who is an admin under their GitLab login and is here on
        // their GitHub one would otherwise be refused for being two people.
        List<User> requesters = currentAccountUsers.resolve();
        ManualReviewOutcome outcome = requestFor(workspace, request, requesters);
        if (outcome.status() == ManualReviewOutcome.Status.FORBIDDEN) {
            // Deliberately the same sentence whether the artifact has no such requester or the requester
            // has no such standing: distinguishing them would let a caller enumerate who is on a team.
            throw new AccessForbiddenException(
                "Only the work's author or assignees, or a workspace admin, can ask for a review of it."
            );
        }
        return ResponseEntity.ok(ReviewRequestOutcomeDTO.from(outcome));
    }

    /**
     * Load the artifact through this workspace and hand it to the one manual-request path.
     *
     * <p>The kind decides which loader answers, and the loaders join the artifact to the workspace
     * rather than trusting the id: nothing in an artifact id relates it to a workspace, so loading by id
     * alone would let one workspace spend its budget reviewing another's work — see
     * {@link ReviewableArtifactLoader}.
     */
    private ManualReviewOutcome requestFor(Workspace workspace, CreateReviewRequestDTO request, List<User> requesters) {
        ArtifactKind kind = parseKind(request.artifactKind());
        if (ScmSignals.PULL_REQUEST.equals(kind)) {
            PullRequest pullRequest = artifactLoader
                .findPullRequestForGate(workspace.getId(), request.artifactId())
                .orElseThrow(() -> notFound(request));
            return manualReviewRequests.requestPullRequestReview(workspace, pullRequest, requesters);
        }
        if (ScmSignals.ISSUE.equals(kind)) {
            Issue issue = artifactLoader
                .findIssueForGate(workspace.getId(), request.artifactId())
                .orElseThrow(() -> notFound(request));
            return manualReviewRequests.requestIssueReview(workspace, issue, requesters);
        }
        // A kind that exists but has no front door here — a chat thread or a document is reviewed on the
        // occasion its source produces, and there is nothing for a person to point at and ask about.
        throw new IllegalArgumentException("Reviews cannot be asked for on artifacts of kind " + kind.value());
    }

    /** A malformed kind and an unknown one are the same answer: this endpoint cannot act on it. */
    private static ArtifactKind parseKind(String raw) {
        try {
            return ArtifactKind.of(raw);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Not an artifact kind: " + raw);
        }
    }

    /**
     * One answer for "no such artifact" and "not this workspace's artifact". Telling them apart would
     * turn this endpoint into a way to probe which work another workspace monitors.
     */
    private static EntityNotFoundException notFound(CreateReviewRequestDTO request) {
        return new EntityNotFoundException(request.artifactKind(), request.artifactId());
    }
}
