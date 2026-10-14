package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackBasisRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@Tag("unit")
class FeedbackDeliveryRoleContextTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    FeedbackLedgerRecorder.class, FeedbackResponseSuppressionFilter.class,
                    RepeatedSummaryCheck.class, ConversationFeedbackTtlPurger.class);

    @ParameterizedTest
    @EnumSource(
            value = RuntimeRole.class,
            names = {"WORKER", "WEBHOOK"})
    void shouldBootWithoutFeedbackPersistenceWhenServerRoleIsDisabled(RuntimeRole role) {
        runner.withPropertyValues(RuntimeRole.SERVER_PROPERTY + "=false", role.property() + "=true")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(FeedbackLedgerRecorder.class)
                        .doesNotHaveBean(FeedbackResponseSuppressionFilter.class)
                        .doesNotHaveBean(RepeatedSummaryCheck.class)
                        .doesNotHaveBean(ConversationFeedbackTtlPurger.class)
                        .doesNotHaveBean(FeedbackRepository.class));
    }

    @Test
    void shouldCreateFeedbackPersistenceHelpersWhenServerRoleUsesItsRepositories() {
        runner.withBean(FeedbackRepository.class, () -> mock(FeedbackRepository.class))
                .withBean(FeedbackBasisRepository.class, () -> mock(FeedbackBasisRepository.class))
                .withBean(FeedbackCopyRepository.class, () -> mock(FeedbackCopyRepository.class))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(FeedbackLedgerRecorder.class)
                        .hasSingleBean(FeedbackResponseSuppressionFilter.class)
                        .hasSingleBean(RepeatedSummaryCheck.class)
                        .hasSingleBean(ConversationFeedbackTtlPurger.class));
    }
}
