import type {
	ItemRef,
	ProblemSummary,
	ReviewBoundFeedback,
	ReviewBoundResult,
	ReviewLineage,
	ReviewLineageEdge,
	ReviewLineageNode,
	ReviewRef,
} from "@/api/types.gen";

import {
	boundResultOf,
	feedbackDetail,
	problemDetail,
	problemIdOf,
	reviewFeedback,
	reviewItem,
	reviewRefOf,
	reviewResults,
	reviews,
} from "./fixtures";

export type LineageKind = ReviewLineageNode["kind"];

export const LINEAGE_RELATION = {
	reviewedBy: "REVIEWED_BY",
	recorded: "RECORDED",
	records: "RECORDS",
	basis: "BASIS",
} as const;

export type LineageRelation = (typeof LINEAGE_RELATION)[keyof typeof LINEAGE_RELATION];

export function lineageKey(kind: LineageKind, id: string): string {
	return `${kind.toLowerCase()}:${id}`;
}

const node = (
	kind: LineageKind,
	id: string,
	fields: Partial<Omit<ReviewLineageNode, "id" | "kind">>,
): ReviewLineageNode => ({
	id: lineageKey(kind, id),
	kind,
	item: null,
	review: null,
	result: null,
	problem: null,
	feedback: null,
	...fields,
});

export const lineageItemNode = (item: ItemRef) => node("ITEM", item.id, { item });
export const lineageReviewNode = (review: ReviewRef) => node("REVIEW", review.id, { review });
export const lineageResultNode = (result: ReviewBoundResult) =>
	node("RESULT", result.id, { result });
export const lineageProblemNode = (problem: ProblemSummary) =>
	node("PROBLEM", problem.id, { problem });
export const lineageFeedbackNode = (feedback: ReviewBoundFeedback) =>
	node("FEEDBACK", feedback.id, { feedback });

export function lineageEdge(
	from: string,
	to: string,
	relation: LineageRelation,
): ReviewLineageEdge {
	return { from, to, relation };
}

export interface LineageResultSpec {
	result: ReviewBoundResult;
	problems?: ProblemSummary[];
}

export interface LineageFeedbackSpec {
	feedback: ReviewBoundFeedback;
	basis: string[];
}

export interface StoryLineageSpec {
	root: { kind: LineageKind; id: string };
	item: ItemRef;
	reviews: ReviewRef[];
	results: LineageResultSpec[];
	feedback: LineageFeedbackSpec[];
}

export function storyLineage(spec: StoryLineageSpec): ReviewLineage {
	const itemKey = lineageKey("ITEM", spec.item.id);
	const nodes = new Map<string, ReviewLineageNode>([[itemKey, lineageItemNode(spec.item)]]);
	const edges: ReviewLineageEdge[] = [];
	for (const review of spec.reviews) {
		const reviewNode = lineageReviewNode(review);
		nodes.set(reviewNode.id, reviewNode);
		edges.push(lineageEdge(itemKey, reviewNode.id, LINEAGE_RELATION.reviewedBy));
	}
	for (const { result, problems = [] } of spec.results) {
		const resultNode = lineageResultNode(result);
		nodes.set(resultNode.id, resultNode);
		edges.push(
			lineageEdge(lineageKey("REVIEW", result.reviewId), resultNode.id, LINEAGE_RELATION.recorded),
		);
		for (const problem of problems) {
			const problemNode = lineageProblemNode(problem);
			nodes.set(problemNode.id, problemNode);
			edges.push(lineageEdge(resultNode.id, problemNode.id, LINEAGE_RELATION.records));
		}
	}
	for (const { feedback, basis } of spec.feedback) {
		const feedbackNode = lineageFeedbackNode(feedback);
		nodes.set(feedbackNode.id, feedbackNode);
		for (const resultId of basis) {
			edges.push(
				lineageEdge(lineageKey("RESULT", resultId), feedbackNode.id, LINEAGE_RELATION.basis),
			);
		}
	}
	return {
		rootId: lineageKey(spec.root.kind, spec.root.id),
		nodes: [...nodes.values()],
		edges: edges.filter((edge) => nodes.has(edge.from) && nodes.has(edge.to)),
	};
}

const boundFeedback = (id: string): ReviewBoundFeedback => {
	const row = reviewFeedback.find((entry) => entry.id === id);
	if (row === undefined) {
		throw new Error(`No feedback ${id} in the fixture`);
	}
	return {
		id: row.id,
		destination: row.destination,
		state: row.state,
		deliveredAt: row.deliveredAt,
		withdrawn: row.withdrawn,
	};
};

