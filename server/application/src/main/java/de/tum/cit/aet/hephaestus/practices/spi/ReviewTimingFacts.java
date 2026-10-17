package de.tum.cit.aet.hephaestus.practices.spi;

public record ReviewTimingFacts(
        double asked,
        double finishedAsked,
        long finishedAfterMerge,
        long finishedAfterMergeStartedByMerge,
        long feedbackWithheldAsMerged) {}
