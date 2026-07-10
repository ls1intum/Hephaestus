package de.tum.cit.aet.hephaestus.integration.core.consumer;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Pure subject helpers for NATS consumers. Repository integrations use scoped wildcard filters such as
 * {@code github.owner.repo.>}; Slack Events API callbacks use the flat {@code slack.>} consumer. Slack
 * interactivity/button postbacks stay on the separate signed HTTP endpoint.
 *
 * @see de.tum.cit.aet.hephaestus.integration.core.webhook.IntegrationKindRouting the HTTP-path router
 */
public final class ConsumerSubjectMath {

    /**
     * NATS subject-prefix allow-list — the kinds that publish to JetStream. Slack Events API callbacks publish as
     * {@code slack.<team>.<scope>.<event>}; Slack interactivity/button postbacks carry no subject.
     */
    private static final Map<String, IntegrationKind> PREFIX_TO_KIND = Map.of(
        "github",
        IntegrationKind.GITHUB,
        "gitlab",
        IntegrationKind.GITLAB,
        "slack",
        IntegrationKind.SLACK
    );

    private ConsumerSubjectMath() {
        // utility class - no instances
    }

    // Subject prefix construction (publisher-mirror)

    /**
     * Builds the NATS subject prefix for a repository identifier.
     * <ul>
     *   <li><b>GitHub:</b> {@code owner/repo} → {@code github.owner.repo} (exactly 2 parts)</li>
     *   <li><b>GitLab:</b> {@code group/sub/project} → {@code gitlab.group~sub.project}
     *       (namespace parts joined with {@code ~})</li>
     * </ul>
     * Dots in any path segment are sanitized to {@code ~} so the NATS token grammar holds.
     */
    public static String buildSubjectPrefix(String streamName, String nameWithOwner) {
        if (streamName == null || streamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Stream name cannot be null or empty.");
        }
        if (nameWithOwner == null || nameWithOwner.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository identifier cannot be null or empty.");
        }

        String sanitized = nameWithOwner.trim().replace(".", "~");
        String[] parts = sanitized.split("/", -1);
        if (Arrays.stream(parts).anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                String.format("Invalid repository format: '%s'. Empty path segments are not allowed.", nameWithOwner)
            );
        }

