package de.tum.cit.aet.hephaestus.practices.feedback;

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
    public void record(DeliveryPolicyEvaluationCommand command) {
        repository.save(DeliveryPolicyEvaluation.builder()
                .workspaceId(command.workspaceId())
                .agentJobId(command.agentJobId())
                .feedbackId(command.feedbackId())
                .admittedRevision(command.admittedRevision())
                .evaluatedRevision(command.evaluatedRevision())
                .resolverVersion(DeliveryPolicyResolver.VERSION)
                .surface(command.surface())
                .stage(command.stage())
                .allowed(command.result().allowed())
                .decisiveReason(command.result().suppressionReason())
                .checks(objectMapper.valueToTree(command.result().checks()))
                .facts(objectMapper.valueToTree(command.facts()))
                .build());
    }
}
