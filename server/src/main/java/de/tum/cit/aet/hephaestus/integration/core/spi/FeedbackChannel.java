package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Objects;
import java.util.Optional;

/**
 * Root feedback SPI — every kind that declares {@link Capability#FEEDBACK_DELIVERY}
 * implements this. {@link InlineFindingChannel} and {@link ApprovalChannel} are
 * separate capability-gated SPIs.
 *
 * <p>Vendor-specific subject formatting (e.g. GitHub {@code owner/repo#42} vs
 * GitLab {@code group/project!42}) lives behind {@link #formatPullRequestSubjectId} so
 * the agent module never branches on {@link IntegrationKind}.
 */
public interface FeedbackChannel {
    IntegrationKind kind();

    SummaryHandle postSummary(FeedbackTarget target, FeedbackContent content);

    /**
     * Edit an already-posted summary <em>in place</em> (ADR 0021 re-review UX): the persistent overview
     * comment is updated rather than re-posted, so a re-reviewed PR/MR keeps ONE evolving summary thread
     * instead of accumulating one comment per run (the Qodo {@code persistent_comment} / CodeRabbit model).
     * {@code externalId} is the handle a prior {@link #postSummary} returned.
     *
     * <p>Returns a typed {@link UpdateOutcome} rather than throwing for recoverable cases, so the caller can
     * tell apart: {@code EDITED} (success), {@code GONE} (the prior comment is confirmed deleted — re-post),
     * {@code TRANSIENT} (a rate-limit / network / unknown vendor error — keep the prior summary, do NOT
     * re-post this run, else a flaky update double-posts a second summary), and {@code UNSUPPORTED}
     * (append-only channel — re-post). A genuine data error (e.g. a blank external id) still throws
     * {@link FeedbackDeliveryException}.
     */
    default UpdateOutcome updateSummary(FeedbackTarget target, String externalId, FeedbackContent content) {
        return UpdateOutcome.unsupported();
    }

    /**
     * Best-effort dedup lookup (#1368 hardening — delivery-recovery crash window): search the target's
     * existing comments for one carrying {@code marker} (the invisible HTML-comment marker every summary
     * post embeds, e.g. {@code PullRequestCommentPoster.SUMMARY_MARKER_PREFIX + jobId}), so a
     * delivery-recovery retry can record the already-posted comment's id instead of posting a duplicate.
     *
     * <p><b>Tri-state, not a boolean (#1368 fix wave, finding #6).</b> A prior {@link Optional}-based
     * signature collapsed "confirmed absent" and "could not determine" into the same empty value — every
     * lookup failure (rate limit, transport error, GraphQL error) or unsupported channel silently fell
     * through to "proceed and post", which is only safe for a CONFIRMED absence. On an actual error, that
     * meant every crash-then-recover cycle risked a duplicate post instead of erring toward "leave it
     * PENDING and try again later". {@link ExistingSummaryLookup.Kind#FOUND} is the only signal a caller
     * should record-and-skip-posting on; {@link ExistingSummaryLookup.Kind#ABSENT} is the only signal a
     * caller should proceed to post on; {@link ExistingSummaryLookup.Kind#UNKNOWN} means neither — the
     * caller must leave the delivery {@code PENDING} for a later recovery attempt (and, once the attempt
     * cap is exhausted, fail the delivery with a clear message rather than guess).
     *
     * <p>Default {@code UNKNOWN} — a channel implements {@code FOUND}/{@code ABSENT} only when a
     * cheap-enough existing-listing query is available AND can distinguish "searched everything, nothing
     * matched" from "could not search". A channel with no such query (e.g. {@code GitlabFeedbackChannel},
     * documented on the class) stays {@code UNKNOWN} forever — its delivery-recovery sweep therefore never
     * auto-reposts, only ever records a found match or exhausts its attempt cap. Deliberately NOT called
     * on the normal (non-recovery) delivery path — it costs an extra provider call, which is only worth
     * paying when a crash has already put a job's delivery status in doubt.
     */
    default ExistingSummaryLookup findExistingSummary(FeedbackTarget target, String marker) {
        return ExistingSummaryLookup.unknown();
    }

