package de.tum.cit.aet.hephaestus.integration.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("OAuthStateNonceStore — unit")
class OAuthStateNonceStoreTest extends BaseUnitTest {

    @Mock
    private OAuthStateNonceRepository repository;

    @InjectMocks
    private OAuthStateNonceStore store;

    @Test
    @DisplayName("issue persists a new row when the nonce is unseen")
    void issuePersistsRow() {
        when(repository.existsById("abc")).thenReturn(false);

        store.issue("abc", 7L, IntegrationKind.GITHUB, Instant.parse("2025-01-01T00:00:00Z"));

        verify(repository).save(any(OAuthStateNonce.class));
    }

    @Test
    @DisplayName("issue skips persistence when the nonce already exists (collision defence)")
    void issueSkipsOnCollision() {
        when(repository.existsById("abc")).thenReturn(true);

        store.issue("abc", 7L, IntegrationKind.GITHUB, Instant.parse("2025-01-01T00:00:00Z"));

        verify(repository, never()).save(any(OAuthStateNonce.class));
    }

    @Test
    @DisplayName("issue rejects null + empty nonce")
    void issueRejectsBlank() {
        assertThatThrownBy(() ->
            store.issue(null, 1L, IntegrationKind.GITHUB, Instant.now())
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            store.issue("", 1L, IntegrationKind.GITHUB, Instant.now())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tryConsume returns true when the atomic UPDATE flipped exactly one row")
    void tryConsumeWinsOnUpdate() {
        when(repository.markConsumed(eq("abc"), any(Instant.class))).thenReturn(1);
        assertThat(store.tryConsume("abc")).isTrue();
    }

    @Test
    @DisplayName("tryConsume returns false when the row was already consumed (0 rows affected)")
    void tryConsumeLosesWhenAlreadyConsumed() {
        when(repository.markConsumed(eq("abc"), any(Instant.class))).thenReturn(0);
        assertThat(store.tryConsume("abc")).isFalse();
    }

    @Test
    @DisplayName("tryConsume returns false for null / empty without touching the repository")
    void tryConsumeRejectsBlank() {
        assertThat(store.tryConsume(null)).isFalse();
        assertThat(store.tryConsume("")).isFalse();
        verify(repository, never()).markConsumed(any(), any());
    }

    @Test
    @DisplayName("concurrent calls to tryConsume map cleanly: single winner per delegated row count")
    void tryConsumeMapsRowCountFaithfully() {
        // Simulate the repository returning row-count for a sequence of attempts.
        // The store must NOT add extra state — it's a thin mapper.
        when(repository.markConsumed(eq("abc"), any(Instant.class)))
            .thenReturn(1)
            .thenReturn(0)
            .thenReturn(0);

        assertThat(store.tryConsume("abc")).isTrue();
        assertThat(store.tryConsume("abc")).isFalse();
        assertThat(store.tryConsume("abc")).isFalse();
        verify(repository, times(3)).markConsumed(eq("abc"), any(Instant.class));
    }
}
