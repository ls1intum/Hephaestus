package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * Root feedback SPI — every kind that declares {@link Capability#FEEDBACK_DELIVERY}
 * implements this. {@link InlineFindingChannel} and {@link ApprovalChannel} are
 * separate capability-gated SPIs (split per agent B3 + D27).
 *
 * <p>The agent layer asks the registry for the right SPI by kind; missing wiring is
 * a compile-/wiring-time error, not a runtime branch.
 */
public interface FeedbackChannel {

    IntegrationKind kind();

    SummaryHandle postSummary(FeedbackTarget target, FeedbackContent content);

    /** Hephaestus's typed reference to the subject the feedback attaches to. */
    record FeedbackTarget(
        IntegrationRef ref,
        String subjectExternalId,
        String resourceUrl
    ) {
    }

    record FeedbackContent(String body, String marker) {
    }

    /** Vendor-side post identifier used by {@code FeedbackPostService} for edit-in-place. */
    record SummaryHandle(String externalId) {
    }
}
