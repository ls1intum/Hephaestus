/**
 * Centralized prompt management for the intelligence service.
 *
 * Prompts are COLOCATED with their features:
 * - src/mentor/chat.prompt.ts - Mentor chat system prompt
 * - src/detector/bad-practice.prompt.ts - Bad practice detector
 */

// Prompt definitions (re-exported from feature modules)
export { badPracticeDetectorPrompt } from "@/detector/bad-practice.prompt";
export {
	greetingContinuePrompt,
	greetingFirstMessagePrompt,
	type MentorChatVariables,
	mentorChatPrompt,
	mentorPrompts,
	returningUserPrompt,
} from "@/mentor/chat.prompt";

// Loader
export type { LoadPromptOptions } from "./loader";
export { loadPrompt } from "./loader";

// Types
export type {
	ChatPromptDefinition,
	ChatPromptMessage,
	CompileResult,
	LangfuseResolvedPrompt,
	LocalResolvedPrompt,
	PromptChatMessage,
	PromptConfig,
	PromptDefinition,
	PromptPlaceholderMessage,
	PromptPlaceholders,
	PromptToolDefinition,
	PromptType,
	PromptVariables,
	ResolvedPrompt,
	TextPromptDefinition,
} from "./types";

export {
	getToolsFromConfig,
	isChatMessage,
	isPlaceholderMessage,
	isTextPromptDefinition,
	MESSAGE_ROLES,
	PROMPT_TYPES,
} from "./types";
