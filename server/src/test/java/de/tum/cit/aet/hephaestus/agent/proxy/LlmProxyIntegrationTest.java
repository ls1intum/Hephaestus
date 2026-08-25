package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmAuthMode;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver.ConnectionRef;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.LlmCatalogTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class LlmProxyIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private WorkspaceAgentBindingRepository agentBindingRepository;

    @Autowired
    private LlmConnectionRepository connectionRepository;

    @Autowired
    private LlmModelRepository modelRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LlmModelResolver modelResolver;

    private JobTokenAuthenticationFilter filter;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JobTokenAuthenticationFilter(jobRepository, new MentorProxyCredentialRegistry(), objectMapper);
        User owner = persistUser("proxy-owner");
        workspace = createWorkspace("proxy-ws", "Proxy Workspace", "proxy-org", AccountType.ORG, owner);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateRunningJobTokenAgainstPersistedRouting() throws Exception {
        AgentJob job = runningJob(true);
        AuthenticationResult result = authenticate(job.getJobToken());

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.authentication())
            .isNotNull()
            .extracting(Authentication::getPrincipal)
            .isInstanceOf(ProxyRouting.class);
    }

    @Test
    void shouldFailClosedWhenCatalogModelIsDisabled() {
        AgentJob job = runningJob(false);
        long connectionId = job.getConfigSnapshot().get("connectionId").asLong();
        long modelId = job.getConfigSnapshot().get("modelId").asLong();

        assertThat(
            modelResolver.resolveProxyCredential(
                new ConnectionRef(FundingSource.INSTANCE, connectionId, modelId, workspace.getId())
            )
        ).isNull();
    }

    private AuthenticationResult authenticate(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.setRequestURI("/internal/llm/chat/completions");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        filter.doFilter(request, response, (filteredRequest, filteredResponse) ->
            authentication.set(Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()))
        );
        return new AuthenticationResult(response.getStatus(), authentication.get());
    }

    private record AuthenticationResult(int status, Authentication authentication) {}

    private AgentJob runningJob(boolean modelEnabled) {
        LlmConnection connection = LlmCatalogTestFixtures.connection("connection-" + System.nanoTime());
        connection.setBaseUrl("https://api.example.com/v1");
        connection.setApiProtocol("openai-completions");
        connection.setAuthMode(LlmAuthMode.BEARER);
        connection.setApiKey("upstream-secret");
        connection = connectionRepository.save(connection);

        LlmModel model = modelRepository.save(
            LlmCatalogTestFixtures.model(
                connection,
                "model-" + System.nanoTime(),
                "catalog-model",
                ModelVisibility.PUBLIC,
                modelEnabled
            )
        );
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setWorkspace(workspace);
        binding.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        binding.setInstanceModel(model);
        binding.setEnabled(true);
        binding.setTimeoutSeconds(600);
        agentBindingRepository.save(binding);

        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("schemaVersion", 1);
        snapshot.put("apiProtocol", "openai-completions");
        snapshot.put("baseUrl", connection.getBaseUrl());
        snapshot.put("upstreamModelId", model.getUpstreamModelId());
        snapshot.put("connectionScope", "INSTANCE");
        snapshot.put("fundingSource", "INSTANCE");
        snapshot.put("connectionId", connection.getId());
        snapshot.put("modelId", model.getId());
        snapshot.put("workspaceId", workspace.getId());
        snapshot.put("timeoutSeconds", 600);
        snapshot.put("allowInternet", false);

        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setStatus(AgentJobStatus.RUNNING);
        job.setConfigSnapshot(snapshot);
        return jobRepository.save(job);
    }
}
