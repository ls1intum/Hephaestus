package de.tum.cit.aet.hephaestus.practices.feedback;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record DeliveryPolicyEvaluationCommand(
    Long workspaceId,
    UUID agentJobId,
    @Nullable UUID feedbackId,
    long admittedRevision,
    @Nullable Long evaluatedRevision,
    DeliveryPolicySurface surface,
    DeliveryPolicyStage stage,
    DeliveryPolicyResolver.Result result,
    DeliveryPolicyFactsSnapshot facts
) {}
