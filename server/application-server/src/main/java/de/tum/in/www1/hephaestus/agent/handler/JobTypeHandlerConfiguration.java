package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.account.UserPreferencesRepository;
import de.tum.in.www1.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.in.www1.hephaestus.agent.context.providers.GitDiffOperations;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.ContributorHistoryProvider;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * Registers all {@link JobTypeHandler} beans and the {@link JobTypeHandlerRegistry}.
 */
@Configuration
public class JobTypeHandlerConfiguration {

    private final ObjectMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;
    private final PracticeFindingRepository practiceFindingRepository;
    private final PracticeReviewProperties reviewProperties;
    private final WorkspaceContextBuilder workspaceContextBuilder;
    private final TaskEnvelopeWriter taskEnvelopeWriter;

    JobTypeHandlerConfiguration(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PracticeFindingRepository practiceFindingRepository,
        PracticeReviewProperties reviewProperties,
        WorkspaceContextBuilder workspaceContextBuilder,
        TaskEnvelopeWriter taskEnvelopeWriter
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.practiceFindingRepository = practiceFindingRepository;
        this.reviewProperties = reviewProperties;
        this.workspaceContextBuilder = workspaceContextBuilder;
        this.taskEnvelopeWriter = taskEnvelopeWriter;
    }

    @Bean
    public PracticeDetectionResultParser practiceDetectionResultParser() {
        return new PracticeDetectionResultParser(objectMapper);
    }

    @Bean
    ContributorHistoryProvider contributorHistoryProvider() {
        return new ContributorHistoryProvider(practiceFindingRepository, objectMapper);
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
    DiffNotePoster diffNotePoster(
        PullRequestCommentPoster commentPoster,
        GitHubGraphQlClientProvider gitHubProvider,
        @Nullable GitLabGraphQlClientProvider gitLabProvider,
        WorkspaceRepository workspaceRepository
    ) {
        return new DiffNotePoster(commentPoster, gitHubProvider, gitLabProvider, workspaceRepository);
    }

    @Bean
    FeedbackDeliveryService feedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        UserPreferencesRepository userPreferencesRepository,
        PullRequestRepository pullRequestRepository
    ) {
        return new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            userPreferencesRepository,
            pullRequestRepository,
            reviewProperties
        );
    }

    @Bean
    public JobTypeHandler pullRequestReviewHandler(
        PracticeRepository practiceRepository,
        GitDiffOperations gitDiffOperations,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        FeedbackDeliveryService feedbackService
    ) {
        return new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            practiceRepository,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            gitDiffOperations,
            resultParser,
            deliveryService,
            feedbackService
        );
    }

    @Bean
    public JobTypeHandlerRegistry jobTypeHandlerRegistry(List<JobTypeHandler> handlers) {
        return new JobTypeHandlerRegistry(handlers);
    }
}
