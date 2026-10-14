package de.tum.cit.aet.hephaestus.practices.spi;

public record ConversationFeedbackPreparedEvent(long workspaceId, long developerId, int feedbackCount) {}
