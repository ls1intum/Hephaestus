package de.tum.cit.aet.hephaestus.integration.scm.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class MonitoredRepositoryScopeTest {

    @Test
    void shouldScopeNativeSqlExactlyLikeTheJpqlFunction() {
        String expected = MonitoredRepositoryScope.PATTERN
                .replace("?1", "rtm.workspace_id")
                .replace("?2", "rtm.native_id")
                .replace("?3", "r.native_id")
                .replace("?4", "r.provider_id");

        assertThat(MonitoredRepositoryScope.NATIVE_RTM_R).isEqualTo(expected);
    }
}
