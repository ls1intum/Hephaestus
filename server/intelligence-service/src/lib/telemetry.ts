import {
	type ChatPromptClient,
	LangfuseClient,
	type TextPromptClient,
} from "@langfuse/client";
import env from "@/env";

/**
 * Langfuse client for prompt management.
 *
 * Uses environment variables for configuration:
 * - LANGFUSE_SECRET_KEY
 * - LANGFUSE_PUBLIC_KEY
 * - LANGFUSE_BASE_URL
 *
 * @see https://langfuse.com/docs/prompt-management/get-started
 */
export const langfuse = new LangfuseClient();

export function isTelemetryEnabled() {
	return Boolean(
		env.LANGFUSE_PUBLIC_KEY && env.LANGFUSE_SECRET_KEY && env.LANGFUSE_BASE_URL,
	);
}

/**
 * Build AI SDK telemetry options, optionally linking a Langfuse prompt.
 *
 * When a prompt is provided, it is serialized and passed as metadata
 * so the LangfuseSpanProcessor can link the generation to the prompt.
 *
 * @see https://langfuse.com/integrations/frameworks/vercel-ai-sdk
 */
export function buildTelemetryOptions(
	prompt: ChatPromptClient | TextPromptClient,
) {
	if (!isTelemetryEnabled()) return undefined;
	return {
		experimental_telemetry: {
			isEnabled: true,
			// AI SDK Telemetry attributes must be primitive/array values.
			// Pass Langfuse prompt metadata as JSON string for the LangfuseSpanProcessor to link.
			metadata: prompt
				? { langfusePrompt: JSON.stringify(prompt.toJSON()) }
				: undefined,
		},
	} as const;
}
