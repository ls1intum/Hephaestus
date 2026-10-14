package de.tum.cit.aet.hephaestus.agent.job;

public record ReviewJobLlmUsageDelta(
        long inputTokens, long outputTokens, long reasoningTokens, long cacheReadTokens, long cacheWriteTokens) {}
