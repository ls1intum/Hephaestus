package de.tum.cit.aet.hephaestus.practices.trace.dto;

import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyCheck;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyCheckStatus;
import org.jspecify.annotations.NonNull;

public record DeliveryPolicyTraceCheckDTO(
    @NonNull DeliveryPolicyCheck check,
    @NonNull DeliveryPolicyCheckStatus status
) {}
