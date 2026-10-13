package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.handler.PublicReviewCompletionService;
import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.ResultsRefusedException;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.signal.PracticeReviewRefusalMetrics;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
public class ResultAdmissionController {

    private static final Logger log = LoggerFactory.getLogger(ResultAdmissionController.class);

    private final ResultAdmissionService admission;
    private final PracticeReviewRefusalMetrics refusals;
    private final PublicReviewCompletionService completion;
    private final JsonMapper mapper;

    public ResultAdmissionController(
            ResultAdmissionService admission,
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
     * answers 422 with fixed reasons and is final. The workspace, recipients and results come from the attempt's
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
        } catch (ResultAdmissionService.StaleAttemptException e) {
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

    @PostMapping("/admit-results")
    @WorkspaceAgnostic("Authenticated sandbox token carries and constrains workspace route")
    public ObjectNode admit(@RequestBody JsonNode request, Authentication authentication) {
        String malformed = malformedAdmission(request);
        if (malformed != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MALFORMED_ADMISSION + ": " + malformed);
        }
        var identity = attemptOf(authentication);
        UUID jobId = identity.jobId();
        // A refusal is 422, never 5xx: the runner repeats only a 5xx or a transport failure, and repeating a
        // decided submission would put the same question again. The reason is already recorded on the job.
        try {
            return admission.admit(identity, request);
        } catch (ResultsRefusedException e) {
            refusals.recordExecutionRefusal(e.reasonCode());
            log.info("Refused this review's results ({}): jobId={}, {}", e.reasonCode(), jobId, e.reason());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.reason(), e);
        } catch (JobDeliveryException e) {
            refusals.recordExecutionRefusal(ResultAdmissionService.INADMISSIBLE_REASON_CODE);
            log.info("Refused this review's results as inadmissible: jobId={}, {}", jobId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), e);
        } catch (ResultAdmissionService.StaleAttemptException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review attempt no longer owns this job", e);
        } catch (ResultAdmissionService.AdmissionConflictException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Results differ from the admitted payload", e);
        } catch (IllegalStateException e) {
            // The evidence store or a verifier failed, not the submission: a 5xx the runner repeats. Mapped
            // here because the shared advice would answer 409, which the runner treats as final.
            log.error("Result admission could not be completed: jobId={}", jobId, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Result admission could not be completed", e);
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
        } catch (ResultAdmissionService.PublicHistoryRefusedException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), e);
        } catch (ResultAdmissionService.StaleAttemptException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review attempt no longer owns this job", e);
        }
    }

    /** The code of a body that is not an admission at all; the runner sends exactly this envelope. */
    static final String MALFORMED_ADMISSION = "malformed_admission";

    private static final Set<String> ENTRY_KEYS = Set.of("practiceSlug", "candidate");

    /**
     * Why the body is not {@code {schemaVersion: 2, results: [{practiceSlug, candidate}, …]}}, or null when it is one.
     * The results are never empty and are sorted by practice; a body of another version comes from another runner and
     * fails explicitly. A repeated practice is the submission's own refusal ({@code ambiguous_practice_results}).
     */
    static @Nullable String malformedAdmission(JsonNode request) {
        if (!request.isObject()) return "the body is not an object";
        for (String name : request.propertyNames()) {
            if (!name.equals("schemaVersion") && !name.equals("results")) return "unknown key " + name;
        }
        JsonNode version = request.path("schemaVersion");
        if (!version.isIntegralNumber() || version.asInt() != ResultAdmissionService.SCHEMA_VERSION) {
            return "expected schemaVersion " + ResultAdmissionService.SCHEMA_VERSION;
        }
        JsonNode results = request.path("results");
        if (!results.isArray() || results.isEmpty()) return "expected a non-empty results array";
        String previous = null;
        for (JsonNode entry : results) {
            if (!entry.isObject() || !ENTRY_KEYS.containsAll(entry.propertyNames()) || !entry.has("candidate")) {
                return "each result is {practiceSlug, candidate}";
            }
            JsonNode slug = entry.path("practiceSlug");
            if (!slug.isString()
                    || !SandboxLayout.PRACTICE_SLUG.matcher(slug.asString()).matches()) {
                return "each result names its practice by slug";
            }
            if (previous != null && previous.compareTo(slug.asString()) > 0) {
                return "the results are sorted by practice";
            }
            previous = slug.asString();
        }
        return null;
    }

    private static ResultAdmissionService.AdmissionIdentity attemptOf(Authentication authentication) {
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
        return new ResultAdmissionService.AdmissionIdentity(
                jobId,
                routing.workspaceId(),
                routing.attempt().number(),
                routing.attempt().workerId());
    }
}
