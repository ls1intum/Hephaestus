import { LangfuseSpanProcessor } from "@langfuse/otel";
import { NodeTracerProvider } from "@opentelemetry/sdk-trace-node";
import env from "@/env";

// Idempotent init in case of hot reload
let started = false;

function isLangfuseConfigured() {
	return Boolean(
		env.LANGFUSE_SECRET_KEY && env.LANGFUSE_PUBLIC_KEY && env.LANGFUSE_BASE_URL,
	);
}

if (!started && isLangfuseConfigured()) {
	const langfuseSpanProcessor = new LangfuseSpanProcessor();
	const tracerProvider = new NodeTracerProvider({
		spanProcessors: [langfuseSpanProcessor],
	});	
	tracerProvider.register();
	started = true;
}

