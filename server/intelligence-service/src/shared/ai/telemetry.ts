import { type ChatPromptClient, LangfuseClient, type TextPromptClient } from "@langfuse/client";
import env from "@/env";
import type { ResolvedPrompt } from "@/prompts/types";

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
	return Boolean(env.LANGFUSE_PUBLIC_KEY && env.LANGFUSE_SECRET_KEY && env.LANGFUSE_BASE_URL);
}

/**
 * Telemetry metadata for AI SDK calls.
 * Values must be primitive types (string, number, boolean, arrays).
 * Passed via experimental_telemetry.metadata to Langfuse.
 */
export type TelemetryMetadata = Record<string, string | number | boolean | string[] | number[]>;

/**
 * Build AI SDK telemetry options for LLM calls.
 *
 * Enables OpenTelemetry tracing with Langfuse integration.
 * When telemetry is disabled (missing env vars), returns undefined
 * so the spread operator becomes a no-op.
 *
 * @param metadata - Optional metadata to attach to the trace
 * @returns Telemetry options object or undefined
 *
 * @example
 * ```typescript
 * const result = streamText({
 *   model: env.defaultModel,
 *   ...getTelemetryOptions({ sessionId: threadId, userId: userLogin }),
 * });
 * ```
 *
 * @see https://langfuse.com/integrations/frameworks/vercel-ai-sdk
 */
export function getTelemetryOptions(metadata?: TelemetryMetadata) {
	if (!isTelemetryEnabled()) {
		return undefined;
	}
	return {
		experimental_telemetry: {
			isEnabled: true,
			metadata,
		},
	} as const;
}

/**
 * Build AI SDK telemetry options with a linked Langfuse prompt.
 *
 * When a prompt is provided, it is serialized and passed as metadata
 * so the LangfuseSpanProcessor can link the generation to the prompt.
 *
 * @param prompt - Langfuse prompt client to link
 * @param functionId - Function identifier used as the trace name in Langfuse
 * @param additionalMetadata - Additional metadata to merge
 * @returns Telemetry options object or undefined
 *
 * @see https://langfuse.com/integrations/frameworks/vercel-ai-sdk
 * @see https://ai-sdk.dev/docs/ai-sdk-core/telemetry
 */
export function getTelemetryOptionsWithPrompt(
	prompt: ChatPromptClient | TextPromptClient,
	functionId: string,
	additionalMetadata?: Omit<TelemetryMetadata, "langfusePrompt">,
) {
	if (!isTelemetryEnabled()) {
		return undefined;
	}
	return {
		experimental_telemetry: {
			isEnabled: true,
			// functionId sets the trace name in Langfuse (e.g., "mentor:chat")
			functionId,
			metadata: {
				// Pass the prompt object directly - Langfuse expects this format, NOT stringified
				// @see https://langfuse.com/integrations/frameworks/vercel-ai-sdk
				langfusePrompt: prompt.toJSON(),
				...additionalMetadata,
			},
		},
	} as const;
}

/**
 * Build AI SDK telemetry options from a resolved prompt.
 *
 * Handles both Langfuse-sourced and local prompts uniformly.
 * When a Langfuse prompt is available, it links the generation to the prompt
 * for versioning and A/B testing analytics.
 *
 * @param resolvedPrompt - Prompt loaded via loadPrompt()
 * @param functionId - Trace name in Langfuse (e.g., "mentor:chat", "detector:bad-practices")
 * @param metadata - Additional metadata to attach to the trace
 * @returns Telemetry options object or undefined
 *
 * @example
 * ```typescript
 * const prompt = await loadPrompt(mentorChatPrompt);
 * const telemetryOptions = buildTelemetryOptions(prompt, "mentor:chat", {
 *   sessionId: threadId,
 *   userId: userLogin,
 * });
 *
 * const result = streamText({
 *   model: env.defaultModel,
 *   ...telemetryOptions,
 * });
 * ```
 */
export function buildTelemetryOptions(
	resolvedPrompt: ResolvedPrompt,
	functionId: string,
	metadata: TelemetryMetadata,
) {
	if (!isTelemetryEnabled()) {
		return undefined;
	}

	// When Langfuse prompt is available, link it for analytics
	if (resolvedPrompt.langfusePrompt) {
		return getTelemetryOptionsWithPrompt(resolvedPrompt.langfusePrompt, functionId, {
			promptSource: resolvedPrompt.source,
			promptVersion: resolvedPrompt.langfuseVersion ?? 0,
			...metadata,
		});
	}

	// Local fallback - still enable telemetry but without prompt linking
	return {
		experimental_telemetry: {
			isEnabled: true,
			functionId,
			metadata: {
				promptSource: resolvedPrompt.source,
				...metadata,
			},
		},
	} as const;
}
