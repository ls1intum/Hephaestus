import { HttpResponse, http } from "msw";

import type { ReviewFeedback, ReviewResult } from "@/api/types.gen";
import { artifactTrace, tracedArtifacts } from "@/components/practice-trace/fixtures";
import { hasText } from "@/lib/text";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";

import {
	feedbackDetail,
	feedbackIdsFrom,
	locationCounts,
	mergedPullRequestFacts,
	mockJobUsageTwoAttempts,
	resultDetail,
	openIssueFacts,
	practiceGroups,
	practiceReviewOverview,
	practiceReviewTiming,
	practiceRevisions,
	busyReviewQueue,
	reasonCounts,
	reviewFeedback,
	reviewJob,
	reviewResults,
	reviewRuns,
	workspaceMembers,
	workspacePractices,
} from "./fixtures";
import { reviewLineage } from "./lineage-fixtures";

// These handlers filter, sort and paginate the fixture the way the server does. A mock that answers
// every URL with the same array leaves a story that clicks a facet testing nothing.

const PAGE_SIZE = 25;

/**
 * Repeated params and comma-joined params both, because which one the generated client emits is its
 * business and a mock that guesses wrong silently ignores the filter.
 */
function values(url: URL, name: string): string[] {
	return url.searchParams
		.getAll(name)
		.flatMap((value) => value.split(","))
		.filter(Boolean);
}

function single(url: URL, name: string): string | undefined {
	return url.searchParams.get(name) ?? undefined;
}

function matches(selected: string[], actual: string | undefined): boolean {
	return selected.length === 0 || (actual !== undefined && selected.includes(actual));
}

function withinScope(
	url: URL,
	row: { agentJobId: string; reviewedWork?: { id: string; kind: string } },
) {
	const agentJobId = single(url, "agentJobId");
	const artifactKind = single(url, "artifactKind");
	const artifactId = single(url, "artifactId");
	if (hasText(agentJobId) && row.agentJobId !== agentJobId) {
		return false;
	}
	if (hasText(artifactKind) && row.reviewedWork?.kind !== artifactKind) {
		return false;
	}
	if (hasText(artifactId) && row.reviewedWork?.id !== artifactId) {
		return false;
	}
	return true;
}

function withinDates(url: URL, at: Date) {
	const from = single(url, "from");
	const to = single(url, "to");
	if (hasText(from) && at < new Date(from)) {
		return false;
	}
	if (hasText(to) && at >= new Date(to)) {
		return false;
	}
	return true;
}

function page(rows: unknown[], url: URL) {
	const number = Number(single(url, "page") ?? 0);
	const size = Number(single(url, "size") ?? PAGE_SIZE);
	return HttpResponse.json({
		content: rows.slice(number * size, number * size + size),
		page: {
			number,
			size,
			totalElements: rows.length,
			totalPages: Math.max(1, Math.ceil(rows.length / size)),
		},
	});
}

function filterResults(rows: ReviewResult[], url: URL) {
	const subjectUserId = single(url, "subjectUserId");
	const invalidated = single(url, "invalidated");
	const disputed = single(url, "disputed");
	return rows.filter(
		(row) =>
			withinScope(url, row) &&
			withinDates(url, row.observedAt) &&
			matches(values(url, "groupSlug"), row.group?.slug) &&
			matches(values(url, "practiceSlug"), row.practiceSlug) &&
			matches(values(url, "outcome"), row.outcome) &&
			(invalidated === undefined || invalidated === String(row.invalidatedAt !== undefined)) &&
			(disputed === undefined || disputed === String(row.disputedAt !== undefined)) &&
			matches(values(url, "severity"), row.severity) &&
			(!hasText(subjectUserId) || String(row.subject?.id) === subjectUserId),
	);
}

