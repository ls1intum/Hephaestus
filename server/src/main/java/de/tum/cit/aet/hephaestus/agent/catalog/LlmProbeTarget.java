package de.tum.cit.aet.hephaestus.agent.catalog;

import org.jspecify.annotations.Nullable;

/**
 * Everything the "test connection" probe needs from a stored LLM connection, and nothing else
 *. Deliberately a projection rather than the entity: the probe makes a real outbound HTTP
 * call with a multi-second timeout, and it must do that with no transaction and no JDBC connection
 * held — enough admins probing one stalled provider would otherwise pin the whole pool. A detached
 * entity would work too, but a record makes it structurally impossible for the probe path to touch a
 * lazy association or write anything back.
 *
 * @param apiKey {@code null} for a deliberately keyless connection (self-hosted vLLM/Ollama gateway)
 */
public record LlmProbeTarget(String baseUrl, LlmAuthMode authMode, @Nullable String apiKey) {}
