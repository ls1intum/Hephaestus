package de.tum.in.www1.hephaestus.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.config.KeycloakProperties;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.integrations.posthog.PosthogClient;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.IdentityProvidersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Unit tests for {@link AccountService}.
 * <p>
 * Covers user settings retrieval/update and the AI review preference
 * query method.
 */
class AccountServiceTest extends BaseUnitTest {

    private static final Long USER_ID = 1L;
    private static final String USER_LOGIN = "testuser";
    private static final String KEYCLOAK_USER_ID = "kc-subject-123";

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private PosthogClient posthogClient;

    @Mock
    private Keycloak keycloak;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        KeycloakProperties keycloakProperties = new KeycloakProperties(
            "http://localhost:8081",
            "hephaestus",
            "hephaestus-confidential",
            null,
            null,
            null,
            null
        );
        accountService = new AccountService(userPreferencesRepository, posthogClient, keycloak, keycloakProperties);
    }

    private User createUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setLogin(USER_LOGIN);
        return user;
    }

    private UserPreferences createPreferences(User user) {
        UserPreferences preferences = new UserPreferences(user);
        preferences.setId(10L);
        return preferences;
    }

    // ── getUserSettings ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserSettings")
    class GetUserSettings {

        @Test
        @DisplayName("returns DTO with all fields including aiReviewEnabled")
        void returnsAllFields() {
            User user = createUser();
            UserPreferences prefs = createPreferences(user);
            prefs.setParticipateInResearch(true);
            prefs.setAiReviewEnabled(false);
            when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

            UserSettingsDTO result = accountService.getUserSettings(user);

            assertThat(result.participateInResearch()).isTrue();
            assertThat(result.aiReviewEnabled()).isFalse();
        }

        @Test
        @DisplayName("returns defaults when no preferences exist")
        void returnsDefaults() {
            User user = createUser();
            when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
            when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

            UserSettingsDTO result = accountService.getUserSettings(user);

            assertThat(result.participateInResearch()).isTrue();
            assertThat(result.aiReviewEnabled()).isTrue();
        }
    }

    // ── updateUserSettings ──────────────────────────────────────────────────

    @Nested
    @DisplayName("updateUserSettings")
    class UpdateUserSettings {

        @Test
        @DisplayName("persists aiReviewEnabled=true")
        void persistsAiReviewEnabledTrue() {
            User user = createUser();
            UserPreferences prefs = createPreferences(user);
            when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));
            when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

            UserSettingsDTO dto = new UserSettingsDTO(true, true);
            UserSettingsDTO result = accountService.updateUserSettings(user, dto, KEYCLOAK_USER_ID);

            assertThat(result.aiReviewEnabled()).isTrue();
            ArgumentCaptor<UserPreferences> captor = ArgumentCaptor.forClass(UserPreferences.class);
            verify(userPreferencesRepository).save(captor.capture());
            assertThat(captor.getValue().isAiReviewEnabled()).isTrue();
        }

        @Test
        @DisplayName("persists aiReviewEnabled=false")
        void persistsAiReviewEnabledFalse() {
            User user = createUser();
            UserPreferences prefs = createPreferences(user);
            when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));
            when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

            UserSettingsDTO dto = new UserSettingsDTO(true, false);
            UserSettingsDTO result = accountService.updateUserSettings(user, dto, KEYCLOAK_USER_ID);

            assertThat(result.aiReviewEnabled()).isFalse();
            ArgumentCaptor<UserPreferences> captor = ArgumentCaptor.forClass(UserPreferences.class);
            verify(userPreferencesRepository).save(captor.capture());
            assertThat(captor.getValue().isAiReviewEnabled()).isFalse();
        }

        @Test
        @DisplayName("throws when aiReviewEnabled is null")
        void throwsWhenAiReviewEnabledNull() {
            User user = createUser();
            UserPreferences prefs = createPreferences(user);
            when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

            UserSettingsDTO dto = new UserSettingsDTO(true, null);

            assertThatThrownBy(() -> accountService.updateUserSettings(user, dto, KEYCLOAK_USER_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("aiReviewEnabled");
        }

        @Test
        @DisplayName("does not touch PostHog when only aiReviewEnabled changes")
        void noPosthogWhenOnlyAiReviewChanges() {
            User user = createUser();
            UserPreferences prefs = createPreferences(user);
            prefs.setAiReviewEnabled(true);
            when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));
            when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

            UserSettingsDTO dto = new UserSettingsDTO(true, false);
            accountService.updateUserSettings(user, dto, KEYCLOAK_USER_ID);

            verify(posthogClient, never()).deletePersonData(any());
        }
    }

    // ── isAiReviewEnabled ───────────────────────────────────────────────────

    @Nested
    @DisplayName("isAiReviewEnabled")
    class IsAiReviewEnabled {

        @Test
        @DisplayName("returns true when preference exists and is enabled")
        void returnsTrueWhenEnabled() {
            UserPreferences prefs = new UserPreferences();
            prefs.setAiReviewEnabled(true);
            when(userPreferencesRepository.findByUserLogin(USER_LOGIN)).thenReturn(Optional.of(prefs));

            assertThat(accountService.isAiReviewEnabled(USER_LOGIN)).isTrue();
        }

        @Test
        @DisplayName("returns false when preference exists and is disabled")
        void returnsFalseWhenDisabled() {
            UserPreferences prefs = new UserPreferences();
            prefs.setAiReviewEnabled(false);
            when(userPreferencesRepository.findByUserLogin(USER_LOGIN)).thenReturn(Optional.of(prefs));

            assertThat(accountService.isAiReviewEnabled(USER_LOGIN)).isFalse();
        }

        @Test
        @DisplayName("returns true (default) when no preference row exists")
        void returnsTrueWhenNoPreferencesExist() {
            when(userPreferencesRepository.findByUserLogin(USER_LOGIN)).thenReturn(Optional.empty());

            assertThat(accountService.isAiReviewEnabled(USER_LOGIN)).isTrue();
        }

        @Test
        @DisplayName("throws IllegalArgumentException when userLogin is null")
        void throwsWhenUserLoginNull() {
            assertThatThrownBy(() -> accountService.isAiReviewEnabled(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userLogin must not be blank");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when userLogin is blank")
        void throwsWhenUserLoginBlank() {
            assertThatThrownBy(() -> accountService.isAiReviewEnabled("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userLogin must not be blank");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when userLogin is empty string")
        void throwsWhenUserLoginEmpty() {
            assertThatThrownBy(() -> accountService.isAiReviewEnabled(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userLogin must not be blank");
        }
    }

    // ── updateUserSettings + PostHog combination ─────────────────────────────

    @Nested
    @DisplayName("updateUserSettings – PostHog interaction")
    class UpdateSettingsPosthogInteraction {

        @Test
        @DisplayName("disabling aiReview while opting out of research triggers PostHog deletion")
        void aiReviewChangeWithResearchOptOutTriggersPosthog() {
            User user = createUser();
            UserPreferences prefs = createPreferences(user);
            prefs.setParticipateInResearch(true);
            prefs.setAiReviewEnabled(true);
            when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));
            when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));
            when(posthogClient.deletePersonData(any())).thenReturn(true);

            UserSettingsDTO dto = new UserSettingsDTO(false, false);
            accountService.updateUserSettings(user, dto, KEYCLOAK_USER_ID);

            verify(posthogClient).deletePersonData(KEYCLOAK_USER_ID);
        }

        @Test
        @DisplayName("round-trip: update then get returns consistent values")
        void roundTripConsistency() {
            User user = createUser();
            UserPreferences prefs = createPreferences(user);
            when(userPreferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));
            when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

            UserSettingsDTO updateDto = new UserSettingsDTO(true, false);
            UserSettingsDTO updated = accountService.updateUserSettings(user, updateDto, KEYCLOAK_USER_ID);

            UserSettingsDTO fetched = accountService.getUserSettings(user);

            assertThat(fetched).isEqualTo(updated);
            assertThat(fetched.participateInResearch()).isTrue();
            assertThat(fetched.aiReviewEnabled()).isFalse();
        }
    }

    // ── getLinkedAccounts ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getLinkedAccounts")
    class GetLinkedAccounts {

        @Mock
        private RealmResource realmResource;

        @Mock
        private IdentityProvidersResource identityProvidersResource;

        @Mock
        private UsersResource usersResource;

        @Mock
        private UserResource userResource;

        private void setupKeycloakMocks() {
            when(keycloak.realm("hephaestus")).thenReturn(realmResource);
            when(realmResource.identityProviders()).thenReturn(identityProvidersResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.get(KEYCLOAK_USER_ID)).thenReturn(userResource);
        }

        private IdentityProviderRepresentation idp(
            String alias,
            String displayName,
            boolean enabled,
            boolean linkOnly
        ) {
            IdentityProviderRepresentation idp = new IdentityProviderRepresentation();
            idp.setAlias(alias);
            idp.setDisplayName(displayName);
            idp.setEnabled(enabled);
            idp.setLinkOnly(linkOnly);
            return idp;
        }

        private FederatedIdentityRepresentation fedIdentity(String provider, String username) {
            FederatedIdentityRepresentation fi = new FederatedIdentityRepresentation();
            fi.setIdentityProvider(provider);
            fi.setUserName(username);
            return fi;
        }

        @Test
        @DisplayName("returns connected and unconnected providers")
        void returnsConnectedAndUnconnectedProviders() {
            setupKeycloakMocks();
            when(identityProvidersResource.findAll()).thenReturn(
                List.of(idp("github", "GitHub", true, false), idp("gitlab", "GitLab", true, false))
            );
            when(userResource.getFederatedIdentity()).thenReturn(List.of(fedIdentity("github", "octocat")));

            List<LinkedAccountDTO> result = accountService.getLinkedAccounts(KEYCLOAK_USER_ID);

            assertThat(result).hasSize(2);
            LinkedAccountDTO github = result
                .stream()
                .filter(a -> a.providerAlias().equals("github"))
                .findFirst()
                .orElseThrow();
            assertThat(github.connected()).isTrue();
            assertThat(github.linkedUsername()).isEqualTo("octocat");

            LinkedAccountDTO gitlab = result
                .stream()
                .filter(a -> a.providerAlias().equals("gitlab"))
                .findFirst()
                .orElseThrow();
            assertThat(gitlab.connected()).isFalse();
            assertThat(gitlab.linkedUsername()).isNull();
        }

        @Test
        @DisplayName("filters out link-only and disabled providers")
        void filtersLinkOnlyAndDisabledProviders() {
            setupKeycloakMocks();
            when(identityProvidersResource.findAll()).thenReturn(
                List.of(
                    idp("github", "GitHub", true, false),
                    idp("saml", "SAML SSO", true, true),
                    idp("legacy", "Legacy", false, false)
                )
            );
            when(userResource.getFederatedIdentity()).thenReturn(List.of());

            List<LinkedAccountDTO> result = accountService.getLinkedAccounts(KEYCLOAK_USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).providerAlias()).isEqualTo("github");
        }

        @Test
        @DisplayName("falls back to alias when displayName is null")
        void fallsBackToAliasWhenDisplayNameNull() {
            setupKeycloakMocks();
            when(identityProvidersResource.findAll()).thenReturn(List.of(idp("github", null, true, false)));
            when(userResource.getFederatedIdentity()).thenReturn(List.of());

            List<LinkedAccountDTO> result = accountService.getLinkedAccounts(KEYCLOAK_USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).providerName()).isEqualTo("github");
        }

        @Test
        @DisplayName("wraps Keycloak exception in BAD_GATEWAY")
        void wrapsKeycloakExceptionInBadGateway() {
            when(keycloak.realm("hephaestus")).thenThrow(new NotFoundException("realm not found"));

            assertThatThrownBy(() -> accountService.getLinkedAccounts(KEYCLOAK_USER_ID))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> {
                    var rse = (org.springframework.web.server.ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(502);
                });
        }
    }

    // ── unlinkAccount ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("unlinkAccount")
    class UnlinkAccount {

        @Mock
        private RealmResource realmResource;

        @Mock
        private UsersResource usersResource;

        @Mock
        private UserResource userResource;

        private void setupKeycloakMocks() {
            when(keycloak.realm("hephaestus")).thenReturn(realmResource);
            when(realmResource.users()).thenReturn(usersResource);
            when(usersResource.get(KEYCLOAK_USER_ID)).thenReturn(userResource);
        }

        private FederatedIdentityRepresentation fedIdentity(String provider) {
            FederatedIdentityRepresentation fi = new FederatedIdentityRepresentation();
            fi.setIdentityProvider(provider);
            fi.setUserName(provider + "-user");
            return fi;
        }

        @Test
        @DisplayName("successfully unlinks when multiple providers exist")
        void successfullyUnlinksWithMultipleProviders() {
            setupKeycloakMocks();
            when(userResource.getFederatedIdentity()).thenReturn(List.of(fedIdentity("github"), fedIdentity("gitlab")));

            accountService.unlinkAccount(KEYCLOAK_USER_ID, "github");

            verify(userResource).removeFederatedIdentity("github");
        }

        @Test
        @DisplayName("throws CONFLICT when unlinking last provider")
        void throwsConflictWhenUnlinkingLastProvider() {
            setupKeycloakMocks();
            when(userResource.getFederatedIdentity()).thenReturn(List.of(fedIdentity("github")));

            assertThatThrownBy(() -> accountService.unlinkAccount(KEYCLOAK_USER_ID, "github"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> {
                    var rse = (org.springframework.web.server.ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                });
        }

        @Test
        @DisplayName("throws NOT_FOUND when provider alias not linked")
        void throwsNotFoundWhenProviderNotLinked() {
            setupKeycloakMocks();
            when(userResource.getFederatedIdentity()).thenReturn(List.of(fedIdentity("github"), fedIdentity("gitlab")));

            assertThatThrownBy(() -> accountService.unlinkAccount(KEYCLOAK_USER_ID, "nonexistent"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> {
                    var rse = (org.springframework.web.server.ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                });
        }

        @Test
        @DisplayName("wraps Keycloak exception in BAD_GATEWAY")
        void wrapsKeycloakExceptionInBadGateway() {
            when(keycloak.realm("hephaestus")).thenThrow(new NotFoundException("realm not found"));

            assertThatThrownBy(() -> accountService.unlinkAccount(KEYCLOAK_USER_ID, "github"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> {
                    var rse = (org.springframework.web.server.ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(502);
                });
        }
    }
}
