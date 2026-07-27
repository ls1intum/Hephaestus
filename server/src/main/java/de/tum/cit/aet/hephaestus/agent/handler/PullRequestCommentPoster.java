package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.ExistingDeliveryLookup;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackContent;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.SummaryHandle;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Posts agent review results as comments on PRs/MRs by dispatching to the per-vendor
 * {@link FeedbackChannel}. Owns sanitization, formatting, and metadata extraction;
 * the GraphQL call lives in the vendor channel under
 * {@code integration/<kind>/feedback/}.
 */
class PullRequestCommentPoster {

    private static final Logger log = LoggerFactory.getLogger(PullRequestCommentPoster.class);

    static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(15);

    /** Maximum comment body length before header/footer (GitHub limit is 65,536). */
    static final int MAX_BODY_LENGTH = 60_000;

    /** Maximum summary length in the collapsible header (prevents total comment from exceeding provider limits). */
    static final int MAX_SUMMARY_LENGTH = 200;

    /**
     * Marker embedded in summary posts so a re-review can locate and edit the prior summary in place.
     * Every path that formats a comment must build it through {@link #summaryMarkerFor}: if the
     * formatted marker and the looked-up one drift, delivery recovery double-posts.
     */
    static final String SUMMARY_MARKER_PREFIX = "<!-- hephaestus:practice-review:";

    // Sanitization patterns

    /** Matches @mentions (e.g., @username) — backtick-escaped to prevent notification spam.
     *  Lookbehind covers start-of-line, whitespace, punctuation, and markdown formatting chars
     *  ({@code * _ ~ > | -}) to prevent bypass via {@code *@user*}, {@code >@user}, or {@code - @user}. */
    private static final Pattern AT_MENTION = Pattern.compile(
        "(?<=^|[\\s(\\[\"'*_~>|#!+={}\\-])@([a-zA-Z0-9][-a-zA-Z0-9._]*)",
        Pattern.MULTILINE
    );

    /** Matches inline markdown images: ![alt](url) — stripped to prevent tracking pixels. */
    private static final Pattern MARKDOWN_IMAGE_INLINE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");

    /** Matches reference-style markdown images: ![alt][ref]. */
    private static final Pattern MARKDOWN_IMAGE_REF = Pattern.compile("!\\[[^\\]]*]\\[[^\\]]*]");

    /** Matches HTML comments — stripped to prevent hidden instructions for AI tools. */
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--[\\s\\S]*?-->");

