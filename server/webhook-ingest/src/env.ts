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
	NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
	// RFC 6335: Valid port range is 1-65535 (0 is reserved)
	PORT: z.coerce.number().int().min(1).max(65535).default(4200),
	LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"]).default("info"),

	// NATS configuration
	// Supports: nats://host:port, tls://host:port, or comma-separated list
	// Examples: nats://localhost:4222, tls://secure.nats.io:4222, nats://a:4222,nats://b:4222
	NATS_URL: z
		.string()
		.refine(
			(url) => {
				const urls = url.split(",").map((u) => u.trim());
				const pattern = /^(nats|tls):\/\/[\w.-]+(:\d+)?$/;
				return urls.every((u) => pattern.test(u));
			},
			{
				message:
					"NATS_URL must be valid NATS URL(s) (nats://host:port or tls://host:port, comma-separated)",
			},
		)
		.default("nats://nats-server:4222"),
	NATS_AUTH_TOKEN: z.string().optional(),

	// Webhook secrets (HMAC-SHA256 requires minimum 32 bytes/256 bits per NIST SP 800-107)
	WEBHOOK_SECRET: z
		.string()
		.min(32, "WEBHOOK_SECRET must be at least 32 characters for HMAC-SHA256 security"),

	// Stream configuration with sensible bounds
	// Max age: 1 day minimum, 5 years maximum (reasonable retention window)
	STREAM_MAX_AGE_DAYS: z.coerce.number().int().min(1).max(1825).default(180),
	// Max messages: 1000 minimum, 100M maximum (prevent accidental 0 or absurd values)
	STREAM_MAX_MSGS: z.coerce.number().int().min(1_000).max(100_000_000).default(2_000_000),

	// Request limits (1-100MB reasonable for webhooks)
	MAX_PAYLOAD_SIZE_MB: z.coerce.number().int().min(1).max(100).default(25),

	// Publish behavior (keep webhook responses within GitHub/GitLab timeout windows)
	// GitHub timeout: 10s, GitLab timeout: configurable (default ~30s)
	// Timeout: 1s-30s (must fit within webhook provider timeout)
	NATS_PUBLISH_TIMEOUT_MS: z.coerce.number().int().min(1000).max(30000).default(9000),
	// Retries: 1-10 (exponential backoff means more than 10 is excessive)
	NATS_PUBLISH_MAX_RETRIES: z.coerce.number().int().min(1).max(10).default(5),
	// Base delay: 50ms-2000ms (too small causes thrashing, too large wastes time budget)
	NATS_PUBLISH_RETRY_BASE_DELAY_MS: z.coerce.number().int().min(50).max(2000).default(200),
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
