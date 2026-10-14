package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;

public record PinnedPractice(
        long practiceId,
        long practiceVersionId,
        PracticeDefinition definition,
        String reviewDigest,
        ReviewMethod method) {}
