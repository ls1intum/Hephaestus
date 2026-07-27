package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class LlmModelResolverTest extends BaseUnitTest {

    @Mock
    private LlmConnectionRepository instanceConnections;

    @Mock
    private WorkspaceLlmConnectionRepository workspaceConnections;

    @Mock
    private LlmModelRepository instanceModels;

    @Mock
    private WorkspaceLlmModelRepository workspaceModels;

    @Mock
    private LlmModelWorkspaceGrantRepository grants;

    @InjectMocks
    private LlmModelResolver resolver;

    private LlmConnection connection;
    private LlmModel model;

    @BeforeEach
    void setUp() {
        connection = new LlmConnection();
        connection.setId(10L);
        connection.setEnabled(true);
        connection.setApiProtocol("openai-responses");
        connection.setBaseUrl("https://api.example.test/v1");
        connection.setAuthMode(LlmAuthMode.BEARER);
        connection.setApiKey("secret");
        model = new LlmModel();
        model.setId(20L);
        model.setUpstreamModelId("gpt-test");
        model.setConnection(connection);
        model.setEnabled(true);
        model.setVisibility(ModelVisibility.PUBLIC);
        lenient().when(instanceConnections.findById(10L)).thenReturn(Optional.of(connection));
        lenient().when(instanceModels.findById(20L)).thenReturn(Optional.of(model));
    }

    /** A per-purpose binding for workspace 30 with no model bound yet. */
    private static WorkspaceAgentBinding binding() {
        Workspace workspace = new Workspace();
        workspace.setId(30L);
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setWorkspace(workspace);
        binding.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        return binding;
    }

    /**
     * The file's one positive case, and it has to assert the payload field by field: this credential is
     * what the proxy dials with, and every field is the same shape as its neighbour. Swapping
     * {@code baseUrl} for {@code apiProtocol}, or handing back the connection's id where the model's
     * upstream id belongs, produces a perfectly non-null credential that talks to the wrong provider.
     */
    @Test
    void shouldResolveActivePublicInstanceModelForWorkspace() {
        var ref = new LlmModelResolver.ConnectionRef(FundingSource.INSTANCE, 10L, 20L, 30L);

        LlmModelResolver.ProxyCredential credential = resolver.resolveProxyCredential(ref);

        assertThat(credential).isNotNull();
        assertThat(credential.baseUrl()).isEqualTo("https://api.example.test/v1");
        assertThat(credential.apiProtocol()).isEqualTo("openai-responses");
        assertThat(credential.authMode()).isEqualTo(LlmAuthMode.BEARER);
        // The model's upstream id, not the catalog's own — the provider has never heard of our ids.
        assertThat(credential.upstreamModelId()).isEqualTo("gpt-test");
        assertThat(credential.apiKey()).isEqualTo("secret");
    }

    /**
     * A connection saved with an empty key is not a connection with an empty key: an empty
     * {@code Authorization: Bearer} header is a request the provider rejects with a confusing 401,
     * whereas no key at all lets the proxy fall through to whatever unauthenticated access the
     * provider offers, which is the honest representation of "no credential configured".
     */
    @Test
    void shouldReportABlankApiKeyAsNoCredentialAtAll() {
        connection.setApiKey("   ");
        var ref = new LlmModelResolver.ConnectionRef(FundingSource.INSTANCE, 10L, 20L, 30L);

        LlmModelResolver.ProxyCredential credential = resolver.resolveProxyCredential(ref);

        assertThat(credential).isNotNull();
        assertThat(credential.apiKey()).isNull();
    }

    @Test
    void shouldRejectDisabledInstanceModelEvenWhenConnectionIsActive() {
        model.setEnabled(false);
        var ref = new LlmModelResolver.ConnectionRef(FundingSource.INSTANCE, 10L, 20L, 30L);
        assertThat(resolver.resolveProxyCredential(ref)).isNull();
    }

    @Test
    void shouldRejectDisabledModelBeforeBuildingRuntimeConfiguration() {
        model.setEnabled(false);
        WorkspaceAgentBinding binding = binding();
        binding.setInstanceModel(model);

        assertThatThrownBy(() -> resolver.resolve(binding))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not available");
    }

    @Test
    void shouldRejectCatalogSnapshotWithoutModelIdentity() {
        var ref = new LlmModelResolver.ConnectionRef(FundingSource.INSTANCE, 10L, null, null);
        assertThat(resolver.resolveProxyCredential(ref)).isNull();
    }

    @Test
    void shouldRejectRevokedGrantedInstanceModel() {
        model.setVisibility(ModelVisibility.GRANTED);
        when(grants.existsByIdModelIdAndIdWorkspaceId(20L, 30L)).thenReturn(false);
        var ref = new LlmModelResolver.ConnectionRef(FundingSource.INSTANCE, 10L, 20L, 30L);
        assertThat(resolver.resolveProxyCredential(ref)).isNull();
    }

    @Test
    void shouldRejectWorkspaceModelOwnedByAnotherWorkspace() {
        Workspace owner = new Workspace();
        owner.setId(31L);
        WorkspaceLlmConnection workspaceConnection = new WorkspaceLlmConnection();
        workspaceConnection.setId(11L);
        workspaceConnection.setWorkspace(owner);
        workspaceConnection.setEnabled(true);
        WorkspaceLlmModel workspaceModel = new WorkspaceLlmModel();
        workspaceModel.setId(21L);
        workspaceModel.setWorkspace(owner);
        workspaceModel.setConnection(workspaceConnection);
        workspaceModel.setEnabled(true);
        var ref = new LlmModelResolver.ConnectionRef(FundingSource.WORKSPACE, 11L, 21L, 30L);
        assertThat(resolver.resolveProxyCredential(ref)).isNull();
    }

    @Test
    void shouldRejectBindingWithoutACatalogModel() {
        WorkspaceAgentBinding binding = binding(); // neither instance nor workspace model set

        assertThatThrownBy(() -> resolver.resolve(binding))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bind");
    }

    @Test
    void shouldNotResolveACredentialForABindingWithoutACatalogModel() {
        // The credential only ever comes from the bound model's connection — an unbound binding has no
        // other place a key could come from, so resolution fails closed.
        WorkspaceAgentBinding binding = binding();

        assertThat(resolver.resolveCredential(binding)).isNull();
    }

    @Test
    void shouldNotResolveLegacyProxyCredential() {
        assertThat(resolver.resolveProxyCredential(LlmModelResolver.ConnectionRef.NONE)).isNull();
    }
}
