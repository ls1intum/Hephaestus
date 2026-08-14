import { HttpResponse, http } from "msw";
import type { ReviewFeedback, ReviewObservation } from "@/api/types.gen";
import {
	feedbackDetail,
	observationDetail,
	practiceAreas,
	reviewFeedback,
	reviewJob,
	reviewObservations,
	reviewRuns,
	workspaceMembers,
	workspacePractices,
} from "./story-mock-data";

/**
 * A stand-in for the review endpoints that actually answers the question it was asked.
 *
 * <p>The list stories used to hand MSW a fixed array and return it for every request. Choosing a
 * severity then changed the URL, refetched, and got the same rows back — so the toolbar looked
 * connected and the list underneath it was a photograph. Combined with a story whose `search` prop
 * could not change (see `@/stories/stateful`), that is two independent reasons a filter appeared
 * to do nothing, and fixing one alone would still have looked broken.
 *
 * <p>These handlers filter and paginate the fixture the way the server does, so a story can show a
 * genuinely filtered list, a second page, or an empty result reached by over-filtering — and a play
 * function that clicks a facet is testing something.
 */

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

/** True when the filter was not asked for, or when it was and this row satisfies it. */
function matches(selected: string[], actual: string | undefined): boolean {
	return selected.length === 0 || (actual !== undefined && selected.includes(actual));
}

function withinScope(
	url: URL,
	row: { agentJobId: string; artifact?: { id: number; type: string } },
) {
	const agentJobId = single(url, "agentJobId");
	const artifactKind = single(url, "artifactKind");
	const artifactId = single(url, "artifactId");
	if (agentJobId && row.agentJobId !== agentJobId) return false;
	if (artifactKind && row.artifact?.type !== artifactKind) return false;
	if (artifactId && String(row.artifact?.id) !== artifactId) return false;
	return true;
}

function withinDates(url: URL, at: Date) {
	const from = single(url, "from");
	const to = single(url, "to");
	if (from && at < new Date(from)) return false;
	if (to && at >= new Date(to)) return false;
	return true;
}

function page<T>(rows: T[], url: URL) {
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

function filterObservations(rows: ReviewObservation[], url: URL) {
	const subjectUserId = single(url, "subjectUserId");
	return rows.filter(
		(row) =>
			withinScope(url, row) &&
			withinDates(url, row.observedAt) &&
			matches(values(url, "areaSlug"), row.area?.slug) &&
			matches(values(url, "practiceSlug"), row.practiceSlug) &&
			matches(values(url, "presence"), row.presence) &&
			matches(values(url, "assessment"), row.assessment) &&
			matches(values(url, "severity"), row.severity) &&
			(!subjectUserId || String(row.subject?.id) === subjectUserId),
	);
}

function filterFeedback(rows: ReviewFeedback[], url: URL) {
	const recipientUserId = single(url, "recipientUserId");
	return rows.filter(
		(row) =>
			withinScope(url, row) &&
			withinDates(url, row.createdAt) &&
			matches(values(url, "deliveryState"), row.deliveryState) &&
			matches(values(url, "channel"), row.channel) &&
			matches(values(url, "suppressionReason"), row.suppressionReason) &&
			(!recipientUserId || String(row.recipient?.id) === recipientUserId),
	);
}

/**
 * Worst first: shortfalls by severity, then strengths, then the observations that judged nothing.
 *
 * This is the server's `ACTIONABILITY` ordering, written out so a story can *show* it. The mock used
 * only to check that the parameter was sent and then answer in the fixture's order — which would let
 * a control that sets the wrong value, or a screen that reads the answer back in its own order, pass
 * a story that claims the list is sorted.
 */
const ACTIONABILITY_RANK: Record<string, number> = { CRITICAL: 0, MAJOR: 1, MINOR: 2, INFO: 3 };

function actionability(row: ReviewObservation): number {
	if (row.assessment === "BAD") return ACTIONABILITY_RANK[row.severity ?? "INFO"] ?? 4;
	return row.assessment === "GOOD" ? 5 : 6;
}

function sortObservations(rows: ReviewObservation[], url: URL) {
	if (single(url, "sort") !== "ACTIONABILITY") return rows;
	// Ties are newest first, as on the server.
	return [...rows].sort(
		(a, b) =>
			actionability(a) - actionability(b) ||
			new Date(b.observedAt).getTime() - new Date(a.observedAt).getTime(),
	);
}

export interface ReviewMockOptions {
	/** Override the rows the observation list serves — used by the long-page stories. */
	observations?: ReviewObservation[];
	/** Override the rows the feedback list serves. */
	feedback?: ReviewFeedback[];
	/**
	 * Reject an observation query that does not ask for this ordering.
	 *
	 * The review detail screen shows the five observations most worth acting on, which is a server
	 * ordering it has to request. A mock that ignores `sort` would answer a screen that forgot to ask
	 * exactly as it answers one that remembered.
	 */
	requireObservationSort?: string;
}

/**
 * Every read the practice-review screens make, answered from the fixture.
 *
 * Spread it into a story's `msw.handlers` and add the ones that story overrides *before* it: MSW
 * matches the first handler that fits, so a story wanting a 500 puts its own handler first.
 */
export function reviewHandlers({
	observations,
	feedback,
	requireObservationSort,
}: ReviewMockOptions = {}) {
	const observationRows = observations ?? reviewObservations;
	const feedbackRows = feedback ?? reviewFeedback;
	return [
		http.get("*/workspaces/:workspaceSlug/practices/reviews/observations", ({ request }) => {
			const url = new URL(request.url);
			if (requireObservationSort && single(url, "sort") !== requireObservationSort) {
				return HttpResponse.json(
					{ detail: `Expected sort=${requireObservationSort}` },
					{ status: 400 },
				);
			}
			return page(sortObservations(filterObservations(observationRows, url), url), url);
		}),
		http.get(
			"*/workspaces/:workspaceSlug/practices/reviews/observations/:observationId",
			({ params }) => HttpResponse.json(observationDetail(String(params.observationId))),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", ({ request }) =>
			page(filterFeedback(feedbackRows, new URL(request.url)), new URL(request.url)),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", ({ params }) =>
			HttpResponse.json(feedbackDetail(String(params.feedbackId))),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
			const url = new URL(request.url);
			const status = single(url, "status");
			return page(
				// Both filters, intersected, exactly as the endpoint applies them. Honouring only
				// `status` here would let a story "prove" a date range that the screen never sent.
				reviewRuns.filter(
					(run) => (!status || run.status === status) && withinDates(url, run.createdAt),
				),
				url,
			);
		}),
		http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId", ({ params }) =>
			HttpResponse.json(reviewJob(String(params.jobId))),
		),
		http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json(workspacePractices)),
		http.get("*/workspaces/:workspaceSlug/practice-areas", () => HttpResponse.json(practiceAreas)),
		http.get("*/workspaces/:workspaceSlug/members", () => HttpResponse.json(workspaceMembers)),
	];
}
