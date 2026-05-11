// Prompt types for the static prompt loader.

export const PROMPT_TYPES = { TEXT: "text", CHAT: "chat" } as const;
export type PromptType = (typeof PROMPT_TYPES)[keyof typeof PROMPT_TYPES];

const MESSAGE_ROLES = { SYSTEM: "system", USER: "user", ASSISTANT: "assistant" } as const;
export type MessageRole = (typeof MESSAGE_ROLES)[keyof typeof MESSAGE_ROLES];

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

interface PromptDefinitionBase {
	readonly name: string;
	readonly description?: string;
	readonly config?: PromptConfig;
	readonly variables?: readonly string[];
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

export type TextCompileResult = string;
export type ChatCompileResult = readonly PromptChatMessage[];
export type CompileResult<T extends PromptType> = T extends typeof PROMPT_TYPES.TEXT
	? TextCompileResult
	: ChatCompileResult;

export type PromptVariables = Readonly<Record<string, string>>;
export type PromptPlaceholders = Readonly<Record<string, readonly PromptChatMessage[]>>;

type CompileFunction<T extends PromptType> = (
	variables?: PromptVariables,
	placeholders?: PromptPlaceholders,
) => CompileResult<T>;

export interface ResolvedPrompt<T extends PromptType = PromptType> {
	readonly definition: PromptDefinition<T>;
	readonly config: PromptConfig;
	readonly compile: CompileFunction<T>;
}
