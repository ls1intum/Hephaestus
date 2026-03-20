package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeDetectionProperties;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * Registers all {@link JobTypeHandler} beans and the {@link JobTypeHandlerRegistry}.
 *
 * <p>Handlers are always available — they are pure domain logic with no infrastructure
 * dependencies beyond repository access. Unlike the sandbox subsystem (conditional on
 * {@code hephaestus.sandbox.enabled}), handler beans are unconditionally created. Follows the
 * same pattern as
 * {@link de.tum.in.www1.hephaestus.agent.adapter.AgentAdapterConfiguration}.
 */
@Configuration
public class JobTypeHandlerConfiguration {

    private final ObjectMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;

    JobTypeHandlerConfiguration(ObjectMapper objectMapper, GitRepositoryManager gitRepositoryManager) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
    }

    @Bean
    public PracticeDetectionResultParser practiceDetectionResultParser(PracticeDetectionProperties properties) {
        return new PracticeDetectionResultParser(objectMapper, properties.maxFindingsPerJob());
    }

    @Bean
    PullRequestCommentPoster pullRequestCommentPoster(
        GitHubGraphQlClientProvider gitHubProvider,
        @Nullable GitLabGraphQlClientProvider gitLabProvider,
        WorkspaceRepository workspaceRepository
    ) {
        return new PullRequestCommentPoster(gitHubProvider, gitLabProvider, workspaceRepository);
    }

    @Bean
    public JobTypeHandler pullRequestReviewHandler(
        PullRequestRepository pullRequestRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        PracticeRepository practiceRepository,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        PullRequestCommentPoster commentPoster
    ) {
        return new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            pullRequestRepository,
            reviewCommentRepository,
            practiceRepository,
            resultParser,
            deliveryService,
            commentPoster
        );
    }

    @Bean
    public JobTypeHandlerRegistry jobTypeHandlerRegistry(List<JobTypeHandler> handlers) {
        return new JobTypeHandlerRegistry(handlers);
    }
}
