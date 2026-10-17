package de.tum.cit.aet.hephaestus.integration.core.spi;

public interface ApprovalWriter {
    IntegrationKind kind();

    void approve(FeedbackTarget target, String message);
}
