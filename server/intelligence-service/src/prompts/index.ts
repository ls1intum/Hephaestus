/**
 * Centralized prompt management for the intelligence service.
 *
 * This module provides:
 * - Type definitions for prompts
 * - Loader with Langfuse integration and fallback
 * - All prompt definitions organized by feature
 *
 * @example
 * ```typescript
 * import { loadPrompt, badPracticeDetectorPrompt } from "@/prompts";
 *
 * const prompt = await loadPrompt(badPracticeDetectorPrompt);
 * const compiled = prompt.compile({ title: "Fix bug", description: "..." });
 * ```
 */

// Detector prompts - explicit export to avoid re-export all
export { badPracticeDetectorPrompt } from "./detector/bad-practice.prompt";
// Loader
export type { LoadPromptOptions } from "./loader";
export { clearPromptCache, getPromptCacheStats, loadPrompt, preloadPrompts } from "./loader";
// Mentor prompts
export { mentorChatPrompt } from "./mentor/chat.prompt";
// Types - explicit exports to avoid barrel file issues
export type {
	ChatMessage,
	PromptConfig,
	PromptDefinition,
	PromptType,
	PromptVariables,
	ResolvedPrompt,
} from "./types";
