package de.tum.cit.aet.hephaestus.core.auth;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Runtime configuration for the core.auth module.
 *
 * <p>All knobs that vary by environment live here: JWT issuer URL, audience, TTL,
 * cookie attributes. Defaults are tuned for production; integration tests override
 * via {@code @TestPropertySource}.
 *
 * @param issuer        Canonical issuer URI; this is what we put in the {@code iss}
 *                      claim and what {@code /.well-known/openid-configuration} reports.
 * @param audience      Default {@code aud} claim for SPA cookies.
 * @param accessTtl     Cookie-JWT lifetime. Short by design — refresh rotates a new jti.
 * @param cookieName    Spring's cookie name. {@code __Host-} prefix is browser-enforced
 *                      ({@code Secure}, no {@code Domain}, {@code Path=/}).
 */
@ConfigurationProperties(prefix = "hephaestus.auth")
public record AuthProperties(
    @DefaultValue("http://localhost:38080") URI issuer,
    @DefaultValue("hephaestus-spa") String audience,
    @DefaultValue("15m") Duration accessTtl,
    @DefaultValue("__Host-HEPHAESTUS_AT") String cookieName
) {}
