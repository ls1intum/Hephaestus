package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * Capability-gated SPI for posting an approval verdict (SCM "approve PR/MR",
 * project-tracker "transition to Done"). Kinds that don't declare
 * {@link Capability#APPROVAL_WORKFLOW} never resolve via this registry.
 */
public interface ApprovalChannel {

    IntegrationKind kind();

    void approve(FeedbackChannel.FeedbackTarget target, String message);
}
