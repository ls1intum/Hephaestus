package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.handler.ObservationAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.PublicReviewCompletionService;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ObservationsRefusedException;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.signal.PracticeReviewRefusalMetrics;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/internal/llm")
@Hidden
@PreAuthorize("isAuthenticated()")
public class ObservationAdmissionController {

    private static final Logger log = LoggerFactory.getLogger(ObservationAdmissionController.class);

    private final ObservationAdmissionService admission;
    private final PracticeReviewRefusalMetrics refusals;
    private final PublicReviewCompletionService completion;
    private final JsonMapper mapper;

    public ObservationAdmissionController(
            ObservationAdmissionService admission,
            PracticeReviewRefusalMetrics refusals,
            PublicReviewCompletionService completion,
            JsonMapper mapper) {
        this.admission = admission;
        this.refusals = refusals;
        this.completion = completion;
        this.mapper = mapper;
    }

    /**
     * The whole review on the work of the attempt that owns the job, submitted once its public writer finished. An
     * acceptance answers 200 with the server's review digest, repeated identically for an identical retry; a refusal
     * answers 422 with fixed reasons and is final. The workspace, recipients and observations come from the attempt's
     * credential and the job's own records, never from the body.
     */
    @PostMapping("/complete-public-review")
    @WorkspaceAgnostic("Authenticated sandbox token carries and constrains workspace route")
    public ResponseEntity<ObjectNode> completePublicReview(
            @RequestBody JsonNode request, Authentication authentication) {
        JsonNode digest = request.path("admissionDigest");
        if (request.path("schemaVersion").asInt(-1) != 1
                || !digest.isString()
                || !request.path("review").isObject()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Expected schemaVersion 1, an admissionDigest and a review object");
        }
        var identity = attemptOf(authentication);
        PublicReviewCompletionService.Outcome outcome;
        try {
            outcome = completion.complete(identity, digest.asString(), request.get("review"));
        } catch (ObservationAdmissionService.StaleAttemptException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review attempt no longer owns this job", e);
        } catch (IllegalStateException e) {
            // A fence or store failed, not the submission: a 5xx the runner repeats with the same review.
            log.error("Public review completion could not be recorded: jobId={}", identity.jobId(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Public review completion could not be recorded", e);
        }
        ObjectNode answer = mapper.createObjectNode();
        answer.put("schemaVersion", 1);
        answer.put("accepted", outcome.accepted());
        if (outcome.accepted()) {
            answer.put("reviewDigest", outcome.reviewDigest());
            return ResponseEntity.ok(answer);
        }
        outcome.reasons().forEach(answer.putArray("reasons")::add);
        log.info("Refused this review's review on the work: jobId={}, reasons={}", identity.jobId(), outcome.reasons());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(answer);
    }

    @PostMapping("/admit-observations")
    @WorkspaceAgnostic("Authenticated sandbox token carries and constrains workspace route")
    public ObjectNode admit(@RequestBody JsonNode request, Authentication authentication) {
        if (request.path("schemaVersion").asInt(-1) != 1
                || !request.path("observations").isArray()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Expected schemaVersion 1 and observations array");
        }
        var identity = attemptOf(authentication);
        UUID jobId = identity.jobId();
        // A refusal is 422, never 5xx: the runner repeats only a 5xx or a transport failure, and repeating a
        // decided submission would put the same question again. The reason is already recorded on the job.
        try {
            return admission.admit(identity, request.path("observations"));
        } catch (ObservationsRefusedException e) {
            refusals.recordExecutionRefusal(e.reasonCode());
            log.info("Refused this review's observations ({}): jobId={}, {}", e.reasonCode(), jobId, e.reason());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.reason(), e);
        } catch (JobDeliveryException e) {
            refusals.recordExecutionRefusal(ObservationAdmissionService.INADMISSIBLE_REASON_CODE);
            log.info("Refused this review's observations as inadmissible: jobId={}, {}", jobId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), e);
        } catch (ObservationAdmissionService.StaleAttemptException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review attempt no longer owns this job", e);
        } catch (ObservationAdmissionService.AdmissionConflictException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Observations differ from the admitted payload", e);
        } catch (IllegalStateException e) {
            // The evidence store or a verifier failed, not the submission: a 5xx the runner repeats. Mapped
            // here because the shared advice would answer 409, which the runner treats as final.
            log.error("Observation admission could not be completed: jobId={}", jobId, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Observation admission could not be completed", e);
        }
    }

    @PostMapping("/public-feedback-history")
    @WorkspaceAgnostic("Authenticated sandbox token carries and constrains workspace route")
    public ObjectNode publicFeedbackHistory(Authentication authentication) {
        var identity = attemptOf(authentication);
        try {
            return admission.publicFeedbackHistory(identity);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Public feedback history could not be read", e);
        } catch (ObservationAdmissionService.PublicHistoryRefusedException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), e);
        } catch (ObservationAdmissionService.StaleAttemptException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review attempt no longer owns this job", e);
        }
    }

    private static ObservationAdmissionService.AdmissionIdentity attemptOf(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof ProxyRouting routing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent-job credential required");
        }
        UUID jobId = routing.sourceId();
        if (jobId == null
                || routing.attempt() == null
                || routing.attempt().sourceType() != LlmUsageSourceType.AGENT_JOB
                || routing.workspaceId() == null
                || routing.attempt().workerId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent-job credential required");
        }
        return new ObservationAdmissionService.AdmissionIdentity(
                jobId,
                routing.workspaceId(),
                routing.attempt().number(),
                routing.attempt().workerId());
    }
}
