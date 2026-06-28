package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.InlineFinding;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel.InlineResult;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the inline diff-note poster (DiffNote → InlineFinding mapping + channel dispatch). Couples to
 * A1: a repo-relative anchor path flows through unchanged, and the (startLine, endLine) → (newLineNumber,
 * startLine) anchor swap is verified.
 */
class DiffNotePosterTest extends BaseUnitTest {

    private final PullRequestCommentPoster commentPoster = mock(PullRequestCommentPoster.class);

    private AgentJob gitlabJob() {
        AgentJob job = TestEntities.agentJob();
        Workspace ws = new Workspace();
        ws.setId(1L);
        job.setWorkspace(ws);
        job.setIntegrationKind(IntegrationKind.GITLAB);
        return job;
    }

    private FeedbackTarget target() {
        return new FeedbackTarget(new IntegrationRef(IntegrationKind.GITLAB, 1L, null), "group/project!42", null);
    }

    /** A recording channel that captures the findings it was asked to post (and whether clear was invoked). */
    private static final class RecordingChannel implements InlineFindingChannel {

        List<InlineFinding> posted;
        boolean cleared;
        RuntimeException clearThrows;

        @Override
        public IntegrationKind kind() {
            return IntegrationKind.GITLAB;
        }

        @Override
        public InlineResult postInlineFindings(FeedbackTarget target, List<InlineFinding> findings) {
            this.posted = findings;
            return new InlineResult(findings.size(), 0, List.of());
        }

        @Override
        public void clearStaleFindings(FeedbackTarget target, String marker) {
            this.cleared = true;
            if (clearThrows != null) {
                throw clearThrows;
            }
        }
    }

    private DiffNotePoster poster(RecordingChannel channel) {
        when(commentPoster.buildTarget(any(), eq(IntegrationKind.GITLAB), eq(1L))).thenReturn(target());
        return new DiffNotePoster(commentPoster, List.of(channel));
    }

    @Test
    void multiLineNote_swapsToEndLineAnchorWithRangeStart_andCarriesRecurrenceKey() {
        RecordingChannel channel = new RecordingChannel();
        DiffNotePoster poster = poster(channel);
        // endLine > startLine ⇒ multi-line: the anchor's newLineNumber is the END line, startLine the range start.
        DiffNote multi = new DiffNote("src/A.java", 10, 14, "Fix this range", "ck-multi");

        poster.reconcileInlineNotes(gitlabJob(), List.of(multi));

        assertThat(channel.posted).hasSize(1);
        InlineFinding f = channel.posted.get(0);
        FindingAnchor.DiffAnchor anchor = (FindingAnchor.DiffAnchor) f.anchor();
        assertThat(anchor.filePath()).isEqualTo("src/A.java");
        assertThat(anchor.newLineNumber()).isEqualTo(14); // end line
        assertThat(anchor.startLine()).isEqualTo(10); // range start
        assertThat(f.recurrenceKey()).isEqualTo("ck-multi");
    }

    @Test
    void singleLineNote_hasNoRangeStart() {
        RecordingChannel channel = new RecordingChannel();
        DiffNotePoster poster = poster(channel);
        DiffNote single = new DiffNote("src/A.java", 10, null, "Fix this line", "ck-single");

        poster.reconcileInlineNotes(gitlabJob(), List.of(single));

        FindingAnchor.DiffAnchor anchor = (FindingAnchor.DiffAnchor) channel.posted.get(0).anchor();
        assertThat(anchor.newLineNumber()).isEqualTo(10);
        assertThat(anchor.startLine()).isNull();
    }

    @Test
    void blankBodyNote_isSkipped_andClearsStaleWhenAllBlank() {
        RecordingChannel channel = new RecordingChannel();
        DiffNotePoster poster = poster(channel);
        // A body that sanitizes to blank yields no finding → the all-empty path clears stale notes instead.
        DiffNote blank = new DiffNote("src/A.java", 10, null, "   ", "ck-blank");

        DiffNotePoster.DiffNoteResult result = poster.reconcileInlineNotes(gitlabJob(), List.of(blank));

        assertThat(result.posted()).isZero();
        assertThat(channel.posted).isNull(); // postInlineFindings never invoked
        assertThat(channel.cleared).isTrue(); // stale-clear path taken
    }

    @Test
    void noFindings_swallowsClearFailure_bestEffort() {
        RecordingChannel channel = new RecordingChannel();
        channel.clearThrows = new RuntimeException("gitlab down");
        DiffNotePoster poster = poster(channel);

        assertThatCode(() -> poster.reconcileInlineNotes(gitlabJob(), List.of())).doesNotThrowAnyException();
        assertThat(channel.cleared).isTrue();
    }

    @Test
    void duplicateChannelKind_inConstructor_throws() {
        InlineFindingChannel a = mock(InlineFindingChannel.class);
        InlineFindingChannel b = mock(InlineFindingChannel.class);
        when(a.kind()).thenReturn(IntegrationKind.GITLAB);
        when(b.kind()).thenReturn(IntegrationKind.GITLAB);

        assertThatThrownBy(() -> new DiffNotePoster(commentPoster, List.of(a, b)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate InlineFindingChannel for kind");
    }

    @Test
    void repoRelativeAnchorPath_flowsThroughUnchanged() {
        // A1 coupling: the poster does not mutate the anchor path — the repo-relativisation must already have
        // happened upstream in DeliveryComposer. A repo-relative path arrives and is posted verbatim.
        RecordingChannel channel = new RecordingChannel();
        DiffNotePoster poster = poster(channel);
        DiffNote note = new DiffNote("src/components/Button.tsx", 1, null, "Remove unused import", "ck-1");

        poster.reconcileInlineNotes(gitlabJob(), List.of(note));

        FindingAnchor.DiffAnchor anchor = (FindingAnchor.DiffAnchor) channel.posted.get(0).anchor();
        assertThat(anchor.filePath()).isEqualTo("src/components/Button.tsx");
    }

    @Test
    void postedFindings_reportCounts() {
        RecordingChannel channel = new RecordingChannel();
        DiffNotePoster poster = poster(channel);
        var captor = ArgumentCaptor.forClass(IntegrationKind.class);

        DiffNotePoster.DiffNoteResult result = poster.reconcileInlineNotes(
            gitlabJob(),
            List.of(new DiffNote("src/A.java", 10, null, "real body", "ck-1"))
        );

        assertThat(result.posted()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(commentPoster).buildTarget(any(), captor.capture(), eq(1L));
        assertThat(captor.getValue()).isEqualTo(IntegrationKind.GITLAB);
    }
}
