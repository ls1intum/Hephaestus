package de.tum.cit.aet.hephaestus.practices.review.dto;

import org.jspecify.annotations.NonNull;

/** The practice's authoritative display name and stable workspace slug. */
public record DeliveredFeedbackPracticeDTO(
        @NonNull String slug, @NonNull String name) {}
