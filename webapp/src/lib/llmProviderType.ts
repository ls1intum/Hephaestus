/**
 * "Provider type" dropdown (#1368 glossary) — the human-facing choice — and its mapping onto the
 * server's `apiProtocol` wire values. Responses-vs-Completions is a defaulted sub-option under
 * OpenAI, never a top-level choice (glossary: "api_protocol").
 *
 * Shared by the instance-admin connection form and the workspace "Your AI provider" form so the two
 * cannot drift.
 */
export type ProviderTypeOption = "OPENAI" | "OPENAI_COMPATIBLE" | "ANTHROPIC" | "AZURE_OPENAI";

export const PROVIDER_TYPE_ORDER: readonly ProviderTypeOption[] = [
	"OPENAI",
	"OPENAI_COMPATIBLE",
	"ANTHROPIC",
	"AZURE_OPENAI",
];

export const PROVIDER_TYPE_LABELS: Record<ProviderTypeOption, string> = {
	OPENAI: "OpenAI",
	OPENAI_COMPATIBLE: "OpenAI-compatible (vLLM, Ollama, gateways)",
	ANTHROPIC: "Anthropic",
	AZURE_OPENAI: "Azure OpenAI",
};

/**
 * Passed as `Select`'s `items` prop so the trigger can render the selected label immediately —
 * without it, Base UI Select has no label to show until the matching item has mounted once.
 */
export const PROVIDER_TYPE_SELECT_ITEMS = PROVIDER_TYPE_ORDER.map((type) => ({
	value: type,
	label: PROVIDER_TYPE_LABELS[type],
}));

/**
 * The server-side wire protocol values (see `ApiProtocolDefaults` / the `apiProtocol` bean
 * validation regex). Kept in one place so a typo can't silently produce an unvalidated string.
 */
export const API_PROTOCOLS = {
	OPENAI_COMPLETIONS: "openai-completions",
	OPENAI_RESPONSES: "openai-responses",
	ANTHROPIC_MESSAGES: "anthropic-messages",
	AZURE_OPENAI_RESPONSES: "azure-openai-responses",
} as const;

/**
 * Default wire protocol for a provider type. OpenAI defaults to the Completions API; pass
 * `useResponsesApi` to opt into the Responses API sub-option (OpenAI only — compatible gateways
 * don't reliably speak it).
 */
export function defaultProtocolFor(type: ProviderTypeOption, useResponsesApi = false): string {
	switch (type) {
		case "ANTHROPIC":
			return API_PROTOCOLS.ANTHROPIC_MESSAGES;
		case "AZURE_OPENAI":
			return API_PROTOCOLS.AZURE_OPENAI_RESPONSES;
		case "OPENAI":
			return useResponsesApi ? API_PROTOCOLS.OPENAI_RESPONSES : API_PROTOCOLS.OPENAI_COMPLETIONS;
		case "OPENAI_COMPATIBLE":
			return API_PROTOCOLS.OPENAI_COMPLETIONS;
	}
}

/**
 * Best-effort reverse mapping for prefilling the dropdown on edit. "OpenAI" and "OpenAI-compatible"
 * both default to the same wire protocol (the split is a copy nicety, not a server concept), so an
 * existing Completions-style connection always resolves back to "OpenAI" here.
 */
export function providerTypeForProtocol(apiProtocol: string): ProviderTypeOption {
	switch (apiProtocol) {
		case API_PROTOCOLS.ANTHROPIC_MESSAGES:
			return "ANTHROPIC";
		case API_PROTOCOLS.AZURE_OPENAI_RESPONSES:
			return "AZURE_OPENAI";
		default:
			return "OPENAI";
	}
}

export function usesResponsesApi(apiProtocol: string): boolean {
	return apiProtocol === API_PROTOCOLS.OPENAI_RESPONSES;
}

/** Mirrors `ApiProtocolDefaults.forProtocol` server-side so the form can preview the default. */
export function authDefaultsForProtocol(apiProtocol: string): {
	headerName: string;
	valuePrefix: string;
} {
	switch (apiProtocol) {
		case API_PROTOCOLS.ANTHROPIC_MESSAGES:
			return { headerName: "x-api-key", valuePrefix: "" };
		case API_PROTOCOLS.AZURE_OPENAI_RESPONSES:
			return { headerName: "api-key", valuePrefix: "" };
		default:
			return { headerName: "Authorization", valuePrefix: "Bearer " };
	}
}
