package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalService;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.dto.DecideFeedbackProposalRequestDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.dto.FeedbackApprovalDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.inapp.FeedbackWithdrawalService;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultInvalidationService;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.PracticeReviewObservationRowDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDetailDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackLocationCountsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewLineageDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewResultDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewResultDetailDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewWorkFactsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.UpdateFeedbackWithdrawalRequestDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.UpdatePracticeResultValidityRequestDTO;
import de.tum.cit.aet.hephaestus.practices.trace.dto.DeliveryPolicyTraceDTO;
import de.tum.cit.aet.hephaestus.practices.web.QueryFilterSupport;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@WorkspaceScopedController
@RequestMapping("/practices/reviews")
@Tag(name = "Practice reviews")
@RequiredArgsConstructor
@Validated
@RequireAtLeastWorkspaceAdmin
public class PracticeReviewOutputController {

    private final ReviewResultQueryService practiceResultQueryService;
    private final ReviewFeedbackQueryService feedbackQueryService;
    private final FeedbackApprovalService feedbackApprovalService;
    private final PracticeResultInvalidationService invalidationService;
    private final FeedbackWithdrawalService withdrawalService;
    private final ReviewLineageService lineageService;
    private final ReviewWorkFactsQueryService workFactsQueryService;

