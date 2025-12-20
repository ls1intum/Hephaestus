/**
 * Prompt loader with Langfuse integration and local fallback.
 *
 * Provides a unified interface to:
 * 1. Fetch prompts from Langfuse (if available)
 * 2. Fall back to local definitions (always available)
 * 3. Cache prompts to reduce API calls
 * 4. Link prompts to Langfuse for telemetry
 */

import type { ChatPromptClient, TextPromptClient } from "@langfuse/client";
import pino from "pino";
import { isTelemetryEnabled, langfuse } from "@/shared/ai/telemetry";
import { extractErrorMessage } from "@/shared/utils";
import type {
	ChatMessage,
	PromptConfig,
	PromptDefinition,
	PromptType,
	PromptVariables,
	ResolvedPrompt,
} from "./types";

const logger = pino({ name: "prompt-loader" });

// ─────────────────────────────────────────────────────────────────────────────
// Cache
// ─────────────────────────────────────────────────────────────────────────────

/** Cache for loaded prompts to avoid repeated API calls */
const promptCache = new Map<string, ResolvedPrompt>();

/** Cache timestamps for TTL management */
const cacheTimestamps = new Map<string, number>();

/** Cache TTL in milliseconds (5 minutes) */
const CACHE_TTL_MS = 5 * 60 * 1000;

function isCacheValid(name: string): boolean {
	const timestamp = cacheTimestamps.get(name);
	if (!timestamp) {
		return false;
	}
	return Date.now() - timestamp < CACHE_TTL_MS;
}

// ─────────────────────────────────────────────────────────────────────────────
// Template Compilation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compile a prompt template by replacing {{variable}} placeholders.
 *
 * Supports Mustache-style syntax: {{variableName}}
 * Missing variables are kept as-is with a warning.
 */
function compileTemplate(template: string, variables: PromptVariables = {}): string {
	return template.replace(/\{\{(\w+)\}\}/g, (match, key: string) => {
		const value = variables[key];
		if (value === undefined) {
			logger.warn({ key, template: template.slice(0, 50) }, "Missing prompt variable");
			return match; // Keep original placeholder
		}
		return String(value);
	});
}

/**
 * Compile chat messages by replacing variables in each message.
 */
function compileChatMessages(
	messages: ChatMessage[],
	variables: PromptVariables = {},
): ChatMessage[] {
	return messages.map((m) => ({
		...m,
		content: compileTemplate(m.content, variables),
	}));
}

// ─────────────────────────────────────────────────────────────────────────────
// Loader Options
// ─────────────────────────────────────────────────────────────────────────────

export interface LoadPromptOptions {
	/** Skip cache and force fresh fetch */
	skipCache?: boolean;

	/** Langfuse label to fetch (default: "production") */
	label?: string;

	/** Specific version to fetch (overrides label) */
	version?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Loader
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Load a prompt from Langfuse with local fallback.
 *
 * Priority:
 * 1. Langfuse (if enabled and available)
 * 2. Local definition (always available)
 *
 * @example
 * ```typescript
 * import { badPracticeDetector } from "@/prompts/detector";
 *
 * const prompt = await loadPrompt(badPracticeDetector);
 * const compiled = prompt.compile({ title: "Fix bug", description: "..." });
 *
 * // Use langfusePrompt for telemetry linking
 * if (prompt.langfusePrompt) {
 *   const opts = getTelemetryOptionsWithPrompt(prompt.langfusePrompt, {...});
 * }
 * ```
 */
export async function loadPrompt<T extends PromptType>(
	definition: PromptDefinition<T>,
	options: LoadPromptOptions = {},
): Promise<ResolvedPrompt<T>> {
	const { name, type } = definition;
	const { skipCache = false, label = "production", version } = options;

	// Check cache first (unless skipped)
	if (!skipCache && isCacheValid(name)) {
		const cached = promptCache.get(name);
		if (cached) {
			logger.debug({ name, source: "cache" }, "Using cached prompt");
			return cached as ResolvedPrompt<T>;
		}
	}

	// Try Langfuse if enabled
	if (isTelemetryEnabled()) {
		try {
			// Fetch prompt from Langfuse with proper type inference
			// The SDK supports both "text" and "chat" types
			// @see https://langfuse.com/docs/prompt-management/get-started
			const langfusePrompt = await langfuse.prompt.get(name, {
				...(version !== undefined ? { version } : { label }),
				type: type as "text" | "chat",
				fallback: definition.prompt as string | ChatMessage[],
			});

			// Extract config from Langfuse or fall back to local definition
			const langfuseConfig = (langfusePrompt.config ?? {}) as PromptConfig;
			const resolvedConfig: PromptConfig = {
				...definition.config, // Local definition as base
				...langfuseConfig, // Langfuse overrides
			};

			const resolved: ResolvedPrompt<T> = {
				definition,
				langfusePrompt: langfusePrompt as TextPromptClient | ChatPromptClient,
				source: "langfuse",
				langfuseVersion: langfusePrompt.version,
				config: resolvedConfig,
				// Use Langfuse SDK's compile() for both text and chat prompts
				// The SDK handles variable substitution for all prompt types
				// @see https://langfuse.com/docs/prompt-management/features/variables
				compile: ((variables?: PromptVariables) => {
					return langfusePrompt.compile(variables);
				}) as ResolvedPrompt<T>["compile"],
			};

			// Update cache
			promptCache.set(name, resolved as ResolvedPrompt);
			cacheTimestamps.set(name, Date.now());

			logger.debug(
				{ name, source: "langfuse", version: langfusePrompt.version },
				"Loaded prompt from Langfuse",
			);
			return resolved;
		} catch (error) {
			logger.warn(
				{ name, error: extractErrorMessage(error) },
				"Failed to load from Langfuse, using local fallback",
			);
		}
	}

	// Use local fallback
	const resolved: ResolvedPrompt<T> = {
		definition,
		langfusePrompt: undefined,
		source: "local",
		config: definition.config ?? {},
		compile: ((variables?: PromptVariables) => {
			if (type === "text") {
				return compileTemplate(definition.prompt as string, variables);
			}
			return compileChatMessages(definition.prompt as ChatMessage[], variables);
		}) as ResolvedPrompt<T>["compile"],
	};

	// Cache local prompts too
	promptCache.set(name, resolved as ResolvedPrompt);
	cacheTimestamps.set(name, Date.now());

	logger.debug({ name, source: "local" }, "Using local prompt fallback");
	return resolved;
}

// ─────────────────────────────────────────────────────────────────────────────
// Cache Management
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Clear the prompt cache.
 *
 * Useful for:
 * - Testing
 * - Forcing refresh after updates
 * - Memory management
 */
export function clearPromptCache(): void {
	promptCache.clear();
	cacheTimestamps.clear();
	logger.debug("Prompt cache cleared");
}

/**
 * Preload multiple prompts at startup.
 *
 * Fetches all prompts in parallel and caches them.
 * Useful for warming the cache before serving traffic.
 */
export async function preloadPrompts(definitions: PromptDefinition[]): Promise<void> {
	const results = await Promise.allSettled(definitions.map((d) => loadPrompt(d)));

	const succeeded = results.filter((r) => r.status === "fulfilled").length;
	const failed = results.filter((r) => r.status === "rejected").length;

	logger.info({ total: definitions.length, succeeded, failed }, "Preloaded prompts");
}

/**
 * Get cache statistics for monitoring.
 */
export function getPromptCacheStats(): { size: number; entries: string[] } {
	return {
		size: promptCache.size,
		entries: Array.from(promptCache.keys()),
	};
}
