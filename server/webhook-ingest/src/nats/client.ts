import type { JetStreamClient, JetStreamManager } from "@nats-io/jetstream";
import { jetstream, jetstreamManager } from "@nats-io/jetstream";
import type { MsgHdrs, NatsConnection } from "@nats-io/nats-core";
import { headers as createHeaders, nanos } from "@nats-io/nats-core";
import { connect } from "@nats-io/transport-node";

import env from "@/env";
import logger from "@/logger";

// Stream configuration matching legacy service (days to nanoseconds)
const streamMaxAgeNs = nanos(env.STREAM_MAX_AGE_DAYS * 24 * 60 * 60 * 1000);
const STREAM_MAX_MSGS = env.STREAM_MAX_MSGS;

// Retry configuration (bounded by webhook response timeout budget)
const MAX_RETRIES = env.NATS_PUBLISH_MAX_RETRIES;
const RETRY_BASE_DELAY_MS = env.NATS_PUBLISH_RETRY_BASE_DELAY_MS;
const PUBLISH_TIMEOUT_MS = env.NATS_PUBLISH_TIMEOUT_MS;

/**
 * Add jitter to retry delay to prevent thundering herd problem.
 * Uses ±25% jitter around the calculated delay (2025 best practice).
 * @see https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
 */
function addJitter(baseDelay: number): number {
	const jitterFactor = 0.25;
	const minDelay = baseDelay * (1 - jitterFactor);
	const maxDelay = baseDelay * (1 + jitterFactor);
	return Math.floor(minDelay + Math.random() * (maxDelay - minDelay));
}

export interface StreamConfig {
	name: string;
	subjects: string[];
}

const STREAMS: StreamConfig[] = [
	{ name: "github", subjects: ["github.>"] },
	{ name: "gitlab", subjects: ["gitlab.>"] },
];

class NATSClient {
	private nc: NatsConnection | null = null;
	private js: JetStreamClient | null = null;
	private jsm: JetStreamManager | null = null;
	private connectionHealthy = false;

	get isConnected(): boolean {
		return this.connectionHealthy && this.nc !== null && !this.nc.isClosed();
	}

	async connect(): Promise<void> {
		// Log only the host, not the full URL (which might contain credentials)
		const urlHosts = env.NATS_URL.split(",")
			.map((u) => {
				try {
					const parsed = new URL(u.trim());
					return parsed.host;
				} catch {
					return "[invalid]";
				}
			})
			.join(",");
		logger.info({ hosts: urlHosts }, "Connecting to NATS...");

		const connectOptions = {
			servers: env.NATS_URL,
			token: env.NATS_AUTH_TOKEN,
			maxReconnectAttempts: -1, // Unlimited reconnect attempts
			reconnectTimeWait: 2000,
		};

		this.nc = await connect(connectOptions);
		this.js = jetstream(this.nc);
		this.jsm = await jetstreamManager(this.nc);
		this.connectionHealthy = true;

		logger.info("Connected to NATS");

		// Set up event handlers for connection lifecycle
		this.nc
			.closed()
			.then(() => {
				this.connectionHealthy = false;
				logger.info("NATS connection closed");
			})
			.catch((error: unknown) => {
				this.connectionHealthy = false;
				logger.error({ error }, "NATS connection closed with error");
			});

		// Track connection status in the background
		this.trackStatus();

		// Initialize streams
		await this.initializeStreams();
	}

	private async initializeStreams(): Promise<void> {
		if (!this.jsm) {
			throw new Error("JetStream manager not initialized");
		}

		for (const streamConfig of STREAMS) {
			try {
				const stream = await this.jsm.streams.get(streamConfig.name);
				await stream.info();
				logger.info({ stream: streamConfig.name }, "Stream already exists");
			} catch (error: unknown) {
				// Check if error indicates stream doesn't exist (404) vs other errors
				const isNotFound =
					error instanceof Error &&
					(error.message.includes("stream not found") ||
						error.message.includes("404") ||
						(error as Error & { code?: string }).code === "404");

				if (isNotFound) {
					logger.info({ stream: streamConfig.name }, "Creating stream");
					await this.jsm.streams.add({
						name: streamConfig.name,
						subjects: streamConfig.subjects,
						retention: "limits", // RetentionPolicy.Limits
						discard: "old", // DiscardPolicy.Old
						storage: "file", // StorageType.File
						max_age: streamMaxAgeNs,
						max_msgs: STREAM_MAX_MSGS,
					});
					logger.info({ stream: streamConfig.name }, "Stream created");
				} else {
					// Unexpected error - rethrow
					logger.error({ stream: streamConfig.name, error }, "Failed to check stream status");
					throw error;
				}
			}
		}
	}