function filterFeedback(rows: ReviewFeedback[], url: URL) {
	const recipientUserId = single(url, "recipientUserId");
	const observationId = single(url, "observationId");
	const fromObservation = hasText(observationId) ? feedbackIdsFrom(observationId) : undefined;
	return rows.filter(
		(row) =>
			withinScope(url, row) &&
			(fromObservation === undefined || fromObservation.has(row.id)) &&
			withinDates(url, row.createdAt) &&
			matches(values(url, "deliveryState"), row.deliveryState) &&
			matches(values(url, "channel"), row.channel) &&
			matches(values(url, "suppressionReason"), row.suppressionReason) &&
			(!hasText(recipientUserId) || String(row.recipient?.id) === recipientUserId),
	);
}

/**
 * The server's `ACTIONABILITY` ordering, applied rather than merely accepted: a mock that checked
 * only that the parameter was sent would pass a story whose control sets the wrong value, or whose
 * screen re-orders the answer on the way in.
 */
const ACTIONABILITY_RANK: Record<string, number> = { CRITICAL: 0, MAJOR: 1, MINOR: 2, INFO: 3 };

function actionability(row: ReviewResult): number {
	switch (row.outcome) {
		case "NOT_MET": {
			return ACTIONABILITY_RANK[row.severity ?? "INFO"] ?? 4;
		}
		case "MET": {
			return 5;
		}
		case "NOT_APPLICABLE":
		case "UNDETERMINED": {
			return 6;
		}
	}
}

function sortResults(rows: ReviewResult[], url: URL) {
	if (single(url, "sort") !== "ACTIONABILITY") {
		return rows;
	}
	// Ties are newest first, as on the server.
	return [...rows].sort(
		(a, b) =>
			actionability(a) - actionability(b) ||
			new Date(b.observedAt).getTime() - new Date(a.observedAt).getTime(),
	);
}

export interface ReviewMockOptions {
	results?: ReviewResult[];
	feedback?: ReviewFeedback[];
	/**
	 * Answer a result query with a 400 unless it asks for this ordering. A screen that shows only
	 * the results most worth acting on has to request that ordering, and a mock that ignores
	 * `sort` answers a screen that forgot to ask exactly as it answers one that remembered.
	 */
	requireResultSort?: string;
}

/**
 * Spread into a story's `msw.handlers` with the handlers that story overrides placed *before* it:
 * MSW matches the first handler that fits.
 */
