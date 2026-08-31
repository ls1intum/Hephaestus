package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AccountHardDeleteSweeperTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountPurger accountPurger;

    @Mock
    private AuthProperties authProperties;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldContinuePurgingWhenOneAccountFails() {
        Duration cooldown = Duration.ofHours(48);
        when(authProperties.deleteCooldown()).thenReturn(cooldown);
        when(accountRepository.findDeletingPastCooldown(eq(NOW.minus(cooldown)), any()))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of(2L));
        doAnswer(invocation -> {
                    if (invocation.<Long>getArgument(0) == 2L) {
                        throw new RuntimeException("purge failed");
                    }
                    return null;
                })
                .when(accountPurger)
                .purge(anyLong());
        AccountHardDeleteSweeper sweeper =
                new AccountHardDeleteSweeper(accountRepository, accountPurger, authProperties, clock);

        assertThat(sweeper.sweepNow()).isEqualTo(1);
        verify(accountPurger).purge(1L);
        verify(accountPurger, times(2)).purge(2L);
    }
}
