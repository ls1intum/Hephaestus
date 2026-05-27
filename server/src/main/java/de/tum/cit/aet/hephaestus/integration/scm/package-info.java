/**
 * Vendor-neutral SCM family root. The shared kernel (entities, repositories, value
 * objects) lives under {@code integration/scm/domain}; vendor adapters under
 * {@code integration/scm/github} and {@code integration/scm/gitlab} write into that
 * kernel via processors. The family-shared orchestrator lives in
 * {@code integration/scm/sync}; the multi-vendor identity resolver currently lives in
 * {@code integration/scm/user} (deferred to Phase 4 of the integration restructure
 * and slated to move into {@code integration/core/connection/identity}). Cross-module
 * coupling goes through {@code integration.core.spi} (interfaces) and
 * {@code integration.events} (domain events).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "SCM",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.integration.scm;
