import { LangfuseSpanProcessor } from "@langfuse/otel";
import { NodeSDK } from "@opentelemetry/sdk-node";
import { isTelemetryEnabled } from "@/shared/ai/telemetry";

/**
 * OpenTelemetry instrumentation with Langfuse integration.
 *
 * This file initializes the OpenTelemetry SDK with the LangfuseSpanProcessor
 * to send traces to Langfuse for observability. The setup follows the Langfuse
 * TypeScript SDK v4 documentation pattern.
 *
 * Features:
 * - Idempotent initialization (safe for hot reload)
 * - Graceful shutdown to flush pending traces
 * - Force flush for serverless environments
 * - Automatic AI SDK trace capture via OpenTelemetry
 * - Service resource attributes via environment variables
 *
 * @see https://langfuse.com/docs/sdk/typescript/guide
 * @see https://langfuse.com/integrations/frameworks/vercel-ai-sdk
 */

// Service metadata is set via environment variables for OTEL
// OTEL_SERVICE_NAME and OTEL_SERVICE_VERSION are auto-detected by the SDK
// These can be set in the .env file or docker-compose

// SDK and processor instances for graceful shutdown/flush
let sdk: NodeSDK | undefined;
let spanProcessor: LangfuseSpanProcessor | undefined;
let started = false;

/**
 * Initialize OpenTelemetry with Langfuse span processor.
 * Idempotent - safe to call multiple times.
 */
export function initTelemetry(): void {
	if (started || !isTelemetryEnabled()) {
		return;
	}

	spanProcessor = new LangfuseSpanProcessor();
	sdk = new NodeSDK({
		// Resource attributes are configured via OTEL_SERVICE_NAME and OTEL_SERVICE_VERSION
		// environment variables, which the SDK auto-detects
		spanProcessors: [spanProcessor],
	});
	sdk.start();
	started = true;
}

/**
 * Force flush pending traces to Langfuse.
 * Useful for serverless environments or before graceful shutdown.
 *
 * @see https://langfuse.com/docs/observability/sdk/instrumentation#client-lifecycle--flushing
 */
export async function flushTelemetry(): Promise<void> {
	if (spanProcessor) {
		await spanProcessor.forceFlush();
	}
}

/**
 * Gracefully shutdown OpenTelemetry, flushing pending traces.
 * Call this on SIGTERM/SIGINT to ensure traces are not lost.
 */
export async function shutdownTelemetry(): Promise<void> {
	if (sdk) {
		await sdk.shutdown();
		sdk = undefined;
		spanProcessor = undefined;
		started = false;
	}
}

// Auto-initialize on module load (backwards compatible)
initTelemetry();
