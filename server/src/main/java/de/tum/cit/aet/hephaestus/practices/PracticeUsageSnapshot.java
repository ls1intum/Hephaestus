package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;

public record PracticeUsageSnapshot(PracticeAutonomy autonomy) implements ConfigAuditSnapshot {}
