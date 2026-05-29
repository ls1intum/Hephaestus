package de.tum.cit.aet.hephaestus.core.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Centralises the HTTP security-header set so every user-facing filter chain (the resource-server
 * chain in {@code SecurityConfig} and the oauth2Login chain in {@code AuthSecurityConfig}) emits an
 * identical, complete set. Single source of truth — change the policy here, not per chain.
 *
 * <p>Headers applied:
 * <ul>
 *   <li>{@code Strict-Transport-Security} — 1y, includeSubDomains (existing behaviour, preserved).</li>
 *   <li>{@code X-Frame-Options: DENY} + {@code X-Content-Type-Options: nosniff} (existing, preserved).</li>
 *   <li>{@code Content-Security-Policy-Report-Only} — API/Swagger-host policy, report-only so it
 *       cannot break Swagger UI while we observe violations.</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}.</li>
 *   <li>{@code Cross-Origin-Opener-Policy: same-origin}.</li>
 *   <li>{@code Cross-Origin-Embedder-Policy: credentialless} — written via a static writer because
 *       the Spring Security {@code CrossOriginEmbedderPolicy} enum on the classpath only models
 *       {@code unsafe-none} / {@code require-corp}, not {@code credentialless}.</li>
 * </ul>
 */
public final class SecurityHeaders {

    private SecurityHeaders() {}

    /**
     * Report-only CSP tuned for an API + Swagger UI host. {@code unsafe-inline} is allowed for
     * styles only (Swagger injects inline styles); scripts are locked to {@code 'self'}. Image
     * sources permit the avatar CDNs we render. Report-only for now so a too-tight directive never
     * breaks the docs UI before we have telemetry.
     */
    private static final String CONTENT_SECURITY_POLICY =
        "default-src 'self'; " +
        "script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: https://avatars.githubusercontent.com https://*.gitlab.com https://gitlab.lrz.de; " +
        "connect-src 'self'; " +
        "frame-ancestors 'none'; " +
        "base-uri 'self'; " +
        "form-action 'self' https://github.com https://gitlab.lrz.de";

    /** Applies the full header set to the given {@link HttpSecurity}. */
    public static void apply(HttpSecurity http) throws Exception {
        http.headers(headers ->
            headers
                .frameOptions(frameOptions -> frameOptions.deny())
                .contentTypeOptions(contentType -> {})
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY).reportOnly())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                // COEP credentialless: the builder enum lacks this value on the current Spring
                // Security version, so emit it directly.
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Embedder-Policy", "credentialless"))
        );
    }
}
