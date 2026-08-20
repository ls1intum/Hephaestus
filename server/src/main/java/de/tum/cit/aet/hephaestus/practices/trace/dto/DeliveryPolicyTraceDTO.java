package de.tum.cit.aet.hephaestus.practices.trace.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyFactsSnapshot;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyStage;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record DeliveryPolicyTraceDTO(
    @NonNull UUID reviewId,
    @NonNull Long admittedRevision,
    @Nullable Long evaluatedRevision,
    @NonNull String resolverVersion,
    @NonNull DeliveryPolicySurface surface,
    @NonNull DeliveryPolicyStage stage,
    @NonNull Boolean allowed,
    @Nullable FeedbackSuppressionReason decisiveReason,
    @NonNull List<DeliveryPolicyTraceCheckDTO> checks,
    @NonNull DeliveryPolicyFactsSnapshot facts,
    @NonNull Instant evaluatedAt
) {}
