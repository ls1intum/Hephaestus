package de.tum.cit.aet.hephaestus.practices.feedback;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DeliveryPolicyEvaluationRecorder {

    private final DeliveryPolicyEvaluationRepository repository;
    private final ObjectMapper objectMapper;

    public DeliveryPolicyEvaluationRecorder(DeliveryPolicyEvaluationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
        Long workspaceId,
        UUID agentJobId,
        @Nullable UUID feedbackId,
        long admittedRevision,
        @Nullable Long evaluatedRevision,
        DeliveryPolicySurface surface,
        DeliveryPolicyStage stage,
        DeliveryPolicyResolver.Result result,
        DeliveryPolicyFactsSnapshot facts
    ) {
        repository.save(
            DeliveryPolicyEvaluation.builder()
                .workspaceId(workspaceId)
                .agentJobId(agentJobId)
                .feedbackId(feedbackId)
                .admittedRevision(admittedRevision)
                .evaluatedRevision(evaluatedRevision)
                .resolverVersion(DeliveryPolicyResolver.VERSION)
                .surface(surface)
                .stage(stage)
                .allowed(result.allowed())
                .decisiveReason(result.suppressionReason())
                .checks(objectMapper.valueToTree(result.checks()))
                .facts(objectMapper.valueToTree(facts))
                .build()
        );
    }
}
