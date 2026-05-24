package de.tum.cit.aet.hephaestus.integration.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the OAuth callback ingress. Single {@code successRedirect} target —
 * error paths return 4xx JSON so dev tools can surface them. Default matches the
 * webapp's {@code /integrations} settings page.
 *
 * <p>Wrapped in a record so the {@link OAuthCallbackController} constructor stays under
 * the 5-param ceiling enforced by {@code controllersAreThin}.
 */
@ConfigurationProperties(prefix = "hephaestus.integration.oauth")
public record OAuthCallbackProperties(String successRedirect) {
    public OAuthCallbackProperties {
        if (successRedirect == null || successRedirect.isBlank()) {
            successRedirect = "/integrations?status=success";
        }
    }
}
