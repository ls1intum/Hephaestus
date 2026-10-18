package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeedbackPublicationContextTest extends BaseUnitTest {
    private final FeedbackPublicationRepository rows = mock(FeedbackPublicationRepository.class);
    private final FeedbackPublicationContext publication =
            new FeedbackPublicationContext(rows, mock(ReviewRepository.class));

    @ParameterizedTest
    @ValueSource(strings = {"99999999999999999999", "7,9223372036854775808"})
    void shouldRefuseAsChangedHistoryWhenTheContextNamesAnIdOutsideTheAccountIdRange(String developers) {
        String context = "v1:" + developers + ":" + "a".repeat(64);
        assertThat(publication.refusal(1, 2, UUID.randomUUID(), 7, context))
                .contains(FeedbackStopReason.PUBLIC_HISTORY_CHANGED);
        verifyNoInteractions(rows);
    }
}
