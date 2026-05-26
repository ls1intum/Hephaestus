package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel.FeedbackContent;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel.SummaryHandle;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
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

    /** Marker appended to summary posts so {@code FeedbackPostService} can locate and edit them. */
    static final String SUMMARY_MARKER_PREFIX = "<!-- hephaestus-agent-feedback:";

    // ── Sanitization patterns ──

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
        IntegrationKind kind = Objects.requireNonNull(
            job.getIntegrationKind(),
            "AgentJob.integrationKind must not be null"
        );
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

    // ── Vendor dispatch ──

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

        // Vendor-specific subject formatting lives on the per-kind FeedbackChannel —
        // see FeedbackChannel.formatPullRequestSubjectId.
        FeedbackChannel channel = requireChannel(kind);
        String subjectExternalId;
        try {
            subjectExternalId = channel.formatPullRequestSubjectId(repoFullName, prNumber);
        } catch (IllegalArgumentException e) {
            throw new JobDeliveryException(e.getMessage());
        }

        // Resource URL conveys the head commit SHA for inline-finding channels that
        // need to anchor the review to a specific commit; null/absent for the summary path.
        String resourceUrl = optionalMetadataText(metadata, "commit_sha");

        IntegrationRef ref = new IntegrationRef(kind, workspaceId, /* instanceKey */ null);
        return new FeedbackTarget(ref, subjectExternalId, resourceUrl);
    }

    private static String summaryMarkerFor(AgentJob job) {
        return SUMMARY_MARKER_PREFIX + job.getId() + " -->";
    }

    // ── Sanitization ──

    /**
     * Sanitizes untrusted agent output for safe inclusion in git provider comments.
     *
     * <p>Applied transformations (order matters):
     * <ol>
     *   <li>Normalize newlines ({@code \r\n} → {@code \n})</li>
     *   <li>Strip invisible Unicode characters (bidi controls, zero-width spaces, BOM)</li>
     *   <li>Strip HTML comments (hidden instruction injection prevention)</li>
     *   <li>Protect markdown autolinks ({@code <https://...>}) from tag stripping</li>
     *   <li>Allowlist HTML tags; strip all attributes from allowed tags (multi-pass)</li>
     *   <li>Strip all markdown images — inline and reference-style (tracking pixel prevention)</li>
     *   <li>Backtick-escape @mentions (notification spam prevention)</li>
     *   <li>Remove standalone approval language ("LGTM", "approved", etc.)</li>
     *   <li>Escape GitLab slash commands (/approve, /merge, /close, etc.)</li>
     *   <li>Collapse excessive newlines</li>
     *   <li>Truncate to {@link #MAX_BODY_LENGTH} characters</li>
     * </ol>
     */
    static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        String result = raw;

        // 1. Normalize newlines
        result = result.replace("\r\n", "\n").replace("\r", "\n");

        // 2. Strip invisible characters (bidi controls, zero-width spaces, BOM)
        result = INVISIBLE_CHARS.matcher(result).replaceAll("");

        // 3. Strip HTML comments (prevents hidden instructions for downstream AI tools)
        result = HTML_COMMENT.matcher(result).replaceAll("");

        // 3b. Protect markdown autolinks (<https://...>) by converting to standard links
        //     before HTML tag stripping would incorrectly remove them
        result = AUTOLINK.matcher(result).replaceAll("$1");

        // 4. Allowlist HTML tags; strip attributes from allowed tags, remove all others.
        //    Loop until stable to prevent nested-tag reconstruction attacks
        //    (e.g., <scr<script>ipt> → <script> after one pass).
        String prev;
        do {
            prev = result;
            result = HTML_TAG.matcher(result).replaceAll(mr -> {
                String tagName = mr.group(1).toLowerCase(Locale.ROOT);
                if (!SAFE_HTML_TAGS.contains(tagName)) {
                    return "";
                }
                // Reconstruct tag without attributes (prevents onclick, onload, etc.)
                String full = mr.group();
                boolean isClosing = full.startsWith("</");
                boolean isSelfClosing = full.endsWith("/>");
                if (isClosing) return "</" + tagName + ">";
                if (isSelfClosing) return "<" + tagName + " />";
                return "<" + tagName + ">";
            });
        } while (!result.equals(prev));

        // 5. Strip all markdown images (inline and reference-style)
        result = MARKDOWN_IMAGE_INLINE.matcher(result).replaceAll("");
        result = MARKDOWN_IMAGE_REF.matcher(result).replaceAll("");

        // 5b. Strip markdown links with unsafe URL schemes (javascript:, data:, etc.)
        //     Keeps the display text, removes only the link target.
        result = UNSAFE_MARKDOWN_LINK.matcher(result).replaceAll("$1");

        // 6. Backtick-escape @mentions
        result = AT_MENTION.matcher(result).replaceAll("`@$1`");

        // 7. Remove approval language (tolerates trailing punctuation)
        result = APPROVAL_LANGUAGE.matcher(result).replaceAll("");

        // 8. Escape GitLab slash commands (/approve, /merge, /close, etc.)
        //    Prefix with backtick to render as inline code instead of being executed
        result = GITLAB_SLASH_COMMAND.matcher(result).replaceAll("`$1`");

        // 9. Collapse excessive newlines
        result = EXCESSIVE_NEWLINES.matcher(result).replaceAll("\n\n");

        // 10. Truncate
        result = result.strip();
        if (result.length() > MAX_BODY_LENGTH) {
            result = result.substring(0, MAX_BODY_LENGTH) + "\n\n[... truncated — comment exceeded length limit]";
        }

        return result;
    }

    // ── Formatting ──

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
            summaryText =
                sanitizedSummary.length() > MAX_SUMMARY_LENGTH
                    ? sanitizedSummary.substring(0, MAX_SUMMARY_LENGTH) + "…"
                    : sanitizedSummary;
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

    /** Appends the metadata footer (agent name, model, duration, disclaimer) to a comment being built. */
    static void appendMetadataFooter(StringBuilder sb, AgentJob job) {
        sb.append("<sub>Hephaestus Agent");

        JsonNode configSnapshot = job.getConfigSnapshot();
        if (configSnapshot != null && configSnapshot.has("model_name")) {
            String modelName = configSnapshot.get("model_name").asText();
            if (!modelName.isBlank()) {
                sb.append(" &middot; ").append(escapeHtml(modelName));
            }
        }

        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            Duration duration = Duration.between(job.getStartedAt(), job.getCompletedAt());
            sb.append(" &middot; ").append(formatDuration(duration));
        }

        sb.append("</sub>\n");
        sb.append("<sub>AI-generated feedback can be inaccurate. React with 👍 or 👎 to give feedback.</sub>\n");
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

    // ── Metadata helpers ──

    static String requireMetadataText(@Nullable JsonNode metadata, String field) {
        if (metadata == null) {
            throw new JobDeliveryException("Missing required metadata field: " + field);
        }
        JsonNode node = metadata.get(field);
        if (node == null || node.isNull()) {
            throw new JobDeliveryException("Missing required metadata field: " + field);
        }
        return node.asText();
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
        return node.asText();
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
