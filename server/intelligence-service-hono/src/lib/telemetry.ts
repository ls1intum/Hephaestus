import env from "@/env";
import { ChatPromptClient, TextPromptClient } from "@langfuse/client";

export function isTelemetryEnabled() {
	return Boolean(
		env.LANGFUSE_PUBLIC_KEY && env.LANGFUSE_SECRET_KEY && env.LANGFUSE_BASE_URL,
	);
}

/** Build AI SDK telemetry options, optionally linking a Langfuse prompt. */
export function buildTelemetryOptions(prompt: ChatPromptClient | TextPromptClient) {
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
