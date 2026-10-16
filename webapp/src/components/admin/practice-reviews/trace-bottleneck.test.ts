import { describe, expect, it } from "vitest";

import type { ReviewBoundFeedback, ReviewDetail, ReviewJob, ReviewLineage } from "@/api/types.gen";
import { REVIEW_STOP_REASON_SENTENCES } from "@/components/practice-vocabulary/review-status-defs";

import { reviewDetailCompleted } from "./fixtures";
import { at } from "./lineage-fixtures";
import { lineageStateCase, STATE_FEEDBACK_ID } from "./lineage-state-fixtures";
import type { TraceNode } from "./review-trace";
import { bottleneckText, feedbackLoss, traceBottleneck, traceSpans } from "./trace-bottleneck";

const REVIEW: TraceNode = { kind: "review" };
const PIECE: TraceNode = { kind: "feedback", id: STATE_FEEDBACK_ID };
const NOW = at("18:00:00");

function bottleneckOf(key: string, node: TraceNode = REVIEW) {
	const { lineage, review } = lineageStateCase(key);
	return traceBottleneck(lineage, node, review, NOW);
}

function withFeedback(
	lineage: ReviewLineage,
	feedbackId: string,
	patch: Partial<ReviewBoundFeedback>,
): ReviewLineage {
	return {
		...lineage,
		nodes: lineage.nodes.map((node) =>
			node.feedback?.id === feedbackId
				? { ...node, feedback: { ...node.feedback, ...patch } }
				: node,
		),
	};
}

function onlyPiece(key: string): ReviewBoundFeedback {
	const piece = lineageStateCase(key).lineage.nodes.find(
		(node) => node.feedback !== null,
	)?.feedback;
	if (piece == null) {
		throw new Error(`${key} has no feedback`);
	}
	return piece;
}

/** The first piece in a state, as a step of the trace. */
function pieceIn(lineage: ReviewLineage, state: ReviewBoundFeedback["state"]): TraceNode {
	const found = lineage.nodes.find((node) => node.feedback?.state === state)?.feedback;
	if (found == null) {
		throw new Error(`The graph has no ${state} piece`);
	}
	return { kind: "feedback", id: found.id };
}

function job(fields: Partial<ReviewJob>): ReviewJob {
	return {
		id: "job",
		attempt: 1,
		status: "COMPLETED",
		processingStatus: "DONE",
		availableAt: at("13:35:00"),
		startedAt: at("13:36:00"),
		completedAt: at("13:43:00"),
		exitCode: 0,
		errorMessage: null,
		traceId: "trace",
		...fields,
	};
}

function detailOf(key: string, overrides: Partial<ReviewDetail> = {}): ReviewDetail {
	const ref = lineageStateCase(key).lineage.nodes.find((node) => node.review !== null)?.review;
	if (ref == null) {
		throw new Error(`${key} has no review`);
	}
	return {
		...reviewDetailCompleted,
		...ref,
		orderedAt: ref.createdAt,
		startedBy: {
			id: "event",
			itemId: ref.item.id,
			name: "scm.pull_request.ready",
			itemVersion: ref.itemVersion,
			occurredAt: at("13:34:50"),
			foundBy: "WEBHOOK",
			state: "STARTED",
			reason: null,
			decidedAt: at("13:34:50"),
			reviewId: ref.id,
		},
		stopReason: null,
		runtime: { jobs: [job({})] },
		...overrides,
	};
}

