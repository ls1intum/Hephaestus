package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * Sealed taxonomy of integration families.
 *
 * <p>A family is a contract bundle — vendors in the same family share a common SPI
 * surface and domain model (e.g. all SCM vendors expose pull requests, diffs, inline
 * findings). Family-specific abstractions live in {@code integration/<family>-lib/}.
 *
 * <p>Closed set; PR-gated extension. Adding a family requires an ADR + sealed-permits
 * update; the compiler then enumerates every consumer that must handle the new variant.
 */
public sealed interface IntegrationFamily
    permits IntegrationFamily.Scm,
            IntegrationFamily.Messaging,
            IntegrationFamily.Knowledge,
            IntegrationFamily.ProjectTracker,
            IntegrationFamily.CiProvider,
            IntegrationFamily.Observability {

    Family kind();

    /** Source-code management — GitHub, GitLab, Bitbucket, Forgejo, Gitea. */
    record Scm() implements IntegrationFamily {
        @Override public Family kind() { return Family.SCM; }
    }

    /** Real-time messaging — Slack, Discord, Microsoft Teams. */
    record Messaging() implements IntegrationFamily {
        @Override public Family kind() { return Family.MESSAGING; }
    }

    /** Knowledge bases — Outline, Confluence, Notion. */
    record Knowledge() implements IntegrationFamily {
        @Override public Family kind() { return Family.KNOWLEDGE; }
    }

    /** Project / issue trackers — Linear, Jira, Asana. */
    record ProjectTracker() implements IntegrationFamily {
        @Override public Family kind() { return Family.PROJECT_TRACKER; }
    }

    /** CI providers — Jenkins, GitHub Actions, CircleCI, Tekton. SCAFFOLDED, no implementations in #1198. */
    record CiProvider() implements IntegrationFamily {
        @Override public Family kind() { return Family.CI_PROVIDER; }
    }

    /** Observability — Datadog, Sentry, Honeycomb. SCAFFOLDED, no implementations in #1198. */
    record Observability() implements IntegrationFamily {
        @Override public Family kind() { return Family.OBSERVABILITY; }
    }

    /** Discriminator enum used by {@link IntegrationKind#family()}. */
    enum Family {
        SCM,
        MESSAGING,
        KNOWLEDGE,
        PROJECT_TRACKER,
        CI_PROVIDER,
        OBSERVABILITY
    }
}
