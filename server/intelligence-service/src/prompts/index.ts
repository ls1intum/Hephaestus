// Re-exports for prompts (definitions colocated with features).

export {
	greetingContinuePrompt,
	greetingFirstMessagePrompt,
	type MentorChatVariables,
	mentorChatPrompt,
	returningUserPrompt,
} from "@/mentor/chat.prompt";

export type { LoadPromptOptions } from "./loader";
export { loadPrompt } from "./loader";

export type {
	ChatPromptDefinition,
	ChatPromptMessage,
	CompileResult,
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
	PROMPT_TYPES,
} from "./types";
