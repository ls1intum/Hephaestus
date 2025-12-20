/**
 * Prompt type definitions for centralized prompt management.
 * @see https://langfuse.com/docs/prompt-management/features/variables
 */

import type { ChatPromptClient, TextPromptClient } from "@langfuse/client";

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

export const PROMPT_TYPES = { TEXT: "text", CHAT: "chat" } as const;
export type PromptType = (typeof PROMPT_TYPES)[keyof typeof PROMPT_TYPES];

export const MESSAGE_ROLES = { SYSTEM: "system", USER: "user", ASSISTANT: "assistant" } as const;
export type MessageRole = (typeof MESSAGE_ROLES)[keyof typeof MESSAGE_ROLES];

// ─────────────────────────────────────────────────────────────────────────────
// Message Types
// ─────────────────────────────────────────────────────────────────────────────

export interface PromptChatMessage {
	readonly role: MessageRole;
	readonly content: string;
}

export interface PromptPlaceholderMessage {
	readonly type: "placeholder";
	readonly name: string;
}

export type ChatPromptMessage = PromptChatMessage | PromptPlaceholderMessage;

export function isChatMessage(msg: ChatPromptMessage): msg is PromptChatMessage {
	return "role" in msg && !("type" in msg);
}

export function isPlaceholderMessage(msg: ChatPromptMessage): msg is PromptPlaceholderMessage {
	return "type" in msg && msg.type === "placeholder";
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool Definitions
// ─────────────────────────────────────────────────────────────────────────────

export interface PromptToolDefinition {
	readonly type: "function";
	readonly function: {
		readonly name: string;
		readonly description?: string;
		readonly parameters: Readonly<Record<string, unknown>>;
	};
}

function isPromptToolDefinition(value: unknown): value is PromptToolDefinition {
	if (typeof value !== "object" || value === null) {
		return false;
	}
	const obj = value as Record<string, unknown>;
	if (obj.type !== "function" || typeof obj.function !== "object" || obj.function === null) {
		return false;
	}
	return typeof (obj.function as Record<string, unknown>).name === "string";
}

// ─────────────────────────────────────────────────────────────────────────────
// Prompt Config
// ─────────────────────────────────────────────────────────────────────────────

export interface PromptConfig {
	readonly model?: string;
	readonly temperature?: number;
	readonly maxTokens?: number;
	readonly maxToolSteps?: number;
	readonly tools?: readonly PromptToolDefinition[];
	readonly [key: string]: unknown;
}

export function getToolsFromConfig(config: PromptConfig): readonly PromptToolDefinition[] {
	const { tools } = config;
	if (!Array.isArray(tools)) {
		return [];
	}
	return tools.filter(isPromptToolDefinition);
}

// ─────────────────────────────────────────────────────────────────────────────
// Prompt Definition
// ─────────────────────────────────────────────────────────────────────────────

interface PromptDefinitionBase {
	readonly name: string;
	readonly description?: string;
	readonly config?: PromptConfig;
	readonly labels?: readonly string[];
	readonly tags?: readonly string[];
	readonly variables?: readonly string[];
	readonly _meta?: { readonly toolsDir?: string };
}

export interface TextPromptDefinition extends PromptDefinitionBase {
	readonly type: typeof PROMPT_TYPES.TEXT;
	readonly prompt: string;
}

export interface ChatPromptDefinition extends PromptDefinitionBase {
	readonly type: typeof PROMPT_TYPES.CHAT;
	readonly prompt: readonly ChatPromptMessage[];
}

export type PromptDefinition<T extends PromptType = PromptType> = T extends typeof PROMPT_TYPES.TEXT
	? TextPromptDefinition
	: T extends typeof PROMPT_TYPES.CHAT
		? ChatPromptDefinition
		: TextPromptDefinition | ChatPromptDefinition;

export function isTextPromptDefinition(def: PromptDefinition): def is TextPromptDefinition {
	return def.type === PROMPT_TYPES.TEXT;
}

// ─────────────────────────────────────────────────────────────────────────────
// Compile Result Types
// ─────────────────────────────────────────────────────────────────────────────

export type TextCompileResult = string;
export type ChatCompileResult = readonly PromptChatMessage[];
export type CompileResult<T extends PromptType> = T extends typeof PROMPT_TYPES.TEXT
	? TextCompileResult
	: ChatCompileResult;

// ─────────────────────────────────────────────────────────────────────────────
// Resolved Prompt
// ─────────────────────────────────────────────────────────────────────────────

export type PromptVariables = Readonly<Record<string, string>>;
export type PromptPlaceholders = Readonly<Record<string, readonly PromptChatMessage[]>>;

type CompileFunction<T extends PromptType> = (
	variables?: PromptVariables,
	placeholders?: PromptPlaceholders,
) => CompileResult<T>;

interface ResolvedPromptBase<T extends PromptType> {
	readonly definition: PromptDefinition<T>;
	readonly config: PromptConfig;
	readonly compile: CompileFunction<T>;
}

export interface LangfuseResolvedPrompt<T extends PromptType> extends ResolvedPromptBase<T> {
	readonly source: "langfuse";
	readonly langfusePrompt: T extends typeof PROMPT_TYPES.TEXT ? TextPromptClient : ChatPromptClient;
	readonly langfuseVersion: number;
}

export interface LocalResolvedPrompt<T extends PromptType> extends ResolvedPromptBase<T> {
	readonly source: "local";
	readonly langfusePrompt?: undefined;
	readonly langfuseVersion?: undefined;
}

export type ResolvedPrompt<T extends PromptType = PromptType> =
	| LangfuseResolvedPrompt<T>
	| LocalResolvedPrompt<T>;
