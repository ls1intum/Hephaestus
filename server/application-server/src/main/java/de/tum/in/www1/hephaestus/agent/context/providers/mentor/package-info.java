/**
 * Mentor aspect providers — implementations of {@code ContentProvider} that materialise JSON
 * "aspects" (user activity, workspace shape, practice catalog, findings history) consumed by
 * the Pi mentor agent via {@code fetch_context}.
 *
 * <p><b>AOP convention for every {@code *AspectProvider}:</b>
 * {@code @Transactional(readOnly=true)} sits on {@code contribute(...)} — the external entry
 * point invoked by {@link de.tum.in.www1.hephaestus.agent.context.WorkspaceContextBuilder}
 * through the Spring proxy — and NEVER on {@code buildPayload(...)}. Annotating
 * {@code buildPayload} would be silently dropped: the only callers are the in-class
 * self-invocations from {@code contribute}, which bypass the proxy. Pinned by
 * {@code MentorAspectProviderArchitectureTest}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Agent Mentor Context")
package de.tum.in.www1.hephaestus.agent.context.providers.mentor;
