package de.tum.cit.aet.hephaestus.agent.job;

public record AgentJobLlmUsageDelta(
        int inputTokens, int outputTokens, int reasoningTokens, int cacheReadTokens, int cacheWriteTokens) {}
