import type { Page, Route } from "@playwright/test";

import { expect, loginAsDevAdmin, test } from "./fixtures";

/**
 * The trace of one review, through the built SPA, the real sign-in and the real router. A review has
 * no page of its own: its old address opens its trace as a sheet over the Reviews list, and a step
 * opens its record's sheet over the trace. The seeded workspace has no review, so the review's own
 * reads are answered here with one fixed review: the addresses, the layers and Escape are the parts
 * under test.
 */

const REVIEW = "5e5e0000-0000-4000-8000-000000000001";
const NOT_MET = "5e5e0000-0000-4000-8000-000000000011";
const MET = "5e5e0000-0000-4000-8000-000000000012";
const COMMENT = "5e5e0000-0000-4000-8000-000000000021";
const WITHHELD = "5e5e0000-0000-4000-8000-000000000022";

const work = {
	id: "42",
	kind: "scm.pull_request",
	label: "#42",
	container: "hephaestustest/demo",
	provider: "GITHUB",
	title: "Cache the member lookup",
	url: "https://github.com/hephaestustest/demo/pull/42",
};

const job = {
	id: REVIEW,
	jobType: "PULL_REQUEST_REVIEW",
	status: "COMPLETED",
	reviewOutcome: "REVIEWED",
	retryCount: 0,
	configSnapshot: {},
	createdAt: "2026-09-01T13:35:00Z",
	availableAt: "2026-09-01T13:35:00Z",
	startedAt: "2026-09-01T13:35:10Z",
	completedAt: "2026-09-01T15:02:00Z",
	deliveryStatus: "DELIVERED",
	target: { type: "scm.pull_request", title: work.title, reviewedWork: work },
};

const result = (id: string, summary: string, outcome: string, feedbackCount: number) => ({
	id,
	agentJobId: REVIEW,
	summary,
	practiceSlug: "errors-carry-context",
	practiceName: "Errors carry their context",
	outcome,
	severity: outcome === "NOT_MET" ? "MAJOR" : undefined,
	observedAt: "2026-09-01T13:39:00Z",
	focus: false,
	invalidated: false,
	problemCount: outcome === "NOT_MET" ? 1 : 0,
	feedbackCount,
	ownFeedbackCount: feedbackCount,
});

const lineage = {
	work,
	review: {
		id: REVIEW,
		status: "COMPLETED",
		phase: "COMPLETED",
		createdAt: job.createdAt,
		startedAt: job.startedAt,
		finishedAt: job.completedAt,
		model: "claude-sonnet-4-5",
	},
	reviewTrigger: "READY",
	workMergedAt: "2026-09-01T14:51:00Z",
	workMoments: [
		{ kind: "READY", occurredAt: "2026-09-01T13:34:50Z", startedReview: true, joinedReview: false },
		{
			kind: "MERGED",
			occurredAt: "2026-09-01T14:51:00Z",
			startedReview: false,
			joinedReview: false,
		},
	],
	results: [
		result(NOT_MET, "A cache miss and a permission failure come back as one 404", "NOT_MET", 2),
		result(MET, "The controller delegates first", "MET", 0),
	],
	moreResults: 0,
	feedback: [
		{
			id: COMMENT,
			agentJobId: REVIEW,
			channel: "IN_CONTEXT",
			location: "GITHUB",
			deliveryState: "DELIVERED",
			createdAt: "2026-09-01T13:41:00Z",
			deliveredAt: "2026-09-01T13:41:30Z",
			placementCount: 1,
			practiceResultIds: [NOT_MET],
			focus: false,
		},
		{
			id: WITHHELD,
			agentJobId: REVIEW,
			channel: "IN_CONTEXT",
			location: "GITHUB",
			deliveryState: "SUPPRESSED",
			suppressionReason: "ARTIFACT_MERGED",
			createdAt: "2026-09-01T14:58:00Z",
			placementCount: 0,
			practiceResultIds: [NOT_MET],
			focus: false,
		},
	],
	moreFeedback: 0,
};

