package de.tum.cit.aet.hephaestus.integration.core.spi;

public interface SummaryWriter {
    IntegrationKind kind();

    SummaryHandle postSummary(FeedbackTarget target, SummaryContent content);

    SummaryLookup findExistingSummary(FeedbackTarget target, SummaryContent expected);

    CopyUpdateReceipt updateSummary(FeedbackTarget target, SummaryHandle existing, SummaryContent content);
}
