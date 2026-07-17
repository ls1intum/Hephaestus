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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * {@code /admin/settings} — instance settings + the silent-mode brake (#1386). Verifies the
 * {@code app_admin} gate on read and write, the engage → release round trip (reason and actor are
 * recorded on engage, the reason is cleared on release), and that a malformed body (missing
 * {@code engaged}) is rejected instead of defaulting to a silent release.
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

    @Autowired
    private InstanceSettingsRepository instanceSettingsRepository;

    @Test
    void concurrentSelfHealSeed_convergesWithoutAbortingTheTransaction() throws Exception {
        // Simulate a ddl-auto / pre-migration schema where the Liquibase seed never ran: the row is
        // absent and the first readers must self-heal it. A plain save() would PK-violate and poison the
        // transaction under a race (Postgres 25P02); the ON CONFLICT upsert must instead converge.
        instanceSettingsRepository.deleteById(InstanceSettings.SINGLETON_ID);

        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = IntStream.range(0, 8)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> instanceSettingsService.get().getId(), pool))
                .toList();
            for (var future : futures) {
                assertThat(future.get()).isEqualTo(InstanceSettings.SINGLETON_ID);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(instanceSettingsRepository.count()).isEqualTo(1);
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

        // The state survives the round trip through a fresh read.
        assertThat(getSettings().silentModeEngaged()).isTrue();
        // ...and the enforcement read port the delivery paths consult sees the engage too (API → DB → SPI).
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
