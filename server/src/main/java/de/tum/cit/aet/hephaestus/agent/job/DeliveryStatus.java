package de.tum.cit.aet.hephaestus.agent.job;

/**
 * The roll-up of one job's delivery phase, not a machine of its own: PENDING while the phase has not
 * concluded, DELIVERED once every egress intent reached a non-failed terminal, FAILED when the phase
 * concluded with an intent lost. It spans job types that have no dispatch rows at all, which is why it
 * cannot simply be read off {@code feedback_dispatch}.
 */
public enum DeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED,
}