        if ("gitlab".equals(streamName)) {
            if (parts.length < 2) {
                throw new IllegalArgumentException(
                    String.format(
                        "Invalid GitLab repository format: '%s'. Expected 'namespace/project'.",
                        nameWithOwner
                    )
                );
            }
            String namespace = String.join("~", Arrays.copyOfRange(parts, 0, parts.length - 1));
            String project = parts[parts.length - 1];
            return streamName + "." + namespace + "." + project;
        }

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                String.format("Invalid repository format: '%s'. Expected format 'owner/repository'.", nameWithOwner)
            );
        }
        return streamName + "." + parts[0] + "." + parts[1];
    }

    // Wildcard subject filters

    /**
     * Wildcard subject filter that matches every event for a single repository.
     *
     * @param streamName    the NATS stream name
     * @param nameWithOwner repository identifier in {@code owner/repo} (or nested for GitLab)
     * @return a subject like {@code github.owner.repo.>}
     */
    public static String repositoryFilter(String streamName, String nameWithOwner) {
        return buildSubjectPrefix(streamName, nameWithOwner) + ".>";
    }

    /**
     * Wildcard subject filter that matches every org-level event. Org events use the literal
     * {@code ?} placeholder where a repository would otherwise sit.
     *
     * @param streamName the NATS stream name
     * @param owner      the organization login
     * @return a subject like {@code github.owner.?.>}
     */
    public static String organizationFilter(String streamName, String owner) {
        return buildSubjectPrefix(streamName, owner + "/?") + ".>";
    }

    /**
     * Wildcard subject filter that matches every installation-level event for the given
     * {@link IntegrationKind}. Today only GitHub publishes installation events (GitLab
     * uses PAT-based auth and has no installation concept); the kind is taken as a
     * parameter so the SPI signature stays vendor-neutral and new installation-capable
     * vendors can plug in without renaming the call site.
     *
     * @param kind installation-aware kind. Must be {@link IntegrationKind#GITHUB} today;
     *             any other kind throws {@link UnsupportedOperationException} with a
     *             clear message rather than silently producing a bogus filter.
     * @return the subject e.g. {@code github.?.?.>}
     * @throws UnsupportedOperationException if {@code kind} does not yet have installation
     *             semantics (anything other than GITHUB today).
     */
    public static String installationAwareSubjectFilter(IntegrationKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        return switch (kind) {
            case GITHUB -> buildSubjectPrefix("github", "?/?") + ".>";
            case GITLAB, SLACK -> throw new UnsupportedOperationException(
                "Installation-aware subject filter not yet supported for kind=" +
                    kind +
                    " (only GITHUB publishes installation events today)"
            );
        };
    }

    /**
     * Resolves the NATS stream name for an {@link IntegrationKind}. Currently a 1:1 mapping
     * (GitHub → {@code "github"}, GitLab → {@code "gitlab"}); kinds without a stream return
     * {@link Optional#empty()} so callers can short-circuit without exceptions on the path.
     *
     * <p>Slack maps to the {@code "slack"} stream (monitored-channel {@code message} ingest).
     * A null kind returns {@link Optional#empty()} so callers can short-circuit on the path.
     */
    public static Optional<String> streamNameFor(@Nullable IntegrationKind kind) {
        if (kind == null) {
            return Optional.empty();
        }
        return switch (kind) {
            case GITHUB -> Optional.of("github");
            case GITLAB -> Optional.of("gitlab");
            case SLACK -> Optional.of("slack");
        };
    }

    // Subject → kind

    /**
     * Explicit allow-list mapping of subject prefix → {@link IntegrationKind}. Returns
     * {@link Optional#empty()} for null, blank, dot-less, or unknown prefixes. Never
     * reflects on input.
     */
    public static Optional<IntegrationKind> kindFromSubjectPrefix(@Nullable String fullSubject) {
        if (fullSubject == null || fullSubject.isBlank()) {
            return Optional.empty();
        }
        int firstDot = fullSubject.indexOf('.');
        if (firstDot <= 0) {
            return Optional.empty();
        }
        String prefix = fullSubject.substring(0, firstDot).toLowerCase(Locale.ROOT);
        return Optional.ofNullable(PREFIX_TO_KIND.get(prefix));
    }

    /**
     * Builds the durable consumer name for a scope. Format:
     * {@code <base>-scope-<scopeId>}. Returned name is suitable for direct use as a
     * JetStream durable identifier.
     */
    public static String scopeConsumerName(String baseConsumerName, long scopeId) {
        if (baseConsumerName == null || baseConsumerName.isBlank()) {
            throw new IllegalArgumentException("Base consumer name cannot be null or blank.");
        }
        return baseConsumerName + "-scope-" + scopeId;
    }

    /**
     * Builds the durable consumer name for the installation-wide consumer. Format:
     * {@code <base>-installation}.
     */
    public static String installationConsumerName(String baseConsumerName) {
        if (baseConsumerName == null || baseConsumerName.isBlank()) {
            throw new IllegalArgumentException("Base consumer name cannot be null or blank.");
        }
        return baseConsumerName + "-installation";
    }

    /**
     * Builds the durable consumer name for a fleet-wide flat-stream consumer:
     * {@code <base>-<stream>}. A flat-stream kind is not repository-scoped, so a single
     * fleet-wide consumer subscribes to {@link #flatStreamSubjectFilter(IntegrationKind)} and
     * resolves the tenant inside the handler (mirroring the installation-wide consumer's shape;
     * today only the messaging kinds, e.g. Slack).
     */
    public static String flatStreamConsumerName(String baseConsumerName, IntegrationKind kind) {
        if (baseConsumerName == null || baseConsumerName.isBlank()) {
            throw new IllegalArgumentException("Base consumer name cannot be null or blank.");
        }
        return baseConsumerName + "-" + resolveStream(kind);
    }

    /**
     * Wildcard subject filter matching every event on a flat-stream kind's stream
     * ({@code <stream>.>}). One fleet-wide filter — a flat-stream kind has no per-scope
     * repository fan-out.
     */
    public static String flatStreamSubjectFilter(IntegrationKind kind) {
        return resolveStream(kind) + ".>";
    }

    private static String resolveStream(IntegrationKind kind) {
        return streamNameFor(kind).orElseThrow(() ->
            new IllegalArgumentException("No NATS stream resolved for kind=" + kind)
        );
    }
}
