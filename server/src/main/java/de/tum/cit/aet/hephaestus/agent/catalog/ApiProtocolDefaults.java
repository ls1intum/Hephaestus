package de.tum.cit.aet.hephaestus.agent.catalog;

/**
 * Default auth-header conventions per LLM wire protocol (#1368). Used to fill in
 * {@code authHeaderName}/{@code authValuePrefix} when a connection is created without them.
 */
final class ApiProtocolDefaults {

    /** Header name and value prefix a protocol expects for its credential. */
    record AuthDefaults(String headerName, String valuePrefix) {}

    private ApiProtocolDefaults() {}

    static AuthDefaults forProtocol(String apiProtocol) {
        return switch (apiProtocol) {
            case "anthropic-messages" -> new AuthDefaults("x-api-key", "");
            case "azure-openai-responses" -> new AuthDefaults("api-key", "");
            // openai-completions and openai-responses both use a Bearer token in Authorization.
            default -> new AuthDefaults("Authorization", "Bearer ");
        };
    }
}
