package de.tum.cit.aet.hephaestus.practices.feedback.decision;

import java.util.UUID;

public record ApprovedFeedbackReadyEvent(long workspaceId, UUID feedbackId) {}
