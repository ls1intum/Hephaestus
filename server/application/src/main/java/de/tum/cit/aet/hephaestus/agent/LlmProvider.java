package de.tum.cit.aet.hephaestus.agent;

/**
 * LLM API providers that agent containers can connect to via the LLM proxy.
 */
public enum LlmProvider {
    ANTHROPIC,
    OPENAI,
    AZURE_OPENAI,
}
