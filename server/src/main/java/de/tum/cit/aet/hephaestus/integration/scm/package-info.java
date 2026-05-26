/**
 * Vendor-neutral SCM domain — entities for commits, PRs, reviews, issues, etc.
 * Vendor adapters under {@code integration/github}, {@code integration/gitlab} write
 * here via processors; cross-module coupling goes through
 * {@code integration.spi} (interfaces) and {@code integration.events} (domain events).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "SCM",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.integration.scm;
