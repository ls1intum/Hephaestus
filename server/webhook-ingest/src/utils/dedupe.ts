import { createHash } from "node:crypto";

/**
 * Build a deterministic deduplication ID for JetStream.
 *
 * Falls back to hashing the raw payload to keep retries idempotent
 * when a provider delivery ID is missing.
 */
export function buildDedupeId(prefix: string, body: Uint8Array, extra?: string): string {
	const hash = createHash("sha256");
	hash.update(body);
	if (extra) {
		hash.update(extra);
	}
	return `${prefix}-${hash.digest("hex")}`;
}
