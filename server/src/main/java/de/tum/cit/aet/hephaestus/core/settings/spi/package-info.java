/**
 * Cross-module port surface for instance settings. The only {@code core.settings} package other
 * modules may depend on; the entity, repository, and admin controller stay encapsulated inside
 * {@code core.settings}.
 */
@org.springframework.modulith.NamedInterface("settings-spi")
package de.tum.cit.aet.hephaestus.core.settings.spi;
