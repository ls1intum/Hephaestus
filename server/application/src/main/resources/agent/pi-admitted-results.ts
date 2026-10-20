import { errorText } from "./pi-error-text.ts";
import { readPracticePageSupport, readConversationEligibility } from "./pi-runner-composition.ts";
import {
	answerText,
	causeText,
	isRetryableStatus,
	isTimeoutAbort,
	retrying,
} from "./pi-runner-retry.ts";
import { OUTCOMES, type Outcome } from "./work/practice-scripts/lib/core/contract.ts";
import { z } from "./work/practice-scripts/lib/core/practice-script.ts";

const citationsSchema = z
	.array(z.looseObject({}))
	.transform((citations) => citations.map((citation, index) => Object.assign(citation, { index })));
/** Validate admission identity and outcome; retain server-owned details for composition. */
export const admittedResultSchema = z
	.looseObject({
		resultId: z.uuid(),
		practiceSlug: z.string().min(1),
		outcome: z.enum(OUTCOMES),
		evidence: z.looseObject({ citations: citationsSchema }),
		problems: z.array(z.looseObject({ id: z.string().min(1), citations: citationsSchema })),
	})
	.refine((result) => (result.outcome === "NOT_MET") === result.problems.length > 0, {
		message: "Only NOT_MET results have problems",
	})
	.transform(({ problems, evidence, ...result }) =>
		Object.assign(result, {
			evidence,
			citations: evidence.citations,
			problems: problems.map(({ id, ...problem }) => Object.assign(problem, { problemId: id })),
		}),
	);
export type AdmittedResult = z.infer<typeof admittedResultSchema>;
type AdmittedCitation = AdmittedResult["citations"][number];

interface LeanCitation {
	index: number;
	sourceKind: unknown;
	path: unknown;
	side: unknown;
	startLine: unknown;
	endLine: unknown;
	anchorable: unknown;
}

export interface LeanResult {
	resultId: string;
	practiceSlug: string;
	outcome: Outcome;
	citations: LeanCitation[];
	problems: {
		problemId: string;
		severity: unknown;
		anchorable: unknown;
		citations: LeanCitation[];
	}[];
}

function leanCitation(citation: AdmittedCitation): LeanCitation {
	return {
		index: citation.index,
		sourceKind: citation.sourceKind,
		path: citation.path,
		side: citation.side,
		startLine: citation.startLine,
		endLine: citation.endLine,
		anchorable: citation.anchorable,
	};
}

export function resultsById(results: readonly AdmittedResult[]): Map<string, AdmittedResult> {
	return new Map(results.map((result) => [result.resultId, result]));
}

export function leanResults(results: readonly AdmittedResult[]): LeanResult[] {
	return results.map((result) => ({
		resultId: result.resultId,
		practiceSlug: result.practiceSlug,
		outcome: result.outcome,
		citations: result.citations.map(leanCitation),
		problems: result.problems.map((problem) => ({
			problemId: problem.problemId,
			severity: problem.severity,
			anchorable: problem.anchorable,
			citations: problem.citations.map(leanCitation),
		})),
	}));
}

const instant = z.iso.datetime({ offset: true });
const currentness = z.enum(["CURRENT", "STALE", "UNVERIFIABLE"]);
const historyBasisSchema = z.object({
	resultId: z.uuid(),
	practiceSlug: z.string().min(1),
	practiceVersionId: z.number().int().nullable(),
	outcome: z.enum(OUTCOMES),
});
/** One bounded server read of earlier private feedback; absent or null means it was not read for this review. */
const privateHistorySchema = z.object({
	readAt: instant,
	coverage: z.object({
		maxEntries: z.number().int().positive(),
		hasMore: z.boolean(),
		omittedWithoutCopyAuthority: z.number().int().nonnegative(),
	}),
	delivered: z.array(
		z.object({
			id: z.uuid(),
			destination: z.enum(["PRACTICE_PAGE", "CONVERSATION"]),
			deliveredAt: instant,
			recordedClaimCurrentness: currentness,
			withdrawn: z.boolean(),
			basedOn: z.array(historyBasisSchema),
			bodyRole: z.enum(["PRACTICE_PAGE_CARD", "CONVERSATION_WORDS"]).nullable(),
			body: z.string().nullable(),
		}),
	),
	queued: z.array(
		z.object({
			threadKey: z.uuid(),
			feedbackId: z.uuid(),
			destination: z.literal("CONVERSATION"),
			practiceSlug: z.string().min(1),
			preparedAt: instant,
			recordedClaimCurrentness: currentness,
			basedOn: z.array(historyBasisSchema),
			bodyRole: z.literal("MENTOR_NOTES").nullable(),
			body: z.string().nullable(),
		}),
	),
});
export type PrivateHistory = z.infer<typeof privateHistorySchema>;
const count = z.number().int().nonnegative();

