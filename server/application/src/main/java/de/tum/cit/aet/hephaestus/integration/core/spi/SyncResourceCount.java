package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One entity class mirrored within a {@link SyncResourceState} — a repository's issues, pull requests,
 * comments, reviews or commits; a Slack channel's messages; an Outline collection's documents.
 *
 * <p>Why this exists: a resource's headline {@link SyncResourceState#itemCount()} is a single number, so
 * a sync that silently stops moving one entity class while the others keep running is invisible — the
 * number keeps going up and every badge stays green. A per-class count plus a per-class
 * {@link #lastSyncedAt()} is what makes "pull requests still sync but comments stopped three days ago"
 * legible.
 *
 * <p>Providers report exactly the classes they actually sync — an SCM repository expands to five or six,
 * a Slack channel to one. Nothing is padded into a uniform shape: a class that an integration does not
 * mirror is simply absent, and a class whose freshness is not tracked reports {@code lastSyncedAt =
 * null} rather than borrowing a sibling's timestamp.
 *
 * @param key          stable machine token for this class ({@code issues}, {@code pullRequests},
 *                     {@code issueComments}, {@code reviews}, {@code reviewComments}, {@code commits},
 *                     {@code messages}, {@code documents}) — the UI's key for icons, ordering and i18n
 * @param label        human-readable display name
 * @param count        mirrored row count for this class
 * @param lastSyncedAt when this class was last synced, if the integration persists a per-class
 *                     watermark; {@code null} means "not tracked", never "never synced"
 */
public record SyncResourceCount(
    @NonNull String key,
    @NonNull String label,
    long count,
    @Nullable Instant lastSyncedAt
) {
    public static final String KEY_ISSUES = "issues";
    public static final String KEY_PULL_REQUESTS = "pullRequests";
    public static final String KEY_ISSUE_COMMENTS = "issueComments";
    public static final String KEY_REVIEWS = "reviews";
    public static final String KEY_REVIEW_COMMENTS = "reviewComments";
    public static final String KEY_COMMITS = "commits";
    public static final String KEY_MESSAGES = "messages";
    public static final String KEY_DOCUMENTS = "documents";
}
