package de.tum.in.www1.hephaestus.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetaDataDTO(
    @NonNull String[] repositoriesToMonitor,
    @NonNull String scheduledDay,
    @NonNull String scheduledTime
) {}
