package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import org.jspecify.annotations.Nullable;

/**
 * The effective, non-secret runtime shape of an LLM model after a binding is resolved — the single
 * thing the runtime needs to talk to a provider, independent of whether the model came from the
 * instance catalog or a workspace's own (BYO) catalog.
 *
 * <p>Deliberately carries NO credential. Records auto-generate {@code toString()} over every
 * component, so putting the API key here would leak it into any log line. The key is resolved
 * separately and live at the injection point (see {@code LlmModelResolver#resolveCredential}); this
 * also means credential rotation and revocation reach in-flight jobs instead of being frozen.
 *
 * <p>{@code apiProtocol} is Pi's own {@code api} token, stored and passed through verbatim
 * (e.g. {@code openai-completions}) — the Java server translates nothing.
 */
public record ResolvedLlmModel(
    String baseUrl,
    String apiProtocol,
    String authHeaderName,
    String authValuePrefix,
    @Nullable String azureApiVersion,
    String upstreamModelId,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    boolean supportsReasoning,
    @Nullable String cacheControlFormat,
    FundingSource fundingSource
) {}
