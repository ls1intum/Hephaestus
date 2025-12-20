/**
 * Centralized prompt management for the intelligence service.
 *
 * This module provides:
 * - Type definitions for prompts
 * - Loader with Langfuse integration and fallback
 * - Re-exports of all prompt definitions from their feature modules
 *
 * Prompts are COLOCATED with their features:
 * - src/mentor/chat.prompt.ts - Mentor chat system prompt
 * - src/detector/bad-practice.prompt.ts - Bad practice detector
 *
 * This barrel file re-exports them for convenience.
 *
 * @example
 * ```typescript
 * import { loadPrompt, badPracticeDetectorPrompt } from "@/prompts";
 *
 * const prompt = await loadPrompt(badPracticeDetectorPrompt);
 * const compiled = prompt.compile({ title: "Fix bug", description: "..." });
 * ```
 */

// ─────────────────────────────────────────────────────────────────────────────
// Prompt Definitions (re-exported from feature modules)
// ─────────────────────────────────────────────────────────────────────────────

// Detector feature
export { badPracticeDetectorPrompt } from "@/detector/bad-practice.prompt";
// Mentor feature - main prompt and sub-prompts
export {
	greetingContinuePrompt,
	greetingFirstMessagePrompt,
	type MentorChatVariables,
	mentorChatPrompt,
	mentorPrompts,
	returningUserPrompt,
} from "@/mentor/chat.prompt";

// ─────────────────────────────────────────────────────────────────────────────
// Loader API
// ─────────────────────────────────────────────────────────────────────────────

export type { LoadPromptOptions } from "./loader";
export { clearPromptCache, getPromptCacheStats, loadPrompt, preloadPrompts } from "./loader";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export type {
	ChatPromptMessage,
	PromptChatMessage,
	PromptConfig,
	PromptDefinition,
	PromptPlaceholderMessage,
	PromptPlaceholders,
	PromptToolDefinition,
	PromptType,
	PromptVariables,
	ResolvedPrompt,
} from "./types";
