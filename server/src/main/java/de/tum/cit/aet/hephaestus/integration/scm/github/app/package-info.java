/**
 * GitHub App token minting service — exposed cross-module for workspace provisioning
 * and the SPI installation-token bridge.
 *
 * <p>Named interface: {@code app}. {@code GitHubAppTokenService} is the single source
 * of truth for installation token minting (JWT sign + caffeine token cache); workspace
 * adapters and the SPI {@code TokenRefresher} both consume it. Everything else under
 * {@code app/} stays internal.
 */
@org.springframework.modulith.NamedInterface("app")
package de.tum.cit.aet.hephaestus.integration.scm.github.app;
