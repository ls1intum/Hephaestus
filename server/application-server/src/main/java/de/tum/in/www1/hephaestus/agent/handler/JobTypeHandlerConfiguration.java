package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.account.UserPreferencesRepository;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.ContributorHistoryProvider;
import de.tum.in.www1.hephaestus.practices.finding.PracticeDetectionProperties;
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

    @Nullable
    private final de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenService gitLabTokenService;

    JobTypeHandlerConfiguration(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PracticeFindingRepository practiceFindingRepository,
        PracticeReviewProperties reviewProperties,
        @org.springframework.beans.factory.annotation.Autowired(
            required = false
        ) @Nullable de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenService gitLabTokenService
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.practiceFindingRepository = practiceFindingRepository;
        this.reviewProperties = reviewProperties;
        this.gitLabTokenService = gitLabTokenService;
    }

    @Bean
    public PracticeDetectionResultParser practiceDetectionResultParser(PracticeDetectionProperties properties) {
        return new PracticeDetectionResultParser(objectMapper, properties.maxFindingsPerJob());
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
        PullRequestRepository pullRequestRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        PracticeRepository practiceRepository,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        FeedbackDeliveryService feedbackService
    ) {
        return new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            pullRequestRepository,
            reviewCommentRepository,
            practiceRepository,
            contributorHistoryProvider(),
            resultParser,
            deliveryService,
            feedbackService,
            gitLabTokenService
        );
    }

    @Bean
    public JobTypeHandlerRegistry jobTypeHandlerRegistry(List<JobTypeHandler> handlers) {
        return new JobTypeHandlerRegistry(handlers);
    }
}
