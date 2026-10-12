import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, HttpResponse, http } from "msw";
import { afterEach, assert, beforeEach, describe, expect, it, vi } from "vitest";

import {
	listPracticeReviewsInfiniteQueryKey,
	listPracticeReviewsQueryKey,
	listTracedArtifactsInfiniteQueryKey,
	listTracedArtifactsQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { AgentJob } from "@/api/types.gen";
import {
	feedbackDetail,
	observationDetail,
	practiceCounts,
	practiceReviewOverview,
	reviewFeedbackDetail,
	reviewJob,
	reviewObservations,
	reviewRuns,
	workspacePractices,
} from "@/components/admin/practice-reviews/fixtures";
import { ACTIVE_REVIEW_POLL_MS } from "@/components/admin/practice-reviews/review-search";
import { reviewHandlers } from "@/components/admin/practice-reviews/story-mock-server";
import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { artifactTrace, tracedArtifacts } from "@/components/practice-trace/fixtures";
import { browserTimeZone } from "@/lib/dates";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules; the timeout is a
// deadlock backstop, not a budget these renders were meant to fit inside.
vi.setConfig({ testTimeout: 20_000 });

const REVIEWS = "/w/acme/admin/practices/reviews";

const requested: URL[] = [];
const record = ({ request }: { request: Request }) => {
	requested.push(new URL(request.url));
};

/** The one-row reads behind the tab counts, which ask the lists' endpoints for their totals alone. */
function isTabCount(url: URL): boolean {
	return url.searchParams.get("size") === "1" && !url.searchParams.has("resultProcessing");
}

/** Every request to one endpoint so far, oldest first. */
function requestsTo(pathname: string): URL[] {
	return requested.filter((url) => url.pathname === `/workspaces/acme${pathname}`);
}

/** A repeatable param's values, whether the client repeated it or joined it with commas. */
function values(url: URL | undefined, name: string): string[] {
	return (url?.searchParams.getAll(name) ?? []).flatMap((value) => value.split(","));
}

/** The overview's own range: the read for the period before it names where it ends, this one does not. */
function currentOverview(): URL | undefined {
	return requestsTo("/practices/reviews/overview").find((url) => !url.searchParams.has("to"));
}

function instant(url: URL | undefined, name: string): number | undefined {
	const value = url?.searchParams.get(name);
	return value == null ? undefined : new Date(value).getTime();
}

/** One review that timed out, for a request that asks for those; any other falls through. */
beforeEach(() => {
	requested.length = 0;
	server.events.on("request:start", record);
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		...reviewHandlers(),
	);
});

afterEach(() => {
	server.events.removeListener("request:start", record);
});

/** A row's own link, named by what it opens and then by its record: "Open observation …, 6 Oct, 14:05". */
function openRow(kind: string, name: string) {
	return { name: (accessibleName: string) => accessibleName.startsWith(`Open ${kind} ${name},`) };
}

