package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import java.util.Set;

/**
 * Single source of truth for mentor aspect output keys. The Java {@code fetch_context}
 * whitelist + the runner's JS whitelist ({@code pi-mentor-runner.mjs#FETCH_CONTEXT_ALLOWED})
 * must agree; the Java side derives from the provider constants so a new provider auto-extends
 * the set, but the JS side needs a manual mirror — keep the file basenames identical.
 */
public final class MentorAspects {

    public static final Set<String> ALLOWED_OUTPUT_KEYS = Set.of(
        UserAspectProvider.OUTPUT_KEY,
        WorkspaceAspectProvider.OUTPUT_KEY,
        PracticeCatalogAspectProvider.OUTPUT_KEY,
        FindingsHistoryAspectProvider.OUTPUT_KEY,
        PracticeStandingAspectProvider.OUTPUT_KEY
    );

    private MentorAspects() {}
}
