/**
 * Repository-lifecycle domain events. Published by {@code RepositoryService} (or
 * the GitHub adapter's deletion path) and subscribed to by vendor-adapters or
 * feature modules that must cascade-delete dependents with no DB-level FK
 * (e.g. GitHub Projects V2 polymorphic ownership rows).
 *
 * <p>Exposed as a {@link org.springframework.modulith.NamedInterface} so
 * cross-module subscribers (today: {@code integration.scm.github.project.ProjectIntegrityService})
 * can subscribe without piercing internal repository-service implementation packages.
 */
@org.springframework.modulith.NamedInterface("events")
package de.tum.cit.aet.hephaestus.integration.scm.domain.repository.events;
