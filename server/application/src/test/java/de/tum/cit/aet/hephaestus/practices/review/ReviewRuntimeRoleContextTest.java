package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.framework.RegisteredItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.MonitoredRepositoryQuery;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultAdmissionPolicy;
import de.tum.cit.aet.hephaestus.practices.result.ResultCommunicationRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultMarkRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultPresentation;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustodyQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewScopeRepositoryRepository;
import de.tum.cit.aet.hephaestus.workspace.spi.MembershipReviewSelection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class ReviewRuntimeRoleContextTest extends BaseUnitTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(
                    ReviewSnapshotQueryService.class,
                    PracticeReviewCoverageService.class,
                    ResultAdmissionPolicy.class,
                    ResultViewQuery.class,
                    ResultPresentation.class,
                    ResultVisibilityPolicy.class,
                    ProductReferenceQuery.class)
            .withBean(WorkspaceRepository.class, () -> mock(WorkspaceRepository.class))
            .withBean(RepositoryToMonitorRepository.class, () -> mock(RepositoryToMonitorRepository.class))
            .withBean(ReviewScopeRepositoryRepository.class, () -> mock(ReviewScopeRepositoryRepository.class))
            .withBean(MonitoredRepositoryQuery.class, () -> mock(MonitoredRepositoryQuery.class))
            .withBean(MembershipReviewSelection.class, () -> mock(MembershipReviewSelection.class))
            .withBean(HistoryCustodyQuery.class, () -> mock(HistoryCustodyQuery.class))
            .withBean(PracticeRepository.class, () -> mock(PracticeRepository.class))
            .withBean(ResultMarkRepository.class, () -> mock(ResultMarkRepository.class))
            .withBean(ProblemRepository.class, () -> mock(ProblemRepository.class))
            .withBean(EvidenceAuthorization.class, () -> mock(EvidenceAuthorization.class))
            .withBean(ProductItemRepository.class, () -> mock(ProductItemRepository.class))
            .withBean(RegisteredItemLabels.class, () -> mock(RegisteredItemLabels.class))
            .withBean(ItemDescriptorRegistry.class, () -> mock(ItemDescriptorRegistry.class))
            .withBean(ResultCommunicationRepository.class, () -> mock(ResultCommunicationRepository.class))
            .withBean(ReviewRepository.class, () -> mock(ReviewRepository.class))
            .withBean(ReviewPracticeRepository.class, () -> mock(ReviewPracticeRepository.class))
            .withBean(PracticeVersionRepository.class, () -> mock(PracticeVersionRepository.class))
            .withBean(ProductItemQuery.class, () -> mock(ProductItemQuery.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

    @Test
    void shouldSupplyFrozenReadsOnTheWorkerWithoutTheServerCommandService() {
        context.withPropertyValues(
                        "hephaestus.runtime.server.enabled=false",
                        "hephaestus.runtime.worker.enabled=true",
                        "hephaestus.runtime.webhook.enabled=false")
                .run(app -> {
                    assertThat(app).hasSingleBean(ReviewSnapshotQuery.class);
                    assertThat(app).hasSingleBean(ResultViewQuery.class);
                    assertThat(app).hasSingleBean(ResultPresentation.class);
                    assertThat(app).hasSingleBean(ResultVisibilityPolicy.class);
                    assertThat(app).hasSingleBean(ProductReferenceQuery.class);
                    assertThat(app).hasSingleBean(ResultAdmissionPolicy.class);
                    assertThat(app).doesNotHaveBean(PracticeReviewCoverageService.class);
                    assertThat(app).doesNotHaveBean(ReviewServiceImpl.class);
                });
    }

    @Test
    void shouldSupplyFrozenReadsOnTheServerWithoutAWorker() {
        context.withPropertyValues(
                        "hephaestus.runtime.server.enabled=true",
                        "hephaestus.runtime.worker.enabled=false",
                        "hephaestus.runtime.webhook.enabled=false")
                .run(app -> {
                    assertThat(app).hasSingleBean(ReviewSnapshotQuery.class);
                    assertThat(app).hasSingleBean(PracticeReviewCoverageService.class);
                    assertThat(app).hasSingleBean(ResultAdmissionPolicy.class);
                });
    }

    @Test
    void shouldExcludeProductPreparationFromTheWebhookOnlyRole() {
        context.withPropertyValues(
                        "hephaestus.runtime.server.enabled=false",
                        "hephaestus.runtime.worker.enabled=false",
                        "hephaestus.runtime.webhook.enabled=true")
                .run(app -> {
                    assertThat(app).doesNotHaveBean(PracticeReviewCoverageService.class);
                    assertThat(app).doesNotHaveBean(ResultAdmissionPolicy.class);
                    assertThat(app).doesNotHaveBean(ReviewSnapshotQuery.class);
                    assertThat(app).doesNotHaveBean(ResultViewQuery.class);
                    assertThat(app).doesNotHaveBean(ResultPresentation.class);
                    assertThat(app).doesNotHaveBean(ResultVisibilityPolicy.class);
                    assertThat(app).doesNotHaveBean(ProductReferenceQuery.class);
                });
    }
}
