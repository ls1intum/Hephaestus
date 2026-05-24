package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * Common shape for the work products Hephaestus reviews.
 *
 * <p>Not a sealed interface — different families' Subject types implement this
 * via their family-lib (e.g. {@code scm-lib/spi/ScmSubject}). The agent layer
 * dispatches on {@link #subjectClass()} via the discriminator persisted on
 * {@code agent_job.subject_class}.
 */
public interface IntegrationSubject {

    long workspaceId();

    IntegrationKind kind();

    SubjectClass subjectClass();

    /** Vendor-side identifier (PR number/id, message ts, document UUID). */
    String externalId();

    /** Canonical URL for human readers and `resource_url` in CloudEvents `source`. */
    String url();
}
