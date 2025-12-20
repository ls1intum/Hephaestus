/**
 * Prompt type definitions for centralized prompt management.
 *
 * This module defines the structure for prompts that:
 * 1. Are version-controlled in git as the source of truth
 * 2. Can sync to Langfuse for editing and A/B testing
 * 3. Work without Langfuse (graceful fallback)
 */

import type { ChatPromptClient, TextPromptClient } from "@langfuse/client";

// ─────────────────────────────────────────────────────────────────────────────
// Prompt Types
// ─────────────────────────────────────────────────────────────────────────────

/** Prompt types supported by Langfuse */
export type PromptType = "text" | "chat";

/** Chat message for chat-type prompts */
export interface ChatMessage {
	role: "system" | "user" | "assistant";
	content: string;
}

/** Model configuration attached to prompts */
export interface PromptConfig {
	/** Model name (e.g., "gpt-4o-mini", "openai:gpt-4o") */
	model?: string;
	/** Temperature for generation (0-2) */
	temperature?: number;
	/** Maximum tokens to generate */
	maxTokens?: number;
	/** Maximum tool call steps for multi-step agents */
	maxToolSteps?: number;
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
	prompt: T extends "text" ? string : ChatMessage[];

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
	compile(variables?: PromptVariables): T extends "text" ? string : ChatMessage[];
}
