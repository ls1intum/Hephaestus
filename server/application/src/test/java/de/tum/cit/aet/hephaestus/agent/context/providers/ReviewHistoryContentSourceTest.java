package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.agent.context.HistoryCopy;
import de.tum.cit.aet.hephaestus.agent.handler.RuntimeReviewTestData;
import de.tum.cit.aet.hephaestus.integration.core.framework.RegisteredItemLabels;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReviewHistoryContentSourceTest extends BaseUnitTest {
    @Test
    void shouldAskForTheFrozenAssignmentAndCopyOnlyVouchedHistory() {
        var results = mock(ResultHistoryQuery.class);
        var copies = mock(HistoryCopies.class);
        var assignment = RuntimeReviewTestData.assignment(5L, null);
        var vouched = mock(ResultHistoryEntry.class);
        var unknown = mock(ResultHistoryEntry.class);
        var actual = new HistoryCopy(true, List.of(), List.of(), List.of());
        var incomplete = new HistoryCopy(false, List.of(), List.of(), List.of());
        when(results.forAssignment(1, assignment.id(), 20)).thenReturn(List.of(vouched, unknown));
        when(copies.of(vouched)).thenReturn(actual);
        when(copies.of(unknown)).thenReturn(incomplete);
        when(copies.record(actual)).thenReturn(true);
        var source = new ReviewHistoryContentSource(
                results, mock(FeedbackHistoryQuery.class), copies, mock(RegisteredItemLabels.class), new JsonMapper());
        assertThat(source.previousResults(assignment)).containsExactly(vouched);
        verify(copies).record(actual);
        verify(copies).record(incomplete);
    }
}
