package de.tum.in.www1.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.context.ContextRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@DisplayName("WorkspaceAspectProvider")
class WorkspaceAspectProviderTest extends BaseUnitTest {

    @Mock
    UserRepository userRepository;

    @Mock
    WorkspaceRepository workspaceRepository;

    @Mock
    MentorAspectQueryRepository queryRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    WorkspaceAspectProvider provider;

    @Test
    @DisplayName("contribute writes workspace.json with all aspect sections")
    void writesWorkspaceJson() throws Exception {
        User user = new User();
        user.setLogin("octo");
        user.setName("Octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));

        Workspace ws = new Workspace();
        ws.setWorkspaceSlug("acme");
        ws.setDisplayName("Acme");
        when(workspaceRepository.findById(eq(1L))).thenReturn(Optional.of(ws));

        when(queryRepository.findRecentChatThreads(eq(1L), eq(2L))).thenReturn(List.of());
        when(queryRepository.findAssignedOpenIssues(eq(1L), eq(2L))).thenReturn(List.of());
        when(queryRepository.findPendingReviewRequestPrs(eq(1L), eq(2L))).thenReturn(List.of());

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        byte[] bytes = files.get("context/target/workspace.json");
        assertThat(bytes).isNotNull();
        JsonNode root = objectMapper.readTree(bytes);
        assertThat(root.get("workspace").get("slug").asText()).isEqualTo("acme");
        assertThat(root.get("workspace").get("displayName").asText()).isEqualTo("Acme");
        assertThat(root.get("recentSessions").isArray()).isTrue();
        assertThat(root.get("assignedIssues").isArray()).isTrue();
        assertThat(root.get("pendingReviewRequests").isArray()).isTrue();
        assertThat(root.get("focusSuggestions").isArray()).isTrue();
    }

    @Test
    @DisplayName("focusSuggestions surfaces stale review requests")
    void staleReviewRequests() {
        PullRequest pr = new PullRequest();
        // 5 days in the past — beyond the 3-day urgency threshold.
        pr.setCreatedAt(Instant.now().minus(Duration.ofDays(5)));
        List<String> suggestions = WorkspaceAspectProvider.computeFocusSuggestions(List.of(), List.of(pr));
        assertThat(suggestions).anyMatch(s -> s.contains("review request"));
    }

    @Test
    @DisplayName("focusSuggestions skips fresh review requests")
    void freshReviewRequests() {
        PullRequest pr = new PullRequest();
        pr.setCreatedAt(Instant.now().minus(Duration.ofHours(2)));
        List<String> suggestions = WorkspaceAspectProvider.computeFocusSuggestions(List.of(), List.of(pr));
        assertThat(suggestions).noneMatch(s -> s.contains("review request"));
    }
}
