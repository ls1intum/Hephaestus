package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;

/** Authoritative runtime model and price resolved together at admission. */
public record AdmittedLlmModel(
    ResolvedLlmModel resolved,
    LlmModelResolver.ConnectionRef connection,
    LlmPriceSnapshot price
) {}
