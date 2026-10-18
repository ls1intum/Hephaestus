import { isRetryableStatus, isTimeoutAbort, retrying } from "./pi-runner-retry.ts";
import { z } from "./work/practice-scripts/lib/core/practice-script.ts";

/** History that changed, or a write of another review with unknown outcome, under the public piece: nothing was prepared. */
export const PUBLICATION_REFUSALS = [
	"PUBLIC_HISTORY_CHANGED",
	"PUBLIC_DELIVERY_UNRESOLVED",
] as const;
export type PublicationRefusal = (typeof PUBLICATION_REFUSALS)[number];

export type FeedbackPreparation =
	| { state: "PREPARED"; receipt: FeedbackPreparationReceipt }
	| { state: "REFUSED"; publication?: PublicationRefusal }
	| { state: "UNAVAILABLE" };

const preparationReceiptSchema = z.strictObject({
	feedbackIds: z.array(z.uuid()),
	preparedAt: z.iso.datetime({ offset: true }),
	contentDigest: z.string().regex(/^[0-9a-f]{64}$/u),
});
export type FeedbackPreparationReceipt = z.infer<typeof preparationReceiptSchema>;

export interface FeedbackComposition {
	destination: "ON_WORK" | "PRACTICE_PAGE" | "CONVERSATION";
	resultIds: string[];
	summary: string | null;
	notes: { problemId: string; citationIndex: number; body: string }[];
	trace: {
		copies: {
			kind: "SUMMARY" | "LINE_NOTE" | "CHAT_TURN";
			position: number;
			basedOn: { resultId: string; problemId?: string }[];
		}[];
		withheld: {
			basedOn: { resultId: string; problemId?: string }[];
			reason: string;
			witnessIds: string[];
		}[];
		replacesId: string | null;
		primaryResultId: string | null;
	};
	page: { title: string; body: string; nextStep: string } | null;
	withheldReason: "COMPOSER_WITHHELD" | null;
}
export interface FeedbackPreparationRequest {
	reviewId: string;
	jobId: string;
	attempt: number;
	basisResultIds: string[];
	composition: FeedbackComposition[];
	publicComposition: boolean;
	publicationContext: string | null;
}

interface PreparationRequest {
	url: string;
	token: string;
	payload: FeedbackPreparationRequest;
	timeoutMs: number;
}

class PreparationError extends Error {
	override name = "PreparationError";
	readonly state: "REFUSED" | "UNAVAILABLE";
	readonly publication: PublicationRefusal | undefined;
	constructor(state: "REFUSED" | "UNAVAILABLE", message: string, publication?: PublicationRefusal) {
		super(message);
		this.state = state;
		this.publication = publication;
	}
}

function publicationRefusal(body: string): PublicationRefusal | undefined {
	try {
		const parsed: unknown = JSON.parse(body);
		const code =
			typeof parsed === "object" && parsed !== null && "code" in parsed ? parsed.code : undefined;
		return PUBLICATION_REFUSALS.find((refusal) => refusal === code);
	} catch {
		return undefined;
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
			if (response.status === 409) {
				// Only a typed publication refusal says that nothing was prepared and fresh history may help.
				const refusalBody = await response.text().catch(() => "");
				throw new PreparationError(
					"REFUSED",
					"Feedback preparation refused",
					publicationRefusal(refusalBody),
				);
			}
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
		const parsed = preparationReceiptSchema.safeParse(answer);
		if (!parsed.success) {
			throw new PreparationError("UNAVAILABLE", "Feedback preparation acknowledgement unavailable");
		}
		return parsed.data;
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
			return error.publication === undefined
				? { state: "REFUSED" }
				: { state: "REFUSED", publication: error.publication };
		}
		if (error instanceof PreparationError || isTimeoutAbort(error)) {
			return { state: "UNAVAILABLE" };
		}
		throw error;
	}
}
