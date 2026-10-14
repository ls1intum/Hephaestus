package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataCopyRecorder;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.ExactPersonIdentityResolver;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationVerifier;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class ScmCaptureRoleContextTest extends BaseUnitTest {
    @ParameterizedTest
    @CsvSource({"true,false,false,5", "false,true,false,5", "false,false,true,0"})
    void shouldRegisterSourcesOnlyWhereReviewInputsArePrepared(
            boolean server, boolean worker, boolean webhook, int expected) {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        ScmItemSourceConfiguration.class,
                        ScmCitationVerifier.class,
                        ScmCaptureConsent.class,
                        ExactPersonIdentityResolver.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(ScmItemService.class, () -> mock(ScmItemService.class))
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(AccountDirectory.class, () -> mock(AccountDirectory.class))
                .withBean(PersonDataCopyRecorder.class, () -> mock(PersonDataCopyRecorder.class))
                .withBean(PersonProcessingSuppression.class, () -> mock(PersonProcessingSuppression.class))
                .withPropertyValues(
                        RuntimeRole.SERVER_PROPERTY + "=" + server,
                        RuntimeRole.WORKER_PROPERTY + "=" + worker,
                        RuntimeRole.WEBHOOK_PROPERTY + "=" + webhook)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(ItemSource.class)).hasSize(expected);
                    assertThat(context.getBeansOfType(CitationVerifier.class)).hasSize(expected == 0 ? 0 : 1);
                    assertThat(context.getBeansOfType(ScmCaptureConsent.class)).hasSize(expected == 0 ? 0 : 1);
                    assertThat(context.getBeansOfType(ExactPersonIdentityResolver.class))
                            .hasSize(expected == 0 ? 0 : 1);
                    if (expected > 0)
                        assertThat(context.getBeansOfType(ItemSource.class).values())
                                .extracting(ItemSource::kind)
                                .containsExactlyInAnyOrder(
                                        ScmItemEvents.ISSUE,
                                        ScmItemEvents.PULL_REQUEST,
                                        ScmItemEvents.PULL_REQUEST_REVIEW,
                                        ScmItemEvents.REPOSITORY,
                                        ScmItemEvents.FILE);
                });
    }
}
