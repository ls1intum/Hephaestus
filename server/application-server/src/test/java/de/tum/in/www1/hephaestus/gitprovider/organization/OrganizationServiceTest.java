package de.tum.in.www1.hephaestus.gitprovider.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@DisplayName("OrganizationService")
class OrganizationServiceTest extends BaseUnitTest {

    private static final Long PROVIDER_ID = 1L;
    private static final long NATIVE_ID = 42L;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private GitProviderRepository gitProviderRepository;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, gitProviderRepository);

        lenient().when(gitProviderRepository.getReferenceById(PROVIDER_ID)).thenReturn(new GitProvider());
        lenient()
            .when(organizationRepository.saveAndFlush(any(Organization.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("upsertIdentity")
    class UpsertIdentity {

        @Test
        @DisplayName("creates new organization when none exists for nativeId")
        void newOrg_createsAndSetsLogin() {
            when(organizationRepository.findByNativeIdAndProviderId(NATIVE_ID, PROVIDER_ID)).thenReturn(
                Optional.empty()
            );

            Organization result = service.upsertIdentity(NATIVE_ID, "MyOrg", PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getLogin()).isEqualTo("MyOrg");
            assertThat(result.getNativeId()).isEqualTo(NATIVE_ID);
            verify(organizationRepository).saveAndFlush(any(Organization.class));
        }

        @Test
        @DisplayName("case-only rename from provider IS persisted (dirty-check is case-sensitive)")
        void caseOnlyRename_isPersisted() {
            Organization existing = createExistingOrg("myorg");
            when(organizationRepository.findByNativeIdAndProviderId(NATIVE_ID, PROVIDER_ID)).thenReturn(
                Optional.of(existing)
            );

            Organization result = service.upsertIdentity(NATIVE_ID, "MyOrg", PROVIDER_ID);

            // The canonical casing from the provider must be saved
            assertThat(result.getLogin()).isEqualTo("MyOrg");
            ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
            verify(organizationRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getLogin()).isEqualTo("MyOrg");
        }

        @Test
        @DisplayName("identical login preserves existing value unchanged")
        void identicalLogin_preservesValue() {
            Organization existing = createExistingOrg("MyOrg");
            when(organizationRepository.findByNativeIdAndProviderId(NATIVE_ID, PROVIDER_ID)).thenReturn(
                Optional.of(existing)
            );

            Organization result = service.upsertIdentity(NATIVE_ID, "MyOrg", PROVIDER_ID);

            // Login unchanged — verify the value is still the original
            assertThat(result.getLogin()).isEqualTo("MyOrg");

            // Verify save was still called (upsertIdentity always saves)
            ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
            verify(organizationRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getLogin()).isEqualTo("MyOrg");
        }

        @Test
        @DisplayName("full rename updates login")
        void fullRename_updatesLogin() {
            Organization existing = createExistingOrg("old-org");
            when(organizationRepository.findByNativeIdAndProviderId(NATIVE_ID, PROVIDER_ID)).thenReturn(
                Optional.of(existing)
            );

            Organization result = service.upsertIdentity(NATIVE_ID, "new-org", PROVIDER_ID);

            assertThat(result.getLogin()).isEqualTo("new-org");
        }

        @Test
        @DisplayName("null login throws IllegalArgumentException")
        void nullLogin_throws() {
            assertThatThrownBy(() -> service.upsertIdentity(NATIVE_ID, null, PROVIDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("login required");
        }

        @Test
        @DisplayName("blank login throws IllegalArgumentException")
        void blankLogin_throws() {
            assertThatThrownBy(() -> service.upsertIdentity(NATIVE_ID, "  ", PROVIDER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("login required");
        }
    }

    private Organization createExistingOrg(String login) {
        Organization org = new Organization();
        org.setId(100L);
        org.setNativeId(NATIVE_ID);
        org.setLogin(login);
        org.setHtmlUrl("https://github.com/" + login);
        return org;
    }
}