    /**
     * Format the vendor's external identifier for a pull request / merge request. GitHub
     * uses {@code repoFullName#prNumber}; GitLab uses {@code repoFullName!prNumber};
     * future kinds add their own. The {@code subjectExternalId} stored on the
     * vendor post id is recorded as a {@code FeedbackPlacement.external_ref} (ADR 0021 C6).
     *
     * @throws IllegalArgumentException if {@code repoFullName} is not well-formed for the
     *     vendor (e.g. GitHub's two-segment {@code owner/repo} requirement).
     */
    String formatPullRequestSubjectId(String repoFullName, int prNumber);

    /**
     * Format the vendor's external identifier for an issue. Both GitHub and GitLab address issues as
     * {@code repoFullName#issueNumber}; the GitLab channel routes a {@code #}-suffixed subject to the
     * issue note path (vs {@code !} for a merge request). Default mirrors that convention; a vendor with
     * a different scheme overrides.
     */
    default String formatIssueSubjectId(String repoFullName, int issueNumber) {
        if (repoFullName == null || repoFullName.isBlank()) {
            throw new IllegalArgumentException("repoFullName is required");
        }
        return repoFullName + "#" + issueNumber;
    }

    /** Hephaestus's typed reference to the subject the feedback attaches to. */
    record FeedbackTarget(IntegrationRef ref, String subjectExternalId, String resourceUrl) {}

    record FeedbackContent(String body, String marker) {}

    /** Vendor-side post identifier recorded on {@code FeedbackPlacement.external_ref} for edit-in-place (ADR 0021 C6). */
    record SummaryHandle(String externalId) {}

    /**
     * Tri-state result of {@link #findExistingSummary} (#1368 fix wave, finding #6) — see that method's
     * javadoc for why this is not a boolean/{@link Optional}.
     */
    record ExistingSummaryLookup(Kind kind, SummaryHandle handle) {
        public enum Kind {
            /** A comment carrying the marker was found. */
            FOUND,
            /** The channel searched everything it can and confirmed no comment carries the marker. */
            ABSENT,
            /** The channel could not determine either way (error, rate limit, or unsupported). */
            UNKNOWN,
        }

        public static ExistingSummaryLookup found(SummaryHandle handle) {
            Objects.requireNonNull(handle, "FOUND outcome requires a SummaryHandle");
            return new ExistingSummaryLookup(Kind.FOUND, handle);
        }

        public static ExistingSummaryLookup absent() {
            return new ExistingSummaryLookup(Kind.ABSENT, null);
        }

        public static ExistingSummaryLookup unknown() {
            return new ExistingSummaryLookup(Kind.UNKNOWN, null);
        }
    }

    /**
     * The outcome of an {@link #updateSummary} attempt. {@code TRANSIENT} is the load-bearing case: the caller
     * must NOT create-fallback on it (that double-posts), only on {@code GONE}/{@code UNSUPPORTED}.
     */
    record UpdateOutcome(Kind kind, SummaryHandle handle, String reason) {
        public enum Kind {
            EDITED,
            GONE,
            TRANSIENT,
            UNSUPPORTED,
        }

        public static UpdateOutcome edited(SummaryHandle handle) {
            // EDITED guarantees a usable handle (the caller dereferences handle().externalId()); a null
            // handle / blank id is a contract bug in an impl — fail at the boundary, not as a downstream NPE.
            Objects.requireNonNull(handle, "EDITED outcome requires a SummaryHandle");
            if (handle.externalId() == null || handle.externalId().isBlank()) {
                throw new IllegalArgumentException("EDITED outcome requires a non-blank externalId");
            }
            return new UpdateOutcome(Kind.EDITED, handle, null);
        }

        /** The prior comment is confirmed gone (a human deleted it) — the caller should re-post. */
        public static UpdateOutcome gone(String reason) {
            return new UpdateOutcome(Kind.GONE, null, reason);
        }

        /** A recoverable failure (rate limit, network, unknown vendor error) — keep the prior summary, do not re-post. */
        public static UpdateOutcome transientFailure(String reason) {
            return new UpdateOutcome(Kind.TRANSIENT, null, reason);
        }

        /** This channel cannot edit in place (append-only) — the caller should re-post. */
        public static UpdateOutcome unsupported() {
            return new UpdateOutcome(Kind.UNSUPPORTED, null, null);
        }
    }
}
