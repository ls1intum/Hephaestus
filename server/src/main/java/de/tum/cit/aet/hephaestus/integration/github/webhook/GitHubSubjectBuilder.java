package de.tum.cit.aet.hephaestus.integration.github.webhook;

import tools.jackson.databind.JsonNode;

/**
 * Builds {@code github.<org>.<repo>.<event>} subjects from a GitHub webhook payload. Missing or
 * blank tokens collapse to {@code ?}; dots inside tokens become {@code ~}. The event is supplied
 * by the caller (GitHub puts it in {@code X-GitHub-Event}, not the body).
 */
public final class GitHubSubjectBuilder {

    private static final String INSTANCE_PLACEHOLDER = "?";

    private GitHubSubjectBuilder() {}

    public static String build(JsonNode payload, String eventType) {
        String org = INSTANCE_PLACEHOLDER;
        String repo = INSTANCE_PLACEHOLDER;

        JsonNode repository = payload.path("repository");
        JsonNode organization = payload.path("organization");

        if (!repository.isMissingNode() && !repository.isNull()) {
            JsonNode owner = repository.path("owner");
            org = sanitize(orPlaceholder(owner.path("login").asText("")));
            repo = sanitize(orPlaceholder(repository.path("name").asText("")));
        } else if (!organization.isMissingNode() && !organization.isNull()) {
            org = sanitize(orPlaceholder(organization.path("login").asText("")));
        }

        String event = sanitize(eventType == null || eventType.isEmpty() ? INSTANCE_PLACEHOLDER : eventType);
        return "github." + org + "." + repo + "." + event;
    }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return INSTANCE_PLACEHOLDER;
        }
        return value.replace('.', '~');
    }

    private static String orPlaceholder(String value) {
        return value == null || value.isEmpty() ? INSTANCE_PLACEHOLDER : value;
    }
}
