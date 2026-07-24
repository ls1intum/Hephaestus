package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link MentorProxyCredentialRegistry#revoke(UUID)} is the dispose-path hook
 * {@code DockerInteractiveSandboxAdapter} calls when a mentor sandbox's container is torn down (any
 * reason). This pins the revoke contract in isolation from the sandbox/docker machinery.
 */
class MentorProxyCredentialRegistryTest extends BaseUnitTest {

    private final MentorProxyCredentialRegistry registry = new MentorProxyCredentialRegistry();

    @Test
    void mintedTokenAuthenticatesBeforeRevoke() {
        UUID sessionId = UUID.randomUUID();
        String token = registry.mint(sessionId, "openai-completions", "https://api.openai.com", null, null, 7L);

        assertThat(registry.validate(token)).isPresent();
    }

    @Test
    void revokedTokenNoLongerAuthenticates() {
        UUID sessionId = UUID.randomUUID();
        String token = registry.mint(sessionId, "openai-completions", "https://api.openai.com", null, null, 7L);

        registry.revoke(sessionId);

        assertThat(registry.validate(token)).isEmpty();
    }

    @Test
    void revokeIsIdempotent() {
        UUID sessionId = UUID.randomUUID();
        String token = registry.mint(sessionId, "openai-completions", "https://api.openai.com", null, null, 7L);

        registry.revoke(sessionId);
        // A second close callback (or a race between idle-reap and manual close) must not throw.
        registry.revoke(sessionId);

        assertThat(registry.validate(token)).isEmpty();
    }

    @Test
    void revokingAnUnknownSessionIsANoOp() {
        // A sandbox that lost the concurrent-attach race never minted a token that got embedded into a
        // container; its dispose path still calls revoke() with that sessionId.
        registry.revoke(UUID.randomUUID());
        // No exception — nothing to assert beyond "didn't throw".
    }

    @Test
    void revokingOneSessionDoesNotAffectAnother() {
        UUID sessionA = UUID.randomUUID();
        UUID sessionB = UUID.randomUUID();
        String tokenA = registry.mint(sessionA, "openai-completions", "https://api.openai.com", null, null, 1L);
        String tokenB = registry.mint(sessionB, "anthropic-messages", "https://api.anthropic.com", null, null, 2L);

        registry.revoke(sessionA);

        assertThat(registry.validate(tokenA)).isEmpty();
        assertThat(registry.validate(tokenB)).isPresent();
    }

    @Test
    void validateReportsRoutingForTheBoundConnection() {
        UUID sessionId = UUID.randomUUID();
        String token = registry.mint(
            sessionId,
            "anthropic-messages",
            "https://api.anthropic.com",
            FundingSource.INSTANCE,
            42L,
            null
        );

        var routing = registry.validate(token).orElseThrow();

        assertThat(routing.apiProtocol()).isEqualTo("anthropic-messages");
        assertThat(routing.baseUrl()).isEqualTo("https://api.anthropic.com");
        assertThat(routing.connectionScope()).isEqualTo(FundingSource.INSTANCE);
        assertThat(routing.connectionId()).isEqualTo(42L);
    }
}
