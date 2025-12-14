import { azure } from "@ai-sdk/azure";
import { openai } from "@ai-sdk/openai";
import { createProviderRegistry } from "ai";

/**
 * Provider registry for AI models.
 * Uses AI SDK's provider registry pattern for environment-based configuration.
 *
 * Supported providers:
 * - openai: Uses OPENAI_API_KEY environment variable
 * - azure: Uses AZURE_API_KEY and AZURE_RESOURCE_NAME environment variables
 *
 * @see https://ai-sdk.dev/docs/ai-sdk-core/provider-management
 */
const registry = createProviderRegistry({
	openai,
	azure,
});

/** List of supported provider names for validation */
export const SUPPORTED_PROVIDERS = ["openai", "azure"] as const;
export type SupportedProvider = (typeof SUPPORTED_PROVIDERS)[number];

/**
 * Get a language model from the provider registry.
 *
 * @param providerAndModel - Model identifier in format "provider:model" (e.g., "openai:gpt-4o", "azure:gpt-4")
 * @returns The configured language model instance
 * @throws Error if the format is invalid or provider is not supported
 *
 * @example
 * ```typescript
 * const model = getModel("openai:gpt-4o");
 * const azureModel = getModel("azure:gpt-4");
 * ```
 */
export const getModel = (providerAndModel: string) => {
	const colonIndex = providerAndModel.indexOf(":");
	if (colonIndex === -1) {
		throw new Error(
			`Invalid model format: "${providerAndModel}". Expected format: <provider>:<model> (e.g., "openai:gpt-4o")`,
		);
	}

	const provider = providerAndModel.slice(0, colonIndex);
	const model = providerAndModel.slice(colonIndex + 1);

	if (!provider || !model) {
		throw new Error(
			`Invalid model format: "${providerAndModel}". Both provider and model name are required.`,
		);
	}

	if (!SUPPORTED_PROVIDERS.includes(provider as SupportedProvider)) {
		throw new Error(
			`Unsupported provider: "${provider}". Supported providers: ${SUPPORTED_PROVIDERS.join(", ")}`,
		);
	}

	return registry.languageModel(
		providerAndModel as `${SupportedProvider}:${string}`,
	);
};