describe("practice review levels", () => {
	/**
	 * A row opens its record over the list rather than instead of it, so closing the record is the
	 * list exactly as the reader left it — filters and all.
	 */
	it("opens an observation over its filtered list and closes back to that list", async () => {
		const observation = reviewObservations.find((row) => row.severity === "MAJOR");
		assert(observation);
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/observations?severity=["MAJOR"]`);

		await userEvent.click(
			await screen.findByRole(
				"link",
				openRow("observation", observation.summary),
				ROUTE_RENDER_WAIT,
			),
		);

		const level = await screen.findByRole(
			"dialog",
			{ name: observation.summary },
			ROUTE_RENDER_WAIT,
		);
		within(level).getByRole("heading", { name: observation.summary });
		expect(router.state.location.searchStr).toBe(
			`?severity=MAJOR&detail=observation:${observation.id}`,
		);

		fireEvent.keyDown(document.body, { key: "Escape" });

		await waitFor(
			() => expect(router.state.location.search).not.toHaveProperty("detail"),
			ROUTE_RENDER_WAIT,
		);
		expect(router.state.location.pathname).toBe(`${REVIEWS}/observations`);
		expect(router.state.location.searchStr).toBe("?severity=MAJOR");
		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
		screen.getByRole("link", openRow("observation", observation.summary));
	});

	/** Closing the level gives focus back to the row it was opened from, so the reader goes on there. */
	it("returns focus to the row the level was opened from", async () => {
		const observation = reviewObservations.find((row) => row.severity === "MAJOR");
		assert(observation);
		renderRouteAtWithRouter(`${REVIEWS}/observations`);
		const row = await screen.findByRole(
			"link",
			openRow("observation", observation.summary),
			ROUTE_RENDER_WAIT,
		);

		await userEvent.click(row);
		const level = await screen.findByRole(
			"dialog",
			{ name: observation.summary },
			ROUTE_RENDER_WAIT,
		);
		await waitFor(() => expect(level.contains(document.activeElement)).toBe(true));

		await userEvent.keyboard("{Escape}");

		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull(), ROUTE_RENDER_WAIT);
		await waitFor(() =>
			expect(document.activeElement).toBe(
				screen.getByRole("link", openRow("observation", observation.summary)),
			),
		);
	});

	/**
	 * Feedback and its observations reach each other through the story. Following one from the other
	 * replaces the level instead of opening over it, so there is never more than one; it is a step of
	 * its own, so Back returns to the record it replaced, and dismissing closes to the page.
	 */
	/** A link from before that stacked several levels opens the one in front. */
	it("opens only the front record of a stacked address", async () => {
		const [source] = reviewFeedbackDetail.observations;
		assert(source);
		const { router } = renderRouteAtWithRouter(
			`${REVIEWS}/runs?detail=${encodeURIComponent(
				JSON.stringify([
					`feedback:${reviewFeedbackDetail.id}`,
					`observation:${source.observationId}`,
				]),
			)}`,
		);
		await screen.findByRole("dialog", { name: source.summary }, ROUTE_RENDER_WAIT);
		expect(screen.getAllByRole("dialog")).toHaveLength(1);
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			`observation:${source.observationId}`,
		]);
	});

	/**
	 * The overview asks for its range in the reader's own days — the zone decides where a day's
	 * bucket starts — and a practice's row opens its sheet: its definition, with no counts of its own.
	 */
	it("opens a practice's sheet as its definition, the counts staying on the overview", async () => {
		const [practice] = practiceCounts;
		assert(practice);
		const { router } = renderRouteAtWithRouter(REVIEWS);

		await userEvent.click(
			await screen.findByRole(
				"link",
				{ name: `Open practice ${practice.practiceName}` },
				ROUTE_RENDER_WAIT,
			),
		);
		const sheet = await screen.findByRole(
			"dialog",
			{ name: practice.practiceName },
			ROUTE_RENDER_WAIT,
		);
		expect(router.state.location.pathname).toBe(REVIEWS);
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			`practice:${practice.practiceSlug}`,
		]);
		await within(sheet).findByRole("heading", { name: "What it is" }, ROUTE_RENDER_WAIT);
		expect(within(sheet).queryByText(/^Counts /u)).toBeNull();
		// Its versions are its history: what changed in each, read from the practice's own versions.
		await within(sheet).findByText("Version 3", undefined, ROUTE_RENDER_WAIT);
		within(sheet).getByText("Changed: Why it matters.");
		expect(requestsTo(`/practices/${practice.practiceSlug}/revisions`)).toHaveLength(1);
		// It is the practice alone: Practice setup's form is where it changes, and no other record is linked.
		const links = within(sheet).getAllByRole("link");
		expect(links.map((link) => link.textContent)).toContain("Edit in Practice setup");
		for (const link of links) {
			expect(link.outerHTML).not.toMatch(/\/reviews/u);
		}

		const overviews = requestsTo("/practices/reviews/overview");
		expect(overviews.at(0)?.searchParams.get("zone")).toBe(browserTimeZone());
		// The reader's midnight, as an instant: the sheet counts from exactly the overview's moment.
		const from = instant(overviews.at(0), "from");
		assert(from !== undefined);
		const start = new Date(from);
		expect([start.getHours(), start.getMinutes(), start.getSeconds()]).toStrictEqual([0, 0, 0]);
		expect(overviews.some((url) => instant(url, "from") === from)).toBe(true);
	});

	/**
	 * Each total is set against the period of the same length before the range: a second read of
	 * the overview that ends where the range starts, in the same zone.
	 */
	it("asks for the period before the range, to set the totals against", async () => {
		renderRouteAtWithRouter(`${REVIEWS}?range=7d`);
		await screen.findByRole("heading", { name: "What the reviews did" }, ROUTE_RENDER_WAIT);
		// The range and the period before it; with 7 days as the range, the week the failures are
		// counted over is the range's own read.
		await waitFor(() => expect(requestsTo("/practices/reviews/overview")).toHaveLength(2));

		const current = currentOverview();
		const previous = requestsTo("/practices/reviews/overview").find((url) =>
			url.searchParams.has("to"),
		);
		const from = instant(current, "from");
		assert(from !== undefined);
		expect(instant(previous, "to")).toBe(from);
		const previousFrom = instant(previous, "from");
		assert(previousFrom !== undefined);
		// Seven days before, give or take the hour a daylight-saving change moves a local midnight.
		expect(Math.round((from - previousFrom) / 3_600_000 / 24)).toBe(7);
		expect(previous?.searchParams.get("zone")).toBe(browserTimeZone());
	});

	/**
	 * The review time is measured over the reviews the overview counts, so it asks from the
	 * overview's own start until now; its warning opens the late reviews it counted.
	 */
	it("asks for the review time over the overview's range, and links the late reviews", async () => {
		renderRouteAtWithRouter(`${REVIEWS}?range=7d`);
		await screen.findByRole("heading", { name: "What the reviews did" }, ROUTE_RENDER_WAIT);
		// The range's alone.
		await waitFor(() => expect(requestsTo("/practices/reviews/timing")).toHaveLength(1));

		const timing = requestsTo("/practices/reviews/timing").at(0);
		expect(instant(timing, "from")).toBe(instant(currentOverview(), "from"));
		expect(timing?.searchParams.has("to")).toBe(false);

		const figure = await screen.findByText(
			"median, start to comment",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		expect(figure.previousElementSibling?.textContent).toBe("15 min");
		expect(
			within(screen.getByRole("list", { name: "Where the time went" }))
				.getAllByRole("listitem")
				.map((item) => item.textContent),
		).toStrictEqual([
			"Until asked2 min",
			"Queue26 s",
			"Preparation20 s",
			"Execution12 min",
			"To developer12 s",
		]);

		const late = screen.getByRole("link", { name: "9 reviews finished after the merge" });
		expect(late.getAttribute("href")).toContain("finishedAfterMerge=true");
	});

	/**
	 * A decision owed does not expire with the range, so the approvals ask for every piece of
	 * feedback awaiting one, oldest first — the order they are worked through in. What failed is
	 * asked for over the range, one row each, since the figures read only totals.
	 */
	it("asks for every decision owed, oldest first, and for the failures over the range", async () => {
		renderRouteAtWithRouter(REVIEWS);
		await screen.findByRole("heading", { name: "Needs you" }, ROUTE_RENDER_WAIT);
		const reviewReads = () => requestsTo("/practices/reviews").filter((url) => !isTabCount(url));
		await waitFor(() => expect(reviewReads()).toHaveLength(1));

		const overviewFrom = instant(currentOverview(), "from");
		const approvals = requestsTo("/practices/reviews/feedback").find(
			(url) => values(url, "deliveryState").join(",") === "AWAITING_APPROVAL",
		);
		const [unprocessedResults] = reviewReads();

		expect(approvals?.searchParams.get("sort")).toBe("OLDEST");
		expect(approvals?.searchParams.has("from")).toBe(false);
		expect(approvals?.searchParams.has("to")).toBe(false);
		// Failed reviews and deliveries are the overview's own counts: no list is read for them.
		expect(
			requestsTo("/practices/reviews/feedback").filter((url) =>
				values(url, "deliveryState").includes("FAILED"),
			),
		).toHaveLength(0);
		expect(values(unprocessedResults, "resultProcessing")).toStrictEqual(["FAILED"]);
		// The unprocessed results follow the range, from the overview's own first day; no week is read.
		expect(instant(unprocessedResults, "from")).toBe(overviewFrom);
		expect(unprocessedResults?.searchParams.has("to")).toBe(true);
		expect(unprocessedResults?.searchParams.get("size")).toBe("1");
		expect(values(unprocessedResults, "status")).toStrictEqual([]);
	});

	/**
	 * A review that ran out of time did not finish either, so it is counted with the failed ones,
	 * as the Reviews tile counts it, and the count opens the list filtered to both.
	 */
	it("counts a review that timed out among the failed reviews of the Reviews tile", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/reviews/overview", () =>
				HttpResponse.json({
					...practiceReviewOverview,
					reviews: { ...practiceReviewOverview.reviews, failed: 0, timedOut: 1 },
				}),
			),
		);
		const { router } = renderRouteAtWithRouter(REVIEWS);

		await userEvent.click(
			await screen.findByRole(
				"link",
				{ name: /failed or timed out, 1 of 32 finished reviews$/u },
				ROUTE_RENDER_WAIT,
			),
		);
		await waitFor(
			() => expect(router.state.location.pathname).toBe(`${REVIEWS}/runs`),
			ROUTE_RENDER_WAIT,
		);
		expect(router.state.location.search).toMatchObject({ status: ["FAILED", "TIMED_OUT"] });
	});

	/**
	 * A practice has no level: its row opens the Observations list filtered to it, with its counts
	 * over the list's days above the rows and the way to Practice setup.
	 */
	/** An address from before that opened a practice as a level opens its sheet. */
	it("opens an address of a practice as the practice's sheet", async () => {
		const [practice] = practiceCounts;
		assert(practice);
		const { router } = renderRouteAtWithRouter(
			`${REVIEWS}/runs?detail=practice:${practice.practiceSlug}`,
		);
		await screen.findByRole("dialog", { name: practice.practiceName }, ROUTE_RENDER_WAIT);
		expect(router.state.location.pathname).toBe(`${REVIEWS}/runs`);
	});

	/** An address from before that opened a piece of work as a level opens the work's sheet. */
	it("opens an address of a piece of work as the work's sheet", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/work?detail=work:issue:1430`);

		await waitFor(
			() => expect(router.state.location.pathname).toBe(`${REVIEWS}/runs`),
			ROUTE_RENDER_WAIT,
		);
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual(["work:issue:1430"]);
		const sheet = await screen.findByRole(
			"dialog",
			{ name: /practice-binding contract/u },
			ROUTE_RENDER_WAIT,
		);
		await within(sheet).findByRole("heading", { name: "State and history" }, ROUTE_RENDER_WAIT);
		// What the provider reported about the issue: its author and when it was opened.
		await within(sheet).findByText("Alex Smith", undefined, ROUTE_RENDER_WAIT);
		within(within(sheet).getByRole("list", { name: "History" })).getByText("Opened");
		expect(requestsTo("/practices/reviews/work/scm.issue/1430")).toHaveLength(1);
	});

	/** Work in a repository nobody monitors answers empty, once, and the sheet says why. */
	it("says the work details are missing when the repository is not monitored", async () => {
		server.use(
			http.get(
				"*/workspaces/:workspaceSlug/practices/reviews/work/:artifactKind/:artifactId",
				() => new HttpResponse(null, { status: 204 }),
			),
		);
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/work?detail=work:issue:1430`);
		const answer =
			/^Server answer: 204 No Content from GET \S*\/practices\/reviews\/work\/scm\.issue\/1430$/u;
		let sheet = await screen.findByRole(
			"dialog",
			{ name: /practice-binding contract/u },
			ROUTE_RENDER_WAIT,
		);
		await within(sheet).findByText(
			/workspace does not monitor the repository/u,
			undefined,
			ROUTE_RENDER_WAIT,
		);
		// What the server answered stands under the sentence, so an admin sees the reply itself.
		within(sheet).getByText(answer);
		expect(within(sheet).queryByRole("alert")).toBeNull();
		expect(requestsTo("/practices/reviews/work/scm.issue/1430")).toHaveLength(1);

		// Opened again from the cache, the sheet still says which answer left it without facts, also
		// while it reads the work again.
		server.use(
			http.get(
				"*/workspaces/:workspaceSlug/practices/reviews/work/:artifactKind/:artifactId",
				async () => {
					await delay("infinite");
					return new HttpResponse(null, { status: 204 });
				},
			),
		);
		const { detail } = router.state.location.search;
		await router.navigate({ to: ".", search: (previous) => ({ ...previous, detail: undefined }) });
		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull(), ROUTE_RENDER_WAIT);
		await router.navigate({
			to: ".",
			search: (previous) => ({ ...previous, detail }),
		});
		sheet = await screen.findByRole(
			"dialog",
			{ name: /practice-binding contract/u },
			ROUTE_RENDER_WAIT,
		);
		await within(sheet).findByText(answer, undefined, ROUTE_RENDER_WAIT);
	});
});

describe("the facts a sheet reads beyond its record", () => {
	it("lists each delivery of a piece of feedback with its attempts, next attempt and last error", async () => {
		const delivered = feedbackDetail("99999999-6666-6666-6666-666666666666");
		server.use(
			http.get(`*/workspaces/:workspaceSlug/practices/reviews/feedback/${delivered.id}`, () =>
				HttpResponse.json({
					...delivered,
					deliveryState: "PARTIALLY_DELIVERED",
					dispatches: [
						{
							id: "d15c0000-0000-4000-8000-000000000009",
							destination: "AUTOMATIC_REVIEW_PACKAGE",
							state: "PENDING",
							attemptCount: 3,
							nextAttemptAt: "2026-10-07T10:40:00Z",
							lastError: "GitHub answered 502 Bad Gateway.",
							createdAt: "2026-10-07T10:00:00Z",
							updatedAt: "2026-10-07T10:30:00Z",
						},
					],
				}),
			),
		);
		renderRouteAtWithRouter(`${REVIEWS}/feedback?detail=feedback:${delivered.id}`);

		const sheet = await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		const history = within(
			await within(sheet).findByRole("list", { name: "History" }, ROUTE_RENDER_WAIT),
		);
		history.getByText("Waiting to be sent");
		history.getByText("The review’s comments · 3 attempts");
		history.getByText(/Next attempt/u);
		history.getByText("GitHub answered 502 Bad Gateway.");
	});

	it("says what a review cost per attempt and gives its trace id", async () => {
		const [review] = reviewRuns;
		assert(review);
		renderRouteAtWithRouter(`${REVIEWS}/runs?detail=review:${review.id}`);

		const sheet = await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		// Technical is open by default.
		await within(sheet).findByText("$0.04 over 2 attempts", undefined, ROUTE_RENDER_WAIT);
		within(sheet).getByText("Attempt 2");
		expect(requestsTo(`/agents/jobs/${review.id}/usage`)).toHaveLength(1);
	});

	it("says an observation came from a review someone requested", async () => {
		const [row] = reviewObservations;
		assert(row);
		const detail = observationDetail(row.id);
		server.use(
			http.get(`*/workspaces/:workspaceSlug/practices/reviews/observations/${row.id}`, () =>
				HttpResponse.json({ ...detail, origin: "MANUAL" }),
			),
		);
		renderRouteAtWithRouter(`${REVIEWS}/observations?detail=observation:${row.id}`);

		const sheet = await screen.findByRole("dialog", { name: row.summary }, ROUTE_RENDER_WAIT);
		await within(sheet).findByText("Requested", undefined, ROUTE_RENDER_WAIT);
		within(sheet).getByText("Origin");
	});
});

/** The fixture's trace with every practice answered and no moment waiting, so nothing on it polls. */
const SETTLED_TRACE = {
	...artifactTrace,
	signals: artifactTrace.signals.filter((entry) => entry.state !== "PENDING"),
	practices: artifactTrace.practices.filter(
		(entry) => entry.outcome !== "PENDING" && entry.outcome !== "RUNNING",
	),
};

/** A row's Request review, then the confirmation it asks first, since it starts an agent job. */
async function requestFromRow(list: HTMLElement, work: string) {
	await userEvent.click(within(list).getByRole("button", { name: `Request review of ${work}` }));
	const dialog = await screen.findByRole("alertdialog", { name: `Request a review of ${work}?` });
	await userEvent.click(within(dialog).getByRole("button", { name: "Request review" }));
}

describe("work without a review", () => {
	const REQUESTS = "*/workspaces/:workspaceSlug/practices/review-requests";
	const LIST = "/practices/trace";

	/** The Work tab's address lands here, keeping the scope, and asks for the work no review started on. */
	it("takes the Work tab's address to the Reviews tab with no review started", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/work?from=2026-08-01&kind=scm.issue`);

		await waitFor(
			() => expect(router.state.location.pathname).toBe(`${REVIEWS}/runs`),
			ROUTE_RENDER_WAIT,
		);
		expect(router.state.location.search).toMatchObject({
			status: "NOT_STARTED",
			from: "2026-08-01",
		});
		await screen.findByRole(
			"list",
			{ name: "Work without a review, most recent first" },
			ROUTE_RENDER_WAIT,
		);
		const read = requestsTo(LIST).at(-1);
		expect(read?.searchParams.get("withoutReview")).toBe("true");
		expect(read?.searchParams.has("from")).toBe(true);
		// Not the reviews: "No review started" lists work instead of them. The Reviews tab still counts them.
		expect(requestsTo("/practices/reviews").filter((url) => !isTabCount(url))).toHaveLength(0);
	});

	/**
	 * The row opens the work's sheet, which says why no review started; a refused ask is a 200
	 * carrying the server's own sentence, said on the sheet it was asked from.
	 */
	it("says on the work's sheet why no review started, and why an ask started none", async () => {
		const bodies: unknown[] = [];
		server.use(
			http.post(REQUESTS, async ({ request }) => {
				bodies.push(await request.json());
				return HttpResponse.json({
					status: "REFUSED",
					reason: "BUDGET_EXHAUSTED",
					reasonDescription: "The workspace's AI budget is used up.",
				});
			}),
			// No review of the work waits or runs, so the sheet lets the reader ask.
			http.get("*/workspaces/:workspaceSlug/practices/trace/:artifactKind/:artifactId", () =>
				HttpResponse.json({ ...SETTLED_TRACE, artifactKind: "scm.issue", artifactId: 1430 }),
			),
		);
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/runs?withoutReview=true`);
		const list = await screen.findByRole(
			"list",
			{ name: "Work without a review, most recent first" },
			ROUTE_RENDER_WAIT,
		);
		await userEvent.click(
			within(list).getByRole("link", { name: "Define the practice-binding contract" }),
		);
		const sheet = await screen.findByRole(
			"dialog",
			{ name: /practice-binding contract/u },
			ROUTE_RENDER_WAIT,
		);
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual(["work:issue:1430"]);
		within(sheet).getByText(
			"This workspace’s review settings did not allow a review of this work.",
		);
		within(sheet).getByRole("link", { name: "Open Review: When and where" });

		await userEvent.click(within(sheet).getByRole("button", { name: "Request review" }));
		await userEvent.click(
			within(await screen.findByRole("alertdialog")).getByRole("button", {
				name: "Request review",
			}),
		);

		await within(sheet).findByText("No review was started", undefined, ROUTE_RENDER_WAIT);
		within(sheet).getByText("The workspace's AI budget is used up.");
		expect(bodies).toStrictEqual([{ artifactKind: "scm.issue", artifactId: 1430 }]);
	});

	/** A started review moves the work off this list, so the list is read again. */
	it("reads the list again once an ask is accepted", async () => {
		server.use(
			http.post(REQUESTS, () =>
				HttpResponse.json({ status: "SUBMITTED", jobId: "0f2b7c1e-9a3d-4c5b-8e1f-2d6a7b8c9d01" }),
			),
		);
		renderRouteAtWithRouter(`${REVIEWS}/runs?withoutReview=true`);
		const list = await screen.findByRole(
			"list",
			{ name: "Work without a review, most recent first" },
			ROUTE_RENDER_WAIT,
		);
		const before = requestsTo(LIST).length;
		await requestFromRow(list, "issue #1430");

		await waitFor(() => expect(requestsTo(LIST).length).toBeGreaterThan(before), ROUTE_RENDER_WAIT);
		expect(screen.queryByText("No review was started")).toBeNull();
	});

	/** An accepted ask refreshes this workspace's lists of work and of reviews, whatever their filters. */
	it("reads the workspace's work and review lists again once an ask is accepted", async () => {
		server.use(
			http.post(REQUESTS, () =>
				HttpResponse.json({ status: "SUBMITTED", jobId: "0f2b7c1e-9a3d-4c5b-8e1f-2d6a7b8c9d01" }),
			),
		);
		const { queryClient } = renderRouteAtWithRouter(`${REVIEWS}/runs?withoutReview=true`);
		const list = await screen.findByRole(
			"list",
			{ name: "Work without a review, most recent first" },
			ROUTE_RENDER_WAIT,
		);
		// Lists no screen shows right now: other filters, pages and infinite reads, and another workspace.
		const acme = { workspaceSlug: "acme" };
		const other = { workspaceSlug: "other" };
		const paged = [
			listTracedArtifactsQueryKey({ path: acme, query: { artifactKind: "scm.issue", page: 2 } }),
			listPracticeReviewsQueryKey({ path: acme, query: { page: 3 } }),
		];
		const infinite = [
			listTracedArtifactsInfiniteQueryKey({ path: acme, query: { artifactKind: "scm.issue" } }),
			listPracticeReviewsInfiniteQueryKey({ path: acme, query: { size: 7 } }),
		];
		const untouched = [
			listTracedArtifactsQueryKey({ path: other }),
			listPracticeReviewsQueryKey({ path: other }),
		];
		for (const key of [...paged, ...untouched]) {
			queryClient.setQueryData(key, { content: [] });
		}
		for (const key of infinite) {
			queryClient.setQueryData(key, { pages: [], pageParams: [] });
		}

		await requestFromRow(list, "issue #1430");

		await waitFor(() => {
			for (const key of [...paged, ...infinite]) {
				expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true);
			}
		}, ROUTE_RENDER_WAIT);
		for (const key of untouched) {
			expect(queryClient.getQueryState(key)?.isInvalidated).toBe(false);
		}
	});

	/**
	 * The work's sheet reads its trace again while a review on it waits or runs, so its moments do not
	 * go stale, and reads the work again once every review on it has settled.
	 */
	it("follows the work's trace until its reviews settle, then reads the work again", async () => {
		vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
		try {
			const TRACE = "/practices/trace/scm.issue/1430";
			const waiting = { ...artifactTrace, artifactKind: "scm.issue", artifactId: 1430 };
			let trace = waiting;
			server.use(
				http.get("*/workspaces/:workspaceSlug/practices/trace/:artifactKind/:artifactId", () =>
					HttpResponse.json(trace),
				),
			);
			const workReads = () =>
				requestsTo(LIST).filter((url) => url.searchParams.get("artifactId") === "1430").length;
			renderRouteAtWithRouter(`${REVIEWS}/runs?detail=work:issue:1430`);
			await screen.findByRole("dialog", { name: /practice-binding contract/u }, ROUTE_RENDER_WAIT);
			await waitFor(() => expect(requestsTo(TRACE).length).toBeGreaterThan(0), ROUTE_RENDER_WAIT);

			const whileWaiting = requestsTo(TRACE).length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			// The interval is faked, so the waits poll on vitest's own timer rather than on setInterval.
			await vi.waitFor(() => expect(requestsTo(TRACE).length).toBeGreaterThan(whileWaiting));

			trace = { ...SETTLED_TRACE, artifactKind: "scm.issue", artifactId: 1430 };
			const before = workReads();
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			await vi.waitFor(() => expect(workReads()).toBeGreaterThan(before));

			const settled = requestsTo(TRACE).length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS * 2);
			expect(requestsTo(TRACE)).toHaveLength(settled);
		} finally {
			vi.useRealTimers();
		}
	});

	/**
	 * Practice answers can settle before their run's results are processed. The sheet follows that run
	 * while processing waits, reads the work again once it ends, and then stops asking.
	 */
	it("follows a run whose results wait to be processed after its answers settled, then reads the work again", async () => {
		vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
		try {
			const jobId = "11111111-1111-1111-1111-111111111111";
			const JOB = `/agents/jobs/${jobId}`;
			const issue = tracedArtifacts.find((work) => work.artifactId === 1430);
			assert(issue);
			let job: AgentJob = { ...reviewJob(jobId), deliveryStatus: "PENDING" };
			let row = { ...issue, reviewedSignalCount: 0 };
			server.use(
				// Every answer settled, nothing waiting: only the run's processing is left to follow.
				http.get("*/workspaces/:workspaceSlug/practices/trace/:artifactKind/:artifactId", () =>
					HttpResponse.json({ ...SETTLED_TRACE, artifactKind: "scm.issue", artifactId: 1430 }),
				),
				http.get(`*/workspaces/:workspaceSlug${JOB}`, () => HttpResponse.json(job)),
				http.get("*/workspaces/:workspaceSlug/practices/trace", () =>
					HttpResponse.json({
						content: [row],
						page: { number: 0, size: 1, totalElements: 1, totalPages: 1 },
					}),
				),
			);
			const workReads = () =>
				requestsTo(LIST).filter((url) => url.searchParams.get("artifactId") === "1430").length;
			renderRouteAtWithRouter(`${REVIEWS}/runs?detail=work:issue:1430`);
			const sheet = await screen.findByRole(
				"dialog",
				{ name: /practice-binding contract/u },
				ROUTE_RENDER_WAIT,
			);
			await within(sheet).findByText(
				"2 moments, none started a review",
				undefined,
				ROUTE_RENDER_WAIT,
			);
			await waitFor(() => expect(requestsTo(JOB).length).toBeGreaterThan(0), ROUTE_RENDER_WAIT);

			// Processing waits: the run is read again, the work is not.
			const pendingReads = requestsTo(JOB).length;
			const before = workReads();
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			// The interval is faked, so the waits poll on vitest's own timer rather than on setInterval.
			await vi.waitFor(() => expect(requestsTo(JOB).length).toBeGreaterThan(pendingReads));
			expect(workReads()).toBe(before);

			job = { ...job, deliveryStatus: "DELIVERED" };
			row = { ...row, reviewedSignalCount: 1 };
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			await vi.waitFor(() => expect(workReads()).toBeGreaterThan(before));
			await within(sheet).findByText("2 moments, 1 started a review", undefined, ROUTE_RENDER_WAIT);

			const settled = requestsTo(JOB).length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS * 2);
			expect(requestsTo(JOB)).toHaveLength(settled);
		} finally {
			vi.useRealTimers();
		}
	});
});

describe("a new review from a row of the Reviews table", () => {
	const REQUESTS = "*/workspaces/:workspaceSlug/practices/review-requests";

	/**
	 * A row's Request review asks for a new review of its work once the reader confirms: the
	 * same ask the sheet's footer makes. The server is mocked; nothing real is reviewed.
	 */
	it("asks for a review of the row's work after the confirmation, and reads the reviews again", async () => {
		const bodies: unknown[] = [];
		server.use(
			http.post(REQUESTS, async ({ request }) => {
				bodies.push(await request.json());
				return HttpResponse.json({
					status: "SUBMITTED",
					jobId: "0f2b7c1e-9a3d-4c5b-8e1f-2d6a7b8c9d01",
				});
			}),
		);
		renderRouteAtWithRouter(`${REVIEWS}/runs`);
		const listReads = () => requestsTo("/practices/reviews").filter((url) => !isTabCount(url));
		// The table first stands as its skeleton, so the rows are found once they are drawn.
		const [button] = await screen.findAllByRole(
			"button",
			{ name: "Request review of pull request #1423" },
			ROUTE_RENDER_WAIT,
		);
		assert(button !== undefined);
		await userEvent.click(button);
		const dialog = await screen.findByRole("alertdialog", {
			name: "Request a review of pull request #1423?",
		});
		// Nothing is asked for until the reader confirms.
		expect(bodies).toHaveLength(0);
		const before = listReads().length;
		await userEvent.click(within(dialog).getByRole("button", { name: "Request review" }));

		await waitFor(() => expect(bodies).toHaveLength(1), ROUTE_RENDER_WAIT);
		expect(bodies[0]).toMatchObject({ artifactKind: "scm.pull_request" });
		await screen.findByText("Review started", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByRole("alertdialog")).toBeNull();
		await waitFor(() => expect(listReads().length).toBeGreaterThan(before), ROUTE_RENDER_WAIT);
	});

	/** While a review of the work is still running, the row offers no second one, and says why. */
	it("keeps Request review disabled on work whose review is still running", async () => {
		const bodies: unknown[] = [];
		server.use(
			http.post(REQUESTS, async ({ request }) => {
				bodies.push(await request.json());
				return HttpResponse.json({ status: "SUBMITTED" });
			}),
		);
		renderRouteAtWithRouter(`${REVIEWS}/runs`);
		const running = await screen.findByRole(
			"button",
			{ name: "Request review of pull request #1436" },
			ROUTE_RENDER_WAIT,
		);
		expect(running.getAttribute("aria-disabled")).toBe("true");
		await userEvent.click(running);
		expect(screen.queryByRole("alertdialog")).toBeNull();
		expect(bodies).toHaveLength(0);
	});
});

describe("practices turned off today", () => {
	/** The practices as they are set up now: "Thin controllers" has its autonomy at Off. */
	beforeEach(() => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices", () =>
				HttpResponse.json(
					workspacePractices.map((practice) =>
						practice.slug === "thin-controllers"
							? { ...practice, autonomy: { ...practice.autonomy, effective: "OFF" } }
							: practice,
					),
				),
			),
		);
	});

	/** The overview's Practices table reads the current setup and hides the practice until asked. */
	it("hides a turned-off practice from the Practices table until it is asked for", async () => {
		renderRouteAtWithRouter(REVIEWS);
		const show = await screen.findByRole(
			"switch",
			{ name: "Show turned-off practices (1 hidden)" },
			ROUTE_RENDER_WAIT,
		);
		expect(screen.queryByRole("link", { name: "Open practice Thin controllers" })).toBeNull();
		screen.getByRole("link", { name: "Open practice Errors carry their context" });

		await userEvent.click(show);
		await screen.findByRole("link", { name: "Open practice Thin controllers" });
	});

	/** The table's switch is in the address, so a reload or Back keeps what the reader asked for. */
	it("keeps the Practices table's turned-off switch in the URL", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}?showTurnedOff=true`);
		await screen.findByRole("link", { name: "Open practice Thin controllers" }, ROUTE_RENDER_WAIT);

		await userEvent.click(screen.getByRole("switch", { name: /^Show turned-off practices/u }));
		await waitFor(() => expect(router.state.location.search).not.toHaveProperty("showTurnedOff"));
		await waitFor(() =>
			expect(screen.queryByRole("link", { name: "Open practice Thin controllers" })).toBeNull(),
		);
	});

	it("keeps the Practices table's sort in the URL, and leaves the default out", async () => {
		const { router } = renderRouteAtWithRouter(REVIEWS);
		await screen.findByRole(
			"table",
			{ name: "Practices, the ones that need a look first" },
			ROUTE_RENDER_WAIT,
		);

		await userEvent.click(screen.getByRole("combobox", { name: "Sort" }));
		await userEvent.click(await screen.findByRole("option", { name: "Busiest first" }));
		await waitFor(() =>
			expect(router.state.location.search).toHaveProperty("practicesSort", "BUSIEST"),
		);
		await screen.findByRole("table", { name: "Practices, busiest first" });

		await userEvent.click(screen.getByRole("combobox", { name: "Sort" }));
		await userEvent.click(await screen.findByRole("option", { name: "Needs a look first" }));
		await waitFor(() => expect(router.state.location.search).not.toHaveProperty("practicesSort"));
	});

	it("opens the Practices table in the sort the URL holds", async () => {
		renderRouteAtWithRouter(`${REVIEWS}?practicesSort=BUSIEST`);
		await screen.findByRole("table", { name: "Practices, busiest first" }, ROUTE_RENDER_WAIT);
		expect(screen.getByRole("combobox", { name: "Sort" }).textContent).toContain("Busiest first");
	});

	/** The Practice facet of a list leaves the practice out the same way. */
	it("leaves a turned-off practice out of the Practice facet until it is asked for", async () => {
		renderRouteAtWithRouter(`${REVIEWS}/observations`);
		await userEvent.click(
			await screen.findByRole("combobox", { name: "Practice" }, ROUTE_RENDER_WAIT),
		);
		const options = await screen.findByRole("listbox", { name: "Practice options" });
		await within(options).findByRole("option", { name: /Errors carry their context/u });
		expect(within(options).queryByRole("option", { name: /Thin controllers/u })).toBeNull();

		await userEvent.click(
			screen.getByRole("switch", { name: "Show turned-off practices (1 hidden)" }),
		);
		await within(options).findByRole("option", { name: /Thin controllers/u });
	});
});
