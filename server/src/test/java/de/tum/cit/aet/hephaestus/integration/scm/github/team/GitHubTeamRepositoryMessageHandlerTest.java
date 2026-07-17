package de.tum.cit.aet.hephaestus.integration.scm.github.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.github.team.dto.GitHubTeamEventDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The repository-tier {@code team} handler exists solely to stop {@code added_to_repository} /
 * {@code removed_from_repository} deliveries — which the deriver keys to {@code repository.team} because
 * they carry a repository object — from being silently ACK-dropped. This locks down its routing key and
 * that it reuses the shared team dispatch rather than forking the logic.
 */
class GitHubTeamRepositoryMessageHandlerTest extends BaseUnitTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private GitHubTeamMessageHandler delegate;

    @Test
    void registersUnderRepositoryTeamKey() {
        GitHubTeamRepositoryMessageHandler handler = new GitHubTeamRepositoryMessageHandler(
            delegate,
            null,
            new TransactionTemplate()
        );

        assertThat(handler.key()).isEqualTo(new EventTypeKey(IntegrationKind.GITHUB, "repository.team"));
    }

    @Test
    void delegatesToSharedTeamDispatch() throws Exception {
        GitHubTeamRepositoryMessageHandler handler = new GitHubTeamRepositoryMessageHandler(
            delegate,
            null,
            new TransactionTemplate()
        );
        GitHubTeamEventDTO event = JSON.readValue(
            "{\"action\":\"added_to_repository\",\"team\":{\"id\":1,\"name\":\"core\",\"permission\":\"push\"}," +
                "\"organization\":{\"login\":\"acme\"},\"repository\":{\"id\":9,\"full_name\":\"acme/widgets\"}}",
            GitHubTeamEventDTO.class
        );

        handler.handleEvent(event);

        verify(delegate).routeTeamEvent(event);
    }
}
