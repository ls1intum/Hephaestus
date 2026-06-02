/**
 * Vendor OAuth callback ingress. Validates HMAC-signed {@code state} and hands off to
 * the per-kind {@code ConnectionStrategy.finalizeConnect}.
 */
@org.springframework.modulith.NamedInterface("oauth")
package de.tum.cit.aet.hephaestus.integration.core.oauth;