    /** Matches any HTML tag for allowlist filtering. */
    private static final Pattern HTML_TAG = Pattern.compile(
        "</?([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*/?>",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Tags allowed in sanitized output. All attributes are stripped from allowed tags.
     * Notably excludes {@code <details>} and {@code <summary>} — these are used by the
     * {@link #formatComment} template, and allowing them in agent content would enable
     * structural breakout attacks ({@code </summary></details>} injected by agent).
     */
    static final Set<String> SAFE_HTML_TAGS = Set.of(
        "br",
        "hr",
        "code",
        "pre",
        "sub",
        "sup",
        "em",
        "strong",
        "b",
        "i",
        "p",
        "ul",
        "ol",
        "li",
        "blockquote",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "table",
        "thead",
        "tbody",
        "tr",
        "td",
        "th"
    );

    /**
     * Matches standalone approval language that could mislead reviewers.
     * Tolerates trailing punctuation (e.g., "LGTM!", "Approved.").
     */
    private static final Pattern APPROVAL_LANGUAGE = Pattern.compile(
        "^\\s*(?:LGTM|(?:looks good to me)|(?:approved)|(?:ready to merge)|(?:ship it)|(?:approved by\\b[^\\n]*))[.!?]*\\s*$",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    /**
     * Matches invisible Unicode characters: bidi controls, zero-width chars, BOM.
     * Prevents text direction attacks and @mention bypass via zero-width spaces.
     * Excludes U+200D (Zero Width Joiner) — used in compound emoji sequences.
     */
    private static final Pattern INVISIBLE_CHARS = Pattern.compile(
        "[\\u200B\\u200C\\u200E\\u200F\\u061C\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]"
    );

    /**
     * Matches GitLab slash commands at the start of a line (e.g., /approve, /merge, /close).
     * These are interpreted as actions by GitLab when posted in MR notes.
     * Escaped by wrapping in backticks (inline code) so they render as plain text.
     */
    private static final Pattern GITLAB_SLASH_COMMAND = Pattern.compile(
        "^(\\s*/(?:approve|merge|close|reopen|assign|unassign|label|unlabel|lock|unlock|" +
            "milestone|estimate|spend|award|subscribe|unsubscribe|todo|done|wip|draft|ready|" +
            "due|remove_due_date|weight|epic|copy_metadata|move|confidential|shrug|tableflip)\\b)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    /** Matches markdown autolinks: &lt;https://...&gt; — protected from HTML tag stripping. */
    private static final Pattern AUTOLINK = Pattern.compile("<(https?://[^>\\s]+)>");

    /**
     * Matches markdown links with non-http(s) URL schemes (e.g., javascript:, data:, vbscript:).
     * These are stripped down to just the display text to prevent phishing/XSS vectors
     * from untrusted agent output.
     */
    private static final Pattern UNSAFE_MARKDOWN_LINK = Pattern.compile("\\[([^\\]]*)\\]\\((?!(?i)https?://)[^)]*\\)");

    /** Matches 3+ consecutive newlines — collapsed to 2. */
    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\\n{3,}");

    private final Map<IntegrationKind, FeedbackChannel> channels;

    PullRequestCommentPoster(List<FeedbackChannel> feedbackChannels) {
        EnumMap<IntegrationKind, FeedbackChannel> map = new EnumMap<>(IntegrationKind.class);
        for (FeedbackChannel channel : feedbackChannels) {
            FeedbackChannel previous = map.putIfAbsent(channel.kind(), channel);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate FeedbackChannel for kind " +
                        channel.kind() +
                        ": " +
                        previous.getClass().getName() +
                        " conflicts with " +
                        channel.getClass().getName()
                );
            }
        }
        this.channels = map;
    }

    /**
     * Posts a formatted review comment on the PR/MR associated with the given job.
     *
     * @param job           the completed agent job (must have metadata with pr_number, repository_full_name)
     * @param reviewComment the raw review comment from agent output (untrusted)
     * @param summary       optional summary line (untrusted), may be null
     * @return the provider-specific comment ID (for future updates), or null if the sanitized body is empty
     * @throws JobDeliveryException if posting fails
     */
    @Nullable
    String postComment(AgentJob job, String reviewComment, @Nullable String summary) {
        String sanitized = sanitize(reviewComment);
        if (sanitized.isBlank()) {
            log.debug("Review comment was empty after sanitization, skipping post: jobId={}", job.getId());
            return null;
        }
        String formatted = formatComment(sanitized, summary != null ? sanitize(summary) : null, job);
        return postFormattedBody(job, formatted);
    }

    /**
     * Posts a fully formatted body to the PR/MR associated with the given job.
     *
     * @param job           the completed agent job (must have metadata)
     * @param formattedBody the fully formatted comment body (already sanitized)
     * @return the provider-specific comment ID
     * @throws JobDeliveryException if posting fails
     */
    @Nullable
    String postFormattedBody(AgentJob job, String formattedBody) {
        long workspaceId = job.getWorkspace().getId();
        IntegrationKind kind = job.getIntegrationKind();
        if (kind == null) {
            // Loud on purpose: the agent already ran, so a silent skip would report DELIVERED with
            // nothing posted.
            throw new JobDeliveryException(
                "AgentJob.integrationKind is null — cannot resolve a delivery channel. jobId=" + job.getId()
            );
        }
        FeedbackChannel channel = requireChannel(kind);
        FeedbackTarget target = buildTarget(job, kind, workspaceId);
        try {
            SummaryHandle handle = channel.postSummary(
                target,
                new FeedbackContent(formattedBody, summaryMarkerFor(job))
            );
            log.info(
                "Posted feedback comment: jobId={}, kind={}, commentId={}",
                job.getId(),
                kind,
                handle.externalId()
            );
            return handle.externalId();
        } catch (FeedbackDeliveryException e) {
            throw new JobDeliveryException(e.getMessage(), e);
        }
    }

    /**
     * Edits an already-posted summary in place so re-reviews keep one evolving thread. A
     * {@code TRANSIENT} outcome means keep the prior summary: a flaky update must not double-post.
     *
     * @param externalRef the vendor comment id returned by a prior {@link #postFormattedBody}
     */
    UpdateResult updateFormattedBody(AgentJob job, String externalRef, String formattedBody) {
        long workspaceId = job.getWorkspace().getId();
        IntegrationKind kind = job.getIntegrationKind();
        if (kind == null) {
            throw new JobDeliveryException(
                "AgentJob.integrationKind is null — cannot resolve a delivery channel. jobId=" + job.getId()
            );
        }
        FeedbackChannel channel = requireChannel(kind);
        FeedbackTarget target = buildTarget(job, kind, workspaceId);
        FeedbackChannel.UpdateOutcome outcome = channel.updateSummary(
            target,
            externalRef,
            new FeedbackContent(formattedBody, summaryMarkerFor(job))
        );
        return switch (outcome.kind()) {
            case EDITED -> {
                log.info(
                    "Edited feedback summary in place: jobId={}, kind={}, commentId={}",
                    job.getId(),
                    kind,
                    outcome.handle().externalId()
                );
                yield new UpdateResult(UpdateResult.Kind.EDITED, outcome.handle().externalId());
            }
            case GONE -> {
                log.info(
                    "Summary edit found the prior comment gone; will post anew: jobId={}, reason={}",
                    job.getId(),
                    outcome.reason()
                );
                yield new UpdateResult(UpdateResult.Kind.GONE, null);
            }
            case TRANSIENT -> {
                log.warn(
                    "Summary edit hit a transient error; keeping the prior summary, not re-posting: jobId={}, reason={}",
                    job.getId(),
                    outcome.reason()
                );
                yield new UpdateResult(UpdateResult.Kind.TRANSIENT, null);
            }
            case UNSUPPORTED -> {
                log.debug(
                    "Channel {} cannot edit a summary in place; caller will post anew: jobId={}",
                    kind,
                    job.getId()
                );
                yield new UpdateResult(UpdateResult.Kind.UNSUPPORTED, null);
            }
        };
    }

    /** Agent-layer mirror of {@link FeedbackChannel.UpdateOutcome}, carrying only the external id the caller needs. */
    record UpdateResult(Kind kind, @Nullable String externalId) {
        enum Kind {
            EDITED,
            GONE,
            TRANSIENT,
            UNSUPPORTED,
        }
    }

    /** As {@link #postFormattedBody}, but the subject is an issue rather than a merge request. */
    @Nullable
    String postIssueFormattedBody(AgentJob job, String formattedBody) {
        long workspaceId = job.getWorkspace().getId();
        IntegrationKind kind = job.getIntegrationKind();
        if (kind == null) {
            throw new JobDeliveryException(
                "AgentJob.integrationKind is null — cannot resolve a delivery channel. jobId=" + job.getId()
            );
        }
        FeedbackChannel channel = requireChannel(kind);
        JsonNode metadata = job.getMetadata();
        String repoFullName = requireMetadataText(metadata, "repository_full_name");
        int issueNumber = requireMetadataInt(metadata, "issue_number");
        String subjectExternalId;
        try {
            subjectExternalId = channel.formatIssueSubjectId(repoFullName, issueNumber);
        } catch (IllegalArgumentException e) {
            throw new JobDeliveryException(e.getMessage());
        }
        FeedbackTarget target = new FeedbackTarget(
            new IntegrationRef(kind, workspaceId, /* instanceKey */ null),
            subjectExternalId,
            /* resourceUrl */ null
        );
        try {
            SummaryHandle handle = channel.postSummary(
                target,
                new FeedbackContent(formattedBody, summaryMarkerFor(job))
            );
            log.info(
                "Posted issue feedback comment: jobId={}, kind={}, commentId={}",
                job.getId(),
                kind,
                handle.externalId()
            );
            return handle.externalId();
        } catch (FeedbackDeliveryException e) {
            throw new JobDeliveryException(e.getMessage(), e);
        }
    }

    /**
     * Does an already-posted summary comment carrying THIS job's marker exist? Anything that cannot be
     * answered — missing metadata, no channel, a failed lookup — is {@code UNKNOWN}, never "absent".
     */
    ExistingDeliveryLookup findExistingSummaryComment(AgentJob job) {
        IntegrationKind kind = job.getIntegrationKind();
        if (kind == null) {
            return ExistingDeliveryLookup.unknown();
        }
        FeedbackChannel channel = channels.get(kind);
        if (channel == null) {
            return ExistingDeliveryLookup.unknown();
        }
        JsonNode metadata = job.getMetadata();
        if (metadata == null || job.getWorkspace() == null) {
            return ExistingDeliveryLookup.unknown();
        }
        try {
            long workspaceId = job.getWorkspace().getId();
            FeedbackTarget target;
            if (metadata.has("issue_number")) {
                String repoFullName = requireMetadataText(metadata, "repository_full_name");
                int issueNumber = requireMetadataInt(metadata, "issue_number");
                String subjectExternalId = channel.formatIssueSubjectId(repoFullName, issueNumber);
                target = new FeedbackTarget(new IntegrationRef(kind, workspaceId, null), subjectExternalId, null);
            } else if (metadata.has("pr_number")) {
                target = buildTarget(job, kind, workspaceId);
            } else {
                return ExistingDeliveryLookup.unknown();
            }
            FeedbackChannel.ExistingSummaryLookup lookup = channel.findExistingSummary(target, summaryMarkerFor(job));
            return switch (lookup.kind()) {
                case FOUND -> ExistingDeliveryLookup.found(lookup.handle().externalId());
                case ABSENT -> ExistingDeliveryLookup.absent();
                case UNKNOWN -> ExistingDeliveryLookup.unknown();
            };
        } catch (RuntimeException e) {
            log.debug(
                "Existing-summary dedup lookup failed (treated as unknown): jobId={}, error={}",
                job.getId(),
                e.getMessage()
            );
            return ExistingDeliveryLookup.unknown();
        }
    }

    // Vendor dispatch

    private FeedbackChannel requireChannel(IntegrationKind kind) {
        FeedbackChannel channel = channels.get(kind);
        if (channel == null) {
            throw new JobDeliveryException(
                "No FeedbackChannel wired for kind " +
                    kind +
                    " — check that the vendor integration is enabled and its channel bean is registered"
            );
        }
        return channel;
    }

    FeedbackTarget buildTarget(AgentJob job, IntegrationKind kind, long workspaceId) {
        JsonNode metadata = job.getMetadata();
        String repoFullName = requireMetadataText(metadata, "repository_full_name");
        int prNumber = requireMetadataInt(metadata, "pr_number");

        FeedbackChannel channel = requireChannel(kind);
        String subjectExternalId;
        try {
            subjectExternalId = channel.formatPullRequestSubjectId(repoFullName, prNumber);
        } catch (IllegalArgumentException e) {
            throw new JobDeliveryException(e.getMessage());
        }

        // Inline-finding channels anchor to this commit; the summary path leaves it null.
        String resourceUrl = optionalMetadataText(metadata, "commit_sha");

        IntegrationRef ref = new IntegrationRef(kind, workspaceId, /* instanceKey */ null);
        return new FeedbackTarget(ref, subjectExternalId, resourceUrl);
    }

    static String summaryMarkerFor(AgentJob job) {
        return SUMMARY_MARKER_PREFIX + job.getId() + " -->";
    }

    // Sanitization

    /**
     * Sanitizes untrusted agent output for safe inclusion in git provider comments. The order of the
     * steps below is load-bearing — autolinks must survive tag stripping, and stripping must reach a
     * fixed point before the markdown passes run.
     */
    static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String result = raw;

        result = result.replace("\r\n", "\n").replace("\r", "\n");
        result = INVISIBLE_CHARS.matcher(result).replaceAll("");
        result = HTML_COMMENT.matcher(result).replaceAll("");

        // Autolinks become plain links first, so the tag stripping below does not eat them.
        result = AUTOLINK.matcher(result).replaceAll("$1");

        // Loop until stable: one pass would let <scr<script>ipt> reassemble into <script>.
        String prev;
        do {
            prev = result;
            result = HTML_TAG.matcher(result).replaceAll(mr -> {
                String tagName = mr.group(1).toLowerCase(Locale.ROOT);
                if (!SAFE_HTML_TAGS.contains(tagName)) {
                    return "";
                }
                // Reconstructed without attributes, so no onclick/onload survives.
                String full = mr.group();
                boolean isClosing = full.startsWith("</");
                boolean isSelfClosing = full.endsWith("/>");
                if (isClosing) return "</" + tagName + ">";
                if (isSelfClosing) return "<" + tagName + " />";
                return "<" + tagName + ">";
            });
        } while (!result.equals(prev));

        result = MARKDOWN_IMAGE_INLINE.matcher(result).replaceAll("");
        result = MARKDOWN_IMAGE_REF.matcher(result).replaceAll("");
        result = UNSAFE_MARKDOWN_LINK.matcher(result).replaceAll("$1");
        result = AT_MENTION.matcher(result).replaceAll("`@$1`");
        result = APPROVAL_LANGUAGE.matcher(result).replaceAll("");
        result = GITLAB_SLASH_COMMAND.matcher(result).replaceAll("`$1`");
        result = EXCESSIVE_NEWLINES.matcher(result).replaceAll("\n\n");

        result = result.strip();
        if (result.length() > MAX_BODY_LENGTH) {
            result = result.substring(0, MAX_BODY_LENGTH) + "\n\n[... truncated — comment exceeded length limit]";
        }

        return result;
    }

    // Formatting

    /**
     * Formats a sanitized review comment with bot disclaimer, collapsible body, and metadata footer.
     */
    static String formatComment(String sanitizedBody, @Nullable String sanitizedSummary, AgentJob job) {
        var sb = new StringBuilder(sanitizedBody.length() + 512);

        // HTML comment marker for identifying bot comments
        sb.append(SUMMARY_MARKER_PREFIX).append(job.getId()).append(" -->\n");

        // Collapsible review body (cap summary to prevent total comment exceeding provider limits)
        String summaryText;
        if (sanitizedSummary != null && !sanitizedSummary.isBlank()) {
            // sanitize() preserves newlines, but summaryText is embedded in the single-line <summary> element;
            // collapse newlines to spaces first so a multi-line agent summary cannot break out of the header
            // (everything after the first \n would otherwise render outside the collapsible block).
            String oneLine = sanitizedSummary.replace('\n', ' ').strip();
            summaryText =
                oneLine.length() > MAX_SUMMARY_LENGTH ? oneLine.substring(0, MAX_SUMMARY_LENGTH) + "…" : oneLine;
        } else {
            summaryText = "Review details";
        }

        sb.append("<details>\n");
        sb.append("<summary><strong>AI Code Review</strong> — ").append(summaryText).append("</summary>\n\n");
        sb.append(sanitizedBody).append("\n\n");
        sb.append("</details>\n\n");

        sb.append("---\n");
        appendMetadataFooter(sb, job);

        return sb.toString();
    }

    static void appendMetadataFooter(StringBuilder sb, AgentJob job) {
        sb.append("<sub>Hephaestus Agent");

        String modelName = snapshotModelName(job.getConfigSnapshot());
        if (modelName != null && !modelName.isBlank()) {
            sb.append(" &middot; ").append(escapeHtml(modelName));
        }

        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            Duration duration = Duration.between(job.getStartedAt(), job.getCompletedAt());
            sb.append(" &middot; ").append(formatDuration(duration));
        }

        sb.append("</sub>\n");
        sb.append("<sub>AI-generated feedback can be inaccurate. React with 👍 or 👎 to give feedback.</sub>\n");
    }

