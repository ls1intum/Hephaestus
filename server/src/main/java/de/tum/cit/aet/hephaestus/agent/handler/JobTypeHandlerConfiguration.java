package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.composition.FeedbackCompositionResultParser;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryChannel;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationTrendService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
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
    private final ContentAddressedStore contentAddressedStore;
    private final PracticeReviewProperties reviewProperties;
    private final WorkspaceContextBuilder workspaceContextBuilder;
    private final TaskEnvelopeWriter taskEnvelopeWriter;
    private final ReactionSuppressionFilter reactionSuppressionFilter;

    JobTypeHandlerConfiguration(
        JsonMapper objectMapper,
        ContentAddressedStore contentAddressedStore,
        PracticeReviewProperties reviewProperties,
        WorkspaceContextBuilder workspaceContextBuilder,
        TaskEnvelopeWriter taskEnvelopeWriter,
        ReactionSuppressionFilter reactionSuppressionFilter
    ) {
        this.objectMapper = objectMapper;
        this.contentAddressedStore = contentAddressedStore;
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
    PullRequestCommentPoster pullRequestCommentPoster(List<SummaryChannel> feedbackChannels) {
        return new PullRequestCommentPoster(feedbackChannels);
    }

    @Bean
    DiffNotePoster diffNotePoster(
        PullRequestCommentPoster commentPoster,
        PracticeFeedbackCommentFormatter commentFormatter,
        List<InlineFeedbackChannel> inlineFeedbackChannels
    ) {
        return new DiffNotePoster(commentPoster, commentFormatter, inlineFeedbackChannels);
    }

    @Bean
    FeedbackDeliveryService feedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        PracticeFeedbackDeliveryPolicy deliveryPolicy,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        ObservationTrendService observationTrendService,
        PracticeFeedbackCommentFormatter commentFormatter,
        PracticeFeedbackDispatchService dispatchService
    ) {
        return new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            deliveryPolicy,
            reviewProperties,
            feedbackLedgerRecorder,
            observationTrendService,
            commentFormatter,
            dispatchService
        );
    }

    @Bean
    PracticeCatalogInjector practiceCatalogInjector(
        PracticeRepository practiceRepository,
        WorkspaceReviewDefaultsProvider workspaceDefaults
    ) {
        return new PracticeCatalogInjector(objectMapper, practiceRepository, workspaceDefaults);
    }

    @Bean
    SecretDiffScanner secretDiffScanner() {
        return new SecretDiffScanner();
    }

    @Bean
    PullRequestReviewHandler pullRequestReviewHandler(
        PracticeCatalogInjector practiceCatalogInjector,
        PracticeDetectionResultParser resultParser,
        FeedbackCompositionResultParser compositionResultParser,
        PracticeDetectionDeliveryService deliveryService,
        FeedbackDeliveryService feedbackService,
        SecretDiffScanner secretDiffScanner,
        InContextDeliveryGate inContextDeliveryGate,
        ApplicationEventPublisher eventPublisher,
        ObservationRepository observationRepository
    ) {
        return new PullRequestReviewHandler(
            objectMapper,
            contentAddressedStore,
            practiceCatalogInjector,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            resultParser,
            compositionResultParser,
            deliveryService,
            feedbackService,
            secretDiffScanner,
            reactionSuppressionFilter,
            inContextDeliveryGate,
            eventPublisher,
            observationRepository
        );
    }

    @Bean
    IssueReviewHandler issueReviewHandler(
        PracticeCatalogInjector practiceCatalogInjector,
        PracticeDetectionResultParser resultParser,
        FeedbackCompositionResultParser compositionResultParser,
        PracticeDetectionDeliveryService deliveryService,
        InContextDeliveryGate inContextDeliveryGate,
        PullRequestCommentPoster commentPoster,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        PracticeFeedbackDeliveryPolicy deliveryPolicy,
        PracticeFeedbackCommentFormatter commentFormatter,
        ObservationRepository observationRepository,
        PracticeFeedbackDispatchService dispatchService
    ) {
        return new IssueReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            practiceCatalogInjector,
            resultParser,
            compositionResultParser,
            deliveryService,
            inContextDeliveryGate,
            commentPoster,
            feedbackLedgerRecorder,
            deliveryPolicy,
            commentFormatter,
            observationRepository,
            dispatchService
        );
    }

    @Bean
    JobTypeHandler conversationReviewHandler(
        PracticeCatalogInjector practiceCatalogInjector,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        ApplicationEventPublisher eventPublisher,
        TransactionTemplate transactionTemplate
    ) {
        return new ConversationReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            practiceCatalogInjector,
            resultParser,
            deliveryService,
            eventPublisher,
            transactionTemplate
        );
    }

    @Bean
    JobTypeHandler documentReviewHandler(
        PracticeCatalogInjector practiceCatalogInjector,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService
    ) {
        return new DocumentReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            practiceCatalogInjector,
            resultParser,
            deliveryService
        );
    }

    @Bean
    JobTypeHandlerRegistry jobTypeHandlerRegistry(List<JobTypeHandler> handlers) {
        return new JobTypeHandlerRegistry(handlers);
    }
}