    @GetMapping("/results")
    @Operation(
            summary = "List practice review results across the workspace",
            description = "Results include linked feedback outcomes and are ordered newest first by default.",
            operationId = "listPracticeReviewResults")
    @ApiResponse(responseCode = "200", description = "Paginated results returned")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid filter or pagination",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PagedModel<ReviewResultDTO>> listResults(
            WorkspaceContext workspaceContext,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @Parameter(
                            description =
                                    "Sorting strategy. ACTIONABILITY orders NOT_MET by severity (CRITICAL, MAJOR, MINOR, INFO), "
                                            + "then MET, then NOT_APPLICABLE and UNDETERMINED together. Within each rank, "
                                            + "result time and ID are descending.")
                    @RequestParam(defaultValue = "NEWEST")
                    ReviewResultSort sort,
            @Valid @ParameterObject ReviewResultFilterParams filter) {
        return ResponseEntity.ok(new PagedModel<>(practiceResultQueryService.list(
                workspaceContext.id(), filter.toFilter(), sort, PageRequest.of(page, size))));
    }

    @GetMapping("/results/{practiceResultId}")
    @Operation(summary = "Get a result with its evidence and linked feedback", operationId = "getPracticeReviewResult")
    @ApiResponse(
            responseCode = "200",
            description = "Result detail returned",
            content = @Content(schema = @Schema(implementation = ReviewResultDetailDTO.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Result not found in this workspace",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ReviewResultDetailDTO> getPracticeResult(
            WorkspaceContext workspaceContext, @PathVariable UUID practiceResultId) {
        return ResponseEntity.ok(practiceResultQueryService.get(workspaceContext.id(), practiceResultId));
    }

    @PatchMapping("/results/{practiceResultId}/validity")
    @AuditExempt(reason = "The practice_result_invalidation row is the domain audit trail")
    @Operation(
            summary = "Invalidate a result that was wrong when recorded, or restore it",
            description = "Invalidating stops feedback citing the result that has not reached anyone yet. "
                    + "Feedback already delivered keeps its record, and a comment already posted on the "
                    + "provider stays there. Restoring does not re-send anything.",
            operationId = "updatePracticeReviewResultValidity")
    @ApiResponse(
            responseCode = "200",
            description = "Result detail after the change",
            content = @Content(schema = @Schema(implementation = ReviewResultDetailDTO.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Result not found in this workspace",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The result is already in the requested state",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ReviewResultDetailDTO> updateResultValidity(
            WorkspaceContext workspaceContext,
            @PathVariable UUID practiceResultId,
            @Valid @RequestBody UpdatePracticeResultValidityRequestDTO request) {
        long actorAccountId = SecurityUtils.getCurrentAccountId().orElseThrow();
        invalidationService.setValidity(
                workspaceContext.id(), practiceResultId, actorAccountId, request.valid(), request.reason());
        return ResponseEntity.ok(practiceResultQueryService.get(workspaceContext.id(), practiceResultId));
    }

    @GetMapping("/result-observations")
    @Operation(
            summary = "List the observations of practice review results across the workspace",
            description = "Newest result first, then each result's display order. Where the result's detail hides"
                    + " its evidence, the row leaves out its summary and place and sets evidenceHidden.",
            operationId = "listPracticeReviewResultObservations")
    @ApiResponse(responseCode = "200", description = "Paginated observations returned")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid filter or pagination",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PagedModel<PracticeReviewObservationRowDTO>> listObservations(
            WorkspaceContext workspaceContext,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @Valid @ParameterObject ReviewObservationFilterParams filter) {
        return ResponseEntity.ok(new PagedModel<>(practiceResultQueryService.listObservations(
                workspaceContext.id(),
                filter.toResultFilter(),
                filter.toObservationFilter(),
                PageRequest.of(page, size))));
    }

    @GetMapping("/feedback")
    @Operation(
            summary = "List practice review feedback across the workspace",
            description = "Results include every delivery state and are ordered newest first by default.",
            operationId = "listPracticeReviewFeedback")
    @ApiResponse(responseCode = "200", description = "Paginated feedback returned")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid filter or pagination",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PagedModel<ReviewFeedbackDTO>> listFeedback(
            WorkspaceContext workspaceContext,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @Parameter(
                            description = "Sorting strategy. NEWEST and OLDEST order by when the feedback was created; "
                                    + "ties by id.")
                    @RequestParam(defaultValue = "NEWEST")
                    ReviewFeedbackSort sort,
            @Valid @ParameterObject ReviewFeedbackFilterParams filter) {
        return ResponseEntity.ok(new PagedModel<>(
                feedbackQueryService.list(workspaceContext.id(), filter.toFilter(), sort, PageRequest.of(page, size))));
    }

    @GetMapping("/feedback/locations")
    @Operation(
            summary = "Count practice review feedback by location and delivery state",
            description = "Counts the feedback that the list returns under the same filter, by where it appears."
                    + " A location with no feedback is left out.",
            operationId = "countPracticeReviewFeedbackByLocation")
    @ApiResponse(responseCode = "200", description = "Counts by location returned")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid filter",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "403",
            description = "Workspace administrator access is required",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ReviewFeedbackLocationCountsDTO>> countFeedbackByLocation(
            WorkspaceContext workspaceContext, @Valid @ParameterObject ReviewFeedbackFilterParams filter) {
        return ResponseEntity.ok(feedbackQueryService.countByLocation(workspaceContext.id(), filter.toFilter()));
    }

    @GetMapping("/feedback/reasons")
    @Operation(
            summary = "Count withheld practice review feedback by reason",
            description = "Counts the feedback that the list returns under the same filter and that carries a"
                    + " suppression reason, by that reason. Withheld feedback without a reason is one count with no"
                    + " reason. A reason with no feedback is left out. Only counts are returned, never the feedback"
                    + " itself.",
            operationId = "countPracticeReviewFeedbackByReason")
    @ApiResponse(responseCode = "200", description = "Counts by reason returned")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid filter",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "403",
            description = "Workspace administrator access is required",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ReviewFeedbackReasonCountDTO>> countFeedbackByReason(
            WorkspaceContext workspaceContext, @Valid @ParameterObject ReviewFeedbackFilterParams filter) {
        return ResponseEntity.ok(feedbackQueryService.countByReason(workspaceContext.id(), filter.toFilter()));
    }

    @GetMapping("/feedback/{feedbackId}")
    @Operation(
            summary = "Get feedback with its stored body, results and placements",
            operationId = "getPracticeReviewFeedback")
    @ApiResponse(
            responseCode = "200",
            description = "Feedback detail returned",
            content = @Content(schema = @Schema(implementation = ReviewFeedbackDetailDTO.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Feedback not found in this workspace",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ReviewFeedbackDetailDTO> getFeedback(
            WorkspaceContext workspaceContext, @PathVariable UUID feedbackId) {
        return ResponseEntity.ok(feedbackQueryService.get(workspaceContext.id(), feedbackId));
    }

    @PatchMapping("/feedback/{feedbackId}/withdrawal")
    @AuditExempt(reason = "The feedback_withdrawal row is the domain audit trail")
    @Operation(
            summary = "Withdraw a card from a developer's practice page, or restore it",
            description = "Only practice-page feedback that is waiting to be read or already shown can be withdrawn."
                    + " The developer sees that it was withdrawn, not what it said; its record and evidence stay."
                    + " Restoring puts it back through the page's ordinary checks and sends nothing.",
            operationId = "updatePracticeReviewFeedbackWithdrawal")
    @ApiResponse(
            responseCode = "200",
            description = "Feedback detail after the change",
            content = @Content(schema = @Schema(implementation = ReviewFeedbackDetailDTO.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Feedback not found in this workspace",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "The feedback is not on a practice page, or is not waiting to be read or shown there",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ReviewFeedbackDetailDTO> updateFeedbackWithdrawal(
            WorkspaceContext workspaceContext,
            @PathVariable UUID feedbackId,
            @Valid @RequestBody UpdateFeedbackWithdrawalRequestDTO request) {
        long actorAccountId = SecurityUtils.getCurrentAccountId().orElseThrow();
        withdrawalService.setWithdrawn(
                workspaceContext.id(), feedbackId, actorAccountId, request.withdrawn(), request.reason());
        return ResponseEntity.ok(feedbackQueryService.get(workspaceContext.id(), feedbackId));
    }

    @PutMapping("/feedback/{feedbackId}/approval")
    @AuditExempt(reason = "The immutable feedback_approval row is the domain audit trail")
    @Operation(summary = "Approve or reject an immutable feedback proposal", operationId = "decideFeedbackProposal")
    public ResponseEntity<FeedbackApprovalDTO> decideFeedbackProposal(
            WorkspaceContext workspaceContext,
            @PathVariable UUID feedbackId,
            @Valid @RequestBody DecideFeedbackProposalRequestDTO request) {
        long actorAccountId = SecurityUtils.getCurrentAccountId().orElseThrow();
        return ResponseEntity.ok(feedbackQueryService.approval(
                feedbackApprovalService.decide(workspaceContext.id(), feedbackId, actorAccountId, request)));
    }

    @GetMapping("/feedback/{feedbackId}/approval")
    @Operation(summary = "Get the decision for a feedback proposal", operationId = "getFeedbackProposalDecision")
    public ResponseEntity<FeedbackApprovalDTO> getFeedbackProposalDecision(
            WorkspaceContext workspaceContext, @PathVariable UUID feedbackId) {
        return ResponseEntity.ok(
                feedbackQueryService.approval(feedbackApprovalService.get(workspaceContext.id(), feedbackId)));
    }

    @GetMapping("/runs/{agentJobId}/delivery-checks")
    @Operation(
            summary = "List the delivery checks a review ran for itself",
            description = "The checks of the review as a whole, not of one piece of feedback; a piece of feedback"
                    + " lists its own on its detail. Oldest first; empty for a review of another workspace.",
            operationId = "listPracticeReviewDeliveryChecks")
    @ApiResponse(responseCode = "200", description = "Delivery checks returned")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid review id",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "403",
            description = "Workspace administrator access is required",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<DeliveryPolicyTraceDTO>> listReviewDeliveryChecks(
            WorkspaceContext workspaceContext, @PathVariable UUID agentJobId) {
        return ResponseEntity.ok(feedbackQueryService.reviewDeliveryChecks(workspaceContext.id(), agentJobId));
    }

    @GetMapping("/work/{artifactKind}/{artifactId}")
    @Operation(
            summary = "Get what the provider reported about a pull request, merge request or issue",
            description = "Its author, state, branches, times, size and the state of the checks on its head, as the"
                    + " last sync stored them. Only work in a repository this workspace monitors that the provider has"
                    + " not deleted: other work answers 204, because nothing is stored about it here. A conversation"
                    + " or a document has no such facts"
                    + " and answers 404.",
            operationId = "getPracticeReviewWorkFacts")
    @ApiResponse(
            responseCode = "200",
            description = "Facts returned",
            content = @Content(schema = @Schema(implementation = ReviewWorkFactsDTO.class)))
    @ApiResponse(
            responseCode = "204",
            description = "Nothing is stored about this work here: it is in no repository this workspace monitors,"
                    + " or the provider deleted it",
            content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Not a pull request, merge request or issue",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ReviewWorkFactsDTO> getWorkFacts(
            WorkspaceContext workspaceContext,
            @Parameter(description = "Kind of work, e.g. scm.pull_request") @PathVariable String artifactKind,
            @PathVariable @Positive Long artifactId) {
        // A path variable is never null, so the kind is always named here.
        ArtifactKind kind = Objects.requireNonNull(QueryFilterSupport.artifactKind(artifactKind));
        return workFactsQueryService
                .get(workspaceContext.id(), kind, artifactId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/lineage")
    @Operation(
            summary = "Trace how a result, piece of feedback, review or piece of work came about",
            description = "Returns the chain from the reviewed work through the review and its results to the"
                    + " feedback written from them, where it went, whether the developer answered or disputes it"
                    + " (never their rating or private notes) and whether their work resolved it. RESULT,"
                    + " FEEDBACK and REVIEW take `id`; WORK takes `artifactKind` and `artifactId` and traces the"
                    + " newest review of that work. Each list is capped and carries a count of what it leaves out.",
            operationId = "getPracticeReviewLineage")
    @ApiResponse(
            responseCode = "200",
            description = "Lineage returned",
            content = @Content(schema = @Schema(implementation = ReviewLineageDTO.class)))
    @ApiResponse(
            responseCode = "400",
            description = "The parameters do not name a record of the requested kind",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Nothing of that kind and id was recorded in this workspace",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "403",
            description = "Workspace administrator access is required",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ReviewLineageDTO> getLineage(
            WorkspaceContext workspaceContext,
            @RequestParam ReviewLineageKind kind,
            @Parameter(description = "The result, feedback or review id; required unless kind is WORK")
                    @RequestParam(required = false)
                    @Nullable
                    UUID id,
            @Parameter(description = "Kind of reviewed work, e.g. scm.pull_request; required when kind is WORK")
                    @RequestParam(required = false)
                    @Nullable
                    String artifactKind,
            @Parameter(description = "Artifact ID; required when kind is WORK")
                    @RequestParam(required = false)
                    @Positive
                    @Nullable
                    Long artifactId,
            @Parameter(
                            description = "true lists the whole review around the record: every result and piece"
                                    + " of feedback of the review, up to 100 each, and the work's moments; false keeps"
                                    + " the short chain")
                    @RequestParam(defaultValue = "false")
                    boolean whole) {
        Long workspaceId = workspaceContext.id();
        return ResponseEntity.ok(
                switch (kind) {
                    case RESULT -> lineageService.ofResult(workspaceId, recordId(id), whole);
                    case FEEDBACK -> lineageService.ofFeedback(workspaceId, recordId(id), whole);
                    case REVIEW -> lineageService.ofReview(workspaceId, recordId(id), whole);
                    case WORK -> ofWork(workspaceId, artifactKind, artifactId, whole);
                });
    }

    private ReviewLineageDTO ofWork(
            Long workspaceId, @Nullable String artifactKind, @Nullable Long artifactId, boolean whole) {
        ArtifactKind work = QueryFilterSupport.artifactKind(artifactKind);
        if (work == null || artifactId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Name the kind of work and its id to trace a piece of work.");
        }
        return lineageService.ofWork(workspaceId, work, artifactId, whole);
    }

    private static UUID recordId(@Nullable UUID id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name the record to trace with its id.");
        }
        return id;
    }
}
