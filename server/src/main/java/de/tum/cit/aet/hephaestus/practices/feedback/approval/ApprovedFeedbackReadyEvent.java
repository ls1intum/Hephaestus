package de.tum.cit.aet.hephaestus.practices.feedback.approval;

import java.util.UUID;

public record ApprovedFeedbackReadyEvent(Long workspaceId, UUID feedbackId) {}
