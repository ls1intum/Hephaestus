package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;

/** Audit snapshot of one practice's loudness tier — how far its review results were allowed to travel. */
public record PracticeUsageSnapshot(PracticeReviewTier reviewTier) implements ConfigAuditSnapshot {}
