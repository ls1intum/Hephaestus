package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "source")
@JsonSubTypes({
    @JsonSubTypes.Type(value = HostCoverage.class, name = "host"),
    @JsonSubTypes.Type(value = ClaimedCoverage.class, name = "claimed")
})
public sealed interface ResultCoverage permits HostCoverage, ClaimedCoverage {}
