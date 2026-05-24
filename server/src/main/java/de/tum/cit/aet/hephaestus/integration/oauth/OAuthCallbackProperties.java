package de.tum.cit.aet.hephaestus.integration.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the OAuth callback ingress.
 *
 * <p>Bundling these two URIs in a single record keeps the {@link OAuthCallbackController}
 * constructor under the architecture-enforced 5-param ceiling — the rule treats each
 * {@code @Value} string as a separate parameter, so naive injection blows the budget.
 *
 * <p>{@code errorRedirect} accepts a single {@code {kind}} placeholder that the controller
 * substitutes with the lowercased integration kind ({@code github}, {@code gitlab},
 * {@code slack}, {@code outline}). The substitution is intentionally simple — there is no
 * generic URL templating engine here; only the documented placeholder is honoured.
 */
@ConfigurationProperties(prefix = "hephaestus.integration.oauth")
public record OAuthCallbackProperties(
    String successRedirect,
    String errorRedirect
) {
    /** Defaults align with the Hephaestus webapp's {@code /integrations} settings page. */
    public OAuthCallbackProperties {
        if (successRedirect == null || successRedirect.isBlank()) {
            successRedirect = "/integrations?status=success";
        }
        if (errorRedirect == null || errorRedirect.isBlank()) {
            errorRedirect = "/integrations?status=error&kind={kind}";
        }
    }
}
