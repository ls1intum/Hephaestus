package de.tum.cit.aet.hephaestus.core.auth.web;

import com.nimbusds.jose.jwk.JWKSet;
import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtSigningKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes Hephaestus as a standard OIDC issuer from day one:
 * {@code /.well-known/openid-configuration} + {@code /.well-known/jwks.json}.
 *
 * <p>Costs ~nothing today (the SPA reads its session from the cookie, not via discovery)
 * but means any future service — the worker runtime, the mentor agent, a CLI, an
 * Issue #1200 third-party plugin — can validate Hephaestus-issued JWTs as a normal OIDC
 * relying party without bespoke wiring. It is also the forward-compat seam that lets
 * Spring Authorization Server be mounted later without resource-server changes.
 */
@RestController
@Tag(name = "Auth discovery", description = "OIDC issuer metadata (public)")
public class WellKnownController {

    private final AuthProperties properties;
    private final JwtSigningKeyService keyService;

    public WellKnownController(AuthProperties properties, JwtSigningKeyService keyService) {
        this.properties = properties;
        this.keyService = keyService;
    }

    @GetMapping(value = "/.well-known/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()")
    @Operation(summary = "OIDC issuer metadata", operationId = "getOpenidConfiguration")
    public Map<String, Object> openidConfiguration() {
        String issuer = properties.issuer().toString();
        return Map.of(
            "issuer",
            issuer,
            "jwks_uri",
            issuer + "/.well-known/jwks.json",
            "id_token_signing_alg_values_supported",
            List.of("ES256"),
            "response_types_supported",
            List.of("code"),
            "subject_types_supported",
            List.of("public"),
            "scopes_supported",
            List.of("user", "app_admin")
        );
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()")
    @Operation(summary = "JWK set (public keys only)", operationId = "getJwks")
    public Map<String, Object> jwks() {
        JWKSet publicSet = keyService.publicJwkSet();
        return publicSet.toJSONObject();
    }
}
