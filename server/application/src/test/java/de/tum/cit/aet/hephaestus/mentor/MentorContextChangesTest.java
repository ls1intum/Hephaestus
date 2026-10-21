package de.tum.cit.aet.hephaestus.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.event.AgentContextChangedEvent;
import de.tum.cit.aet.hephaestus.core.event.AiChoiceChangedEvent;
import de.tum.cit.aet.hephaestus.core.event.RepositoryMonitoringChangedEvent;
import de.tum.cit.aet.hephaestus.core.event.ScmMirrorErasedEvent;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MentorContextChangesTest extends BaseUnitTest {

    @Test
    void shouldTellTheWorkersOfTheWorkspaceWhenWhatItsTurnsMayReadChanges() {
        List<Object> published = new ArrayList<>();
        var changes = new MentorContextChanges(published::add, mock(MentorConversationRepository.class));

        changes.visibilityChanged(new AgentContextChangedEvent(1));
        changes.monitoringChanged(new RepositoryMonitoringChangedEvent(2, List.of(5L)));
        changes.mirrorErased(new ScmMirrorErasedEvent(3));

        assertThat(published)
                .containsExactly(
                        new MentorLedgerEvents.ContextChanged(1),
                        new MentorLedgerEvents.ContextChanged(2),
                        new MentorLedgerEvents.ContextChanged(3));
    }

    @Test
    void shouldTellEveryWorkspaceOfTheAccountWhenItsAiChoiceChanges() {
        List<Object> published = new ArrayList<>();
        MentorConversationRepository conversations = mock(MentorConversationRepository.class);
        when(conversations.findMemberWorkspaceIds(9L)).thenReturn(List.of(4L, 5L));

        new MentorContextChanges(published::add, conversations).aiChoiceChanged(new AiChoiceChangedEvent(9));

        assertThat(published)
                .containsExactly(new MentorLedgerEvents.ContextChanged(4), new MentorLedgerEvents.ContextChanged(5));
    }
}
