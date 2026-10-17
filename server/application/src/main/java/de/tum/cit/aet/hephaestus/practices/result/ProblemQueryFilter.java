package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ProblemQueryFilter(
        @Nullable UUID resultId,
        @Nullable String path,
        @Nullable List<Severity> severities,
        boolean openOnly) {}
