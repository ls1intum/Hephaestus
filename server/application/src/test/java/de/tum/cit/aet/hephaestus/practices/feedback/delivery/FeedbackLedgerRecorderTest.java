package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackBasisRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class FeedbackLedgerRecorderTest {
    @Test
    void shouldSurfaceAConstraintFailureBeforeSendingIsPossible() {
        var feedback = mock(FeedbackRepository.class);
        var basis = mock(FeedbackBasisRepository.class);
        var copies = mock(FeedbackCopyRepository.class);
        var row = Feedback.builder().build();
        doThrow(new IllegalStateException("basis constraint")).when(basis).flush();
        assertThatThrownBy(() -> new FeedbackLedgerRecorder(feedback, basis, copies).record(row, List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("basis constraint");
    }
}
