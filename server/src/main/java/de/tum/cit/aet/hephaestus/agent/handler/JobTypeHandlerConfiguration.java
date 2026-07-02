package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.account.UserPreferencesRepository;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.context.providers.GitDiffOperations;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationTrendService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers all {@link JobTypeHandler} beans and the {@link JobTypeHandlerRegistry}.
 *
 * <p>This class is in the dependency chain of {@link WorkspaceContextBuilder} (via any
 * {@code ContentSource} it produces and consumes). Beans produced here that are needed
 * by a {@code ContentSource} must be declared as top-level {@code @Component}s instead,
 * otherwise a circular dependency forms.
 */
@Configuration
public class JobTypeHandlerConfiguration {

    private final JsonMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;
    private final PracticeReviewProperties reviewProperties;
    private final WorkspaceContextBuilder workspaceContextBuilder;
    private final TaskEnvelopeWriter taskEnvelopeWriter;
    private final ReactionSuppressionFilter reactionSuppressionFilter;

    JobTypeHandlerConfiguration(
        JsonMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PracticeReviewProperties reviewProperties,
        WorkspaceContextBuilder workspaceContextBuilder,
        TaskEnvelopeWriter taskEnvelopeWriter,
        ReactionSuppressionFilter reactionSuppressionFilter
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.reviewProperties = reviewProperties;
        this.workspaceContextBuilder = workspaceContextBuilder;
        this.taskEnvelopeWriter = taskEnvelopeWriter;
        this.reactionSuppressionFilter = reactionSuppressionFilter;
    }

    @Bean
    PracticeDetectionResultParser practiceDetectionResultParser() {
        return new PracticeDetectionResultParser(objectMapper);
    }

    @Bean
    PullRequestCommentPoster pullRequestCommentPoster(List<FeedbackChannel> feedbackChannels) {
        return new PullRequestCommentPoster(feedbackChannels);
    }

    @Bean
    DiffNotePoster diffNotePoster(
        PullRequestCommentPoster commentPoster,
        List<InlineFindingChannel> inlineFindingChannels
    ) {
        return new DiffNotePoster(commentPoster, inlineFindingChannels);
    }

    @Bean
    FeedbackDeliveryService feedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        UserPreferencesRepository userPreferencesRepository,
        PullRequestRepository pullRequestRepository,
        WorkspaceRepository workspaceRepository,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        ObservationTrendService observationTrendService
    ) {
        return new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            userPreferencesRepository,
            pullRequestRepository,
            workspaceRepository,
            reviewProperties,
            feedbackLedgerRecorder,
            observationTrendService
        );
    }

    @Bean
    PracticeCatalogInjector practiceCatalogInjector(PracticeRepository practiceRepository) {
        return new PracticeCatalogInjector(objectMapper, practiceRepository);
    }

    @Bean
    SecretDiffScanner secretDiffScanner() {
        return new SecretDiffScanner();
    }

    @Bean
    JobTypeHandler pullRequestReviewHandler(
        PracticeCatalogInjector practiceCatalogInjector,
        GitDiffOperations gitDiffOperations,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        FeedbackDeliveryService feedbackService,
        SecretDiffScanner secretDiffScanner
    ) {
        return new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            practiceCatalogInjector,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            gitDiffOperations,
            resultParser,
            deliveryService,
            feedbackService,
            secretDiffScanner,
            reactionSuppressionFilter
        );
    }

    @Bean
    JobTypeHandler issueReviewHandler(
        PracticeCatalogInjector practiceCatalogInjector,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        PullRequestCommentPoster commentPoster,
        FeedbackLedgerRecorder feedbackLedgerRecorder
    ) {
        return new IssueReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            practiceCatalogInjector,
            resultParser,
            deliveryService,
            commentPoster,
            feedbackLedgerRecorder
        );
    }

    @Bean
    JobTypeHandlerRegistry jobTypeHandlerRegistry(List<JobTypeHandler> handlers) {
        return new JobTypeHandlerRegistry(handlers);
    }
}
