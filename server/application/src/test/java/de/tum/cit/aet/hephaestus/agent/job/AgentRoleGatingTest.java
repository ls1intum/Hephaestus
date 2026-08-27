package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.settings.InstanceSettingsService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class AgentRoleGatingTest extends BaseUnitTest {

    @Test
    void shouldRequireAgentAndWorkerRolesWhenExecutingJobs() {
        ConditionalOnExpression condition = AgentJobExecutor.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(condition.value())
                .contains(RuntimeRole.AGENT_ENABLED_PROPERTY)
                .contains(RuntimeRole.WORKER_PROPERTY);
    }

    @Test
    void shouldEnableSubmissionComponentsFromAgentCapabilityAlone() {
        assertAgentEnabledCondition(AgentJobEventListener.class);
        assertAgentEnabledCondition(IssueAgentJobEventListener.class);
        assertAgentEnabledCondition(BotCommandProcessor.class);
    }

    @Test
    void shouldRequireServerRoleWhenRecoveringJobs() {
        assertThat(AgentJobZombieSweeper.class.isAnnotationPresent(ConditionalOnServerRole.class))
                .isTrue();
        assertAgentEnabledCondition(AgentJobZombieSweeper.class);
    }

    @Test
    void shouldAllowSettingsServiceWithoutServerOnlyAuditLogger() {
        assertThat(InstanceSettingsService.class.isAnnotationPresent(ConditionalOnServerRole.class))
                .isFalse();
        assertThat(InstanceSettingsService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getGenericParameterTypes())
                        .anySatisfy(type -> {
                            assertThat(type).isInstanceOf(ParameterizedType.class);
                            ParameterizedType parameterized = (ParameterizedType) type;
                            assertThat(parameterized.getRawType()).isEqualTo(Optional.class);
                            assertThat(parameterized.getActualTypeArguments()).containsExactly(AuthEventLogger.class);
                        }));
    }

    private void assertAgentEnabledCondition(Class<?> type) {
        ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.prefix()).isEqualTo("hephaestus.agent");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }
}
