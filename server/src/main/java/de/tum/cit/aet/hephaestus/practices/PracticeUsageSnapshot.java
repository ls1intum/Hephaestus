package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;

public record PracticeUsageSnapshot(boolean usedInNewReviews) implements ConfigAuditSnapshot {}
