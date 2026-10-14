package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.core.web.PageResponseDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionService;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.dto.CreateFeedbackDecisionRequestDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.dto.FeedbackDecisionDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackCheckDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewFeedbackDetailDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewFeedbackSort;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@WorkspaceScopedController
@ConditionalOnServerRole
@RequireAtLeastWorkspaceAdmin
@RequestMapping("/practices/reviews")
@RequiredArgsConstructor
@Validated
public class FeedbackReviewController {
    private final FeedbackReadService reader;
    private final FeedbackDecisionService decisions;
    private final FeedbackStoreService store;

    @GetMapping("/feedback")
    @Operation(operationId = "listReviewFeedback")
    public PageResponseDTO<ReviewFeedbackDTO> list(
            WorkspaceContext workspace,
            @Valid @ParameterObject @ModelAttribute FeedbackQueryFilter filter,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponseDTO.from(reader.list(
                workspace.id(),
                filter,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                filter.sort() == ReviewFeedbackSort.OLDEST ? Sort.Direction.ASC : Sort.Direction.DESC,
                                "createdAt",
                                "id"))));
    }

    @GetMapping("/feedback/locations")
    @Operation(operationId = "countReviewFeedbackByLocation")
    public List<ReasonCountDTO> locations(
            WorkspaceContext workspace, @Valid @ParameterObject @ModelAttribute FeedbackQueryFilter filter) {
        return reader.locations(workspace.id(), filter);
    }

    @GetMapping("/feedback/reasons")
    @Operation(operationId = "countReviewFeedbackByReason")
    public List<ReasonCountDTO> reasons(
            WorkspaceContext workspace, @Valid @ParameterObject @ModelAttribute FeedbackQueryFilter filter) {
        return reader.reasons(workspace.id(), filter);
    }

    @GetMapping("/feedback/{feedbackId}")
    @Operation(operationId = "getReviewFeedback")
    public ReviewFeedbackDetailDTO detail(WorkspaceContext workspace, @PathVariable UUID feedbackId) {
        return reader.detail(workspace.id(), feedbackId);
    }

    @GetMapping("/feedback/{feedbackId}/decisions")
    @Operation(operationId = "listFeedbackDecisions")
    public List<FeedbackDecisionDTO> decisions(WorkspaceContext workspace, @PathVariable UUID feedbackId) {
        return reader.decisionHistory(workspace.id(), feedbackId);
    }

    @AuditExempt(reason = "Feedback decisions retain the actor, note and timestamp")
    @PostMapping("/feedback/{feedbackId}/decisions")
    @Operation(operationId = "createFeedbackDecision")
    public ResponseEntity<FeedbackDecisionDTO> decide(
            WorkspaceContext workspace,
            @PathVariable UUID feedbackId,
            @Valid @RequestBody CreateFeedbackDecisionRequestDTO request) {
        var decision = decisions.decide(
                workspace.id(), feedbackId, SecurityUtils.getCurrentAccountId().orElseThrow(), request);
        var saved = reader.decisionHistory(workspace.id(), feedbackId).stream()
                .filter(row -> row.id().equals(decision.getId()))
                .findFirst()
                .orElseThrow();
        return ResponseEntity.status(201).body(saved);
    }

    @AuditExempt(reason = "Retry retains the physical write fences and send attempt history")
    @PostMapping("/feedback/{feedbackId}/retry")
    @Operation(operationId = "retryFeedback")
    public ResponseEntity<ReviewFeedbackDetailDTO> retry(WorkspaceContext workspace, @PathVariable UUID feedbackId) {
        store.retry(
                workspace.id(), feedbackId, SecurityUtils.getCurrentAccountId().orElseThrow());
        return ResponseEntity.accepted().body(reader.detail(workspace.id(), feedbackId));
    }

    @GetMapping("/{reviewId}/feedback-checks")
    @Operation(operationId = "listReviewFeedbackChecks")
    public List<FeedbackCheckDTO> checks(WorkspaceContext workspace, @PathVariable UUID reviewId) {
        return reader.reviewChecks(workspace.id(), reviewId);
    }
}
