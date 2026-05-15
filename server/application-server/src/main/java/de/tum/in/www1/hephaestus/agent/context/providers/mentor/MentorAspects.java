package de.tum.in.www1.hephaestus.agent.context.providers.mentor;

import java.util.Set;

/**
 * Single source of truth for mentor aspect output keys. The Java {@code fetch_context}
 * whitelist + the runner's JS whitelist (`pi-mentor-runner.mjs#FETCH_CONTEXT_ALLOWED`) must
 * agree; the Java side derives from the provider constants so a new provider auto-extends
 * the set, but the JS side needs a manual mirror — keep the file basenames identical.
 *
 * <p><b>AOP convention for every mentor aspect provider:</b> {@code @Transactional(readOnly=true)}
 * sits on {@code contribute(...)} — the external entry point invoked by
 * {@link de.tum.in.www1.hephaestus.agent.context.WorkspaceContextBuilder} through the Spring
 * proxy — and NEVER on {@code buildPayload(...)}. Annotating {@code buildPayload} would be
 * silently dropped: the only callers are the in-class self-invocations from {@code contribute},
 * which bypass the proxy. Pinned by {@code MentorAspectProviderArchitectureTest}.
 */
public final class MentorAspects {

    public static final Set<String> ALLOWED_OUTPUT_KEYS = Set.of(
        UserAspectProvider.OUTPUT_KEY,
        WorkspaceAspectProvider.OUTPUT_KEY,
        PracticeCatalogAspectProvider.OUTPUT_KEY,
        FindingsHistoryAspectProvider.OUTPUT_KEY
    );

    private MentorAspects() {}
}
