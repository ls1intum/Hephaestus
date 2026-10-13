package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmApiProtocol;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import java.util.Arrays;
import java.util.List;

/** The things a workspace runs an LLM for; at most one {@link WorkspaceAgentBinding} per purpose and data-handling tier. */
public enum AgentPurpose {
    /** Pull-request, issue, and conversation practice review. */
    PRACTICE_REVIEW(ModelKind.CHAT),
    /** Interactive mentor turns (web SSE and Slack). */
    MENTOR(ModelKind.CHAT),
    /** Closed questions that practice scripts ask within a practice review. */
    PRACTICE_DECISION(ModelKind.DECISION),
    /** Embeddings that practice scripts compute within a practice review. */
    PRACTICE_EMBEDDING(ModelKind.EMBEDDING),
    /** Reranking that practice scripts do within a practice review. */
    PRACTICE_RERANKING(ModelKind.RERANKING);

    private static final List<AgentPurpose> PRACTICE_SCRIPT = Arrays.stream(values())
            .filter(purpose -> purpose.kind != ModelKind.CHAT)
            .toList();

    private final ModelKind kind;

    AgentPurpose(ModelKind kind) {
        this.kind = kind;
    }

    /** The kind of model that this purpose calls. */
    public ModelKind kind() {
        return kind;
    }

    public boolean accepts(LlmApiProtocol protocol) {
        return protocol.serves(kind);
    }

    /** Every purpose that a model on {@code protocol} can serve. */
    public static List<AgentPurpose> servedBy(LlmApiProtocol protocol) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.accepts(protocol))
                .toList();
    }

    /**
     * The purposes that give a practice review its optional models for practice scripts, one per non-chat kind.
     * The chat model of practice scripts is the review's own model.
     */
    public static List<AgentPurpose> practiceScript() {
        return PRACTICE_SCRIPT;
    }
}
