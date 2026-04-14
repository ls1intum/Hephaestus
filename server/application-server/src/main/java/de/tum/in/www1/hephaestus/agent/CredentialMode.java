package de.tum.in.www1.hephaestus.agent;

/**
 * Authentication mode for agent container access to LLM providers.
 *
 * <ul>
 *   <li>{@link #PROXY} — container routes through internal LLM proxy; no internet needed</li>
 *   <li>{@link #API_KEY} — container calls the provider directly with an API key; internet required</li>
 *   <li>{@link #OAUTH} — container calls the provider directly with an OAuth token; supported for Claude Code only</li>
 * </ul>
 */
public enum CredentialMode {
    PROXY,
    API_KEY,
    OAUTH,
}
