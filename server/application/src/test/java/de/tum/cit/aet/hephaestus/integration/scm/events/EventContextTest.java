package de.tum.cit.aet.hephaestus.integration.scm.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.ProcessingContext;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventContextTest extends BaseUnitTest {
    @Test
    void shouldKeepTheProcessingReadTimeWhenTheEventIsPublishedLater() {
        var at = Instant.parse("2026-10-14T12:00:00Z");
        var context = mock(ProcessingContext.class);
        when(context.observedAt()).thenReturn(at);
        when(context.source()).thenReturn(DataSource.GRAPHQL_SYNC);
        when(context.correlationId()).thenReturn("read");
        assertThat(EventContext.from(context).occurredAt()).isEqualTo(at);
    }

    @Test
    void shouldUseTheSuppliedSyncTimeAndProviderIdentity() {
        var at = Instant.parse("2026-10-14T12:00:00Z");
        var repository = new RepositoryRef(1L, "owner/project", "main");
        var event = EventContext.forSync(2L, repository, IdentityProviderType.GITLAB, at);
        assertThat(event.occurredAt()).isEqualTo(at);
        assertThat(event.repository()).isEqualTo(repository);
        assertThat(event.providerType()).isEqualTo(IdentityProviderType.GITLAB);
        assertThat(event.source()).isEqualTo(DataSource.GRAPHQL_SYNC);
    }
}
