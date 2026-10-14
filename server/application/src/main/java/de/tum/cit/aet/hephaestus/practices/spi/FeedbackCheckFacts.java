package de.tum.cit.aet.hephaestus.practices.spi;

public record FeedbackCheckFacts(
        long admittedRolloutRevision,
        long evaluatedRolloutRevision,
        boolean inScope,
        boolean evidenceAllowed,
        boolean modelAllowed,
        boolean deliveryEnabled,
        boolean incorrectBasis,
        boolean currentVersion,
        boolean approvalCurrent,
        boolean writeStarted) {}