export function reviewHandlers({ results, feedback, requireResultSort }: ReviewMockOptions = {}) {
	const resultRows = results ?? reviewResults;
	const feedbackRows = feedback ?? reviewFeedback;
	return [
		// One fixed range whatever `from` and `zone` asked for: the overview's counts are the server's
		// arithmetic, and a story reads them rather than proving them.
		http.get("*/workspaces/:workspaceSlug/practices/reviews/overview", () =>
			HttpResponse.json(practiceReviewOverview),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/timing", () =>
			HttpResponse.json(practiceReviewTiming),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/queue", () =>
			HttpResponse.json(busyReviewQueue),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/results", ({ request }) => {
			const url = new URL(request.url);
			if (hasText(requireResultSort) && single(url, "sort") !== requireResultSort) {
				return HttpResponse.json({ detail: `Expected sort=${requireResultSort}` }, { status: 400 });
			}
			return page(sortResults(filterResults(resultRows, url), url), url);
		}),
		http.get(
			"*/workspaces/:workspaceSlug/practices/reviews/results/:practiceResultId",
			({ params }) => HttpResponse.json(resultDetail(String(params.practiceResultId))),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", ({ request }) => {
			const url = new URL(request.url);
			const rows = filterFeedback(feedbackRows, url);
			// The fixture is newest first, as the endpoint's default order is.
			return page(single(url, "sort") === "OLDEST" ? [...rows].reverse() : rows, url);
		}),
		...feedbackCountHandlers(feedbackRows),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", ({ params }) =>
			HttpResponse.json(feedbackDetail(String(params.feedbackId))),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/lineage", ({ request }) => {
			const url = new URL(request.url);
			return HttpResponse.json(
				reviewLineage(single(url, "kind") ?? "REVIEW", single(url, "id") ?? ""),
			);
		}),
		http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
			const url = new URL(request.url);
			return page(
				// Every filter, intersected, exactly as the endpoint applies them. Honouring only
				// `status` here would let a story "prove" a date range that the screen never sent.
				reviewRuns.filter(
					(run) =>
						withinScope(url, { agentJobId: run.id, reviewedWork: run.target.reviewedWork }) &&
						// "On hold" and "Waiting" join the listed statuses as more alternatives.
						((values(url, "status").length === 0 &&
							single(url, "onHold") !== "true" &&
							single(url, "waiting") !== "true") ||
							values(url, "status").includes(run.status) ||
							(single(url, "onHold") === "true" &&
								run.status === "QUEUED" &&
								hasText(run.holdReason)) ||
							(single(url, "waiting") === "true" &&
								run.status === "QUEUED" &&
								run.holdReason !== "BUDGET")) &&
						matches(values(url, "resultProcessing"), run.resultProcessing) &&
						withinDates(url, run.createdAt),
				),
				url,
			);
		}),
		http.get("*/workspaces/:workspaceSlug/practices/trace", ({ request }) => {
			const url = new URL(request.url);
			const kind = single(url, "artifactKind");
			const id = single(url, "artifactId");
			return page(
				tracedArtifacts.filter(
					(work) =>
						(!hasText(kind) || work.artifactKind === kind) &&
						(!hasText(id) || String(work.artifactId) === id) &&
						(single(url, "withoutReview") !== "true" || work.reviewedSignalCount === 0),
				),
				url,
			);
		}),
		// Whichever work is asked for is named as asked, so a level reads as that work's own trace.
		http.get(
			"*/workspaces/:workspaceSlug/practices/trace/:artifactKind/:artifactId",
			({ params }) =>
				HttpResponse.json({
					...artifactTrace,
					artifactKind: String(params.artifactKind),
					artifactId: Number(params.artifactId),
				}),
		),
		http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId", ({ params }) =>
			HttpResponse.json(reviewJob(String(params.jobId))),
		),
		http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId/usage", () =>
			HttpResponse.json(mockJobUsageTwoAttempts),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/runs/:jobId/delivery-checks", () =>
			HttpResponse.json([]),
		),
		// A pull request reads as merged and an issue as open; other work has no provider facts.
		http.get(
			"*/workspaces/:workspaceSlug/practices/reviews/work/:artifactKind/:artifactId",
			({ params }) => {
				const artifactId = Number(params.artifactId);
				if (params.artifactKind === "scm.pull_request") {
					return HttpResponse.json({ ...mergedPullRequestFacts, artifactId });
				}
				if (params.artifactKind === "scm.issue") {
					return HttpResponse.json({ ...openIssueFacts, artifactId });
				}
				return HttpResponse.json({ detail: "No such work" }, { status: 404 });
			},
		),
		http.get("*/workspaces/:workspaceSlug/practices/:practiceSlug/revisions", () =>
			HttpResponse.json(practiceRevisions),
		),
		http.get("*/workspaces/:workspaceSlug/practices/definition-options", () =>
			HttpResponse.json(mockPracticeDefinitionOptions),
		),
		// No practice in the fixture has a practice script.
		http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId/practice-scripts", () =>
			HttpResponse.json([]),
		),
		http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(workspacePractices)),
		http.get("*/workspaces/:workspaceSlug/practice-groups", () =>
			HttpResponse.json(practiceGroups),
		),
		http.get("*/workspaces/:workspaceSlug/members", () => HttpResponse.json(workspaceMembers)),
	];
}

/**
 * The counts the feedback list's own filter selects, by location and by reason. A test that answers
 * `feedback/:feedbackId` itself puts these before it, since that path would take them too.
 */
export function feedbackCountHandlers(feedbackRows: ReviewFeedback[] = reviewFeedback) {
	return [
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/locations", ({ request }) =>
			HttpResponse.json(locationCounts(filterFeedback(feedbackRows, new URL(request.url)))),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/reasons", ({ request }) =>
			HttpResponse.json(reasonCounts(filterFeedback(feedbackRows, new URL(request.url)))),
		),
	];
}
