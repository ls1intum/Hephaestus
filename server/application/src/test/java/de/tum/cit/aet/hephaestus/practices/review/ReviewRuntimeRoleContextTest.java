package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class ReviewRuntimeRoleContextTest extends BaseUnitTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ReviewSnapshotQueryService.class)
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
                .run(app -> assertThat(app).doesNotHaveBean(ReviewSnapshotQuery.class));
    }
}
