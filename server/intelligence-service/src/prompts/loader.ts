/**
 * Prompt loader with Langfuse integration and local fallback.
 */

import { LRUCache } from "lru-cache";
import pino from "pino";
import { isTelemetryEnabled, langfuse } from "@/shared/ai/telemetry";
import { extractErrorMessage } from "@/shared/utils";
import {
	type ChatPromptDefinition,
	isChatMessage,
	isTextPromptDefinition,
	type LangfuseResolvedPrompt,
	type LocalResolvedPrompt,
	PROMPT_TYPES,
	type PromptChatMessage,
	type PromptConfig,
	type PromptDefinition,
	type PromptType,
	type PromptVariables,
	type ResolvedPrompt,
	type TextPromptDefinition,
} from "./types";

const logger = pino({ name: "prompt-loader" });

// ─────────────────────────────────────────────────────────────────────────────
// LRU Cache with proper eviction
// ─────────────────────────────────────────────────────────────────────────────

interface CacheEntry {
	readonly prompt: ResolvedPrompt;
	readonly promptType: PromptType;
}

/** TTL for cached prompts (5 minutes) */
const CACHE_TTL_MS = 5 * 60 * 1000;
/** Maximum number of prompts to cache */
const MAX_CACHE_SIZE = 100;

/**
 * LRU cache for prompts with TTL-based expiration.
 * Uses proper O(1) LRU eviction via lru-cache library.
 */
const promptCache = new LRUCache<string, CacheEntry>({
	max: MAX_CACHE_SIZE,
	ttl: CACHE_TTL_MS,
});

function getCachedPrompt<T extends PromptType>(
	name: string,
	expectedType: T,
): ResolvedPrompt<T> | undefined {
	const entry = promptCache.get(name);
	if (!entry) {
		return undefined;
	}
	// Type mismatch = cache miss
	if (entry.promptType !== expectedType) {
		promptCache.delete(name);
		return undefined;
	}
	return entry.prompt as ResolvedPrompt<T>;
}

function setCachedPrompt<T extends PromptType>(
	name: string,
	prompt: ResolvedPrompt<T>,
	promptType: T,
): void {
	promptCache.set(name, { prompt, promptType });
}

// ─────────────────────────────────────────────────────────────────────────────
// Template Compilation
// ─────────────────────────────────────────────────────────────────────────────

function compileTextTemplate(template: string, variables: PromptVariables = {}): string {
	return template.replace(/\{\{(\w+)\}\}/g, (_match, key: string) => {
		const value = variables[key];
		if (value === undefined) {
			// Log at error level to make missing variables very visible
			logger.error(
				{ key, template: template.slice(0, 100) },
				"MISSING PROMPT VARIABLE - check caller",
			);
			// Return a marker that will be visible in LLM output (not silent!)
			return `[MISSING:${key}]`;
		}
		return value;
	});
}

function compileChatMessages(
	messages: readonly PromptChatMessage[],
	variables: PromptVariables = {},
): readonly PromptChatMessage[] {
	return messages.map((msg) => ({
		role: msg.role,
		content: compileTextTemplate(msg.content, variables),
	}));
}

// ─────────────────────────────────────────────────────────────────────────────
// Config Helpers
// ─────────────────────────────────────────────────────────────────────────────

function mergeConfigs(
	localConfig: PromptConfig | undefined,
	langfuseConfig: unknown,
): PromptConfig {
	const remote =
		typeof langfuseConfig === "object" && langfuseConfig !== null
			? (langfuseConfig as Record<string, unknown>)
			: {};
	return { ...localConfig, ...remote };
}

// ─────────────────────────────────────────────────────────────────────────────
// Loader Options
// ─────────────────────────────────────────────────────────────────────────────

