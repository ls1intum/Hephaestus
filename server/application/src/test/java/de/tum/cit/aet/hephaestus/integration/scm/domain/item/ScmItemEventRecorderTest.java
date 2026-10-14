package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.itemevent.PracticeReviewRefusalMetrics;
import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.scm.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ScmItemEventRecorderTest extends BaseUnitTest {
    private static final ItemRef ROOT = new ItemRef(1, 2, ScmItemEvents.REPOSITORY, 3L, null, null);
    private static final ItemRef ITEM = new ItemRef(1, 4, ScmItemEvents.PULL_REQUEST, 5L, 2L, null);

    @ParameterizedTest
    @EnumSource(
            value = ItemCaptureCode.class,
            names = {"VERSION_UNAVAILABLE", "SOURCE_NOT_READY"})
    void shouldRetainNativeWorkWithoutRecordingAnUnprovedVersion(ItemCaptureCode code) {
        var fixture = fixture();
        when(fixture.items().pin(ITEM, null)).thenThrow(new ItemCaptureException(code, "Provider read incomplete"));

        fixture.recorder().onPullRequest(event());

        verify(fixture.writer()).restore(1, ITEM.itemId());
        verify(fixture.writer(), never()).record(any());
        verify(fixture.refusals()).recordSubmissionRefusal(code.name());
    }

    @Test
    void shouldPropagateAnUnexpectedSourceFailure() {
        var fixture = fixture();
        when(fixture.items().pin(ITEM, null))
                .thenThrow(new ItemCaptureException(ItemCaptureCode.CAPTURE_FAILED, "Source failure"));

        assertThatThrownBy(() -> fixture.recorder().onPullRequest(event()))
                .isInstanceOf(ItemCaptureException.class)
                .hasMessage("Source failure");
        verify(fixture.writer(), never()).record(any());
        verifyNoInteractions(fixture.refusals());
    }

    @Test
    void shouldRecordTheExactAvailableVersion() {
        var fixture = fixture();
        var event = event();
        var version = new ItemVersion("scm-version", null);
        when(fixture.items().pin(ITEM, null))
                .thenReturn(new ItemSnapshot(
                        ITEM,
                        version,
                        new IntegrationRef(IntegrationKind.GITLAB, 1, null),
                        Map.of(),
                        List.of(),
                        new ItemLabel(4, "Change", "merge request", 7, "owner/repo", null, IntegrationKind.GITLAB)));

        fixture.recorder().onPullRequest(event);

        verify(fixture.writer())
                .record(new ItemEventInput(
                        1,
                        4,
                        ScmItemEvents.PULL_REQUEST_OPENED,
                        version.value(),
                        event.context().occurredAt(),
                        FoundBy.SYNC,
                        null,
                        null));
        verifyNoInteractions(fixture.refusals());
    }

    private Fixture fixture() {
        var writer = mock(ItemWriter.class);
        var items = mock(ScmItemService.class);
        var access = mock(ScmRepositoryAccess.class);
        var repositories = mock(RepositoryRepository.class);
        var queries = mock(ScmItemSourceQuery.class);
        var refusals = mock(PracticeReviewRefusalMetrics.class);
        var repository = new Repository();
        repository.setId(3L);
        repository.setNameWithOwner("owner/repo");
        when(repositories.findById(3L)).thenReturn(Optional.of(repository));
        when(queries.monitoredWorkspaces("owner/repo")).thenReturn(List.of(1L));
        when(writer.upsert(new ItemRegistration(1, ScmItemEvents.REPOSITORY, 3L, null, null)))
                .thenReturn(ROOT);
        when(writer.upsert(new ItemRegistration(1, ScmItemEvents.PULL_REQUEST, 5L, 2L, null)))
                .thenReturn(ITEM);
        return new Fixture(
                new ScmItemEventRecorder(writer, items, access, repositories, queries, refusals),
                writer,
                items,
                refusals);
    }

    private ScmDomainEvent.PullRequestCreated event() {
        var repository = new RepositoryRef(3L, "owner/repo", "main");
        var pull = new ScmEventPayload.PullRequestData(
                5L,
                7,
                "Change",
                null,
                Issue.State.OPEN,
                false,
                false,
                0,
                0,
                0,
                null,
                repository,
                null,
                null,
                null,
                null,
                null,
                null);
        return new ScmDomainEvent.PullRequestCreated(pull, EventContext.forSync(1L, repository));
    }

    private record Fixture(
            ScmItemEventRecorder recorder,
            ItemWriter writer,
            ScmItemService items,
            PracticeReviewRefusalMetrics refusals) {}
}
