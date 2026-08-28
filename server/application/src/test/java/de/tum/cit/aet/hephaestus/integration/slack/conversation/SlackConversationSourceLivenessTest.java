package de.tum.cit.aet.hephaestus.integration.slack.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class SlackConversationSourceLivenessTest extends BaseUnitTest {

    @Mock
    private SlackThreadRepository threadRepository;

    @Test
    void delegatesExactThreadValidation() {
        when(threadRepository.existsDeliverableThread(77L, 1L, "C123", "1700000000.100000", 789L))
                .thenReturn(true);
        var liveness = new SlackConversationSourceLiveness(threadRepository);

        boolean result = liveness.isDeliverableThread(1L, 77L, "C123", "1700000000.100000", 789L);

        assertThat(result).isTrue();
        verify(threadRepository).existsDeliverableThread(77L, 1L, "C123", "1700000000.100000", 789L);
    }
}
