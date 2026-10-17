package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyRecorder;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryCopiesTest extends BaseUnitTest {
    @Test
    void unavailableAuthorityNeverRecordsCopiedWords() {
        var recorder = mock(PersonDataCopyRecorder.class);
        assertThat(new HistoryCopies(recorder)
                        .record(new HistoryCopy(false, List.of(), List.of(1L), List.of(UUID.randomUUID()))))
                .isFalse();
        verifyNoInteractions(recorder);
    }

    @Test
    void copiesExactRetainedRepositoriesAndDependencies() {
        var recorder = mock(PersonDataCopyRecorder.class);
        var source = UUID.randomUUID();
        assertThat(new HistoryCopies(recorder).record(new HistoryCopy(true, List.of(), List.of(7L), List.of(source))))
                .isTrue();
        verify(recorder).recordRepository(7);
        verify(recorder).recordCopiedJob(source);
    }
}
