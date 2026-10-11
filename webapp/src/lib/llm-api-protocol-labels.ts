import type { AgentBinding } from "@/api/types.gen";
import type { LlmApiProtocol } from "@/lib/llm-provider-type";

type AgentPurpose = AgentBinding["purpose"];

/** Where the connection form offers an API: with the chat APIs, or with the APIs for practice scripts. */
export type LlmApiProtocolGroup = "CHAT" | "PRACTICE_SCRIPT";

export interface LlmApiProtocolDef {
	label: string;
	group: LlmApiProtocolGroup;
	/** Shown under the API select while this option is chosen. */
	description?: string;
}

/** The one home for an API's words. Declaration order is the order the connection form offers. */
export const LLM_API_PROTOCOL_LABELS = {
	"openai-responses": { label: "Responses API", group: "CHAT" },
	"openai-completions": {
		label: "Chat Completions API",
		group: "CHAT",
		description:
			"Use only if the endpoint does not serve the Responses API. It can also serve the decision model if it returns token probabilities and answers without reasoning.",
	},
	"openai-decisions": {
		label: "Decisions API",
		group: "PRACTICE_SCRIPT",
	},
	"openai-embeddings": {
		label: "Embeddings API",
		group: "PRACTICE_SCRIPT",
	},
	"cohere-rerank": {
		label: "Rerank API (Cohere-compatible)",
		group: "PRACTICE_SCRIPT",
		description: "Choose a provider that reports token usage, or set No metered API cost.",
	},
} satisfies Record<LlmApiProtocol, LlmApiProtocolDef>;

const GROUP_LABELS = {
	CHAT: "Chat models",
	PRACTICE_SCRIPT: "For practice scripts",
} satisfies Record<LlmApiProtocolGroup, string>;

/** The API to connect for a purpose for practice scripts, which a picker with no such model names. */
const PRACTICE_SCRIPT_API: Partial<Record<AgentPurpose, LlmApiProtocol>> = {
	PRACTICE_DECISION: "openai-decisions",
	PRACTICE_EMBEDDING: "openai-embeddings",
	PRACTICE_RERANKING: "cohere-rerank",
};

const isProtocol = (key: string): key is LlmApiProtocol =>
	Object.hasOwn(LLM_API_PROTOCOL_LABELS, key);

const PROTOCOLS = Object.keys(LLM_API_PROTOCOL_LABELS).filter(isProtocol);

export const LLM_API_PROTOCOL_GROUPS = (["CHAT", "PRACTICE_SCRIPT"] as const).map((group) => ({
	group,
	label: GROUP_LABELS[group],
	protocols: PROTOCOLS.filter((protocol) => LLM_API_PROTOCOL_LABELS[protocol].group === group),
}));

export const LLM_API_PROTOCOL_SELECT_ITEMS = PROTOCOLS.map((protocol) => ({
	value: protocol,
	label: LLM_API_PROTOCOL_LABELS[protocol].label,
}));

export function llmApiProtocolDescription(protocol: LlmApiProtocol): string | undefined {
	const def: LlmApiProtocolDef = LLM_API_PROTOCOL_LABELS[protocol];
	return def.description;
}

/** True for an API that serves only practice scripts. */
export function isPracticeScriptApi(protocol: LlmApiProtocol): boolean {
	return LLM_API_PROTOCOL_LABELS[protocol].group === "PRACTICE_SCRIPT";
}

/** The API to connect for `purpose`; none for a chat purpose. */
export function practiceScriptApiFor(purpose: AgentPurpose): LlmApiProtocol | undefined {
	return PRACTICE_SCRIPT_API[purpose];
}

/**
 * What a test says when `{baseUrl}/models` does not exist. Many endpoints for practice scripts serve no model
 * list, but a wrong base URL path looks the same, so the words name both.
 */
export const UNLISTED_MODELS =
	"No model list at this address. Add the model by its ID. If its calls fail too, check the base URL.";

/** The answers with which a provider says `{baseUrl}/models` does not exist there. */
const NO_MODEL_LIST_STATUSES: ReadonlySet<number> = new Set([404, 405, 501]);

/**
 * Whether a failed test of an endpoint for practice scripts means only that it lists no models: many embedding,
 * decision and rerank endpoints serve no `{baseUrl}/models`. Any other failure, such as a wrong key
 * or a host that did not answer, is a real fault and keeps the server's own words.
 */
export function listsNoModels(
	protocol: LlmApiProtocol,
	probe: { reachable: boolean; statusCode?: number },
): boolean {
	return (
		isPracticeScriptApi(protocol) &&
		!probe.reachable &&
		probe.statusCode !== undefined &&
		NO_MODEL_LIST_STATUSES.has(probe.statusCode)
	);
}
