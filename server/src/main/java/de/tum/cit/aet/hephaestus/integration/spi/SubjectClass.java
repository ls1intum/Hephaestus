package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * Discriminator for {@link IntegrationSubject}s — the kind of work product Hephaestus
 * reviews or contextualizes. Persisted on {@code agent_job.subject_class} for
 * polymorphic dispatch in the agent layer (no {@code switch (kind)} branching).
 */
public enum SubjectClass {
    /** SCM pull request / merge request. */
    PULL_REQUEST,
    /** SCM issue. */
    ISSUE,
    /** Outline document. */
    OUTLINE_DOCUMENT,
    /** Slack message thread (parent + replies). */
    SLACK_MESSAGE_THREAD,
    /** Linear issue. */
    LINEAR_ISSUE,
    /** Jira issue. */
    JIRA_ISSUE,
    /** Confluence page. */
    CONFLUENCE_PAGE,
    /** Notion page. */
    NOTION_PAGE,
    /** CI build (scaffolded; no CI implementation in #1198). */
    BUILD,
    /** Observability alert/incident (scaffolded). */
    ALERT
}
