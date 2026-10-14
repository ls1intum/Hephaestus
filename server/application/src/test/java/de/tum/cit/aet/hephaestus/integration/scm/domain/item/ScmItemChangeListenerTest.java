package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultStore;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScmItemChangeListenerTest extends BaseUnitTest {
    @Test
    void shouldEndOnlyExplicitWorkspaceItemsOnIssueClosure() {
        var query = mock(ScmItemSourceQuery.class);
        var results = mock(ResultStore.class);
        var item = mock(ScmItemSourceQuery.WorkspaceItem.class);
        when(item.getWorkspaceId()).thenReturn(11L);
        when(item.getItemId()).thenReturn(22L);
        when(query.issueItems(33L)).thenReturn(List.of(item));
        var issue = mock(ScmEventPayload.IssueData.class);
        when(issue.id()).thenReturn(33L);
        var event = mock(ScmDomainEvent.IssueClosed.class);
        when(event.issue()).thenReturn(issue);
        new ScmItemChangeListener(query, results).onClosed(event);
        verify(results).endCurrent(eq(11L), eq(22L), eq(ResultEndReason.WORK_CHANGED), any());
    }
}
