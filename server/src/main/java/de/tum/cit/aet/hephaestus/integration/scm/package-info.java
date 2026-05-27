/**
 * Vendor-neutral SCM family root. The shared kernel (entities, repositories, value
 * objects) lives under {@code integration/scm/domain}; vendor adapters under
 * {@code integration/scm/github} and {@code integration/scm/gitlab} write into that
 * kernel via processors. The family-shared orchestrator lives in
 * {@code integration/scm/sync}. The multi-vendor identity resolver moved out in
 * Phase 4 — see {@code integration/core/connection/identity} — because it bridges
 * authenticated JWTs to SCM user rows across vendors and belongs next to the
 * Connection registry, not in the SCM family. Cross-module coupling goes through
 * {@code integration.core.spi} (interfaces) and {@code integration.core.events}
 * (domain events).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "SCM",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.integration.scm;
