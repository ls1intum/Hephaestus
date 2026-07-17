package de.tum.cit.aet.hephaestus.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code AgentConfig} is the only audited resource that holds a credential, so it is the one place a
 * secret could reach the append-only trail — where it could never be edited out again.
 *
 * <p>The trap is specific: {@code llmApiKey} is encrypted at rest via {@code EncryptedStringConverter},
 * but {@code getLlmApiKey()} returns <em>plaintext</em>, so the obvious implementation — snapshot the
 * field like every other one — writes the key in the clear. These tests poison the entity and assert
 * the mapping drops it.
 *
 * <p>Companion to {@code ConfigAuditSnapshotArchTest}, which enforces the same rule structurally over
 * every snapshot by component name; this one covers the actual value flow.
 */
@Tag("unit")
class AgentConfigSnapshotTest {

    private static final String SENTINEL = "sk-live-CANARY-must-never-be-audited";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void doesNotSerializeTheApiKey() throws Exception {
        // Serialized form is what actually lands in old_value/new_value.
        assertThat(MAPPER.writeValueAsString(AgentConfigSnapshot.of(configWithKey(SENTINEL)))).doesNotContain(SENTINEL);
    }

    @Test
    void doesNotExposeTheApiKeyThroughToString() {
        // A record's toString prints every component, so this catches the key being added as one even
        // if a future serializer config would have hidden it.
        assertThat(AgentConfigSnapshot.of(configWithKey(SENTINEL))).hasToString(
            AgentConfigSnapshot.of(configWithKey("a-completely-different-key")).toString()
        );
    }

    @Test
    void recordsOnlyWhetherAKeyIsPresent() {
        // What an auditor actually asks — "was a credential added or removed here?" — without the
        // material. Fails if the presence flag is derived from something other than the key.
        assertThat(AgentConfigSnapshot.of(configWithKey(SENTINEL)).llmApiKeySet()).isTrue();
        assertThat(AgentConfigSnapshot.of(configWithKey(null)).llmApiKeySet()).isFalse();
        assertThat(AgentConfigSnapshot.of(configWithKey("  ")).llmApiKeySet()).isFalse();
    }

    @Test
    void stripsCredentialsFromTheBaseUrl() throws Exception {
        // A gateway URL may legitimately carry userinfo, which would otherwise be a cleartext credential
        // in an append-only column — and one the name-based ArchUnit guard cannot see, since
        // "llmBaseUrl" reads innocuous.
        AgentConfig config = configWithKey(SENTINEL);
        config.setLlmBaseUrl("https://svc:" + SENTINEL + "@gateway.internal:8443/v1?x=1");
        AgentConfigSnapshot snapshot = AgentConfigSnapshot.of(config);

        assertThat(snapshot.llmBaseUrl()).isEqualTo("https://gateway.internal:8443/v1?x=1");
        assertThat(MAPPER.writeValueAsString(snapshot)).doesNotContain(SENTINEL);
    }

    @Test
    void keepsAnOrdinaryBaseUrlIntact() {
        AgentConfig config = configWithKey(null);
        config.setLlmBaseUrl("https://gateway.internal/v1");
        assertThat(AgentConfigSnapshot.of(config).llmBaseUrl()).isEqualTo("https://gateway.internal/v1");
    }

    @Test
    void refusesToPassThroughAnUnparseableBaseUrl() {
        // Fail closed: a value we cannot parse is a value we cannot prove is credential-free.
        AgentConfig config = configWithKey(null);
        config.setLlmBaseUrl("ht tp://" + SENTINEL);
        assertThat(AgentConfigSnapshot.of(config).llmBaseUrl()).isEqualTo("<unparseable>");
    }

    @Test
    void capturesTheFieldsAnAuditorNeeds() {
        // "Who swapped the model" is the question this snapshot exists to answer.
        AgentConfigSnapshot snapshot = AgentConfigSnapshot.of(configWithKey(SENTINEL));
        assertThat(snapshot.name()).isEqualTo("primary");
        assertThat(snapshot.modelName()).isEqualTo("gpt-oss-120b");
        assertThat(snapshot.credentialMode()).isEqualTo(CredentialMode.API_KEY);
        assertThat(snapshot.enabled()).isTrue();
    }

    private static AgentConfig configWithKey(String key) {
        AgentConfig config = new AgentConfig();
        config.setName("primary");
        config.setEnabled(true);
        config.setLlmProvider(LlmProvider.OPENAI);
        config.setModelName("gpt-oss-120b");
        config.setCredentialMode(CredentialMode.API_KEY);
        config.setLlmApiKey(key);
        return config;
    }
}
