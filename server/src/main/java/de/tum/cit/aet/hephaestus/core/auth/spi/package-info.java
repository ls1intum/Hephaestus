/**
 * Cross-module SPI for the auth module — the only {@code core.auth} contracts other modules
 * may depend on. {@code AccountRepository} (read handle on the principal) and
 * {@code AccountRoleQuery} (login → feature-flag/role check) live here. Implementations and
 * domain entities stay encapsulated inside {@code core.auth}.
 */
@org.springframework.modulith.NamedInterface("auth-spi")
package de.tum.cit.aet.hephaestus.core.auth.spi;
