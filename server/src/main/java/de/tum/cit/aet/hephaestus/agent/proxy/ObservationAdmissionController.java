package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.handler.ObservationAdmissionService;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/internal/llm")
@Hidden
@PreAuthorize("isAuthenticated()")
public class ObservationAdmissionController {

    private final ObservationAdmissionService admission;

    public ObservationAdmissionController(ObservationAdmissionService admission) {
        this.admission = admission;
    }

    @PostMapping("/admit-observations")
    @WorkspaceAgnostic("Authenticated sandbox token carries and constrains workspace route")
    public ObjectNode admit(@RequestBody JsonNode request, Authentication authentication) {
        if (request.path("schemaVersion").asInt(-1) != 1 || !request.path("observations").isArray()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Expected schemaVersion 1 and observations array"
            );
        }
        if (!(authentication.getPrincipal() instanceof ProxyRouting routing)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent-job credential required");
        }
        UUID jobId = routing.sourceId();
        if (
            jobId == null || routing.attempt() == null || routing.attempt().sourceType() != LlmUsageSourceType.AGENT_JOB
        ) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent-job credential required");
        }
        try {
            return admission.admit(jobId, request.path("observations"));
        } catch (ObservationAdmissionService.AdmissionConflictException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Observations differ from the admitted payload", e);
        }
    }
}
