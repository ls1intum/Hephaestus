package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabels;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultMarkRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultPresentation;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

class ReviewRuntimeRoleContextTest extends BaseUnitTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(
                    ReviewSnapshotQueryService.class,
                    ResultViewQuery.class,
                    ResultPresentation.class,
                    ResultVisibilityPolicy.class,
                    ProductReferenceQuery.class)
            .withBean(PracticeRepository.class, () -> mock(PracticeRepository.class))
            .withBean(ResultMarkRepository.class, () -> mock(ResultMarkRepository.class))
            .withBean(ProblemRepository.class, () -> mock(ProblemRepository.class))
            .withBean(EvidenceAuthorization.class, () -> mock(EvidenceAuthorization.class))
            .withBean(AccountIdentityQuery.class, () -> mock(AccountIdentityQuery.class))
            .withBean(GitProviderRegistry.class, () -> mock(GitProviderRegistry.class))
            .withBean(ItemLabels.class, () -> mock(ItemLabels.class))
            .withBean(ItemCatalog.class, () -> mock(ItemCatalog.class))
            .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
            .withBean(ReviewRepository.class, () -> mock(ReviewRepository.class))
            .withBean(ReviewPracticeRepository.class, () -> mock(ReviewPracticeRepository.class))
            .withBean(PracticeVersionRepository.class, () -> mock(PracticeVersionRepository.class))
            .withBean(ProductItemQuery.class, () -> mock(ProductItemQuery.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

    @Test
    void shouldSupplyFrozenReadsOnTheWorkerWithoutTheServerCommandService() {
        context.withPropertyValues("hephaestus.runtime.server.enabled=false", "hephaestus.runtime.worker.enabled=true")
                .run(app -> {
                    assertThat(app).hasSingleBean(ReviewSnapshotQuery.class);
                    assertThat(app).hasSingleBean(ResultViewQuery.class);
                    assertThat(app).hasSingleBean(ResultPresentation.class);
                    assertThat(app).hasSingleBean(ResultVisibilityPolicy.class);
                    assertThat(app).hasSingleBean(ProductReferenceQuery.class);
                    assertThat(app).doesNotHaveBean(ReviewServiceImpl.class);
                });
    }

    @Test
    void shouldSupplyFrozenReadsOnTheServerWithoutAWorker() {
        context.withPropertyValues("hephaestus.runtime.server.enabled=true", "hephaestus.runtime.worker.enabled=false")
                .run(app -> assertThat(app).hasSingleBean(ReviewSnapshotQuery.class));
    }

    @Test
    void shouldExcludeProductPreparationFromTheWebhookOnlyRole() {
        context.withPropertyValues(
                        "hephaestus.runtime.server.enabled=false",
                        "hephaestus.runtime.worker.enabled=false",
                        "hephaestus.runtime.webhook.enabled=true")
                .run(app -> {
                    assertThat(app).doesNotHaveBean(ReviewSnapshotQuery.class);
                    assertThat(app).doesNotHaveBean(ResultViewQuery.class);
                    assertThat(app).doesNotHaveBean(ResultPresentation.class);
                    assertThat(app).doesNotHaveBean(ResultVisibilityPolicy.class);
                    assertThat(app).doesNotHaveBean(ProductReferenceQuery.class);
                });
    }
}
