package de.tum.cit.aet.hephaestus.core.configuration;

import java.util.List;
import org.jspecify.annotations.NonNull;

public record ConfigurationFactDTO(
        @NonNull String id,
        @NonNull String subject,
        @NonNull List<ConfigurationRole> roles,
        @NonNull ConfigurationRequirement requirement,
        @NonNull ConfigurationStatus status,
        @NonNull String explanation,
        @NonNull String documentationUrl) {}
