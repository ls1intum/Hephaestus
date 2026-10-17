package de.tum.cit.aet.hephaestus.integration.scm.feedback;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackNotSentException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNoteWriter;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScmLineNotePosterTest extends BaseUnitTest {
    @Test
    void shouldRefuseAmbiguousProviderWritersBeforeAnyCreate() {
        var first = mock(LineNoteWriter.class);
        var second = mock(LineNoteWriter.class);
        when(first.kind()).thenReturn(IntegrationKind.GITHUB);
        when(second.kind()).thenReturn(IntegrationKind.GITHUB);
        var poster = new ScmLineNotePoster(List.of(first, second));
        var target = ScmFeedbackFixtures.target(
                new IntegrationRef(IntegrationKind.GITHUB, 1L, null), "owner/repo#1", "b".repeat(40));
        assertThatThrownBy(() -> poster.post(target, List.of(), (attempting, completed) -> {
                    throw new AssertionError("Ambiguous writer reached the fence");
                }))
                .isInstanceOf(FeedbackNotSentException.class);
    }
}
