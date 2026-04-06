package de.tum.in.www1.hephaestus.account;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An enabled identity provider available for login")
public record IdentityProviderDTO(
    @Schema(
        description = "Identity provider alias used as idpHint (e.g. 'github', 'gitlab-lrz')",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String alias,
    @Schema(description = "Display name of the identity provider", requiredMode = Schema.RequiredMode.REQUIRED)
    String displayName,
    @Schema(description = "Provider type (e.g. 'github', 'oidc')", requiredMode = Schema.RequiredMode.REQUIRED)
    String type
) {}
