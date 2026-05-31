package de.tum.cit.aet.hephaestus.core.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

/**
 * Proves a freshly generated signing key survives the full DB round-trip <em>through the
 * service</em> when sealing is enabled: {@code generateNewKeyRow()} seals on write, and the
 * private-key read path unseals on load, yielding a usable ES256 signing JWK. Also pins that
 * the public JWK set never leaks the private key.
 *
 * <p>The repository is mocked as an in-memory holder so we exercise the real seal/unseal seam
 * without a database — the sealer is a real {@link JwtSigningKeySealer} built from a 32-char key.
 */
class JwtSigningKeyServiceSealingTest extends BaseUnitTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef"; // exactly 32 chars

    private static MockEnvironment env(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return env;
    }

    private JwtSigningKeyService serviceWith(JwtSigningKeyRepository repo, JwtSigningKeySealer sealer) {
        return new JwtSigningKeyService(repo, env("dev"), sealer);
    }

    @Test
    void generatedSealedKey_roundTripsThroughDbLoad() {
        JwtSigningKeySealer sealer = new JwtSigningKeySealer(KEY, "dev");
        assertThat(sealer.isEnabled()).isTrue();

        JwtSigningKeyRepository repo = mock(JwtSigningKeyRepository.class);
        when(repo.countByActiveTrue()).thenReturn(0L);
        ArgumentCaptor<JwtSigningKey> saved = ArgumentCaptor.forClass(JwtSigningKey.class);
        when(repo.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));

        JwtSigningKeyService service = serviceWith(repo, sealer);
        service.ensureActiveKey();

        JwtSigningKey row = saved.getValue();
        // Persisted blob must be SEALED, not raw DER, and tagged accordingly.
        assertThat(row.getEncryptionKeyId()).isEqualTo(JwtSigningKeySealer.KEY_ID);
        assertThat(row.getPrivateKeyPem()[0]).isEqualTo(JwtSigningKeySealer.FORMAT_VERSION_V1);

        // Feed the sealed row back as the active set; the read path must unseal and produce a
        // private-key-bearing ES256 JWK usable for signing.
        when(repo.findActive()).thenReturn(List.of(row));
        JWK current = service.currentSigningKey();

        assertThat(current).isInstanceOf(ECKey.class);
        ECKey ec = (ECKey) current;
        assertThat(ec.isPrivate()).isTrue();
        assertThat(ec.getKeyID()).isEqualTo(row.getKid());

        // Public set must NOT carry the private key.
        JWKSet publicSet = service.publicJwkSet();
        assertThat(publicSet.getKeys()).hasSize(1);
        assertThat(((ECKey) publicSet.getKeys().get(0)).isPrivate()).isFalse();
    }

    @Test
    void disabledSealer_storesUnsealedAndStillLoads() {
        JwtSigningKeySealer sealer = new JwtSigningKeySealer((String) null, "dev"); // disabled
        assertThat(sealer.isEnabled()).isFalse();

        JwtSigningKeyRepository repo = mock(JwtSigningKeyRepository.class);
        when(repo.countByActiveTrue()).thenReturn(0L);
        ArgumentCaptor<JwtSigningKey> saved = ArgumentCaptor.forClass(JwtSigningKey.class);
        when(repo.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));

        JwtSigningKeyService service = serviceWith(repo, sealer);
        service.ensureActiveKey();

        JwtSigningKey row = saved.getValue();
        assertThat(row.getEncryptionKeyId()).isEqualTo("v0-unsealed");

        when(repo.findActive()).thenReturn(List.of(row));
        assertThat(((ECKey) service.currentSigningKey()).isPrivate()).isTrue();
    }

    @Test
    void prod_existingUnsealedRow_failsClosed() {
        // A legacy v0-unsealed row must fail closed in prod at BOTH enforcement points: the
        // non-swallowed startup assertion (so boot aborts) and key materialization / signing
        // (so a deferred first-issuance can't sign with the forgeable key either). ensureActiveKey()
        // itself is best-effort and intentionally does NOT throw here — the swallowing @PostConstruct
        // would hide it, which is exactly the inert-guard bug this guards against.
        JwtSigningKeySealer sealer = new JwtSigningKeySealer(KEY, "prod");
        JwtSigningKeyRepository repo = mock(JwtSigningKeyRepository.class);

        JwtSigningKey legacy = new JwtSigningKey();
        legacy.setKid("legacy-kid");
        legacy.setEncryptionKeyId("v0-unsealed");
        when(repo.findActive()).thenReturn(List.of(legacy));

        JwtSigningKeyService service = new JwtSigningKeyService(repo, env("prod"), sealer);

        // Startup assertion (called from AuthJwtConfig OUTSIDE the swallow) aborts boot.
        org.assertj.core.api.Assertions.assertThatThrownBy(service::assertProdKeysSealed)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unsealed");
        // Signing path also refuses to materialize the unsealed key.
        org.assertj.core.api.Assertions.assertThatThrownBy(service::currentSigningKey)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("unsealed");
    }

    @Test
    void prod_emptyTable_bootstrapsSealedKey() {
        JwtSigningKeySealer sealer = new JwtSigningKeySealer(KEY, "prod");
        JwtSigningKeyRepository repo = mock(JwtSigningKeyRepository.class);
        when(repo.countByActiveTrue()).thenReturn(0L);
        ArgumentCaptor<JwtSigningKey> saved = ArgumentCaptor.forClass(JwtSigningKey.class);
        when(repo.save(saved.capture())).thenAnswer(inv -> inv.getArgument(0));

        JwtSigningKeyService service = new JwtSigningKeyService(repo, env("prod"), sealer);

        // Prod + sealer enabled = OK to auto-generate a SEALED key (this is the fix that lets prod boot).
        service.ensureActiveKey();

        assertThat(saved.getValue().getEncryptionKeyId()).isEqualTo(JwtSigningKeySealer.KEY_ID);
    }
}
