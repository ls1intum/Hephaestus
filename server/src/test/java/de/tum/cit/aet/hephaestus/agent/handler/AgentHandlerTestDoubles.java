package de.tum.cit.aet.hephaestus.agent.handler;

import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class AgentHandlerTestDoubles {

    @Bean
    @Primary
    PullRequestCommentPoster testPullRequestCommentPoster() {
        return mock(PullRequestCommentPoster.class);
    }

    @Bean
    @Primary
    DiffNotePoster testDiffNotePoster() {
        return mock(DiffNotePoster.class);
    }

    @Bean
    @Primary
    AccountPreferencesQuery testAccountPreferencesQuery() {
        return mock(AccountPreferencesQuery.class);
    }
}
