package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PreviousPracticePageFeedback;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

@Tag("unit")
class PracticePageSupportReaderTest {

    @Test
    void shouldReadOnlyRecentAuthorizedSupport() {
        var results = mock(ResultRepository.class);
        var evidence = mock(EvidenceAuthorization.class);
        Instant now = Instant.parse("2026-10-14T00:00:00Z");
        var recent = Result.builder()
                .id(UUID.randomUUID())
                .recordedAt(now)
                .outcome(ResultOutcome.NOT_MET)
                .build();
        var denied = Result.builder()
                .id(UUID.randomUUID())
                .recordedAt(now)
                .outcome(ResultOutcome.NOT_MET)
                .build();
        var old = Result.builder()
                .id(UUID.randomUUID())
                .recordedAt(now.minus(Duration.ofDays(91)))
                .build();
        when(results.findCurrentForDeveloper(eq(1L), eq(2L), eq(ResultMarkKind.INCORRECT), any()))
                .thenReturn(new PageImpl<>(List.of(recent, denied, old)));
        when(evidence.permitsForNewDelivery(eq(1L), any(), any())).thenReturn(Set.of(recent.getId()));
        assertThat(new PracticePageSupportReader(
                                results,
                                evidence,
                                Clock.fixed(now, ZoneOffset.UTC),
                                mock(PreviousPracticePageFeedback.class),
                                mock(PracticeRepository.class))
                        .read(1, 2))
                .containsExactly(recent);
    }
}
