/**
 * Vendor-neutral SCM family root. Shared kernel in {@code scm/domain}; vendor adapters
 * in {@code scm/github} and {@code scm/gitlab}; family-shared orchestrator in
 * {@code scm/sync}. Cross-module coupling goes through {@code integration.core.spi}
 * and {@code integration.core.events}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "SCM",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.integration.scm;
