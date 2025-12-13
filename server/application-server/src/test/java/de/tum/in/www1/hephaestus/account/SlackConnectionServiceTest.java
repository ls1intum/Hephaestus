package de.tum.in.www1.hephaestus.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SlackConnectionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Keycloak keycloak;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private UserResource userResource;

    private SlackConnectionService slackConnectionService;

    private final String REALM = "hephaestus";
    private final String KEYCLOAK_BASE_URL = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        slackConnectionService = new SlackConnectionService(userRepository, keycloak);
        ReflectionTestUtils.setField(slackConnectionService, "realm", REALM);
        ReflectionTestUtils.setField(slackConnectionService, "slackEnabled", true);
    }

    @Test
    void testGetConnectionStatus_Connected() {
        User user = new User();
        user.setSlackUserId("U12345");

        SlackConnectionDTO dto = slackConnectionService.getConnectionStatus(user, KEYCLOAK_BASE_URL);

        assertThat(dto.connected()).isTrue();
        assertThat(dto.slackUserId()).isEqualTo("U12345");
        assertThat(dto.slackEnabled()).isTrue();
    }

    @Test
    void testGetConnectionStatus_Disconnected() {
        User user = new User();

        SlackConnectionDTO dto = slackConnectionService.getConnectionStatus(user, KEYCLOAK_BASE_URL);

        assertThat(dto.connected()).isFalse();
        assertThat(dto.slackUserId()).isNull();
        assertThat(dto.linkUrl()).contains("/realms/hephaestus/account/#/security/linked-accounts");
    }

    @Test
    void testSyncSlackIdentity_Success() {
        User user = new User();
        user.setLogin("testuser");
        String keycloakUserId = "k-123";

        // Mock Keycloak chain
        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(keycloakUserId)).thenReturn(userResource);

        FederatedIdentityRepresentation identity = new FederatedIdentityRepresentation();
        identity.setIdentityProvider("slack");
        identity.setUserId("U98765");

        when(userResource.getFederatedIdentity()).thenReturn(List.of(identity));

        SlackConnectionDTO dto = slackConnectionService.syncSlackIdentity(user, keycloakUserId, KEYCLOAK_BASE_URL);

        assertThat(dto.connected()).isTrue();
        assertThat(dto.slackUserId()).isEqualTo("U98765");

        verify(userRepository).save(user);
        assertThat(user.getSlackUserId()).isEqualTo("U98765");
    }
}