	private trackStatus(): void {
		const nc = this.nc;
		if (!nc) {
			return;
		}
		(async () => {
			for await (const status of nc.status()) {
				switch (status.type) {
					case "disconnect":
					case "reconnecting":
						this.connectionHealthy = false;
						logger.warn({ status }, "NATS connection interrupted");
						break;
					case "reconnect":
						this.connectionHealthy = true;
						logger.info({ status }, "NATS connection restored");
						break;
					case "error":
						logger.error({ status }, "NATS connection error");
						break;
					default:
						logger.debug({ status }, "NATS status update");
				}
			}
		})().catch((error) => {
			logger.error({ error }, "Failed to track NATS connection status");
		});
	}

	async publish(
		subject: string,
		message: Uint8Array,
		headerMap?: Map<string, string>,
	): Promise<void> {
		if (!this.js) {
			throw new Error("JetStream not initialized");
		}
		if (!this.isConnected) {
			throw new Error("NATS connection not healthy");
		}

		let msgHeaders: MsgHdrs | undefined;
		if (headerMap && headerMap.size > 0) {
			msgHeaders = createHeaders();
			for (const [key, value] of headerMap) {
				msgHeaders.set(key, value);
			}
		}

		const ack = await this.js.publish(subject, message, {
			headers: msgHeaders,
		});
		logger.debug({ subject, seq: ack.seq, stream: ack.stream }, "Published message");
	}

	private async publishWithTimeout(
		subject: string,
		message: Uint8Array,
		headerMap: Map<string, string> | undefined,
		timeoutMs: number,
	): Promise<void> {
		if (timeoutMs <= 0) {
			throw new Error("Publish timeout exceeded");
		}

		let timeoutId: NodeJS.Timeout | undefined;
		const timeoutPromise = new Promise<never>((_, reject) => {
			timeoutId = setTimeout(() => {
				reject(new Error("NATS publish timed out"));
			}, timeoutMs);
		});

		try {
			await Promise.race([this.publish(subject, message, headerMap), timeoutPromise]);
		} finally {
			if (timeoutId) {
				clearTimeout(timeoutId);
			}
		}
	}

	async publishWithRetry(
		subject: string,
		message: Uint8Array,
		headerMap?: Map<string, string>,
	): Promise<void> {
		const startedAt = Date.now();

		for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
			const elapsedMs = Date.now() - startedAt;
			const remainingMs = PUBLISH_TIMEOUT_MS - elapsedMs;
			if (remainingMs <= 0) {
				break;
			}

			try {
				await this.publishWithTimeout(subject, message, headerMap, remainingMs);
				return;
			} catch (error) {
				const isLastAttempt = attempt >= MAX_RETRIES - 1;
				const baseWait = RETRY_BASE_DELAY_MS * 2 ** attempt;
				const waitTime = Math.min(addJitter(baseWait), remainingMs);

				// Use WARN for retries, ERROR only on final failure
				const logMethod = isLastAttempt ? logger.error : logger.warn;
				logMethod.call(
					logger,
					{
						error,
						subject,
						attempt: attempt + 1,
						maxRetries: MAX_RETRIES,
						waitTimeMs: waitTime,
						remainingMs,
					},
					isLastAttempt
						? "NATS publish failed, no retries remaining"
						: "NATS publish failed, retrying...",
				);

				if (!isLastAttempt && waitTime > 0) {
					await new Promise((resolve) => setTimeout(resolve, waitTime));
				}
			}
		}

		throw new Error(`Failed to publish to ${subject} within ${PUBLISH_TIMEOUT_MS}ms`);
	}

	async close(): Promise<void> {
		if (this.nc) {
			await this.nc.drain();
			logger.info("NATS connection drained and closed");
			this.connectionHealthy = false;
			this.nc = null;
			this.js = null;
			this.jsm = null;
		}
	}
}

// Singleton instance
export const natsClient = new NATSClient();
