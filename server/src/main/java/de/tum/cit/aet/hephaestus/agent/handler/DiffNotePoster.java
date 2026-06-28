package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Posts inline diff notes on PRs/MRs by dispatching to the per-vendor
 * {@link InlineFindingChannel}. Sanitization and DiffNote→InlineFinding mapping
 * live here; the GraphQL calls live in the channel under
 * {@code integration/<kind>/feedback/}.
 */
class DiffNotePoster {

    private static final Logger log = LoggerFactory.getLogger(DiffNotePoster.class);

    /** Invisible marker appended to diff note bodies to identify hephaestus-posted notes. */
    static final String HEPHAESTUS_MARKER = "<!-- hephaestus-diff-note -->";

    private final PullRequestCommentPoster commentPoster;
    private final Map<IntegrationKind, InlineFindingChannel> channels;

    DiffNotePoster(PullRequestCommentPoster commentPoster, List<InlineFindingChannel> inlineFindingChannels) {
        this.commentPoster = commentPoster;
        EnumMap<IntegrationKind, InlineFindingChannel> map = new EnumMap<>(IntegrationKind.class);
        for (InlineFindingChannel channel : inlineFindingChannels) {
            InlineFindingChannel previous = map.putIfAbsent(channel.kind(), channel);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate InlineFindingChannel for kind " +
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
     * Posts diff notes for the given job by routing to the per-kind
     * {@link InlineFindingChannel}.
     *
     * @param job       the completed agent job (must have metadata with repository info)
     * @param diffNotes the sanitized diff notes to post
     * @return result with posted/failed counts
     */
    DiffNoteResult reconcileInlineNotes(AgentJob job, List<DiffNote> diffNotes) {
        IntegrationKind kind = Objects.requireNonNull(
            job.getIntegrationKind(),
            "AgentJob.integrationKind must not be null"
        );
        InlineFindingChannel channel = channels.get(kind);
        if (channel == null) {
            throw new JobDeliveryException(
                "No InlineFindingChannel wired for kind " +
                    kind +
                    " — check that the vendor integration is enabled and its channel bean is registered"
            );
        }

        FeedbackChannel.FeedbackTarget target = commentPoster.buildTarget(job, kind, job.getWorkspace().getId());

        List<InlineFindingChannel.InlineFinding> findings = mapFindings(diffNotes == null ? List.of() : diffNotes);

        // Zero-note re-run: nothing to reconcile against, so clear this run's prior inline notes outright —
        // the empty-diff pathology where a re-reviewed PR keeps line-numbered notes on code no longer in the
        // diff. When there ARE findings we DON'T clear-then-post; postInlineFindings reconciles by correlation
        // key (edit-in-place / preserve-human / delete-truly-gone), so a stable finding keeps its one thread
        // instead of being destroyed and re-created every run. On GitHub (append-only threads) clearStaleFindings
        // does not delete — it minimizes the vanished threads as OUTDATED — so it is NOT a no-op there.
        if (findings.isEmpty()) {
            try {
                channel.clearStaleFindings(target, HEPHAESTUS_MARKER);
            } catch (RuntimeException e) {
                log.warn(
                    "Stale inline-note clear failed (best-effort), continuing: kind={}, jobId={}, error={}",
                    kind,
                    job.getId(),
                    e.getMessage()
                );
            }
            return new DiffNoteResult(0, 0, List.of());
        }

        try {
            InlineFindingChannel.InlineResult result = channel.postInlineFindings(target, findings);
            log.debug(
                "Inline finding delivery: kind={}, posted={}, failed={}, jobId={}",
                kind,
                result.posted(),
                result.failed(),
                job.getId()
            );
            // Surface the per-finding DeliveredSignals so the ledger recorder can persist each placement's
            // external_ref / thread_external_ref / posted_state instead of hardcoding POSTED + null. Channels
            // that cannot reconcile per-thread (GitHub) report empty signals and the placement stays anchor-only.
            return new DiffNoteResult(result.posted(), result.failed(), result.signals());
        } catch (FeedbackDeliveryException e) {
            throw new JobDeliveryException(e.getMessage(), e);
        }
    }

    private static List<InlineFindingChannel.InlineFinding> mapFindings(List<DiffNote> diffNotes) {
        List<InlineFindingChannel.InlineFinding> findings = new ArrayList<>(diffNotes.size());
        for (DiffNote note : diffNotes) {
            String sanitized = PullRequestCommentPoster.sanitize(note.body());
            if (sanitized.isBlank()) {
                continue;
            }
            // DiffNote uses (startLine, endLine) where endLine==null means single-line.
            // DiffAnchor uses (newLineNumber, startLine) where startLine==null means single-line:
            //   newLineNumber is the END line that the annotation attaches to, and startLine
            //   the (optional) range start. For single-line notes, both shapes converge.
            boolean isMultiLine = note.endLine() != null && note.endLine() > note.startLine();
            FindingAnchor.DiffAnchor anchor = isMultiLine
                ? new FindingAnchor.DiffAnchor(note.filePath(), note.endLine(), note.startLine())
                : new FindingAnchor.DiffAnchor(note.filePath(), note.startLine(), null);
            findings.add(
                new InlineFindingChannel.InlineFinding(anchor, sanitized, HEPHAESTUS_MARKER, note.recurrenceKey())
            );
        }
        return findings;
    }

    /**
     * Outcome of an inline-note reconcile. {@code signals} carries the per-finding
     * {@link InlineFindingChannel.DeliveredSignal}s so the caller can persist each placement's durable
     * handle; it is empty for the zero-note clear path and for channels that cannot reconcile per-thread.
     */
    record DiffNoteResult(int posted, int failed, List<InlineFindingChannel.DeliveredSignal> signals) {}
}
