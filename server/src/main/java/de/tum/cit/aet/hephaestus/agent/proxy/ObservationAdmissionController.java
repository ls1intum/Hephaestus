package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.handler.ObservationAdmissionService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Authenticated mid-run trust-boundary callback used by the practice runner. */
@RestController
@RequestMapping("/internal/llm")
public class ObservationAdmissionController {

    private final ObservationAdmissionService admission;

    public ObservationAdmissionController(ObservationAdmissionService admission) {
        this.admission = admission;
    }

    @PostMapping("/admit-observations")
    public ObjectNode admit(@RequestBody JsonNode request, Authentication authentication) {
        if (request.path("schemaVersion").asInt(-1) != 1 || !request.path("observations").isArray()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Expected schemaVersion 1 and observations array"
            );
        }
        ProxyRouting routing = (ProxyRouting) authentication.getPrincipal();
        UUID jobId = routing.sourceId();
        if (
            jobId == null ||
            routing.attempt() == null ||
            routing.attempt().sourceType() != de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType.AGENT_JOB
        ) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent-job credential required");
        }
        try {
            return admission.admit(jobId, request.path("observations"));
        } catch (ObservationAdmissionService.AdmissionConflictException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Observations differ from the admitted payload");
        }
    }
}
