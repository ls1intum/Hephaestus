package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.egress.OutboundEgressGuard;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNoteWriter;
import de.tum.cit.aet.hephaestus.integration.scm.feedback.ScmFeedbackFixtures;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabTokenService;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

public final class GitLabCorrectionTestFixture {
    private GitLabCorrectionTestFixture() {}

    public static LineNoteWriter writer(HttpGraphQlClient client) {
        var provider = mock(GitLabGraphQlClientProvider.class);
        when(provider.forScope(1L)).thenReturn(client);
        return new GitLabLineNoteWriter(
                provider,
                WebClient.builder(),
                mock(GitLabTokenService.class),
                mock(OutboundEgressGuard.class),
                ScmFeedbackFixtures.resolver(),
                mock(GitLabDiffVersionReader.class));
    }
}
