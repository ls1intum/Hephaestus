import { LangfuseSpanProcessor } from "@langfuse/otel";
import { NodeSDK } from "@opentelemetry/sdk-node";
import env from "@/env";

// Idempotent start in case of hot reload
let started = false;

function shouldEnableLangfuse() {
	return Boolean(
		env.LANGFUSE_SECRET_KEY && env.LANGFUSE_PUBLIC_KEY && env.LANGFUSE_BASE_URL,
	);
}

export let sdk: NodeSDK | undefined;

if (!started) {
	const spanProcessors = shouldEnableLangfuse()
		? [new LangfuseSpanProcessor()]
		: [];
	sdk = new NodeSDK({ spanProcessors });
	sdk.start();
	started = true;
}
