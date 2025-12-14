import { LangfuseSpanProcessor } from "@langfuse/otel";
import { NodeSDK } from "@opentelemetry/sdk-node";
import env from "@/env";

/**
 * OpenTelemetry instrumentation with Langfuse integration.
 *
 * This file initializes the OpenTelemetry SDK with the LangfuseSpanProcessor
 * to send traces to Langfuse for observability. The setup follows the Langfuse
 * TypeScript SDK v4 documentation pattern.
 *
 * @see https://langfuse.com/docs/sdk/typescript/guide
 */

// Idempotent init in case of hot reload
let started = false;

function isLangfuseConfigured() {
	return Boolean(env.LANGFUSE_SECRET_KEY && env.LANGFUSE_PUBLIC_KEY && env.LANGFUSE_BASE_URL);
}

if (!started && isLangfuseConfigured()) {
	const sdk = new NodeSDK({
		spanProcessors: [new LangfuseSpanProcessor()],
	});
	sdk.start();
	started = true;
}