export interface LoadPromptOptions {
	readonly skipCache?: boolean;
	readonly label?: string;
	readonly version?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Loader
// ─────────────────────────────────────────────────────────────────────────────

export async function loadPrompt(
	definition: TextPromptDefinition,
	options?: LoadPromptOptions,
): Promise<ResolvedPrompt<typeof PROMPT_TYPES.TEXT>>;

export async function loadPrompt(
	definition: ChatPromptDefinition,
	options?: LoadPromptOptions,
): Promise<ResolvedPrompt<typeof PROMPT_TYPES.CHAT>>;

export async function loadPrompt<T extends PromptType>(
	definition: PromptDefinition<T>,
	options: LoadPromptOptions = {},
): Promise<ResolvedPrompt<T>> {
	const { name } = definition;
	const { skipCache = false, label = "production", version } = options;
	const promptType = definition.type as T;

	if (!skipCache) {
		const cached = getCachedPrompt<T>(name, promptType);
		if (cached) {
			logger.debug({ name, source: "cache" }, "Using cached prompt");
			return cached;
		}
	}

	if (isTelemetryEnabled()) {
		try {
			const baseOptions = version !== undefined ? { version } : { label };

			if (isTextPromptDefinition(definition)) {
				const langfusePrompt = await langfuse.prompt.get(name, {
					...baseOptions,
					type: "text",
					fallback: definition.prompt,
				});
				const config = mergeConfigs(definition.config, langfusePrompt.config);
				const resolved: LangfuseResolvedPrompt<typeof PROMPT_TYPES.TEXT> = {
					definition,
					langfusePrompt,
					source: "langfuse",
					langfuseVersion: langfusePrompt.version,
					config,
					compile: (vars) => langfusePrompt.compile(vars),
				};
				setCachedPrompt(name, resolved, PROMPT_TYPES.TEXT);
				logger.debug(
					{ name, source: "langfuse", version: langfusePrompt.version },
					"Loaded prompt",
				);
				return resolved as ResolvedPrompt<T>;
			}

			const chatDef = definition as ChatPromptDefinition;
			const chatMessages = chatDef.prompt.filter(isChatMessage);
			const langfusePrompt = await langfuse.prompt.get(name, {
				...baseOptions,
				type: "chat",
				fallback: chatMessages.map((m) => ({ role: m.role, content: m.content })),
			});
			const config = mergeConfigs(chatDef.config, langfusePrompt.config);
			const resolved: LangfuseResolvedPrompt<typeof PROMPT_TYPES.CHAT> = {
				definition: chatDef,
				langfusePrompt,
				source: "langfuse",
				langfuseVersion: langfusePrompt.version,
				config,
				compile: (vars) =>
					langfusePrompt
						.compile(vars)
						.filter(
							(msg): msg is PromptChatMessage =>
								typeof msg === "object" &&
								msg !== null &&
								"role" in msg &&
								"content" in msg &&
								typeof msg.role === "string" &&
								typeof msg.content === "string",
						),
			};
			setCachedPrompt(name, resolved, PROMPT_TYPES.CHAT);
			logger.debug({ name, source: "langfuse", version: langfusePrompt.version }, "Loaded prompt");
			return resolved as ResolvedPrompt<T>;
		} catch (error) {
			logger.warn({ name, error: extractErrorMessage(error) }, "Langfuse failed, using local");
		}
	}

	// Local fallback
	if (isTextPromptDefinition(definition)) {
		const resolved: LocalResolvedPrompt<typeof PROMPT_TYPES.TEXT> = {
			definition,
			source: "local",
			config: definition.config ?? {},
			compile: (vars) => compileTextTemplate(definition.prompt, vars),
		};
		setCachedPrompt(name, resolved, PROMPT_TYPES.TEXT);
		return resolved as ResolvedPrompt<T>;
	}

	const chatDef = definition as ChatPromptDefinition;
	const chatMessages = chatDef.prompt.filter(isChatMessage);
	const resolved: LocalResolvedPrompt<typeof PROMPT_TYPES.CHAT> = {
		definition: chatDef,
		source: "local",
		config: chatDef.config ?? {},
		compile: (vars) => compileChatMessages(chatMessages, vars),
	};
	setCachedPrompt(name, resolved, PROMPT_TYPES.CHAT);
	return resolved as ResolvedPrompt<T>;
}

// ─────────────────────────────────────────────────────────────────────────────
// Cache Management (for testing)
// ─────────────────────────────────────────────────────────────────────────────

export function clearPromptCache(): void {
	promptCache.clear();
}

export function getPromptCacheStats(): { size: number; entries: string[] } {
	return { size: promptCache.size, entries: Array.from(promptCache.keys()) };
}

export async function preloadPrompts(
	definitions: readonly PromptDefinition[],
	options: LoadPromptOptions = {},
): Promise<void> {
	await Promise.all(
		definitions.map((def) => {
			if (def.type === "text") {
				return loadPrompt(def, options);
			}
			return loadPrompt(def, options);
		}),
	);
}
