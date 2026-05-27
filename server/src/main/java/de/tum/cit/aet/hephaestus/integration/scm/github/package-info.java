/**
 * GitHub vendor adapter — webhook + JetStream consumer + sync + lifecycle.
 *
 * <p>OPEN module by empirical necessity: full CLOSED encapsulation would require a deep
 * SPI-inversion refactor of workspace provisioning, agent context providers, contributor
 * sync, and Jackson polymorphism mixins — all of which legitimately need vendor-specific
 * types today. The {@code allowedDependencies} list below pins the OUTBOUND boundary
 * (what this adapter may reach into) — this is the more critical direction because
 * vendor adapters reaching into arbitrary modules is what would erode the unified-integration
 * boundary. NamedInterfaces still mark the most public sub-surfaces ({@code lifecycle},
 * {@code sync}, {@code app}, {@code installation}, {@code project}, {@code common},
 * {@code graphql-model}) for documenter / IDE navigation.
 *
 * <p>Following the audit recommendation but tempered with reality: the CLOSED rollout is
 * deferred until a Phase 5 that refactors workspace and agent to call through the SPI
 * for vendor-specific operations.
 * Cross-module callers go through:
 * <ul>
 *   <li>The SPI / events / handler / oauth / consumer / webhook named interfaces of
 *       {@code integration.core} for vendor-neutral contracts.</li>
 *   <li>The named interfaces of this module — {@code lifecycle}, {@code sync},
 *       {@code app}, {@code installation}, {@code project}, {@code common},
 *       {@code graphql-model} — for vendor-specific surfaces that workspace
 *       provisioning, agent contribution sync, and Jackson polymorphism mixins
 *       legitimately need.</li>
 * </ul>
 *
 * <p>The {@code allowedDependencies} list captures the outbound modules this adapter
 * legitimately depends on. Verified empirically by {@code ModulithVerificationTest} —
 * adding a new dependency requires an explicit entry here with a one-line rationale.
 *
 * <p>Rationale per entry:
 * <ul>
 *   <li>{@code integration.core} (+ all NamedInterfaces of it) — the vendor-neutral
 *       substrate every adapter consumes: SPI contracts, events bus, NATS consumer
 *       fleet, webhook ingest, OAuth state, handler dispatcher.</li>
 *   <li>{@code integration.scm} — the SCM domain kernel (shared User, PullRequest,
 *       Issue entities the GitHub processors write into).</li>
 *   <li>{@code core} — repo-wide utility module (LoggingUtils, WorkspaceAgnostic
 *       marker, webhook config types).</li>
 *   <li>{@code workspace} — legitimate cross-module: GithubLifecycleListener owns
 *       Workspace/RepositoryToMonitor row lifecycle from installation events;
 *       GitHubGraphQlClientProvider reads workspace token state.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · SCM · GitHub",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    allowedDependencies = {
        "integration.core",
        "integration.core::events",
        "integration.core::spi",
        "integration.core::handler",
        "integration.core::oauth",
        "integration.core::consumer",
        "integration.core::webhook",
        "integration.scm",
        "core",
        "core::webhook",
        "workspace"
    }
)
package de.tum.cit.aet.hephaestus.integration.scm.github;
