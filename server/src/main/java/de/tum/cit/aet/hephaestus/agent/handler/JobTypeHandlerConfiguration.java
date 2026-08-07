package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.cit.aet.hephaestus.agent.task.TaskEnvelopeWriter;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.spi.InlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryChannel;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationTrendService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
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
        List<InlineFindingChannel> inlineFindingChannels
    ) {
        return new DiffNotePoster(commentPoster, commentFormatter, inlineFindingChannels);
    }

    @Bean
    FeedbackDeliveryService feedbackDeliveryService(
        PullRequestCommentPoster commentPoster,
        DiffNotePoster diffNotePoster,
        PracticeFeedbackDeliveryPolicy deliveryPolicy,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        ObservationTrendService observationTrendService,
        PracticeFeedbackCommentFormatter commentFormatter
    ) {
        return new FeedbackDeliveryService(
            commentPoster,
            diffNotePoster,
            deliveryPolicy,
            reviewProperties,
            feedbackLedgerRecorder,
            observationTrendService,
            commentFormatter
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
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        FeedbackDeliveryService feedbackService,
        SecretDiffScanner secretDiffScanner,
        PracticeTierGate practiceTierGate
    ) {
        return new PullRequestReviewHandler(
            objectMapper,
            contentAddressedStore,
            practiceCatalogInjector,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            resultParser,
            deliveryService,
            feedbackService,
            secretDiffScanner,
            reactionSuppressionFilter,
            practiceTierGate
        );
    }

    @Bean
    JobTypeHandler issueReviewHandler(
        PracticeCatalogInjector practiceCatalogInjector,
        PracticeDetectionResultParser resultParser,
        PracticeDetectionDeliveryService deliveryService,
        PullRequestCommentPoster commentPoster,
        FeedbackLedgerRecorder feedbackLedgerRecorder,
        PracticeFeedbackDeliveryPolicy deliveryPolicy,
        PracticeFeedbackCommentFormatter commentFormatter,
        PracticeTierGate practiceTierGate
    ) {
        return new IssueReviewHandler(
            objectMapper,
            workspaceContextBuilder,
            taskEnvelopeWriter,
            practiceCatalogInjector,
            resultParser,
            deliveryService,
            commentPoster,
            feedbackLedgerRecorder,
            deliveryPolicy,
            commentFormatter,
            practiceTierGate
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
    JobTypeHandlerRegistry jobTypeHandlerRegistry(List<JobTypeHandler> handlers) {
        return new JobTypeHandlerRegistry(handlers);
    }
}
