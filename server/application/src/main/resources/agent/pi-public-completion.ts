import { isRecord } from "./pi-result-normalize.ts";
import { isRetryableStatus, isTimeoutAbort, retrying } from "./pi-runner-retry.ts";

export type PublicCompletion =
	| { state: "ACCEPTED"; reviewDigest: string }
	| { state: "REFUSED" }
	| { state: "UNAVAILABLE" };

interface CompletionRequest {
	url: string;
	token: string;
	admissionDigest: string;
	review: unknown;
	/** Remaining run safety, capped by the admission response timeout at the caller. */
	timeoutMs: number;
}

class CompletionError extends Error {
	override name = "CompletionError";
	readonly state: "REFUSED" | "UNAVAILABLE";
	constructor(state: "REFUSED" | "UNAVAILABLE", message: string) {
		super(message);
		this.state = state;
	}
}

/** Submit one immutable public decision; an explicit refusal never becomes terminal fallback. */
export async function completePublicReview(request: CompletionRequest): Promise<PublicCompletion> {
	const body = JSON.stringify({
		schemaVersion: 1,
		admissionDigest: request.admissionDigest,
		review: request.review,
	});
	const deadline = Date.now() + request.timeoutMs;
	const post = async () => {
		const remaining = deadline - Date.now();
		if (remaining <= 0) {
			throw new DOMException("Public completion response deadline reached", "TimeoutError");
		}
		let response: Response;
		try {
			response = await fetch(request.url, {
				method: "POST",
				headers: { authorization: `Bearer ${request.token}`, "content-type": "application/json" },
				body,
				signal: AbortSignal.timeout(remaining),
			});
		} catch (error) {
			if (isTimeoutAbort(error)) {
				throw error;
			}
			throw new CompletionError("UNAVAILABLE", "Public completion transport unavailable");
		}
		if (isRetryableStatus(response.status)) {
			await response.body?.cancel().catch(() => {
				/* Closing an unavailable response cannot change its HTTP decision. */
			});
			throw new CompletionError("UNAVAILABLE", "Public completion server unavailable");
		}
		if (!response.ok) {
			// The HTTP refusal stays final even if closing its body loses the connection.
			await response.body?.cancel().catch(() => {
				/* Closing an unavailable response cannot change its HTTP decision. */
			});
			throw new CompletionError("REFUSED", "Public completion refused");
		}
		let answer: unknown;
		try {
			answer = await response.json();
		} catch {
			throw new CompletionError("UNAVAILABLE", "Public completion acknowledgement unavailable");
		}
		if (isRecord(answer) && answer.schemaVersion === 1 && answer.accepted === false) {
			throw new CompletionError("REFUSED", "Public completion refused");
		}
		if (
			!isRecord(answer) ||
			answer.schemaVersion !== 1 ||
			answer.accepted !== true ||
			typeof answer.reviewDigest !== "string" ||
			!/^[0-9a-f]{64}$/u.test(answer.reviewDigest)
		) {
			throw new CompletionError("UNAVAILABLE", "Public completion acknowledgement unavailable");
		}
		return answer.reviewDigest;
	};
	try {
		return {
			state: "ACCEPTED",
			reviewDigest: await retrying(
				post,
				(e) => e instanceof CompletionError && e.state === "UNAVAILABLE",
				{
					attempts: 4,
				},
			),
		};
	} catch (error) {
		if (error instanceof CompletionError && error.state === "REFUSED") {
			return { state: "REFUSED" };
		}
		if (error instanceof CompletionError || isTimeoutAbort(error)) {
			return { state: "UNAVAILABLE" };
		}
		throw error;
	}
}