export const admissionResponseSchema = z.looseObject({
	contractVersion: z.literal(4),
	admissionDigest: z.string().regex(/^[0-9a-f]{64}$/u),
	admitted: z.array(admittedResultSchema),
	withheld: z.array(z.object({ practiceSlug: z.string(), reason: z.string() })),
	publicReviewed: z.boolean(),
	privateProgress: z.object({
		decidedResultIds: z.array(z.uuid()),
		practicePageDeliveries: count,
		conversationDeliveries: count,
	}),
	practicePageSupport: z.unknown().nullable(),
	conversationEligibility: z.unknown().nullable(),
	privateHistory: privateHistorySchema.nullish(),
});

/** Routing is read only after the results it accompanies; it never changes what was admitted. */
export function parseAdmission(value: unknown) {
	const response = admissionResponseSchema.parse(value);
	for (const withheld of response.withheld) {
		console.error(`[pi-runner] admission withheld ${withheld.practiceSlug}: ${withheld.reason}`);
	}
	const { decidedResultIds, practicePageDeliveries, conversationDeliveries } =
		response.privateProgress;
	const decided = new Set(decidedResultIds);
	return {
		results: response.admitted,
		digest: response.admissionDigest,
		publicReviewed: response.publicReviewed,
		// What earlier attempts of this review prepared privately: the practices they decided and the room they used.
		privateProgress: {
			decidedPractices: [
				...new Set(
					response.admitted
						.filter((result) => decided.has(result.resultId))
						.map((result) => result.practiceSlug),
				),
			].toSorted(),
			deliveries: {
				PRACTICE_PAGE: practicePageDeliveries,
				CONVERSATION: conversationDeliveries,
			},
		},
		practicePageSupport: readPracticePageSupport(response.practicePageSupport, response.admitted),
		conversationEligibility: readConversationEligibility(
			response.conversationEligibility,
			response.admitted,
		),
		privateHistory: response.privateHistory ?? null,
	};
}
export type Admission = ReturnType<typeof parseAdmission>;

// A timeout can leave verification active; retry only interrupted transport or retryable HTTP responses.
export const ADMISSION_ATTEMPT_TIMEOUT_MS = 10 * 60_000;
export const ADMISSION_ATTEMPTS = 4;
export class AdmissionUnreachableError extends Error {
	name = "AdmissionUnreachableError";
}
export type AdmissionFetch = (body: string, signal: AbortSignal) => Promise<Response>;
async function postAdmission(body: string, send: AdmissionFetch): Promise<unknown> {
	let response: Response;
	try {
		response = await send(body, AbortSignal.timeout(ADMISSION_ATTEMPT_TIMEOUT_MS));
	} catch (error) {
		if (isTimeoutAbort(error)) {
			// The server is still verifying; a second attempt would only queue the same work behind it.
			throw new Error(`result admission did not answer within ${ADMISSION_ATTEMPT_TIMEOUT_MS}ms`, {
				cause: error,
			});
		}
		// Node reports every transport failure as `TypeError: fetch failed`; only the cause says which
		// one it was, and a report that has just the message cannot tell a restart from a wrong URL.
		throw new AdmissionUnreachableError(
			`result admission could not be sent: ${errorText(error)}${causeText(error)}`,
		);
	}
	if (isRetryableStatus(response.status)) {
		throw new AdmissionUnreachableError(`result admission failed: HTTP ${response.status}`);
	}
	if (!response.ok) {
		// The server has decided, and it said why. Asking again puts the same question, so the run ends
		// here — with the reason in the transcript, which is the only place a reader can find it.
		throw new Error(
			`result admission was refused: HTTP ${response.status} — ${await answerText(response)}`,
		);
	}
	try {
		return await response.json();
	} catch (error) {
		// A server that dies while writing its answer ends the body mid-stream. The admission may well
		// have been recorded; asking again replays it rather than admitting it twice.
		throw new AdmissionUnreachableError(
			`result admission answer was cut short: ${errorText(error)}${causeText(error)}`,
		);
	}
}

export async function admit(request: unknown, send: AdmissionFetch): Promise<Admission> {
	const body = JSON.stringify(request);
	const response: unknown = await retrying(
		async () => postAdmission(body, send),
		(error) => error instanceof AdmissionUnreachableError,
		{ attempts: ADMISSION_ATTEMPTS },
		(attempt, error, delayMs) =>
			console.error(
				`[pi-runner] admission attempt ${attempt}/${ADMISSION_ATTEMPTS} did not arrive (${errorText(error)}); retrying in ${delayMs}ms`,
			),
	);
	return parseAdmission(response);
}
