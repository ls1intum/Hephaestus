/**
 * Mentor content sources — implementations of {@code ContentSource} that materialise JSON
 * context files (user activity, workspace shape, practice catalog, findings history) consumed by
 * the Pi mentor agent via {@code fetch_context}.
 *
 * <p><b>AOP convention for every {@code *ContentSource}:</b>
 * {@code @Transactional(readOnly=true)} sits on {@code contribute(...)} — the external entry
 * point invoked by {@link de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder}
 * through the Spring proxy — and NEVER on {@code buildPayload(...)}. Annotating
 * {@code buildPayload} would be silently dropped: the only callers are the in-class
 * self-invocations from {@code contribute}, which bypass the proxy. Pinned by
 * {@code MentorContentSourceArchitectureTest}.
 */
package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;
