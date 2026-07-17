package de.tum.cit.aet.hephaestus.core.auth.stepup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.AuthPropertiesFixture;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventData;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventWriter;
import de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** The step-up gate's decision logic + its startup config guards (issue #1323). */
class StepUpPolicyTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-06-02T10:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(5); // AuthPropertiesFixture default

    private AuthEventWriter authEventWriter;
    private SimpleMeterRegistry meterRegistry;
    private StepUpPolicy policy;

    @BeforeEach
    void setUp() {
        authEventWriter = mock(AuthEventWriter.class);
        meterRegistry = new SimpleMeterRegistry();
        AuthProperties properties = AuthPropertiesFixture.defaults();
        policy = new StepUpPolicy(
            properties,
            new AuthEventLogger(authEventWriter),
            new AuthMetrics(meterRegistry),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private double stepUpDenied(String action) {
        var counter = meterRegistry.find("auth.step_up.denied").tag("action", action).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void recentAuthTimePassesWithoutAuditNoise() {
        assertThatCode(() ->
            policy.requireRecentAuthentication(
                NOW.minus(MAX_AGE).plusSeconds(1),
                AuthEvent.EventType.APP_ROLE_CHANGED,
                2L,
                1L
            )
        ).doesNotThrowAnyException();
        verifyNoInteractions(authEventWriter);
    }

    @Test
    void staleAuthTimeThrowsTheChallengeAndAuditsTheDenial() {
        assertThatThrownBy(() ->
            policy.requireRecentAuthentication(
                NOW.minus(MAX_AGE).minusSeconds(1),
                AuthEvent.EventType.IMPERSONATION_BEGIN,
                2L,
                1L
            )
        ).isInstanceOfSatisfying(StepUpRequiredException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getBody().getProperties()).containsEntry("code", "step_up_required");
            assertThat(e.getBody().getProperties()).containsEntry("maxAgeSeconds", MAX_AGE.toSeconds());
        });

        ArgumentCaptor<AuthEventData> captor = ArgumentCaptor.forClass(AuthEventData.class);
        verify(authEventWriter).write(captor.capture());
        AuthEventData event = captor.getValue();
        assertThat(event.type()).isEqualTo(AuthEvent.EventType.IMPERSONATION_BEGIN);
        assertThat(event.result()).isEqualTo(AuthEvent.Result.FAILURE);
        assertThat(event.failureReason()).isEqualTo("step_up_required");
        assertThat(event.accountId()).isEqualTo(2L);
        assertThat(event.actingAccountId()).isEqualTo(1L);
        // A denial is countable so an operator can alert on a spike (OWASP A09), tagged by action.
        assertThat(stepUpDenied("impersonation_begin")).isEqualTo(1.0);
    }

    @Test
    void missingAuthTimeIsTreatedAsStale() {
        // Fail-safe: a token without the claim (pre-deploy mint, hand-crafted) must not pass the gate.
        assertThatThrownBy(() ->
            policy.requireRecentAuthentication(null, AuthEvent.EventType.APP_ROLE_CHANGED, 2L, 1L)
        ).isInstanceOf(StepUpRequiredException.class);
    }

    @Test
    void nonPositiveWindowFailsAtStartup() {
        // A zero/negative window denies every gated action forever, and bootstrap-admin can't rescue it
        // (it self-disables while an admin exists) — refuse to boot rather than brick the instance.
        assertThatThrownBy(() -> policyWithStepUpMaxAge(Duration.ZERO))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("step-up-max-age");
        assertThatThrownBy(() -> policyWithStepUpMaxAge(Duration.ofMinutes(-1))).isInstanceOf(
            IllegalStateException.class
        );
    }

    @Test
    void windowAtOrAboveTheSessionCeilingStillBoots() {
        // Inert (auth_time can never outlive the session ceiling) is a misconfiguration, not a lockout:
        // warn and boot. Only the fail-closed direction may refuse to start.
        assertThatCode(() ->
            policyWithStepUpMaxAge(AuthPropertiesFixture.defaults().sessionMaxLifetime())
        ).doesNotThrowAnyException();
    }

    private StepUpPolicy policyWithStepUpMaxAge(Duration stepUpMaxAge) {
        return new StepUpPolicy(
            AuthPropertiesFixture.withStepUpMaxAge(stepUpMaxAge),
            new AuthEventLogger(authEventWriter),
            new AuthMetrics(new SimpleMeterRegistry()),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
