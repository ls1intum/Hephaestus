package de.tum.cit.aet.hephaestus.integration.slack.domain;

/**
 * How a {@link MentorTurnRating} was captured. Only {@link #BUTTON} exists today (the feedback buttons on a mentor
 * reply); the enum is kept explicit so a future capture path (e.g. a slash command or a scheduled prompt) is a
 * value add rather than a schema change.
 */
public enum RatingSource {
    BUTTON,
}
