package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliverySuppressedException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressSuppressedException;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryChannel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reconciles sanitized inline observations through the provider-specific channel. */
class DiffNotePoster {

    private static final Logger log = LoggerFactory.getLogger(DiffNotePoster.class);

    /** Invisible marker appended to diff note bodies to identify hephaestus-posted notes. */
    static final String HEPHAESTUS_MARKER = "<!-- hephaestus-diff-note -->";

    private final PullRequestCommentPoster commentPoster;
    private final PracticeFeedbackCommentFormatter commentFormatter;
    private final Map<IntegrationKind, InlineFeedbackChannel> channels;

    DiffNotePoster(
        PullRequestCommentPoster commentPoster,
        PracticeFeedbackCommentFormatter commentFormatter,
        List<InlineFeedbackChannel> inlineFeedbackChannels
    ) {
        this.commentPoster = commentPoster;
        this.commentFormatter = commentFormatter;
        EnumMap<IntegrationKind, InlineFeedbackChannel> map = new EnumMap<>(IntegrationKind.class);
        for (InlineFeedbackChannel channel : inlineFeedbackChannels) {
            InlineFeedbackChannel previous = map.putIfAbsent(channel.kind(), channel);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate InlineFeedbackChannel for kind " +
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

    DiffNoteResult reconcileInlineNotes(AgentJob job, List<DiffNote> diffNotes) {
        IntegrationKind kind = Objects.requireNonNull(
            job.getIntegrationKind(),
            "AgentJob.integrationKind must not be null"
        );
        InlineFeedbackChannel channel = channels.get(kind);
        if (channel == null) {
            throw new JobDeliveryException(
                "No InlineFeedbackChannel wired for kind " +
                    kind +
                    " — check that the vendor integration is enabled and its channel bean is registered"
            );
        }

        SummaryChannel.FeedbackTarget target = commentPoster.buildTarget(job, kind, job.getWorkspace().getId());

        List<InlineFeedbackChannel.InlineFeedback> observations = mapObservations(
            diffNotes == null ? List.of() : diffNotes
        );

        // An empty reconcile clears stale notes; non-empty channels reconcile by recurrence key.
        if (observations.isEmpty()) {
            try {
                channel.clearStaleFeedback(target, HEPHAESTUS_MARKER);
            } catch (OutboundEgressSuppressedException e) {
                throw new JobDeliverySuppressedException(e.getMessage(), e);
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
            InlineFeedbackChannel.InlineResult result = channel.postInlineFeedback(target, observations);
            log.debug(
                "Inline observation delivery: kind={}, posted={}, failed={}, jobId={}",
                kind,
                result.posted(),
                result.failed(),
                job.getId()
            );
            return new DiffNoteResult(
                result.posted(),
                result.failed(),
                result.signals(),
                result.suppressed(),
                result.suppressedRecurrenceKeys()
            );
        } catch (OutboundEgressSuppressedException e) {
            throw new JobDeliverySuppressedException(e.getMessage(), e);
        } catch (FeedbackDeliveryException e) {
            throw new JobDeliveryException(e.getMessage(), e);
        }
    }

    private List<InlineFeedbackChannel.InlineFeedback> mapObservations(List<DiffNote> diffNotes) {
        List<InlineFeedbackChannel.InlineFeedback> observations = new ArrayList<>(diffNotes.size());
        for (DiffNote note : diffNotes) {
            String sanitized = PullRequestCommentPoster.sanitize(note.body());
            if (sanitized.isBlank()) {
                continue;
            }
            // A multi-line DiffAnchor stores the end first and optional range start second.
            boolean isMultiLine = note.endLine() != null && note.endLine() > note.startLine();
            FeedbackAnchor.DiffAnchor anchor = isMultiLine
                ? new FeedbackAnchor.DiffAnchor(note.filePath(), note.endLine(), note.startLine())
                : new FeedbackAnchor.DiffAnchor(note.filePath(), note.startLine(), null);
            observations.add(
                new InlineFeedbackChannel.InlineFeedback(
                    anchor,
                    commentFormatter.appendSettingsNotice(sanitized),
                    HEPHAESTUS_MARKER,
                    note.recurrenceKey()
                )
            );
        }
        return observations;
    }

    record DiffNoteResult(
        int posted,
        int failed,
        List<InlineFeedbackChannel.DeliveredSignal> signals,
        boolean suppressed,
        List<String> suppressedRecurrenceKeys
    ) {
        DiffNoteResult(int posted, int failed, List<InlineFeedbackChannel.DeliveredSignal> signals) {
            this(posted, failed, signals, false, List.of());
        }
    }
}
