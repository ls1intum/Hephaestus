package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * Discriminator for Subjects — the kind of work product Hephaestus reviews or
 * contextualizes. Persisted on {@code agent_job.subject_class} for polymorphic dispatch
 * in the agent layer (no {@code switch (kind)} branching).
 *
 * <p>Only values with a wired ingest path live here. Add a value the same PR that
 * implements its handler — aspirational entries become silent classification bugs.
 */
public enum SubjectClass {
    PULL_REQUEST,
    ISSUE,
    OUTLINE_DOCUMENT,
    SLACK_MESSAGE_THREAD,
}
