package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JobTypeHandlerRegistry")
class JobTypeHandlerRegistryTest extends BaseUnitTest {

    private JobTypeHandler createMockHandler(AgentJobType type) {
        JobTypeHandler handler = mock(JobTypeHandler.class);
        when(handler.jobType()).thenReturn(type);
        return handler;
    }

    private List<JobTypeHandler> allHandlers() {
        // Create one mock handler for each AgentJobType value
        return java.util.Arrays.stream(AgentJobType.values()).map(this::createMockHandler).toList();
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should index handlers by job type")
        void shouldIndexHandlersByJobType() {
            var handlers = allHandlers();
            var registry = new JobTypeHandlerRegistry(handlers);

            for (AgentJobType type : AgentJobType.values()) {
                assertThat(registry.getHandler(type)).isNotNull();
                assertThat(registry.getHandler(type).jobType()).isEqualTo(type);
            }
        }

        @Test
        @DisplayName("should throw on duplicate handler for same type")
        void shouldThrowOnDuplicateHandler() {
            var handler1 = createMockHandler(AgentJobType.PULL_REQUEST_REVIEW);
            var handler2 = createMockHandler(AgentJobType.PULL_REQUEST_REVIEW);

            assertThatThrownBy(() -> new JobTypeHandlerRegistry(List.of(handler1, handler2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
        }

        @Test
        @DisplayName("should throw when job type has no handler")
        void shouldThrowOnMissingHandler() {
            assertThatThrownBy(() -> new JobTypeHandlerRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No JobTypeHandler registered for");
        }
    }

    @Nested
    @DisplayName("getHandler")
    class GetHandler {

        @Test
        @DisplayName("should return correct handler for each type")
        void shouldReturnCorrectHandlerForEachType() {
            var handlers = allHandlers();
            var registry = new JobTypeHandlerRegistry(handlers);

            for (AgentJobType type : AgentJobType.values()) {
                JobTypeHandler handler = registry.getHandler(type);
                assertThat(handler).isNotNull();
                assertThat(handler.jobType()).isEqualTo(type);
            }
        }

        @Test
        @DisplayName("should reject null job type")
        void shouldRejectNullJobType() {
            var registry = new JobTypeHandlerRegistry(allHandlers());
            assertThatThrownBy(() -> registry.getHandler(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
