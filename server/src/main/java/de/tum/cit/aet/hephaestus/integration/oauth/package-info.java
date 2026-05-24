/**
 * Vendor OAuth callback ingress.
 *
 * <p>Houses {@link de.tum.cit.aet.hephaestus.integration.oauth.OAuthCallbackController},
 * the single HTTP entry point that consumes a vendor's OAuth redirect, validates the
 * HMAC-signed {@code state} parameter, and hands off to the per-kind
 * {@code ConnectionStrategy.finalizeConnect}.
 *
 * <p>Cross-cutting trait — not vendor-specific — so it lives at the integration top
 * level alongside {@code webhook/}, {@code manifest/}, {@code registry/}.
 *
 * <p>Authentication on this path is NOT JWT-based; the vendor browser redirect arrives
 * unauthenticated. The HMAC over the state parameter IS the auth mechanism. See the
 * controller Javadoc for the full security model.
 */
package de.tum.cit.aet.hephaestus.integration.oauth;
