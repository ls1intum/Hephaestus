package de.tum.in.www1.hephaestus.account;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "An identity provider account that can be linked to the user")
public record LinkedAccountDTO(
    @Schema(description = "Identity provider alias (e.g. 'github', 'gitlab-lrz')") String providerAlias,
    @Schema(description = "Display name of the identity provider") String providerName,
    @Schema(description = "Whether the user has linked this provider") boolean connected,
    @Nullable @Schema(description = "Username on the external provider, if connected") String linkedUsername
) {}
