package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionRequestDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.AdmissionResponseDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.PrepareFeedbackRequestDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.PrepareFeedbackResponseDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.PublicFeedbackHistoryDTO;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnWorkerRole
@RequestMapping("/internal/llm")
@Hidden
@PreAuthorize("isAuthenticated()")
public class ResultAdmissionController {
    private final ResultAdmissionService admission;

    public ResultAdmissionController(ResultAdmissionService admission) {
        this.admission = admission;
    }

    @PostMapping("/admit-results")
    @WorkspaceAgnostic("The worker credential supplies the workspace and exact job attempt")
    public AdmissionResponseDTO admitResults(
            @Valid @RequestBody AdmissionRequestDTO request, Authentication authentication) {
        var identity = attemptOf(authentication);
        return checked(() -> admission.admit(identity, request));
    }

    @PostMapping("/prepare-feedback")
    @WorkspaceAgnostic("The worker credential supplies the workspace and exact job attempt")
    public PrepareFeedbackResponseDTO prepareFeedback(
            @Valid @RequestBody PrepareFeedbackRequestDTO request, Authentication authentication) {
        var identity = attemptOf(authentication);
        return checked(() -> admission.prepareFeedback(identity, request));
    }

    @PostMapping("/public-feedback-history")
    @WorkspaceAgnostic("The worker credential supplies the workspace and exact job attempt")
    public PublicFeedbackHistoryDTO readPublicFeedbackHistory(Authentication authentication) {
        var identity = attemptOf(authentication);
        return checked(() -> admission.publicFeedbackHistory(identity));
    }

    private static <T> T checked(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ResultAdmissionService.StaleAttemptException
                | ResultAdmissionService.AdmissionConflictException conflict) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "The review attempt or admitted payload changed", conflict);
        } catch (JobDeliveryException | ResultAdmissionService.PublicHistoryRefusedException refused) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, refused.getMessage(), refused);
        }
    }

    private static ResultAdmissionService.AdmissionIdentity attemptOf(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof ProxyRouting routing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Review job credential required");
        }
        var attempt = routing.attempt();
        var workspaceId = routing.workspaceId();
        if (attempt == null || attempt.sourceType() != UsageSourceType.REVIEW_JOB || workspaceId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Review job credential required");
        }
        String workerId = attempt.workerId();
        if (workerId == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Review worker credential required");
        return new ResultAdmissionService.AdmissionIdentity(
                attempt.sourceId(), workspaceId, attempt.number(), workerId);
    }
}
