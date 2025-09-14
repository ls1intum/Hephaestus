import { LangfuseClient } from "@langfuse/client";
import env from "@/env";

let client: LangfuseClient | undefined;

export function getLangfuseClient(): LangfuseClient | undefined {
	if (
		!env.LANGFUSE_PUBLIC_KEY ||
		!env.LANGFUSE_SECRET_KEY ||
		!env.LANGFUSE_BASE_URL
	) {
		return undefined;
	}
	if (!client) {
		client = new LangfuseClient({
			publicKey: env.LANGFUSE_PUBLIC_KEY,
			secretKey: env.LANGFUSE_SECRET_KEY,
			baseUrl: env.LANGFUSE_BASE_URL,
		});
	}
	return client;
}
