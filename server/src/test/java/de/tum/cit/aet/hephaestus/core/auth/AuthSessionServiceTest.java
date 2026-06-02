package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventWriter;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwt;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipal;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Pins the terminal-{@code result} telemetry of {@link AuthSessionService#refresh}. The timer alone
 * cannot distinguish a real re-mint from a rotation race or a suspended-account early-return; this
 * suite asserts that each branch records the right {@code auth.token.refresh.result} tag so an
 * operator can alert on an abnormal {@code noop}/{@code suspended}/{@code error} rate. Each test
 * fails if its branch stops recording (or records the wrong result).
 */
class AuthSessionServiceTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-06-02T10:00:00Z");
    private static final long ACCOUNT_ID = 42L;

    private IssuedJwtRepository issuedJwtRepository;
    private AccountRepository accountRepository;
    private HephaestusJwtIssuer jwtIssuer;
    private SimpleMeterRegistry meterRegistry;
    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        JwtPrincipalFactory principalFactory = mock(JwtPrincipalFactory.class);
        issuedJwtRepository = mock(IssuedJwtRepository.class);
        accountRepository = mock(AccountRepository.class);
        jwtIssuer = mock(HephaestusJwtIssuer.class);
        AuthProperties properties = mock(AuthProperties.class);
        AuthEventLogger eventLogger = new AuthEventLogger(mock(AuthEventWriter.class));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();

        lenient().when(properties.cookieName()).thenReturn("__Host-HEPHAESTUS_AT");
        lenient()
            .when(principalFactory.forAccountId(ACCOUNT_ID))
            .thenReturn(new JwtPrincipal(ACCOUNT_ID, "alice", null, Set.of()));

        service = new AuthSessionService(
            principalFactory,
            issuedJwtRepository,
            accountRepository,
            jwtIssuer,
            eventLogger,
            properties,
            clock,
            new AuthMetrics(meterRegistry)
        );
    }

    private double refreshResult(String tag) {
        var counter = meterRegistry.find("auth.token.refresh.result").tag("result", tag).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private Account activeAccount() {
        Account account = new Account("Alice");
        account.setId(ACCOUNT_ID);
        account.setStatus(Account.Status.ACTIVE);
        return account;
    }

    @Test
    void refresh_whenConditionalRevokeAffectsZeroRows_recordsNoopAndDoesNotReMint() {
        UUID jti = UUID.randomUUID();
        // A concurrent refresh/logout already rotated this jti — the conditional UPDATE matches 0 rows.
        when(issuedJwtRepository.revoke(eq(jti), any(), eq(IssuedJwt.RevokedReason.ROTATE))).thenReturn(0);

        service.refresh(ACCOUNT_ID, jti, null, mock(HttpServletRequest.class), new MockHttpServletResponse());

        assertThat(refreshResult("noop")).isEqualTo(1.0);
        assertThat(refreshResult("success")).isZero();
        verify(jwtIssuer, never()).issue(any(), any(), any());
    }

    @Test
    void refresh_whenAccountNotActive_recordsSuspendedAndDoesNotReMint() {
        UUID jti = UUID.randomUUID();
        when(issuedJwtRepository.revoke(eq(jti), any(), eq(IssuedJwt.RevokedReason.ROTATE))).thenReturn(1);
        Account suspended = activeAccount();
        suspended.setStatus(Account.Status.SUSPENDED);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(suspended));

        service.refresh(ACCOUNT_ID, jti, null, mock(HttpServletRequest.class), new MockHttpServletResponse());

        assertThat(refreshResult("suspended")).isEqualTo(1.0);
        verify(jwtIssuer, never()).issue(any(), any(), any());
    }

    @Test
    void refresh_whenAccountMissing_recordsSuspended() {
        UUID jti = UUID.randomUUID();
        when(issuedJwtRepository.revoke(eq(jti), any(), eq(IssuedJwt.RevokedReason.ROTATE))).thenReturn(1);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        service.refresh(ACCOUNT_ID, jti, null, mock(HttpServletRequest.class), new MockHttpServletResponse());

        assertThat(refreshResult("suspended")).isEqualTo(1.0);
    }

    @Test
    void refresh_whenReMintSucceeds_recordsSuccessAndSetsCookie() {
        UUID jti = UUID.randomUUID();
        when(issuedJwtRepository.revoke(eq(jti), any(), eq(IssuedJwt.RevokedReason.ROTATE))).thenReturn(1);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount()));
        HephaestusJwtIssuer.Token token = new HephaestusJwtIssuer.Token(
            "fresh-token",
            UUID.randomUUID(),
            NOW.plus(Duration.ofMinutes(15))
        );
        when(jwtIssuer.issue(any(), any(), any())).thenReturn(token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.refresh(ACCOUNT_ID, jti, null, mock(HttpServletRequest.class), response);

        assertThat(refreshResult("success")).isEqualTo(1.0);
        assertThat(response.getCookie("__Host-HEPHAESTUS_AT")).isNotNull();
        assertThat(response.getCookie("__Host-HEPHAESTUS_AT").getValue()).isEqualTo("fresh-token");
    }

    @Test
    void refresh_whenReMintThrows_recordsErrorAndPropagates() {
        UUID jti = UUID.randomUUID();
        when(issuedJwtRepository.revoke(eq(jti), any(), eq(IssuedJwt.RevokedReason.ROTATE))).thenReturn(1);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(activeAccount()));
        when(jwtIssuer.issue(any(), any(), any())).thenThrow(new IllegalStateException("signing key unavailable"));

        assertThatThrownBy(() ->
            service.refresh(ACCOUNT_ID, jti, null, mock(HttpServletRequest.class), new MockHttpServletResponse())
        ).isInstanceOf(IllegalStateException.class);

        assertThat(refreshResult("error")).isEqualTo(1.0);
        assertThat(refreshResult("success")).isZero();
        // The timer's count still reflects the call (finally ran) — telemetry is not lost on failure.
        assertThat(meterRegistry.find("auth.token.refresh").timer().count()).isEqualTo(1L);
    }
}
