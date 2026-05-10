// Compiles {{var}} prompts (defined inline in feature modules) and memoises by name.

import pino from "pino";
import {
	type ChatPromptDefinition,
	isChatMessage,
	isTextPromptDefinition,
	PROMPT_TYPES,
	type PromptChatMessage,
	type PromptDefinition,
	type PromptType,
	type PromptVariables,
	type ResolvedPrompt,
	type TextPromptDefinition,
} from "./types";

const logger = pino({ name: "prompt-loader" });

const promptCache = new Map<string, { prompt: ResolvedPrompt; promptType: PromptType }>();

function compileTextTemplate(template: string, variables: PromptVariables = {}): string {
	return template.replace(/\{\{(\w+)\}\}/g, (_match, key: string) => {
		const value = variables[key];
		if (value === undefined) {
			logger.error(
				{ key, template: template.slice(0, 100) },
				"MISSING PROMPT VARIABLE - check caller",
			);
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

export interface LoadPromptOptions {
	readonly skipCache?: boolean;
}

export function loadPrompt(
	definition: TextPromptDefinition,
	options?: LoadPromptOptions,
): ResolvedPrompt<typeof PROMPT_TYPES.TEXT>;
export function loadPrompt(
	definition: ChatPromptDefinition,
	options?: LoadPromptOptions,
): ResolvedPrompt<typeof PROMPT_TYPES.CHAT>;
export function loadPrompt<T extends PromptType>(
	definition: PromptDefinition<T>,
	options: LoadPromptOptions = {},
): ResolvedPrompt<T> {
	const { name } = definition;
	const promptType = definition.type as T;

	if (!options.skipCache) {
		const cached = promptCache.get(name);
		if (cached && cached.promptType === promptType) {
			return cached.prompt as ResolvedPrompt<T>;
		}
	}

	if (isTextPromptDefinition(definition)) {
		const resolved: ResolvedPrompt<typeof PROMPT_TYPES.TEXT> = {
			definition,
			config: definition.config ?? {},
			compile: (vars) => compileTextTemplate(definition.prompt, vars),
		};
		promptCache.set(name, { prompt: resolved, promptType: PROMPT_TYPES.TEXT });
		return resolved as ResolvedPrompt<T>;
	}

	const chatDef = definition as ChatPromptDefinition;
	const chatMessages = chatDef.prompt.filter(isChatMessage);
	const resolved: ResolvedPrompt<typeof PROMPT_TYPES.CHAT> = {
		definition: chatDef,
		config: chatDef.config ?? {},
		compile: (vars) => compileChatMessages(chatMessages, vars),
	};
	promptCache.set(name, { prompt: resolved, promptType: PROMPT_TYPES.CHAT });
	return resolved as ResolvedPrompt<T>;
}