describe("traceBottleneck for the review itself", () => {
	it("keeps returned worker time separate from an open review awaiting finalization", () => {
		const { lineage } = lineageStateCase("running");
		const detail = detailOf("running", {
			runtime: {
				jobs: [
					job({
						startedAt: at("16:41:00"),
						completedAt: at("16:44:00"),
						processingStatus: "PENDING",
					}),
				],
			},
		});
		expect(traceBottleneck(lineage, REVIEW, detail, NOW)).toMatchObject({
			kind: "waiting",
			title: "Awaiting finalization",
		});
		expect(traceSpans(lineage, REVIEW, detail).find((entry) => entry.kind === "run")).toMatchObject(
			{ text: "3 min", to: at("16:44:00") },
		);
		expect(detail.status).toBe("RUNNING");
	});

	it.each(["QUEUED", "RUNNING"] as const)(
		"keeps execution open when a sibling invocation is %s",
		(status) => {
			const { lineage } = lineageStateCase("running");
			const detail = detailOf("running", {
				runtime: {
					jobs: [job({ processingStatus: "PENDING" }), job({ status, completedAt: null })],
				},
			});
			expect(traceBottleneck(lineage, REVIEW, detail, NOW).title).not.toBe("Awaiting finalization");
			expect(traceSpans(lineage, REVIEW, detail).some((entry) => entry.kind === "run")).toBe(false);
		},
	);

	it("keeps failed invocation processing distinct from review success", () => {
		const { lineage } = lineageStateCase("running");
		const detail = detailOf("running", {
			runtime: { jobs: [job({ status: "FAILED", processingStatus: "FAILED" })] },
		});
		expect(traceBottleneck(lineage, REVIEW, detail, NOW)).toMatchObject({
			kind: "waiting",
			title: "Processing failed",
		});
		expect(detail.status).toBe("RUNNING");
	});

	it("says why a failed review stopped", () => {
		expect(bottleneckOf("failed")).toMatchObject({
			kind: "failed",
			stage: "review",
			title: "Review failed",
			text: REVIEW_STOP_REASON_SENTENCES.EXECUTION_FAILED,
			tone: "destructive",
		});
	});

	it("prefers the runtime job's error and counts the attempts", () => {
		const { lineage } = lineageStateCase("failed");
		const detail = detailOf("failed", {
			stopReason: "EXECUTION_FAILED",
			runtime: {
				jobs: [
					job({ attempt: 1, status: "FAILED", errorMessage: "The diff could not be read" }),
					job({ attempt: 2, status: "FAILED", errorMessage: "The diff could not be read" }),
				],
			},
		});
		expect(traceBottleneck(lineage, REVIEW, detail, NOW).text).toBe(
			"The diff could not be read. It was tried 2 times.",
		);
	});

	it("says a failed review without a known reason plainly", () => {
		const { lineage } = lineageStateCase("failed");
		expect(traceBottleneck(lineage, REVIEW, undefined, NOW).text).toBe(
			"It stopped on an error. The error is not recorded here.",
		);
	});

	it("names a timeout", () => {
		expect(bottleneckOf("timed-out")).toMatchObject({ kind: "failed", title: "Review timed out" });
	});

	it("names why a review was cancelled", () => {
		expect(bottleneckOf("cancelled")).toMatchObject({
			title: "Review cancelled",
			text: REVIEW_STOP_REASON_SENTENCES.CANCELLED_BY_ADMIN,
		});
	});

	it("says a partial scan stopped early and that its results stand", () => {
		const bottleneck = bottleneckOf("scan-many-jobs");
		expect(bottleneck).toMatchObject({
			kind: "failed",
			title: "Review did not finish",
			tone: "warning",
		});
		expect(bottleneck.text).toBe(
			`${REVIEW_STOP_REASON_SENTENCES.BUDGET} What it recorded up to then still stands.`,
		);
	});

	it("says how long a queued review has waited", () => {
		expect(bottleneckOf("queued")).toMatchObject({
			kind: "waiting",
			title: "In the queue",
			span: "queue",
			tone: "warning",
		});
	});

	it("says a gone review cannot be told", () => {
		expect(bottleneckOf("record-gone")).toMatchObject({ kind: "none", title: "Not known" });
	});

	it("says that not-met results led to no feedback", () => {
		const bottleneck = bottleneckOf("not-met-silent");
		expect(bottleneck).toMatchObject({ kind: "lost", title: "Not told" });
		expect(bottleneck.text).toMatch(/not met, but the review composed no feedback/u);
	});
});

describe("traceBottleneck for lost feedback", () => {
	it("says a withheld piece was withheld without inventing a reason", () => {
		expect(bottleneckOf("state-withheld", PIECE)).toMatchObject({
			kind: "lost",
			stage: "feedback",
			title: "Withheld",
			text: "It was withheld before it reached the developer. Its feedback record says why.",
			tone: "warning",
		});
	});

	it("takes the tone of what the loss asks of an admin", () => {
		expect(bottleneckOf("state-failed", PIECE).tone).toBe("destructive");
		expect(bottleneckOf("state-rejected", PIECE)).toMatchObject({
			title: "Rejected",
			tone: "secondary",
		});
	});

	it("counts the reasons where some pieces reached the developer and some did not", () => {
		const bottleneck = bottleneckOf("delivery-states");
		expect(bottleneck).toMatchObject({ kind: "lost", title: "Partly lost", tone: "destructive" });
		expect(bottleneck.text).toBe(
			"2 of 8 pieces reached the developer; 3 did not: withheld (1), rejected (1), delivery failed (1).",
		);
	});

	it("says one lost piece of a review on its own", () => {
		expect(bottleneckOf("origin-backfill")).toMatchObject({ kind: "lost", title: "Withheld" });
	});

	it("does not count feedback replaced after it reached the developer as lost", () => {
		const { lineage } = lineageStateCase("delivery-states");
		expect(traceBottleneck(lineage, pieceIn(lineage, "REPLACED"), undefined, NOW).kind).not.toBe(
			"lost",
		);
	});
});

