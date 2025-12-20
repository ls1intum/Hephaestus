/**
 * Prompt type definitions for centralized prompt management.
 *
 * This module defines the structure for prompts that:
 * 1. Are version-controlled in git as the source of truth
 * 2. Can sync to Langfuse for editing and A/B testing
 * 3. Work without Langfuse (graceful fallback)
 *
 * NOTE: PromptChatMessage is distinct from AI SDK's UIMessage/ChatMessage.
 * This type is for Langfuse prompt templates (system/user/assistant with string content).
 * AI SDK's UIMessage is for runtime chat with typed parts (text, tool calls, etc.).
 */

import type { ChatPromptClient, TextPromptClient } from "@langfuse/client";

// ─────────────────────────────────────────────────────────────────────────────
// Prompt Types
// ─────────────────────────────────────────────────────────────────────────────

/** Prompt types supported by Langfuse */
export type PromptType = "text" | "chat";

/**
 * Chat message for Langfuse chat-type prompts.
 *
 * This is a simple role+content structure used in Langfuse prompt templates.
 * NOT to be confused with AI SDK's UIMessage which has typed parts.
 */
export interface PromptChatMessage {
	role: "system" | "user" | "assistant";
	content: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Prompt Config (Langfuse v4 Best Practices)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tool definition for storing function/tool parameters in Langfuse config.
 * Matches OpenAI function calling format.
 *
 * @see https://langfuse.com/docs/prompt-management/features/config#function-calling
 */
export interface PromptToolDefinition {
	type: "function";
	function: {
		name: string;
		description?: string;
		parameters: Record<string, unknown>;
	};
}

/**
 * Structured output response format for Langfuse config.
 *
 * @see https://langfuse.com/docs/prompt-management/features/config#structured-outputs
 */
export interface PromptResponseFormat {
	type: "json_schema";
	json_schema: {
		name: string;
		schema: Record<string, unknown>;
		strict?: boolean;
	};
}

/**
 * Model configuration attached to prompts.
 *
 * Following Langfuse v4 best practices, this config can store:
 * - Model parameters (model, temperature, maxTokens)
 * - Tool/function definitions for agents
 * - Response format for structured outputs
 *
 * Because config is versioned with the prompt, you can manage all parameters
 * in one place and update them without touching application code.
 *
 * @see https://langfuse.com/docs/prompt-management/features/config
 */
export interface PromptConfig {
	/** Model name (e.g., "gpt-4o-mini", "openai:gpt-4o") */
	model?: string;
	/** Temperature for generation (0-2) */
	temperature?: number;
	/** Maximum tokens to generate */
	maxTokens?: number;
	/** Maximum tool call steps for multi-step agents */
	maxToolSteps?: number;
	/**
	 * Tool definitions in OpenAI function format.
	 * Store tool schemas here for versioning alongside prompts.
	 */
	tools?: readonly PromptToolDefinition[] | PromptToolDefinition[];
	/**
	 * Tool choice strategy: auto, required, none, or specific tool.
	 */
	toolChoice?: "auto" | "required" | "none" | { type: "tool"; toolName: string };
	/**
	 * Response format for structured outputs.
	 * Use with json_schema type for guaranteed JSON structure.
	 */
	responseFormat?: PromptResponseFormat;
	/** Additional configuration values */
	[key: string]: unknown;
}

/** Variables that can be interpolated into the prompt: {{variable}} */
export type PromptVariables = Record<string, string>;

// ─────────────────────────────────────────────────────────────────────────────
// Prompt Definition (stored in git)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Base definition for all prompts stored in git.
 *
 * @example
 * ```typescript
 * export const badPracticeDetector: PromptDefinition<"text"> = {
 *   name: "bad-practice-detector",
 *   type: "text",
 *   prompt: "Analyze {{title}} for bad practices...",
 *   config: { model: "gpt-4o-mini", temperature: 0.2 },
 *   labels: ["production"],
 *   variables: ["title", "description"],
 * };
 * ```
 */
export interface PromptDefinition<T extends PromptType = PromptType> {
	/** Unique identifier used in Langfuse */
	name: string;

	/** Prompt type: "text" for simple strings, "chat" for message arrays */
	type: T;

	/** The prompt content - template string with {{variables}} */
	prompt: T extends "text" ? string : PromptChatMessage[];

	/** Description for documentation and Langfuse UI */
	description?: string;

	/** Optional model configuration */
	config?: PromptConfig;

	/** Labels for Langfuse deployment (e.g., ["production", "staging"]) */
	labels?: string[];

	/** Tags for organization in Langfuse */
	tags?: string[];

	/** List of expected variables for documentation */
	variables?: string[];

	/**
	 * Metadata for prompt management CLI.
	 * Used for automatic discovery and sync with Langfuse.
	 */
	_meta?: {
		/**
		 * Directory containing tool files (relative to src/).
		 * Each *.tool.ts in this directory exports a *Definition.
		 * @example "mentor/tools"
		 */
		toolsDir?: string;
	};
}

// ─────────────────────────────────────────────────────────────────────────────
// Resolved Prompt (ready for use)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A resolved prompt ready for use.
 *
 * Either fetched from Langfuse or using local fallback.
 * Provides a unified interface regardless of source.
 */
export interface ResolvedPrompt<T extends PromptType = PromptType> {
	/** Original definition from git */
	definition: PromptDefinition<T>;

	/** Langfuse prompt client for telemetry linking (undefined if using fallback) */
	langfusePrompt?: TextPromptClient | ChatPromptClient;

	/** Source of the resolved prompt */
	source: "langfuse" | "local";

	/** Langfuse version number (undefined if using fallback) */
	langfuseVersion?: number;

	/**
	 * Configuration from Langfuse or local definition.
	 *
	 * Use this to get model parameters, max steps, etc.
	 * Falls back to local definition.config if Langfuse unavailable.
	 */
	config: PromptConfig;

	/**
	 * Compile the prompt with variables.
	 *
	 * @param variables - Variables to interpolate into the template
	 * @returns Compiled prompt string (for text) or messages array (for chat)
	 */
	compile(variables?: PromptVariables): T extends "text" ? string : PromptChatMessage[];
}
