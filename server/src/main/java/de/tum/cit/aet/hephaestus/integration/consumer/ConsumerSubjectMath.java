package de.tum.cit.aet.hephaestus.integration.consumer;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.lang.Nullable;

/**
 * Pure-function subject-arithmetic helpers for NATS consumers.
 *
 * <p>This class exists so the consumer wiring can build wildcard subject filters and parse
 * kind-from-prefix without touching either the NATS client or Spring. Every method is
 * static, side-effect-free, and deterministic — they are unit-testable in milliseconds and
 * can be composed in benchmarks.
 *
 * <h2>Filter shapes (consumer side)</h2>
 * <ul>
 *   <li><b>Repository filter</b> — {@code <stream>.<owner>.<repo>.>} matches every event for
 *       a single repository. Wildcards keep the filter set small (one entry per repo, not
 *       one entry per repo*eventType).</li>
 *   <li><b>Organization filter</b> — {@code <stream>.<owner>.?.>} matches every org-level
 *       event (the {@code ?} is the literal placeholder publishers emit when there is no
 *       repository context).</li>
 *   <li><b>Installation filter</b> — {@code github.?.?.>} matches every GitHub installation
 *       event. GitLab uses PAT-based auth so installation events are GitHub-only here.</li>
 * </ul>
 *
 * <h2>Why wildcards?</h2>
 * Without wildcards a scope with 200 repos and 12 event types creates 2,400 filter
 * subjects. NATS validates each subject against the stream when the consumer is
 * created or updated, so the cost is O(n*m) round-trips on large filter lists. Using
 * wildcards collapses this to O(n) repos.
 *
 * <h2>Subject-prefix → kind</h2>
 * {@link #kindFromSubjectPrefix(String)} duplicates the allow-list used by the public
 * router ({@code IntegrationKindRouting} for HTTP paths) and the message dispatcher
 * ({@code IntegrationMessageDispatcher#kindFromSubjectPrefix}). The duplication is
 * deliberate: this class is pure (no Spring), can be called from anywhere on the hot
 * path, and we never want to call {@link IntegrationKind#valueOf(String)} on
 * subject-derived input (reflection-on-user-input is the bug class we are precluding).
 * Adding a new kind requires touching all three allow-lists — failure to do so causes
 * silent NAKs at the consumer rather than misrouting, which is the cheaper failure mode.
 *
 * @see IntegrationMessageDispatcher#kindFromSubjectPrefix(String) the dispatcher's copy
 * @see de.tum.cit.aet.hephaestus.integration.webhook.IntegrationKindRouting the HTTP router's copy
 */
public final class ConsumerSubjectMath {

    /**
     * Mirror of the allow-list in {@link IntegrationMessageDispatcher} and
     * {@code IntegrationKindRouting}. Kept here to avoid a cyclic dependency between
     * the consumer's pure-utility surface and the (non-pure) dispatcher bean.
     */
    private static final Map<String, IntegrationKind> PREFIX_TO_KIND = Map.of(
        "github", IntegrationKind.GITHUB,
        "gitlab", IntegrationKind.GITLAB,
        "slack", IntegrationKind.SLACK,
        "outline", IntegrationKind.OUTLINE
    );

    private ConsumerSubjectMath() {
        // utility class - no instances
    }

    // -------------------------------------------------------------------------
    // Subject prefix construction (publisher-mirror)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Wildcard subject filters
    // -------------------------------------------------------------------------

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
     * Wildcard subject filter that matches every installation-level event.
     *
     * <p>GitHub-only: GitLab uses PAT-based auth and has no installation concept. The
     * caller is responsible for not subscribing this filter against a GitLab stream.
     *
     * @return the subject {@code github.?.?.>}
     */
    public static String installationFilterGithub() {
        return buildSubjectPrefix("github", "?/?") + ".>";
    }

    /**
     * Resolves the NATS stream name for an {@link IntegrationKind}. Currently a 1:1 mapping
     * (GitHub → {@code "github"}, GitLab → {@code "gitlab"}); kinds without a stream return
     * {@link Optional#empty()} so callers can short-circuit without exceptions on the path.
     *
     * <p>Messaging/knowledge kinds (Slack, Outline) do not have JetStream subscriptions in
     * this slice — their events flow through other channels. The empty return is the signal
     * to skip, not an error.
     */
    public static Optional<String> streamNameFor(@Nullable IntegrationKind kind) {
        if (kind == null) {
            return Optional.empty();
        }
        return switch (kind) {
            case GITHUB -> Optional.of("github");
            case GITLAB -> Optional.of("gitlab");
            case SLACK, OUTLINE -> Optional.empty();
        };
    }

    // -------------------------------------------------------------------------
    // Subject → kind
    // -------------------------------------------------------------------------

    /**
     * Explicit allow-list mapping of subject prefix → {@link IntegrationKind}. Returns
     * {@link Optional#empty()} for null, blank, dot-less, or unknown prefixes. Never
     * reflects on input.
     *
     * <p>Mirrors {@link IntegrationMessageDispatcher#kindFromSubjectPrefix(String)} so this
     * class can be used on hot paths without pulling the dispatcher bean in.
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
}
