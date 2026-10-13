package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What selects observations themselves, beside the {@link ResultQueryFilter} that selects their results.
 *
 * @param severities the weight of the observation at its place, not its result's severity
 */
public record ObservationQueryFilter(
        @Nullable List<Severity> severities, @Nullable UUID practiceResultId) {
    public String @Nullable [] severityNames() {
        if (severities == null || severities.isEmpty()) {
            return null;
        }
        return severities.stream().map(Enum::name).toArray(String[]::new);
    }
}
