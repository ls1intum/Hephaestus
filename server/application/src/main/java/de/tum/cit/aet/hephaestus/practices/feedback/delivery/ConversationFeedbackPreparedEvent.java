package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import org.springframework.modulith.NamedInterface;

@NamedInterface("conversation-nudge")
public record ConversationFeedbackPreparedEvent(long workspaceId, long developerId, int feedbackCount) {}
