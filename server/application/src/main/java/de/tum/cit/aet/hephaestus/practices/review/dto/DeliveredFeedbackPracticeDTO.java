package de.tum.cit.aet.hephaestus.practices.review.dto;

import org.jspecify.annotations.NonNull;

public record DeliveredFeedbackPracticeDTO(
        @NonNull String slug, @NonNull String name) {}
