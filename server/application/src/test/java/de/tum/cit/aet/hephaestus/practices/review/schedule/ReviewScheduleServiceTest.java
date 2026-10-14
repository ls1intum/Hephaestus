package de.tum.cit.aet.hephaestus.practices.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.review.ProductReferenceQuery;
import de.tum.cit.aet.hephaestus.practices.review.schedule.dto.ReviewScheduleInputDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCadence;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

class ReviewScheduleServiceTest extends BaseUnitTest {
    final ReviewScheduleRepository schedules = mock(ReviewScheduleRepository.class);
    final ReviewBatchService batches = mock(ReviewBatchService.class);
    final ProductReferenceQuery references = mock(ProductReferenceQuery.class);
    final ConfigAuditPort audit = mock(ConfigAuditPort.class);
    final ReviewScheduleService service =
            new ReviewScheduleService(schedules, batches, references, audit, Clock.systemUTC(), mock(JdbcClient.class));
    final WorkspaceContext context = new WorkspaceContext(1L, "ws", "Workspace", AccountType.ORG, null, Set.of());

    @Test
    void repositoryRequiresBudgetAndNoLookback() {
        assertThatThrownBy(() -> service.create(
                        context, new ReviewScheduleInputDTO("scm.repository", ReviewCadence.DAILY, 1, true, "1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(
                        context, new ReviewScheduleInputDTO("scm.repository", ReviewCadence.DAILY, null, true, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void budgetBoundsAndPrecisionAreValidated() {
        for (String budget : List.of("0", "100000.0001", "0.12345", "-1"))
            assertThatThrownBy(() -> service.create(
                            context,
                            new ReviewScheduleInputDTO("scm.repository", ReviewCadence.DAILY, null, true, budget)))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datedWorkTakesLookbackWithoutScanBudget() {
        assertThatThrownBy(() -> service.create(
                        context, new ReviewScheduleInputDTO("scm.pull_request", ReviewCadence.DAILY, 1, true, "1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(
                        context, new ReviewScheduleInputDTO("scm.pull_request", ReviewCadence.DAILY, 3, true, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(
                        context, new ReviewScheduleInputDTO("scm.issue", ReviewCadence.WEEKLY, 8, true, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleEnabledUpdateDoesNotChangeSchedule() {
        var s = ReviewSchedule.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .kind("scm.pull_request")
                .version(2)
                .enabled(true)
                .build();
        when(schedules.findByIdAndWorkspaceId(s.getId(), 1)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.enabled(context, s.getId(), false, EntityTagPrecondition.parse("\"1\"")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(s.isEnabled()).isTrue();
    }

    @Test
    void foreignScheduleIsNotFound() {
        var id = UUID.randomUUID();
        when(schedules.findByIdAndWorkspaceId(id, 1)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.require(1, id)).isInstanceOf(EntityNotFoundException.class);
    }
}
