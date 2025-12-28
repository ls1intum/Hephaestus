import { createHash } from "node:crypto";

/**
 * Build a deterministic deduplication ID for JetStream.
 *
 * Falls back to hashing the raw payload to keep retries idempotent
 * when a provider delivery ID is missing.
 *
 * Uses first 32 hex chars (128 bits) of SHA-256 for performance.
 * 128 bits provides ~2^64 collision resistance which is sufficient
 * for deduplication within NATS stream retention windows.
 */
export function buildDedupeId(prefix: string, body: Uint8Array, extra?: string): string {
	const hash = createHash("sha256");
	hash.update(body);
	if (extra) {
		hash.update(extra);
	}
	// Truncate to 32 chars (128 bits) - sufficient for deduplication
	return `${prefix}-${hash.digest("hex").slice(0, 32)}`;
}