/** The result's own read, for its level. */
const resultDetail = {
	id: NOT_MET,
	agentJobId: REVIEW,
	summary: "A cache miss and a permission failure come back as one 404",
	practiceSlug: "errors-carry-context",
	practiceName: "Errors carry their context",
	outcome: "NOT_MET",
	severity: "MAJOR",
	claimCurrentness: "CURRENT",
	origin: "LIVE",
	observedAt: "2026-09-01T13:39:00Z",
	reviewedWork: work,
	evidenceRationale: "Both cases end in the same NotFoundException.",
	observations: [],
	disputes: [],
	invalidations: [],
	feedback: [],
};

const emptyPage = { content: [], page: { number: 0, size: 25, totalElements: 0, totalPages: 0 } };

function json(body: unknown) {
	return async (route: Route) =>
		route.fulfill({ contentType: "application/json", body: JSON.stringify(body) });
}

/** A read of the seeded workspace, by the end of its path. */
function at(path: string) {
	return (url: URL) => url.pathname.endsWith(`/workspaces/e2e${path}`);
}

/** Answers the review's own reads; everything else goes to the server. */
async function serveReview(page: Page) {
	await page.route(at(`/agents/jobs/${REVIEW}`), json(job));
	await page.route(at("/practices/reviews/lineage"), json(lineage));
	await page.route(at("/practices/reviews/results"), json(emptyPage));
	await page.route(at(`/practices/reviews/results/${NOT_MET}`), json(resultDetail));
	await page.route(at("/practices/reviews/feedback"), json(emptyPage));
	await page.route(at("/practices/reviews"), json(emptyPage));
	await page.route(
		(url) => url.pathname.includes("/workspaces/e2e/practices/trace/"),
		async (route) =>
			route.fulfill({ status: 404, contentType: "application/problem+json", body: "{}" }),
	);
}

/** The trace in front: the sheet with the story of the record it was opened for. */
function traceOf(page: Page) {
	return page
		.getByRole("dialog")
		.filter({ has: page.getByRole("group", { name: "How this came about" }) });
}

test("a review's old address opens its trace over the Reviews list, standing on the review", async ({
	page,
}) => {
	await loginAsDevAdmin(page);
	await serveReview(page);

	await page.goto(`/w/e2e/admin/practices/reviews/review/${REVIEW}`);
	await expect(page).toHaveURL(/\/admin\/practices\/reviews\/runs\?/u);
	await expect(page).toHaveURL(new RegExp(`detail=.*trace(?:%3A|:)review(?:%3A|:)${REVIEW}`, "u"));
	const trace = traceOf(page);
	await expect(trace).toBeVisible();
	const story = trace.getByRole("group", { name: "How this came about" });

	// "You started here" stays on the record the trace was opened for.
	await expect(story.locator('[data-here="true"]')).toHaveAttribute("data-lineage-node", "review");
	await expect(trace.getByText("You started here").first()).toBeVisible();
	// The merge sits on the rail before the piece it held back.
	await expect(story.locator('[data-step="merged"]')).toBeVisible();
	await expect(story.getByText("Withheld: already merged")).toBeVisible();
	// The trace links nowhere inside Practice reviews: a step opens its record over it.
	await expect(story.getByRole("link")).toHaveCount(0);
});

test("a step opens its record's sheet over the trace, and Escape closes it first", async ({
	page,
}) => {
	await loginAsDevAdmin(page);
	await serveReview(page);

	await page.goto(`/w/e2e/admin/practices/reviews/review/${REVIEW}`);
	const trace = traceOf(page);
	const story = trace.getByRole("group", { name: "How this came about" });
	await expect(story.locator('[data-here="true"]')).toHaveAttribute("data-lineage-node", "review");

	await story
		.getByRole("button", { name: new RegExp(resultDetail.summary, "u") })
		.first()
		.click();
	const level = page.getByRole("dialog", { name: resultDetail.summary });
	await expect(level).toBeVisible();
	await expect(page).toHaveURL(new RegExp(`detail=.*result(?:%3A|:)${NOT_MET}`, "u"));
	// The sheet over the trace is the same as from its row: no story, no way on to another record.
	await expect(level.getByRole("group", { name: "How this came about" })).toHaveCount(0);

	// The first Escape closes the sheet; the trace stays.
	await page.keyboard.press("Escape");
	await expect(level).toBeHidden();
	await expect(trace).toBeVisible();

	// The second closes the trace.
	await page.keyboard.press("Escape");
	await expect(trace).toBeHidden();
	await expect(page).not.toHaveURL(/detail=/u);
});
