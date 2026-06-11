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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.json.JsonMapper;

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
            new PracticeCatalogInjector(objectMapper, practiceRepository),
            workspaceContextBuilder,
            envelopeWriter,
            gitDiffOperations,
            parser,
            deliveryService,
            feedbackService,
            new SecretDiffScanner()
        );
    }

    private JobTypeHandler issueReviewHandler() {
        var parser = new PracticeDetectionResultParser(objectMapper);
        var envelopeWriter = new TaskEnvelopeWriter(objectMapper);
        return new IssueReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            envelopeWriter,
            new PracticeCatalogInjector(objectMapper, practiceRepository),
            parser,
            deliveryService
        );
    }

    /** A registry with the full handler set (every {@link AgentJobType} mapped). */
    private JobTypeHandlerRegistry fullRegistry() {
        return new JobTypeHandlerRegistry(List.of(prReviewHandler(), issueReviewHandler()));
    }

    @Nested
    class Construction {

        @Test
        void shouldIndexHandlersByJobType() {
            var pr = prReviewHandler();
            var issue = issueReviewHandler();
            var registry = new JobTypeHandlerRegistry(List.of(pr, issue));

            assertThat(registry.getHandler(AgentJobType.PULL_REQUEST_REVIEW)).isSameAs(pr);
            assertThat(registry.getHandler(AgentJobType.ISSUE_REVIEW)).isSameAs(issue);
        }

        @Test
        void shouldThrowOnDuplicateHandler() {
            var handler1 = prReviewHandler();
            var handler2 = prReviewHandler();

            assertThatThrownBy(() -> new JobTypeHandlerRegistry(List.of(handler1, handler2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("PULL_REQUEST_REVIEW");
        }

        @Test
        void shouldThrowOnMissingHandler() {
            assertThatThrownBy(() -> new JobTypeHandlerRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No JobTypeHandler registered for")
                .hasMessageContaining("PULL_REQUEST_REVIEW");
        }
    }

    @Nested
    class GetHandler {

        @Test
        void shouldRejectNullJobType() {
            var registry = fullRegistry();
            assertThatThrownBy(() -> registry.getHandler(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