    /** {@code model_name} is the pre-catalog key; a current snapshot carries {@code upstreamModelId}. */
    @Nullable
    private static String snapshotModelName(@Nullable JsonNode configSnapshot) {
        if (configSnapshot == null) {
            return null;
        }
        String current = optionalMetadataText(configSnapshot, "upstreamModelId");
        return current != null ? current : optionalMetadataText(configSnapshot, "model_name");
    }

    static String formatDuration(Duration duration) {
        long totalSeconds = Math.max(0, duration.toSeconds());
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes + "m " + seconds + "s";
    }

    /** Lightweight HTML escaping for trusted short strings (e.g., model names). */
    static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // Metadata helpers

    static String requireMetadataText(@Nullable JsonNode metadata, String field) {
        if (metadata == null) {
            throw new JobDeliveryException("Missing required metadata field: " + field);
        }
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull()) {
            throw new JobDeliveryException("Missing required metadata field: " + field);
        }
        return node.asString();
    }

    @Nullable
    static String optionalMetadataText(@Nullable JsonNode metadata, String field) {
        if (metadata == null) {
            return null;
        }
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asString();
    }

    static int requireMetadataInt(@Nullable JsonNode metadata, String field) {
        if (metadata == null) {
            throw new JobDeliveryException("Missing required metadata field: " + field);
        }
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull()) {
            throw new JobDeliveryException("Missing required metadata field: " + field);
        }
        if (!node.isNumber()) {
            throw new JobDeliveryException(
                "Expected numeric metadata field '" + field + "', got: " + node.getNodeType()
            );
        }
        return node.asInt();
    }
}
