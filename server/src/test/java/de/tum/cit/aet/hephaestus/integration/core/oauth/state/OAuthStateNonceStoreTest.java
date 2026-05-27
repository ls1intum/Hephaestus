package de.tum.cit.aet.hephaestus.integration.core.oauth.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Unit tests for {@link OAuthStateNonceStore} — the atomic single-use claim contract.
 *
 * <p>The store delegates the load-bearing atomicity to
 * {@code OAuthStateNonceRepository.markConsumed(nonce, now)} — these tests verify
 * the SERVICE-LAYER plumbing: input validation, mapping of {@code rows-affected}
 * to {@code true/false}, and collision avoidance on issue.
 */
class OAuthStateNonceStoreTest extends BaseUnitTest {

    @Mock
    private OAuthStateNonceRepository repository;

    @InjectMocks
    private OAuthStateNonceStore store;

    @Test
    void issuePersistsRow() {
        when(repository.existsById("abc")).thenReturn(false);

        store.issue("abc", 7L, IntegrationKind.GITHUB, Instant.parse("2025-01-01T00:00:00Z"));

        verify(repository).save(any(OAuthStateNonce.class));
    }

    @Test
    void issueSkipsOnCollision() {
        when(repository.existsById("abc")).thenReturn(true);

        store.issue("abc", 7L, IntegrationKind.GITHUB, Instant.parse("2025-01-01T00:00:00Z"));

        verify(repository, never()).save(any(OAuthStateNonce.class));
    }

    @Test
    void issueRejectsBlank() {
        assertThatThrownBy(() -> store.issue(null, 1L, IntegrationKind.GITHUB, Instant.now())).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() -> store.issue("", 1L, IntegrationKind.GITHUB, Instant.now())).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void tryConsumeWinsOnUpdate() {
        when(repository.markConsumed(eq("abc"), any(Instant.class))).thenReturn(1);
        assertThat(store.tryConsume("abc")).isTrue();
    }

    @Test
    void tryConsumeLosesWhenAlreadyConsumed() {
        when(repository.markConsumed(eq("abc"), any(Instant.class))).thenReturn(0);
        assertThat(store.tryConsume("abc")).isFalse();
    }

    @Test
    void tryConsumeRejectsBlank() {
        assertThat(store.tryConsume(null)).isFalse();
        assertThat(store.tryConsume("")).isFalse();
        verify(repository, never()).markConsumed(any(), any());
    }

    @Test
    void tryConsumeMapsRowCountFaithfully() {
        // Simulate the repository returning row-count for a sequence of attempts.
        // The store must NOT add extra state — it's a thin mapper.
        when(repository.markConsumed(eq("abc"), any(Instant.class))).thenReturn(1).thenReturn(0).thenReturn(0);

        assertThat(store.tryConsume("abc")).isTrue();
        assertThat(store.tryConsume("abc")).isFalse();
        assertThat(store.tryConsume("abc")).isFalse();
        verify(repository, times(3)).markConsumed(eq("abc"), any(Instant.class));
    }
}
