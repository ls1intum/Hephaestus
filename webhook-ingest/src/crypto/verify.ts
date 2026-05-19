import { createHmac, timingSafeEqual } from "node:crypto";

/**
 * Verify GitHub webhook signature supporting SHA-256 and SHA-1 algorithms.
 *
 * Uses constant-time comparison to prevent timing attacks.
 *
 * @param signatureHeader - The X-Hub-Signature-256 or X-Hub-Signature header value
 * @param secret - The webhook secret configured in GitHub
 * @param body - The raw request body as bytes
 * @returns true if the signature is valid, false otherwise
 */
export function verifyGitHubSignature(
	signatureHeader: string | null | undefined,
	secret: string,
	body: Uint8Array,
): boolean {
	if (!(signatureHeader && secret)) {
		return false;
	}

	let algorithm: "sha256" | "sha1";
	let prefix: string;

	if (signatureHeader.startsWith("sha256=")) {
		algorithm = "sha256";
		prefix = "sha256=";
	} else if (signatureHeader.startsWith("sha1=")) {
		algorithm = "sha1";
		prefix = "sha1=";
	} else {
		return false;
	}

	const mac = createHmac(algorithm, secret);
	mac.update(Buffer.from(body));
	const expected = prefix + mac.digest("hex");

	// Use timing-safe comparison to prevent timing attacks
	const signatureBuffer = Buffer.from(signatureHeader);
	const expectedBuffer = Buffer.from(expected);

	if (signatureBuffer.length !== expectedBuffer.length) {
		return false;
	}

	return timingSafeEqual(signatureBuffer, expectedBuffer);
}

/**
 * Verify GitLab webhook token using constant-time comparison.
 *
 * GitLab sends the secret token in the X-GitLab-Token header
 * without HMAC signing.
 *
 * @param token - The X-GitLab-Token header value
 * @param secret - The expected webhook secret
 * @returns true if the token matches, false otherwise
 */
export function verifyGitLabToken(token: string | null | undefined, secret: string): boolean {
	if (!(token && secret)) {
		return false;
	}

	const tokenBuffer = Buffer.from(token);
	const secretBuffer = Buffer.from(secret);

	if (tokenBuffer.length !== secretBuffer.length) {
		return false;
	}

	return timingSafeEqual(tokenBuffer, secretBuffer);
}
