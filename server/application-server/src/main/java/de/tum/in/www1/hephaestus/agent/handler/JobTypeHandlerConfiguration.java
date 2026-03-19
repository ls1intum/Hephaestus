package de.tum.in.www1.hephaestus.agent.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobTypeHandler;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeDetectionProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public JobTypeHandler pullRequestReviewHandler(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        PullRequestRepository pullRequestRepository,
        PullRequestReviewCommentRepository reviewCommentRepository,
        PracticeDetectionDeliveryService deliveryService,
        PracticeDetectionProperties properties
    ) {
        var resultParser = new PracticeDetectionResultParser(objectMapper, properties.maxFindingsPerJob());
        return new PullRequestReviewHandler(
            objectMapper,
            gitRepositoryManager,
            pullRequestRepository,
            reviewCommentRepository,
            resultParser,
            deliveryService
        );
    }

    @Bean
    public JobTypeHandlerRegistry jobTypeHandlerRegistry(List<JobTypeHandler> handlers) {
        return new JobTypeHandlerRegistry(handlers);
    }
}
