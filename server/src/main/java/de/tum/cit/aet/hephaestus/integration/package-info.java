/**
 * Unified integration framework.
 *
 * <p>Houses all vendor adapters (GitHub, GitLab, Slack, Outline, …) end-to-end plus the
 * cross-cutting trait modules (webhook ingest, realtime ingest, NATS consumer, identity,
 * feedback-post tracking, connection aggregate, framework bootstrap) and the family
 * libraries (scm-lib, messaging-lib, knowledge-lib, project-tracker-lib).
 *
 * <p>Three coexisting structural axes:
 * <ul>
 *   <li><b>Vendor coherence</b> — {@code integration/scm/{github,gitlab}/...}, {@code integration/{slack,outline}/...}
 *   <li><b>Family-shared abstractions</b> — {@code integration/{scm,messaging,knowledge,project-tracker}-lib/...}
 *   <li><b>Cross-cutting traits</b> — {@code integration/core/{webhook,consumer,events,spi,handler,connection,oauth,feedback,framework}/...}
 * </ul>
 *
 * <p>What is NOT an integration: per-user, outbound-first, ephemeral-subject endpoints
 * (IDE plugins, in-browser SSE streams). Those live elsewhere (e.g. {@code mentor/transport/}).
 *
 * <p>Cross-module access goes ONLY through {@code integration :: spi} and
 * {@code integration :: events} named interfaces (now physically under {@code integration/core/}).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration Framework",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package de.tum.cit.aet.hephaestus.integration;
