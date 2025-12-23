import path from "node:path";
import process from "node:process";
import { config } from "dotenv";
import { expand } from "dotenv-expand";
import { z } from "zod";

const ENV_FILE_PATH = process.env.NODE_ENV === "test" ? ".env.test" : ".env";

expand(
	config({
		path: path.resolve(process.cwd(), ENV_FILE_PATH),
	}),
);

const EnvSchema = z.object({
	NODE_ENV: z.string().default("development"),
	PORT: z.coerce.number().default(4200),
	LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"]).default("info"),

	// NATS configuration
	NATS_URL: z.string().url().default("nats://nats-server:4222"),
	NATS_AUTH_TOKEN: z.string().optional(),

	// Webhook secrets
	WEBHOOK_SECRET: z.string().min(1, "WEBHOOK_SECRET is required"),

	// Stream configuration
	STREAM_MAX_AGE_DAYS: z.coerce.number().default(180),
	STREAM_MAX_MSGS: z.coerce.number().default(2_000_000),

	// Request limits
	MAX_PAYLOAD_SIZE_MB: z.coerce.number().default(25),

	// Publish behavior (keep webhook responses within GitHub/GitLab timeout windows)
	NATS_PUBLISH_TIMEOUT_MS: z.coerce.number().default(9000),
	NATS_PUBLISH_MAX_RETRIES: z.coerce.number().default(5),
	NATS_PUBLISH_RETRY_BASE_DELAY_MS: z.coerce.number().default(200),
});

export type Env = z.infer<typeof EnvSchema>;

const parseResult = EnvSchema.safeParse(process.env);

if (!parseResult.success) {
	const errorTree = z.treeifyError(parseResult.error);
	const errorMessage = JSON.stringify(errorTree.properties, null, 2);
	throw new Error(`Invalid environment configuration:\n${errorMessage}`);
}

const env: Env = parseResult.data;

export default env;
