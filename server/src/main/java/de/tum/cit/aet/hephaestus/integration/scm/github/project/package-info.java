/**
 * GitHub Projects V2 entities + integrity services. Exposed to workspace because Project
 * ownership is tracked at the workspace level (workspaces own projects). ScmEventPayload also
 * references {@code Project}, {@code ProjectItem}, {@code ProjectStatusUpdate} as
 * value-object snapshots — these are the still-pre-Phase-4 entity leaks documented in the
 * deferred {@code coreEventsDoesNotImportScmDomainEntities} ArchUnit rule.
 *
 * <p>Named interface: {@code project}.
 */
@org.springframework.modulith.NamedInterface("project")
package de.tum.cit.aet.hephaestus.integration.scm.github.project;
