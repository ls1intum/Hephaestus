package de.tum.in.www1.hephaestus.meta;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetaDataDTO(String[] repositoriesToMonitor) {

}
