package de.tum.cit.aet.hephaestus.agent.job;

/** Aggregate delivery outcome for a job, including lanes without dispatch rows. */
public enum DeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED,
}
