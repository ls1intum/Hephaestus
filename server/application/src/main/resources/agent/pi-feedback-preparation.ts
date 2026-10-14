import { isRecord } from "./pi-result-normalize.ts";
import { isRetryableStatus, isTimeoutAbort, retrying } from "./pi-runner-retry.ts";

export type FeedbackPreparation =
	| { state: "PREPARED"; receipt: FeedbackPreparationReceipt }
	| { state: "REFUSED" }
	| { state: "UNAVAILABLE" };

export interface FeedbackPreparationReceipt {
	feedbackIds: string[];
	preparedAt: string;
	contentDigest: string;
}

export interface FeedbackComposition {
	destination: "ON_WORK" | "PRACTICE_PAGE" | "CONVERSATION";
	resultIds: string[];
	summary: string | null;
	notes: { problemId: string; body: string }[];
	withheldReason: "COMPOSER_WITHHELD" | null;
}
export interface FeedbackPreparationRequest {
	reviewId: string;
	jobId: string;
	attempt: number;
	basisResultIds: string[];
	composition: FeedbackComposition[];
}

interface PreparationRequest {
	url: string;
	token: string;
	payload: FeedbackPreparationRequest;
	/** Remaining run safety, capped by the admission response timeout at the caller. */
	timeoutMs: number;
}

class PreparationError extends Error {
	override name = "PreparationError";
	readonly state: "REFUSED" | "UNAVAILABLE";
	constructor(state: "REFUSED" | "UNAVAILABLE", message: string) {
		super(message);
		this.state = state;
	}
}

/** Submit one immutable feedback proposal; an explicit refusal never becomes terminal fallback. */
export async function prepareFeedback(request: PreparationRequest): Promise<FeedbackPreparation> {
	const body = JSON.stringify(request.payload);
	const deadline = Date.now() + request.timeoutMs;
	const post = async () => {
		const remaining = deadline - Date.now();
		if (remaining <= 0) {
			throw new DOMException("Feedback preparation response deadline reached", "TimeoutError");
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
			throw new PreparationError("UNAVAILABLE", "Feedback preparation transport unavailable");
		}
		if (isRetryableStatus(response.status)) {
			await response.body?.cancel().catch(() => {
				/* Closing an unavailable response cannot change its HTTP decision. */
			});
			throw new PreparationError("UNAVAILABLE", "Feedback preparation server unavailable");
		}
		if (!response.ok) {
			// The HTTP refusal stays final even if closing its body loses the connection.
			await response.body?.cancel().catch(() => {
				/* Closing an unavailable response cannot change its HTTP decision. */
			});
			throw new PreparationError("REFUSED", "Feedback preparation refused");
		}
		let answer: unknown;
		try {
			answer = await response.json();
		} catch {
			throw new PreparationError("UNAVAILABLE", "Feedback preparation acknowledgement unavailable");
		}
		if (
			!isRecord(answer) ||
			!Array.isArray(answer.feedbackIds) ||
			!answer.feedbackIds.every((id) => typeof id === "string" && id !== "") ||
			typeof answer.preparedAt !== "string" ||
			!Number.isFinite(Date.parse(answer.preparedAt)) ||
			typeof answer.contentDigest !== "string" ||
			!/^[0-9a-f]{64}$/u.test(answer.contentDigest)
		) {
			throw new PreparationError("UNAVAILABLE", "Feedback preparation acknowledgement unavailable");
		}
		return {
			feedbackIds: answer.feedbackIds,
			preparedAt: answer.preparedAt,
			contentDigest: answer.contentDigest,
		};
	};
	try {
		return {
			state: "PREPARED",
			receipt: await retrying(
				post,
				(e) => e instanceof PreparationError && e.state === "UNAVAILABLE",
				{
					attempts: 4,
				},
			),
		};
	} catch (error) {
		if (error instanceof PreparationError && error.state === "REFUSED") {
			return { state: "REFUSED" };
		}
		if (error instanceof PreparationError || isTimeoutAbort(error)) {
			return { state: "UNAVAILABLE" };
		}
		throw error;
	}
}
