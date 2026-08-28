package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import org.jspecify.annotations.Nullable;

public record PracticeUsageSnapshot(@Nullable PracticeAutonomy autonomy) implements ConfigAuditSnapshot {}