function reviewIdsFor(kind: LineageKind, id: string): string[] {
	switch (kind) {
		case "ITEM": {
			return reviews.filter((review) => review.item.id === id).map((review) => review.id);
		}
		case "REVIEW": {
			return [id];
		}
		case "RESULT": {
			return reviewResults.filter((result) => result.id === id).map((result) => result.reviewId);
		}
		case "PROBLEM": {
			return [problemDetail(id).reviewId];
		}
		case "FEEDBACK": {
			return [...new Set(feedbackDetail(id).basis.map((entry) => entry.result.reviewId))];
		}
	}
}

export function reviewLineage(kind: LineageKind, id: string): ReviewLineage {
	const reviewIds = new Set(reviewIdsFor(kind, id));
	const held = reviews.filter((review) => reviewIds.has(review.id));
	const [first] = held;
	const item =
		kind === "ITEM" || first === undefined
			? (reviews.find((review) => review.item.id === id)?.item ?? reviewItem)
			: first.item;
	const feedback = reviewFeedback.filter((entry) => reviewIds.has(entry.reviewId));
	return storyLineage({
		root: { kind, id },
		item,
		reviews: held.map(reviewRefOf),
		results: reviewResults
			.filter((result) => reviewIds.has(result.reviewId))
			.map((result) => ({ result: boundResultOf(result), problems: result.problems })),
		feedback: feedback.map((entry) => ({
			feedback: boundFeedback(entry.id),
			basis: feedbackDetail(entry.id).basis.map((basis) => basis.resultId),
		})),
	});
}

export const LINEAGE_REVIEW_ID = "11111111-1111-1111-1111-111111111111";
export const EARLIER_REVIEW_ID = "11111111-1111-1111-1111-000000000000";
export const LINEAGE_SCAN_ID = "dddddddd-9999-9999-9999-999999999999";

export const at = (time: string): Date => new Date(`2026-07-28T${time}Z`);
export const dayAt = (day: string, time: string): Date => new Date(`2026-${day}T${time}Z`);

export const LINEAGE_RESULT = {
	cache404: "66666666-6666-6666-6666-666666666666",
	testNames: "77777777-7777-7777-7777-777777777777",
	description: "88888888-8888-8888-8888-888888888888",
	thinController: "55555555-5555-5555-5555-555555555555",
} as const;

export const LINEAGE_FEEDBACK = {
	onGitHub: "44444444-4444-4444-4444-444444444444",
	praise: "33333333-3333-3333-3333-333333333333",
	replaced: "99999999-3333-3333-3333-333333333333",
	nothingNew: "aaaaaaaa-3333-3333-3333-333333333333",
	withdrawn: "bbbbbbbb-3333-3333-3333-333333333333",
	restored: "cccccccc-3333-3333-3333-333333333333",
	rejected: "dddddddd-3333-3333-3333-333333333333",
	markedIncorrect: "eeeeeeee-3333-3333-3333-333333333333",
} as const;

export const lineageResultFocus: ReviewLineage = reviewLineage("RESULT", LINEAGE_RESULT.cache404);

export const lineageProblemFocus: ReviewLineage = reviewLineage(
	"PROBLEM",
	problemIdOf(LINEAGE_RESULT.cache404, 0),
);

export const lineageFeedbackFocus: ReviewLineage = reviewLineage(
	"FEEDBACK",
	LINEAGE_FEEDBACK.onGitHub,
);

export const lineageItemFocus: ReviewLineage = reviewLineage("ITEM", reviewItem.id);

export const wholeReview: ReviewLineage = reviewLineage("REVIEW", LINEAGE_REVIEW_ID);

export const wholeReviewManyFeedback: ReviewLineage = (() => {
	const extra = [
		[LINEAGE_RESULT.thinController, LINEAGE_RESULT.testNames],
		[LINEAGE_RESULT.description],
		[LINEAGE_RESULT.description, LINEAGE_RESULT.cache404],
		[LINEAGE_RESULT.cache404, LINEAGE_RESULT.thinController],
		[LINEAGE_RESULT.testNames],
		[LINEAGE_RESULT.thinController, LINEAGE_RESULT.description],
	].map((basis, index) => {
		const feedbackNode = lineageFeedbackNode({
			id: `eeeeeeee-0000-4000-8000-00000000000${index}`,
			destination: index % 2 === 0 ? "PRACTICE_PAGE" : "ON_WORK",
			state: "DELIVERED",
			deliveredAt: at(`13:4${index}:50`),
			withdrawn: false,
		});
		return {
			node: feedbackNode,
			edges: basis.map((resultId) =>
				lineageEdge(lineageKey("RESULT", resultId), feedbackNode.id, LINEAGE_RELATION.basis),
			),
		};
	});
	return {
		...wholeReview,
		nodes: [...wholeReview.nodes, ...extra.map((entry) => entry.node)],
		edges: [...wholeReview.edges, ...extra.flatMap((entry) => entry.edges)],
	};
})();
