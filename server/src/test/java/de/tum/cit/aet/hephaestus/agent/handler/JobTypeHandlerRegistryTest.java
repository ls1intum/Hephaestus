package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.GitDiffOperations;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("JobTypeHandlerRegistry")
class JobTypeHandlerRegistryTest extends BaseUnitTest {

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceContextBuilder workspaceContextBuilder;

    @Mock
    private GitDiffOperations gitDiffOperations;

    @Mock
    private PracticeDetectionDeliveryService deliveryService;

    @Mock
    private FeedbackDeliveryService feedbackService;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private JobTypeHandler prReviewHandler() {
        var parser = new PracticeDetectionResultParser(objectMapper);
        var envelopeWriter = new TaskEnvelopeWriter(objectMapper);
        return new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            practiceRepository,
            workspaceContextBuilder,
            envelopeWriter,
            gitDiffOperations,
            parser,
            deliveryService,
            feedbackService
        );
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should index handlers by job type")
        void shouldIndexHandlersByJobType() {
            var handler = prReviewHandler();
            var registry = new JobTypeHandlerRegistry(List.of(handler));

            assertThat(registry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).isSameAs(handler);
        }

        @Test
        @DisplayName("should throw on duplicate handler for same type")
        void shouldThrowOnDuplicateHandler() {
            var handler1 = prReviewHandler();
            var handler2 = prReviewHandler();

            assertThatThrownBy(() -> new JobTypeHandlerRegistry(List.of(handler1, handler2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("PULL_REQUEST_REVIEW");
        }

        @Test
        @DisplayName("should throw when job type has no handler")
        void shouldThrowOnMissingHandler() {
            assertThatThrownBy(() -> new JobTypeHandlerRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No JobTypeHandler registered for")
                .hasMessageContaining("PULL_REQUEST_REVIEW");
        }
    }

    @Nested
    @DisplayName("getHandler")
    class GetHandler {

        @Test
        @DisplayName("should reject null job type")
        void shouldRejectNullJobType() {
            var registry = new JobTypeHandlerRegistry(List.of(prReviewHandler()));
            assertThatThrownBy(() -> registry.getHandler(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
