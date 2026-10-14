package de.tum.cit.aet.hephaestus.agent.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryWriter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class AgentHandlerTestDoubles {

    @Bean
    @Primary
    ScmSummaryPoster testPullRequestCommentPoster() {
        return mock(ScmSummaryPoster.class);
    }

    @Bean
    @Primary
    ScmLineNotePoster testDiffNotePoster() {
        return mock(ScmLineNotePoster.class);
    }

    @Bean
    @Primary
    AccountPreferencesQuery testAccountPreferencesQuery() {
        return mock(AccountPreferencesQuery.class);
    }

    static void resolveSummaryWrites(ScmSummaryPoster poster) {
        SummaryWriter channel = mock(SummaryWriter.class);
        when(poster.summaryWrite(any(), anyBoolean(), anyString(), anyString())).thenAnswer(invocation -> {
            ReviewJob job = invocation.getArgument(0);
            return new ScmSummaryPoster.SummaryWrite(
                    job,
                    channel,
                    new SummaryWriter.FeedbackTarget(
                            new IntegrationRef(IntegrationKind.GITLAB, job.getWorkspaceId(), null),
                            "test-subject",
                            null),
                    new SummaryWriter.FeedbackContent(invocation.getArgument(2), invocation.getArgument(3)));
        });
    }
}
