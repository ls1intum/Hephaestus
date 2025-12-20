import { azure } from "@ai-sdk/azure";
import { openai } from "@ai-sdk/openai";
import type { LanguageModelV3 } from "@ai-sdk/provider";
import { createProviderRegistry } from "ai";

/**
 * Provider registry for AI models.
 * Uses AI SDK's provider registry pattern for environment-based configuration.
 *
 * Supported providers:
 * - openai: Uses OPENAI_API_KEY environment variable
 * - azure: Uses AZURE_API_KEY and AZURE_RESOURCE_NAME environment variables
 * - fake: Placeholder for CI/OpenAPI generation (never actually called)
 *
 * @see https://ai-sdk.dev/docs/ai-sdk-core/provider-management
 */
const registry = createProviderRegistry({
	openai,
	azure,
});

/** List of supported provider names for validation */
export const SUPPORTED_PROVIDERS = ["openai", "azure", "fake"] as const;
export type SupportedProvider = (typeof SUPPORTED_PROVIDERS)[number];
type RealProvider = Exclude<SupportedProvider, "fake">;

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
export const getModel = (providerAndModel: string): LanguageModelV3 => {
	const colonIndex = providerAndModel.indexOf(":");
	if (colonIndex === -1) {
		throw new Error(
			`Invalid model format: "${providerAndModel}". Expected format: <provider>:<model> (e.g., "openai:gpt-4o")`,
		);
	}

	const provider = providerAndModel.slice(0, colonIndex);
	const model = providerAndModel.slice(colonIndex + 1);

	if (!(provider && model)) {
		throw new Error(
			`Invalid model format: "${providerAndModel}". Both provider and model name are required.`,
		);
	}

	if (!SUPPORTED_PROVIDERS.includes(provider as SupportedProvider)) {
		throw new Error(
			`Unsupported provider: "${provider}". Supported providers: ${SUPPORTED_PROVIDERS.join(", ")}`,
		);
	}

	// Fake provider returns a stub model for CI/OpenAPI generation
	// This model is never actually used - it's only needed to satisfy type requirements
	if (provider === "fake") {
		return createFakeModel(model);
	}

	return registry.languageModel(providerAndModel as `${RealProvider}:${string}`);
};

/**
 * Creates a fake language model for CI and OpenAPI generation.
 * This model should never be called in production - it will throw if invoked.
 */
function createFakeModel(modelId: string): LanguageModelV3 {
	const throwNotImplemented = (): never => {
		throw new Error(
			`Fake model "${modelId}" was invoked. This model is only for CI/OpenAPI generation and should never be called.`,
		);
	};

	// Minimal stub that satisfies LanguageModelV3 interface at runtime
	// This is never actually invoked - only needed for module loading during OpenAPI generation
	return {
		specificationVersion: "v3",
		provider: "fake",
		modelId,
		supportedUrls: [],
		doGenerate: throwNotImplemented,
		doStream: throwNotImplemented,
	} as unknown as LanguageModelV3;
}