describe("traceBottleneck for feedback on its way", () => {
	it("says an admin's approval is awaited", () => {
		expect(bottleneckOf("state-awaiting-approval", PIECE).title).toBe("Awaiting approval");
	});

	it("says a ready piece is not sent yet", () => {
		expect(bottleneckOf("state-ready", PIECE)).toMatchObject({
			kind: "waiting",
			title: "Not sent yet",
		});
	});

	it("says a piece for a conversation waits for one", () => {
		const { lineage } = lineageStateCase("state-ready");
		const forChat = withFeedback(lineage, STATE_FEEDBACK_ID, { destination: "CONVERSATION" });
		expect(traceBottleneck(forChat, PIECE, undefined, NOW).title).toBe(
			"Waiting for a conversation",
		);
	});

	it("says an unconfirmed delivery honestly", () => {
		expect(bottleneckOf("state-unconfirmed", PIECE)).toMatchObject({
			kind: "waiting",
			title: "Delivery unconfirmed",
		});
	});
});

describe("traceBottleneck where nothing was lost", () => {
	it("says nothing had to be sent where nothing was recorded", () => {
		expect(bottleneckOf("empty").text).toBe("Nothing on this path had to be sent.");
	});

	it("finds no bottleneck on a quick delivered path", () => {
		// The ordinary case takes no colour.
		expect(bottleneckOf("complete", PIECE)).toMatchObject({
			kind: "none",
			title: "No bottleneck",
			text: "Nothing was lost and no wait took half an hour or more.",
			tone: "secondary",
		});
	});

	it("names the queue where it was the longest wait", () => {
		const { lineage } = lineageStateCase("complete");
		const detail = detailOf("complete", {
			completedAt: at("15:52:00"),
			runtime: { jobs: [job({ startedAt: at("15:45:00"), completedAt: at("15:52:00") })] },
		});
		const bottleneck = traceBottleneck(lineage, REVIEW, detail, NOW);
		expect(bottleneck).toMatchObject({ kind: "slow", title: "Slow queue", span: "queue" });
		expect(bottleneck.text).toBe("The review waited 2 h 10 min in the queue for a worker.");
	});
});

describe("traceSpans", () => {
	it("measures each stage from the record, in order, and marks a long wait", () => {
		const lineage = withFeedback(lineageStateCase("complete").lineage, STATE_FEEDBACK_ID, {
			deliveredAt: at("14:20:00"),
		});
		const spans = traceSpans(lineage, PIECE, detailOf("complete"));
		expect(spans.map((entry) => [entry.kind, entry.label, entry.text, entry.slow])).toStrictEqual([
			["asked", "Until asked", "10 s", false],
			["queue", "Queue", "1 min", false],
			["run", "Run", "7 min", false],
			["delivery", "To developer", "36 min", true],
		]);
		expect(traceBottleneck(lineage, PIECE, detailOf("complete"), NOW)).toMatchObject({
			kind: "slow",
			title: "Slow delivery",
			span: "delivery",
		});
	});

	it("leaves out what the record cannot tell", () => {
		const { lineage } = lineageStateCase("queued");
		expect(traceSpans(lineage, REVIEW)).toStrictEqual([]);
		const expired = lineageStateCase("expired-runtime");
		const kinds = traceSpans(expired.lineage, REVIEW, expired.review).map((entry) => entry.kind);
		expect(kinds).not.toContain("queue");
		expect(kinds).not.toContain("run");
	});
});

describe("feedbackLoss", () => {
	it.each(["state-awaiting-approval", "state-ready", "state-delivered", "state-unconfirmed"])(
		"leaves a piece that stands or waits alone: %s",
		(key) => {
			expect(feedbackLoss(onlyPiece(key))).toBeUndefined();
		},
	);

	it("says a withdrawn piece was delivered only where a delivery is recorded", () => {
		const piece: ReviewBoundFeedback = {
			id: STATE_FEEDBACK_ID,
			destination: "PRACTICE_PAGE",
			state: "DELIVERED",
			deliveredAt: at("13:41:30"),
			withdrawn: true,
		};
		expect(feedbackLoss(piece)?.text).toBe("An admin withdrew it after it was delivered.");
		expect(feedbackLoss({ ...piece, state: "READY", deliveredAt: null })).toMatchObject({
			title: "Withdrawn",
			text: "An admin withdrew it before it reached the developer.",
		});
	});

	it("says a piece replaced before it was delivered was replaced", () => {
		const piece: ReviewBoundFeedback = {
			id: STATE_FEEDBACK_ID,
			destination: "ON_WORK",
			state: "REPLACED",
			deliveredAt: null,
			withdrawn: false,
		};
		expect(feedbackLoss(piece)?.title).toBe("Replaced");
	});
});

describe("bottleneckText", () => {
	it("never repeats the verdict: where it already says the reason, only the few words stand", () => {
		const bottleneck = bottleneckOf("state-rejected", PIECE);
		expect(
			bottleneckText(bottleneck, "Not delivered: an admin rejected it before it was sent."),
		).toBe("rejected");
		expect(bottleneckText(bottleneck, "This feedback did not reach the developer.")).toBe(
			bottleneck.text,
		);
	});
});
