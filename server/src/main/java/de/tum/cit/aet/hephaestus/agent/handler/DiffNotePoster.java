package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
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
    DiffNoteResult postDiffNotes(AgentJob job, List<DiffNote> diffNotes) {
        if (diffNotes == null || diffNotes.isEmpty()) {
            return new DiffNoteResult(0, 0);
        }

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
        List<InlineFindingChannel.InlineFinding> findings = mapFindings(diffNotes);
        if (findings.isEmpty()) {
            return new DiffNoteResult(0, 0);
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
            return new DiffNoteResult(result.posted(), result.failed());
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
            findings.add(new InlineFindingChannel.InlineFinding(anchor, sanitized, HEPHAESTUS_MARKER));
        }
        return findings;
    }

    record DiffNoteResult(int posted, int failed) {}
}
