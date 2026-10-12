package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.JobEvidenceFiles;
import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryChannel;
import de.tum.cit.aet.hephaestus.integration.scm.ReviewTargetQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionService;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final WorkspaceContextBuilder workspaceContextBuilder;
    private final TaskEnvelopeWriter taskEnvelopeWriter;
    private final FeedbackResponseSuppressionFilter feedbackResponseSuppressionFilter;

    JobTypeHandlerConfiguration(
            JsonMapper objectMapper,
            WorkspaceContextBuilder workspaceContextBuilder,
            TaskEnvelopeWriter taskEnvelopeWriter,
            FeedbackResponseSuppressionFilter feedbackResponseSuppressionFilter) {
        this.objectMapper = objectMapper;
        this.workspaceContextBuilder = workspaceContextBuilder;
        this.taskEnvelopeWriter = taskEnvelopeWriter;
        this.feedbackResponseSuppressionFilter = feedbackResponseSuppressionFilter;
    }

    @Bean
    PullRequestCommentPoster pullRequestCommentPoster(List<SummaryChannel> feedbackChannels) {
        return new PullRequestCommentPoster(feedbackChannels);
    }

    @Bean
    DiffNotePoster diffNotePoster(
            PullRequestCommentPoster commentPoster,
            PracticeFeedbackCommentFormatter commentFormatter,
            List<InlineFeedbackChannel> inlineFeedbackChannels) {
        return new DiffNotePoster(commentPoster, commentFormatter, inlineFeedbackChannels);
    }

    @Bean
    FeedbackDeliveryService feedbackDeliveryService(
            PracticeFeedbackDeliveryPolicy deliveryPolicy,
            FeedbackLedgerRecorder feedbackLedgerRecorder,
            PracticeFeedbackCommentFormatter commentFormatter,
            PracticeFeedbackDispatchService dispatchService,
            AgentJobRepository agentJobRepository,
            PublicReviewCompletionRepository completions) {
        return new FeedbackDeliveryService(
                deliveryPolicy,
                feedbackLedgerRecorder,
                commentFormatter,
                dispatchService,
                agentJobRepository,
                completions);
    }

    /** Only the worker role prepares a review, so its practice script inputs are no bean of their own. */
    @Bean
    PracticeCatalogInjector practiceCatalogInjector(
            PracticeRepository practiceRepository,
            WorkspaceReviewDefaultsProvider workspaceDefaults,
            PracticeResultRepository practiceResultRepository,
            ObservationRepository observationRepository,
            ResultVisibilityPolicy practiceResultVisibility,
            ConversationSourceLiveness conversationLiveness,
            AgentJobRepository agentJobRepository,
            ReviewTargetQuery reviewTargets,
            UserRepository userRepository) {
        return new PracticeCatalogInjector(
                objectMapper,
                practiceRepository,
                workspaceDefaults,
                new PracticeScriptInputs(
                        objectMapper,
                        practiceResultRepository,
                        observationRepository,
                        practiceResultVisibility,
                        conversationLiveness,
                        agentJobRepository,
                        reviewTargets,
                        userRepository));
    }

    @Bean
    PracticeReviewPreparation practiceReviewPreparation(
            PracticeCatalogInjector practiceCatalogInjector,
            GitRepositoryManager gitRepositoryManager,
            JobEvidenceFiles evidenceFiles,
            PracticeRevisionService practiceRevisionService,
            AnsweredPractices answeredPractices) {
        return new PracticeReviewPreparation(
                workspaceContextBuilder,
                practiceCatalogInjector,
                taskEnvelopeWriter,
                gitRepositoryManager,
                evidenceFiles,
                practiceRevisionService,
                answeredPractices);
    }

    @Bean
    PullRequestReviewHandler pullRequestReviewHandler(
            PracticeReviewPreparation preparation,
            FeedbackCompositionResultParser compositionResultParser,
            ReviewOutputService deliveryService,
            FeedbackDeliveryService feedbackService,
            InContextDeliveryGate inContextDeliveryGate,
            PracticeResultRepository practiceResultRepository,
            PublicReviewEligibility publicReviewEligibility) {
        return new PullRequestReviewHandler(
                objectMapper,
                preparation,
                compositionResultParser,
                deliveryService,
                feedbackService,
                feedbackResponseSuppressionFilter,
                inContextDeliveryGate,
                practiceResultRepository,
                publicReviewEligibility);
    }

    @Bean
    IssueReviewHandler issueReviewHandler(
            PracticeReviewPreparation preparation,
            FeedbackCompositionResultParser compositionResultParser,
            ReviewOutputService deliveryService,
            InContextDeliveryGate inContextDeliveryGate,
            FeedbackLedgerRecorder feedbackLedgerRecorder,
            PracticeFeedbackDeliveryPolicy deliveryPolicy,
            PracticeFeedbackCommentFormatter commentFormatter,
            FeedbackResponseSuppressionFilter feedbackResponseSuppressionFilter,
            PracticeResultRepository practiceResultRepository,
            PracticeFeedbackDispatchService dispatchService,
            FeedbackDeliveryService feedbackDeliveryService,
            PublicReviewEligibility publicReviewEligibility) {
        return new IssueReviewHandler(
                objectMapper,
                preparation,
                compositionResultParser,
                deliveryService,
                inContextDeliveryGate,
                feedbackLedgerRecorder,
                deliveryPolicy,
                commentFormatter,
                feedbackResponseSuppressionFilter,
                practiceResultRepository,
                dispatchService,
                feedbackDeliveryService,
                publicReviewEligibility);
    }

    @Bean
    JobTypeHandler conversationReviewHandler(
            PracticeReviewPreparation preparation,
            ReviewOutputService deliveryService,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate transactionTemplate) {
        return new ConversationReviewHandler(
                objectMapper, preparation, deliveryService, eventPublisher, transactionTemplate);
    }

    @Bean
    JobTypeHandler documentReviewHandler(PracticeReviewPreparation preparation, ReviewOutputService deliveryService) {
        return new DocumentReviewHandler(objectMapper, preparation, deliveryService);
    }

    @Bean
    JobTypeHandlerRegistry jobTypeHandlerRegistry(List<JobTypeHandler> handlers) {
        return new JobTypeHandlerRegistry(handlers);
    }
}
