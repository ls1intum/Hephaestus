package de.tum.cit.aet.hephaestus.gitprovider.webhook;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/**
 * Builds {@code gitlab.<namespace>.<project>.<event>} subjects from a GitLab webhook payload.
 *
 * <p>Invariants pinned by tests and ArchUnit rules:
 * <ul>
 *   <li>Case-folding uses {@code Locale.ROOT} — a naked {@code toLowerCase()} on a Turkish-locale
 *       JVM corrupts subjects (dotted-i).</li>
 *   <li>{@code .} is sanitised to {@code ~} via single-char replace; regex would over-match.</li>
 *   <li>Group fallback {@code firstNonBlank(full_path, path, group_path)} treats empty strings as
 *       missing, not just {@code null}.</li>
 *   <li>GitLab's {@code /-/} URL separator is handled with raw {@code String.split} —
 *       {@link java.net.URI} escapes the dash differently.</li>
 *   <li>{@code ?} is a literal NATS subject token here, NOT a wildcard.</li>
 * </ul>
 */
public final class GitLabSubjectBuilder {

    private static final String INSTANCE_PLACEHOLDER = "?";
    private static final String UNKNOWN_EVENT = "unknown";

    private GitLabSubjectBuilder() {}

    public static String build(JsonNode payload) {
        String rawEventName = firstNonEmptyText(
            payload.path("object_kind"),
            payload.path("event_name"),
            UNKNOWN_EVENT
        ).toLowerCase(Locale.ROOT);
        String eventToken = sanitizeToken(normalizeEventName(rawEventName));
        if (eventToken.isEmpty()) {
            eventToken = UNKNOWN_EVENT;
        }

        NamespaceAndProject result = extractFromProject(payload);
        if (result.namespace == null) {
            result = extractFromGroup(payload);
        }
        if (result.namespace == null) {
            result = extractFromObjectAttributes(payload);
        }

        String namespace = result.namespace == null ? INSTANCE_PLACEHOLDER : result.namespace;
        String project = result.project == null ? INSTANCE_PLACEHOLDER : result.project;
        return "gitlab." + namespace + "." + project + "." + eventToken;
    }

    static NamespaceAndProject extractFromProject(JsonNode payload) {
        JsonNode project = payload.path("project");
        String pathWithNamespace = firstNonBlank(
            payload.path("path_with_namespace").asText(""),
            project.path("path_with_namespace").asText("")
        );
        if (pathWithNamespace != null) {
            List<String> parts = sanitizeParts(pathWithNamespace);
            if (parts.size() >= 2) {
                String namespace = String.join("~", parts.subList(0, parts.size() - 1));
                return new NamespaceAndProject(namespace, parts.get(parts.size() - 1));
            }
        }
        return NamespaceAndProject.EMPTY;
    }

    static NamespaceAndProject extractFromGroup(JsonNode payload) {
        JsonNode group = payload.path("group");
        String groupPath = firstNonBlank(
            group.path("full_path").asText(""),
            group.path("path").asText(""),
            group.path("group_path").asText("")
        );
        if (groupPath != null) {
            List<String> parts = sanitizeParts(groupPath);
            if (!parts.isEmpty()) {
                return new NamespaceAndProject(String.join("~", parts), INSTANCE_PLACEHOLDER);
            }
        }
        return NamespaceAndProject.EMPTY;
    }

    static NamespaceAndProject extractFromObjectAttributes(JsonNode payload) {
        JsonNode objectAttributes = payload.path("object_attributes");
        boolean hasProject = objectAttributes.hasNonNull("project_id");
        String url = objectAttributes.path("url").asText("");
        if (!url.contains("://")) {
            return NamespaceAndProject.EMPTY;
        }

        int schemeEnd = url.indexOf("://");
        String afterScheme = url.substring(schemeEnd + 3);
        int firstSlash = afterScheme.indexOf('/');
        String path = firstSlash < 0 ? "" : afterScheme.substring(firstSlash + 1);
        int separator = path.indexOf("/-/");
        if (separator >= 0) {
            path = path.substring(0, separator);
        }

        List<String> parts = sanitizeParts(path);
        if (hasProject && parts.size() > 1) {
            String namespace = String.join("~", parts.subList(0, parts.size() - 1));
            return new NamespaceAndProject(namespace, parts.get(parts.size() - 1));
        }
        if (!parts.isEmpty()) {
            return new NamespaceAndProject(String.join("~", parts), INSTANCE_PLACEHOLDER);
        }
        return NamespaceAndProject.EMPTY;
    }

    static String normalizeEventName(String lower) {
        if (lower.startsWith("user_") && lower.contains("_group")) {
            return "member";
        }
        if (lower.startsWith("subgroup_")) {
            return "subgroup";
        }
        if (lower.startsWith("project_")) {
            return "project";
        }
        if (lower.equals("work_item")) {
            return "issue";
        }
        return lower;
    }

    private static String sanitizeToken(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace('.', '~');
    }

    private static List<String> sanitizeParts(String path) {
        List<String> result = new ArrayList<>();
        for (String segment : path.split("/", -1)) {
            if (!segment.isEmpty()) {
                result.add(sanitizeToken(segment));
            }
        }
        return result;
    }

    /** Returns the first non-null, non-blank string; treats empty strings as missing. */
    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonEmptyText(JsonNode a, JsonNode b, String fallback) {
        String first = a.asText("");
        if (!first.isEmpty()) {
            return first;
        }
        String second = b.asText("");
        if (!second.isEmpty()) {
            return second;
        }
        return fallback;
    }

    record NamespaceAndProject(String namespace, String project) {
        static final NamespaceAndProject EMPTY = new NamespaceAndProject(null, null);
    }
}
