package de.tum.cit.aet.hephaestus.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.settings.InstanceSettingsAdminController.InstanceSettingsDTO;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * {@code /admin/settings} — the app_admin gate, the engage/release round trip, and enforcement
 * visibility through {@link SilentModeQuery}.
 */
@Tag("integration")
class InstanceSettingsAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    /** The same bean the delivery paths inject — proves the API engage is visible to enforcement. */
    @Autowired
    private SilentModeQuery silentModeQuery;

    @Autowired
    private InstanceSettingsService instanceSettingsService;

    /** The brake is a global singleton: a failed assertion must not leave the shared context silenced. */
    @AfterEach
    void releaseSilentMode() {
        instanceSettingsService.updateSilentMode(false, null, null);
    }

    @Test
    @WithUser
    void nonAdminCannotReadOrWriteSettings() {
        webTestClient
            .get()
            .uri("/admin/settings")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();

        webTestClient
            .put()
            .uri("/admin/settings/silent-mode")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("engaged", true))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    void anonymousIsRejected() {
        webTestClient.get().uri("/admin/settings").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @WithAdminUser
    void engageAndReleaseRoundTrip() {
        InstanceSettingsDTO initial = getSettings();
        assertThat(initial.silentModeEngaged()).isFalse();

        InstanceSettingsDTO engaged = putSilentMode(Map.of("engaged", true, "reason", "incident #42"));
        assertThat(engaged.silentModeEngaged()).isTrue();
        assertThat(engaged.silentModeReason()).isEqualTo("incident #42");
        assertThat(engaged.silentModeChangedAt()).isNotNull();
        assertThat(engaged.silentModeChangedBy()).isNotBlank();

        assertThat(getSettings().silentModeEngaged()).isTrue();
        // API → DB → SPI: the port the delivery paths consult sees it too.
        assertThat(silentModeQuery.isSilentModeEngaged()).isTrue();

        Map<String, Object> release = new HashMap<>();
        release.put("engaged", false);
        InstanceSettingsDTO released = putSilentMode(release);
        assertThat(released.silentModeEngaged()).isFalse();
        assertThat(released.silentModeReason()).as("reason is cleared on release").isNull();
    }

    @Test
    @WithAdminUser
    void missingEngagedFieldIsRejectedNotDefaultedToRelease() {
        webTestClient
            .put()
            .uri("/admin/settings/silent-mode")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("reason", "no engaged flag"))
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    private InstanceSettingsDTO getSettings() {
        InstanceSettingsDTO dto = webTestClient
            .get()
            .uri("/admin/settings")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(InstanceSettingsDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(dto).isNotNull();
        return dto;
    }

    private InstanceSettingsDTO putSilentMode(Map<String, Object> body) {
        InstanceSettingsDTO dto = webTestClient
            .put()
            .uri("/admin/settings/silent-mode")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(InstanceSettingsDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(dto).isNotNull();
        return dto;
    }
}
