package de.tum.cit.aet.hephaestus.core.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProviderIdentityRefDTO(
        @NonNull IntegrationKind provider,
        @Nullable String login,
        @Nullable String displayName,
        @Nullable String avatarUrl) {}
