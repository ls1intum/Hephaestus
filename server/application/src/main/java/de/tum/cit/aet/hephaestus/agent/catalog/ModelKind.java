package de.tum.cit.aet.hephaestus.agent.catalog;

import java.util.Locale;

/**
 * What a model does with a request. Each {@link LlmApiProtocol} serves exactly one kind, and each
 * agent purpose needs exactly one kind.
 */
public enum ModelKind {
    /** Writes text from a conversation. The practice review and Heph run on it. */
    CHAT,
    /** Answers closed questions with a choice or a level. */
    DECISION,
    /** Turns text into vectors. */
    EMBEDDING,
    /** Orders documents by their relevance to a query. */
    RERANKING;

    /**
     * The name of this kind as a model for practice scripts slot: a key of the models for practice scripts file, an entry of
     * the practice script runner's {@code MODEL_SLOTS}, and the {@code {slot}} segment of the practice script proxy
     * paths.
     */
    public String slot() {
        return name().toLowerCase(Locale.ROOT);
    }
}
