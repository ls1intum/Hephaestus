package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility;
import de.tum.cit.aet.hephaestus.agent.mentor.MentorLlmConfig;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import java.math.BigDecimal;

/**
 * Object-mother for the instance LLM catalog entities, the sibling of {@link TestEntities} and
 * {@link WorkspaceTestFixtures}.
 *
 * <p>A dozen test classes each grew their own private {@code seedInstanceModel(...)} that built the
 * same enabled, PUBLIC model on the same {@code openai-completions} connection. They agreed on the
 * shape by accident, which means a new required field on either entity has to be discovered a dozen
 * times. One factory, one place to change.
 *
 * <p>Both factories return <b>unsaved</b> entities with only the fields the catalog actually requires
 * — persist them through the repository the test already has, and set anything else on the returned
 * instance (both entities are fully mutable).
 */
public final class LlmCatalogTestFixtures {

    /** The protocol every catalog fixture speaks unless a test is specifically about protocols. */
    public static final String OPENAI_COMPLETIONS = "openai-completions";

    /**
     * A syntactically valid, deliberately non-resolvable host. {@code .example} is reserved by
     * RFC 2606, so a fixture connection can never accidentally egress to a real provider.
     */
    public static final String BASE_URL = "https://api.openai.example/v1";

    private LlmCatalogTestFixtures() {}

    /** An unsaved, enabled instance connection: {@code slug}, an {@code .example} host, no API key. */
    public static LlmConnection connection(String slug) {
        LlmConnection connection = new LlmConnection();
        connection.setSlug(slug);
        connection.setDisplayName("Connection " + slug);
        connection.setBaseUrl(BASE_URL);
        connection.setApiProtocol(OPENAI_COMPLETIONS);
        connection.setEnabled(true);
        return connection;
    }

    /** An unsaved, enabled, {@link ModelVisibility#PUBLIC} model on {@code connection}. */
    public static LlmModel model(LlmConnection connection, String slug, String upstreamModelId) {
        return model(connection, slug, upstreamModelId, ModelVisibility.PUBLIC, true);
    }

    /** As {@link #model(LlmConnection, String, String)}, with visibility and enablement chosen. */
    public static LlmModel model(
        LlmConnection connection,
        String slug,
        String upstreamModelId,
        ModelVisibility visibility,
        boolean enabled
    ) {
        LlmModel model = new LlmModel();
        model.setConnection(connection);
        model.setSlug(slug);
        model.setDisplayName("Model " + slug);
        model.setUpstreamModelId(upstreamModelId);
        model.setVisibility(visibility);
        model.setEnabled(enabled);
        return model;
    }

    /**
     * What {@code LlmAdmissionService} hands a mentor turn: a resolved instance model carrying the
     * price frozen at admission. Input alone is priced, at $10 per million tokens, so a turn's
     * expected cost reads directly off its input-token count.
     *
     * <p>There is deliberately no unpriced variant. Admission refuses to start a turn without a price
     * snapshot, so a fixture that produced one would let tests exercise a state the system cannot
     * reach.
     */
    public static MentorLlmConfig admittedMentorConfig() {
        return new MentorLlmConfig(
            "openai-responses",
            BASE_URL,
            "test-model",
            null,
            null,
            false,
            FundingSource.INSTANCE,
            1L,
            1L,
            null,
            new LlmPriceSnapshot(
                FundingSource.INSTANCE,
                PricingState.PRICED,
                12L,
                null,
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            ),
            false,
            600
        );
    }
}
