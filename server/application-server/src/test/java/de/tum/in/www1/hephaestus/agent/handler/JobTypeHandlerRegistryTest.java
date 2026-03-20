package de.tum.in.www1.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("JobTypeHandlerRegistry")
class JobTypeHandlerRegistryTest extends BaseUnitTest {

    @Mock
    private GitRepositoryManager gitRepositoryManager;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private PullRequestReviewCommentRepository reviewCommentRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeDetectionDeliveryService deliveryService;

    @Mock
    private PullRequestCommentPoster commentPoster;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JobTypeHandler prReviewHandler() {
        var parser = new PracticeDetectionResultParser(objectMapper, 100);
        return new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            pullRequestRepository,
            reviewCommentRepository,
            practiceRepository,
            parser,
            deliveryService,
            commentPoster
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
