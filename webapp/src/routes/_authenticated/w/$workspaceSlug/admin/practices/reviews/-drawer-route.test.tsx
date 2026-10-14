import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, assert, beforeEach, describe, expect, it, vi } from "vitest";

import { getReviewLineageQueryKey } from "@/api/@tanstack/react-query.gen";
import type { ReviewDetail, ReviewLineage, ReviewResult } from "@/api/types.gen";
import {
	feedbackDetail,
	practiceCounts,
	resultDetail,
	reviewDetail,
	reviewFeedback,
	reviewItem,
	reviewProblems,
	reviewRefOf,
	reviewResults,
	reviewUsageTwoAttempts,
	reviews,
	workspacePractices,
} from "@/components/admin/practice-reviews/fixtures";
import {
	LINEAGE_REVIEW_ID,
	storyLineage,
} from "@/components/admin/practice-reviews/lineage-fixtures";
import { RESULT_WORDS_UNAVAILABLE } from "@/components/admin/practice-reviews/result-words";
import { ACTIVE_REVIEW_POLL_MS } from "@/components/admin/practice-reviews/review-search";
import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { browserTimeZone } from "@/lib/dates";
import { itemName } from "@/lib/item-kinds";
import { server } from "@/mocks/server";
import { reviewHandlers } from "@/routes/_authenticated/w/$workspaceSlug/admin/practices/reviews/-reviews-server";
import { deferred } from "@/test/async";
import { ObserverStub } from "@/test/observers";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules; the timeout is a
// deadlock backstop, not a budget these renders were meant to fit inside.
vi.setConfig({ testTimeout: 20_000 });

const REVIEWS = "/w/acme/admin/practices/reviews";
const OVERVIEW = `${REVIEWS}/overview`;

const requested: URL[] = [];
const record = ({ request }: { request: Request }) => {
	requested.push(new URL(request.url));
};

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

beforeEach(() => {
	// jsdom needs an IntersectionObserver stub for paged lists.
	vi.stubGlobal("IntersectionObserver", ObserverStub);
	requested.length = 0;
	server.events.on("request:start", record);
	server.use(...reviewHandlers());
});

afterEach(() => {
	server.events.removeListener("request:start", record);
});

function openRow(kind: string, name: string) {
	return { name: (accessibleName: string) => accessibleName.startsWith(`Open ${kind} ${name},`) };
}

function summarized(predicate: (result: ReviewResult) => boolean): ReviewResult & {
	summary: string;
} {
	const result = reviewResults.find((row) => row.summary !== null && predicate(row));
	assert(result);
	const { summary } = result;
	assert(summary !== null);
	return { ...result, summary };
}

describe("practice review levels", () => {
	/**
	 * A row opens its record over the list rather than instead of it, so closing the record is the
	 * list exactly as the reader left it — filters and all.
	 */
	it("opens a result over its filtered list and closes back to that list", async () => {
		const result = summarized((row) => row.severity === "MAJOR");
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/results?severity=["MAJOR"]`);

		await userEvent.click(
			await screen.findByRole("link", openRow("result", result.summary), ROUTE_RENDER_WAIT),
		);

		const level = await screen.findByRole("dialog", { name: result.summary }, ROUTE_RENDER_WAIT);
		within(level).getByRole("heading", { name: result.summary });
		expect(router.state.location.searchStr).toBe(`?severity=MAJOR&detail=result:${result.id}`);

		fireEvent.keyDown(document.body, { key: "Escape" });

		await waitFor(
			() => expect(router.state.location.search).not.toHaveProperty("detail"),
			ROUTE_RENDER_WAIT,
		);
		expect(router.state.location.pathname).toBe(`${REVIEWS}/results`);
		expect(router.state.location.searchStr).toBe("?severity=MAJOR");
		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
		screen.getByRole("link", openRow("result", result.summary));
	});

	it("opens the result of a problem over the problems list", async () => {
		const [problem] = reviewProblems;
		assert(problem);
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/problems`);

		await userEvent.click(
			await screen.findByRole("link", openRow("result", problem.summary), ROUTE_RENDER_WAIT),
		);

		await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			`result:${problem.resultId}`,
		]);
		fireEvent.keyDown(document.body, { key: "Escape" });
		await waitFor(
			() => expect(router.state.location.search).not.toHaveProperty("detail"),
			ROUTE_RENDER_WAIT,
		);
		expect(router.state.location.pathname).toBe(`${REVIEWS}/problems`);
	});

	/** Closing the level gives focus back to the row it was opened from, so the reader goes on there. */
	it("returns focus to the row the level was opened from", async () => {
		const result = summarized((row) => row.severity === "MAJOR");
		renderRouteAtWithRouter(`${REVIEWS}/results`);
		const row = await screen.findByRole(
			"link",
			openRow("result", result.summary),
			ROUTE_RENDER_WAIT,
		);

		await userEvent.click(row);
		const level = await screen.findByRole("dialog", { name: result.summary }, ROUTE_RENDER_WAIT);
		await waitFor(() => expect(level.contains(document.activeElement)).toBe(true));

		await userEvent.keyboard("{Escape}");

		await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull(), ROUTE_RENDER_WAIT);
		await waitFor(() =>
			expect(document.activeElement).toBe(
				screen.getByRole("link", openRow("result", result.summary)),
			),
		);
	});

	/** A link from before that stacked several levels opens the one in front. */
	it("opens only the front record of a stacked address", async () => {
		const feedback = reviewFeedback.find(
			(row) => feedbackDetail(row.id).basis.at(0)?.result.summary != null,
		);
		assert(feedback);
		const [source] = feedbackDetail(feedback.id).basis;
		assert(source?.result.summary != null);
		const { router } = renderRouteAtWithRouter(
			`${REVIEWS}?detail=${encodeURIComponent(
				JSON.stringify([`feedback:${feedback.id}`, `result:${source.resultId}`]),
			)}`,
		);
		await screen.findByRole("dialog", { name: source.result.summary }, ROUTE_RENDER_WAIT);
		expect(screen.getAllByRole("dialog")).toHaveLength(1);
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			`result:${source.resultId}`,
		]);
	});

	/**
	 * The overview asks for its range in the reader's own days — the zone decides where a day's
	 * bucket starts — and a practice's row opens its sheet: its definition, with no counts of its own.
	 */
	it("opens a practice's sheet as its definition, the counts staying on the overview", async () => {
		const [practice] = practiceCounts;
		assert(practice);
		const { router } = renderRouteAtWithRouter(OVERVIEW);

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
		expect(router.state.location.pathname).toBe(OVERVIEW);
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			`practice:${practice.practiceSlug}`,
		]);
		await within(sheet).findByRole("heading", { name: "What it is" }, ROUTE_RENDER_WAIT);
		expect(within(sheet).queryByText(/^Counts /u)).toBeNull();
		await waitFor(() =>
			expect(requestsTo(`/practices/${practice.practiceSlug}/versions`)).toHaveLength(1),
		);
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
		renderRouteAtWithRouter(`${OVERVIEW}?range=7d`);
		await screen.findByRole("heading", { name: "What the reviews did" }, ROUTE_RENDER_WAIT);
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

	it("asks for the review time over the overview's range", async () => {
		renderRouteAtWithRouter(`${OVERVIEW}?range=7d`);
		await screen.findByRole("heading", { name: "What the reviews did" }, ROUTE_RENDER_WAIT);
		await waitFor(() => expect(requestsTo("/practices/reviews/timing")).toHaveLength(1));

		const timing = requestsTo("/practices/reviews/timing").at(0);
		expect(instant(timing, "from")).toBe(instant(currentOverview(), "from"));
		expect(timing?.searchParams.has("to")).toBe(false);
	});

	/**
	 * A decision owed does not expire with the range, so the approvals ask for every piece of
	 * feedback awaiting one, oldest first — the order they are worked through in.
	 */
	it("asks for every decision owed, oldest first, whatever the range", async () => {
		renderRouteAtWithRouter(OVERVIEW);
		await screen.findByRole("heading", { name: "Needs you" }, ROUTE_RENDER_WAIT);
		await waitFor(() =>
			expect(
				requestsTo("/practices/reviews/feedback").some(
					(url) => values(url, "state").join(",") === "AWAITING_APPROVAL",
				),
			).toBe(true),
		);

		const approvals = requestsTo("/practices/reviews/feedback").find(
			(url) => values(url, "state").join(",") === "AWAITING_APPROVAL",
		);
		expect(approvals?.searchParams.get("sort")).toBe("OLDEST");
		expect(approvals?.searchParams.has("from")).toBe(false);
		expect(approvals?.searchParams.has("to")).toBe(false);
		expect(
			requestsTo("/practices/reviews/feedback").filter((url) =>
				values(url, "state").includes("FAILED"),
			),
		).toHaveLength(0);
	});

	/** An address from before that opened a practice as a level opens its sheet. */
	it("opens an address of a practice as the practice's sheet", async () => {
		const [practice] = practiceCounts;
		assert(practice);
		const { router } = renderRouteAtWithRouter(
			`${REVIEWS}?detail=practice:${practice.practiceSlug}`,
		);
		await screen.findByRole("dialog", { name: practice.practiceName }, ROUTE_RENDER_WAIT);
		expect(router.state.location.pathname).toBe(REVIEWS);
	});
});

describe("the facts a sheet reads beyond its record", () => {
	it("says what a review cost per attempt", async () => {
		const [review] = reviews;
		assert(review);
		server.use(
			http.get(`*/workspaces/:workspaceSlug/practices/reviews/${review.id}`, () =>
				HttpResponse.json({ ...reviewDetail(review.id), usage: reviewUsageTwoAttempts }),
			),
		);
		renderRouteAtWithRouter(`${REVIEWS}?detail=review:${review.id}`);

		const sheet = await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		// Technical is open by default.
		await within(sheet).findByText("$0.04 over 2 attempts", undefined, ROUTE_RENDER_WAIT);
		within(sheet).getByText("Attempt 2");
		expect(requestsTo(`/practices/reviews/${review.id}`)).toHaveLength(1);
	});

	it("says a result came from a review someone requested", async () => {
		const row = summarized(() => true);
		const detail = resultDetail(row.id);
		server.use(
			http.get(`*/workspaces/:workspaceSlug/practices/reviews/results/${row.id}`, () =>
				HttpResponse.json({ ...detail, origin: "REQUEST" }),
			),
		);
		renderRouteAtWithRouter(`${REVIEWS}/results?detail=result:${row.id}`);

		const sheet = await screen.findByRole("dialog", { name: row.summary }, ROUTE_RENDER_WAIT);
		await within(sheet).findByText("Requested", undefined, ROUTE_RENDER_WAIT);
	});
});

describe("a new review from a row of the Reviews table", () => {
	const REQUESTS = "*/workspaces/:workspaceSlug/practices/review-requests";

	it("asks for a review of the row's item after the confirmation, and reads the reviews again", async () => {
		const bodies: unknown[] = [];
		const completed = reviewDetail();
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/reviews", () =>
				HttpResponse.json({
					content: [completed],
					number: 0,
					size: 25,
					totalElements: 1,
					totalPages: 1,
					numberOfElements: 1,
					first: true,
					last: true,
					empty: false,
				}),
			),
			http.post(REQUESTS, async ({ request }) => {
				bodies.push(await request.json());
				return HttpResponse.json({
					status: "SUBMITTED",
					reviewId: "0f2b7c1e-9a3d-4c5b-8e1f-2d6a7b8c9d01",
					reason: null,
				});
			}),
		);
		renderRouteAtWithRouter(REVIEWS);
		const itemLabel = itemName(reviewItem);
		const name = `${itemLabel.charAt(0).toLowerCase()}${itemLabel.slice(1)}`;
		const listReads = () =>
			requestsTo("/practices/reviews").filter((url) => url.searchParams.get("size") !== "1");
		// The table first stands as its skeleton, so the rows are found once they are drawn.
		const [button] = await screen.findAllByRole(
			"button",
			{ name: `Request review of ${name}` },
			ROUTE_RENDER_WAIT,
		);
		assert(button !== undefined);
		await userEvent.click(button);
		const dialog = await screen.findByRole("alertdialog", {
			name: `Request a review of ${name}?`,
		});
		// Nothing is asked for until the reader confirms.
		expect(bodies).toHaveLength(0);
		const before = listReads().length;
		await userEvent.click(within(dialog).getByRole("button", { name: "Request review" }));

		await waitFor(() => expect(bodies).toHaveLength(1), ROUTE_RENDER_WAIT);
		expect(bodies[0]).toStrictEqual({ itemId: reviewItem.id });
		await screen.findByText("Review started", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByRole("alertdialog")).toBeNull();
		await waitFor(() => expect(listReads().length).toBeGreaterThan(before), ROUTE_RENDER_WAIT);
	});
});

describe("practices turned off today", () => {
	beforeEach(() => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices", () =>
				HttpResponse.json(
					workspacePractices.map((practice) =>
						practice.slug === "thin-controllers"
							? { ...practice, feedbackMode: { ...practice.feedbackMode, effective: "OFF" } }
							: practice,
					),
				),
			),
		);
	});

	/** The overview's Practices table reads the current setup and hides the practice until asked. */
	it("hides a turned-off practice from the Practices table until it is asked for", async () => {
		renderRouteAtWithRouter(OVERVIEW);
		const show = await screen.findByRole(
			"switch",
			{ name: "Show turned-off practices (1 hidden)" },
			ROUTE_RENDER_WAIT,
		);
		expect(screen.queryByRole("link", { name: "Open practice Thin controllers" })).toBeNull();
		screen.getByRole("link", { name: "Open practice Errors carry their context" });

		await userEvent.click(show);
		await screen.findByRole("link", { name: "Open practice Thin controllers" }, ROUTE_RENDER_WAIT);
	});

	/** The table's switch is in the address, so a reload or Back keeps what the reader asked for. */
	it("keeps the Practices table's turned-off switch in the URL", async () => {
		const { router } = renderRouteAtWithRouter(`${OVERVIEW}?showTurnedOff=true`);
		await screen.findByRole("link", { name: "Open practice Thin controllers" }, ROUTE_RENDER_WAIT);

		await userEvent.click(screen.getByRole("switch", { name: /^Show turned-off practices/u }));
		await waitFor(() => expect(router.state.location.search).not.toHaveProperty("showTurnedOff"));
		await waitFor(() =>
			expect(screen.queryByRole("link", { name: "Open practice Thin controllers" })).toBeNull(),
		);
	});

	it("keeps the Practices table's sort in the URL, and leaves the default out", async () => {
		const { router } = renderRouteAtWithRouter(OVERVIEW);
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
		renderRouteAtWithRouter(`${OVERVIEW}?practicesSort=BUSIEST`);
		await screen.findByRole("table", { name: "Practices, busiest first" }, ROUTE_RENDER_WAIT);
		expect(screen.getByRole("combobox", { name: "Sort" }).textContent).toContain("Busiest first");
	});

	/** The Practice facet of a list leaves the practice out the same way. */
	it("leaves a turned-off practice out of the Practice facet until it is asked for", async () => {
		renderRouteAtWithRouter(`${REVIEWS}/results`);
		await userEvent.click(
			await screen.findByRole("combobox", { name: "Practice" }, ROUTE_RENDER_WAIT),
		);
		const options = await screen.findByRole("listbox", { name: "Practice options" });
		await within(options).findByRole("option", { name: /Errors carry their context/u });
		expect(within(options).queryByRole("option", { name: /Thin controllers/u })).toBeNull();

		await userEvent.click(
			await screen.findByRole("switch", { name: /^Show turned-off practices/u }),
		);
		await within(options).findByRole("option", { name: /Thin controllers/u });
	});
});

it("keeps hidden result facts and navigation without restoring its text", async () => {
	const [first] = reviewResults;
	assert(first);
	const detail = resultDetail(first.id);
	server.use(
		http.get("*/workspaces/:workspaceSlug/practices/reviews/results/:id", () =>
			HttpResponse.json({
				...detail,
				summary: null,
				explanation: null,
				problems: [],
				evidenceHidden: true,
				evidence: null,
				example: null,
			}),
		),
	);
	const { router } = renderRouteAtWithRouter(`${REVIEWS}/results?detail=result:${detail.id}`);
	const heading = await screen.findByRole(
		"heading",
		{ name: "Result text unavailable", level: 2 },
		ROUTE_RENDER_WAIT,
	);
	const dialog = heading.closest<HTMLElement>('[role="dialog"]');
	assert(dialog);
	const panel = within(dialog);
	panel.getByText(RESULT_WORDS_UNAVAILABLE);
	panel.getByText(detail.practiceName);
	await userEvent.click(panel.getByRole("button", { name: "Close" }));
	await waitFor(() => expect(router.state.location.searchStr).not.toContain("detail="));
});

function lineageOf(review: ReviewDetail, item = review.item): ReviewLineage {
	return storyLineage({
		root: { kind: "REVIEW", id: review.id },
		item,
		reviews: [{ ...reviewRefOf(review), item }],
		results: [],
		feedback: [],
	});
}

const unavailable = (detail: string) => HttpResponse.json({ status: 503, detail }, { status: 503 });

describe("a review's trace while the review runs", () => {
	const RUNNING_ID = "3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84";
	const base = reviewDetail(LINEAGE_REVIEW_ID);

	function reviewAs(id: string, status: ReviewDetail["status"]): ReviewDetail {
		const active = status === "QUEUED" || status === "RUNNING";
		return {
			...base,
			id,
			status,
			completedAt: active ? null : base.createdAt,
			activeJobCount: active ? 1 : 0,
		};
	}

	function serveTrace(getLineage: () => Response, getReview: () => Response) {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/reviews/lineage", () => getLineage()),
			http.get(`*/workspaces/:workspaceSlug/practices/reviews/${RUNNING_ID}`, () => getReview()),
		);
	}

	it("shows a failed trace refresh and resumes only after Retry", async () => {
		vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
		try {
			const review = reviewAs(RUNNING_ID, "RUNNING");
			let reply = () => HttpResponse.json(lineageOf(review));
			serveTrace(
				() => reply(),
				() => HttpResponse.json(review),
			);
			renderRouteAtWithRouter(`${REVIEWS}?detail=trace:review:${RUNNING_ID}`);
			await screen.findByRole("group", { name: "How this came about" }, ROUTE_RENDER_WAIT);
			reply = () => unavailable("Trace temporarily unavailable");
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			const retry = await screen.findByRole("button", { name: "Retry" }, ROUTE_RENDER_WAIT);
			expect(screen.queryByRole("group", { name: "How this came about" })).toBeNull();
			const failedReads = requestsTo("/practices/reviews/lineage").length;
			await vi.advanceTimersByTimeAsync(2 * ACTIVE_REVIEW_POLL_MS);
			expect(requestsTo("/practices/reviews/lineage")).toHaveLength(failedReads);
			reply = () => HttpResponse.json(lineageOf(review));
			await userEvent.click(retry);
			await screen.findByRole("group", { name: "How this came about" }, ROUTE_RENDER_WAIT);
			const recovered = requestsTo("/practices/reviews/lineage").length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			await vi.waitFor(() =>
				expect(requestsTo("/practices/reviews/lineage").length).toBeGreaterThan(recovered),
			);
		} finally {
			vi.useRealTimers();
		}
	});

	it("keeps the newly selected finished trace when an older running read arrives", async () => {
		vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
		const release = deferred();
		try {
			const secondId = "6f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84";
			const first = reviewAs(RUNNING_ID, "RUNNING");
			const second = reviewAs(secondId, "COMPLETE");
			const secondItem = { ...second.item, label: "Second selected work", title: null };
			const lineages = new Map([
				[RUNNING_ID, async () => HttpResponse.json(lineageOf(first))],
				[secondId, async () => HttpResponse.json(lineageOf(second, secondItem))],
			]);
			server.use(
				http.get("*/workspaces/:workspaceSlug/practices/reviews/lineage", async ({ request }) => {
					const id = new URL(request.url).searchParams.get("reviewId");
					assert(id !== null);
					const reply = lineages.get(id);
					assert(reply);
					return reply();
				}),
				...[first, second].map((review) =>
					http.get(`*/workspaces/:workspaceSlug/practices/reviews/${review.id}`, () =>
						HttpResponse.json(review),
					),
				),
			);
			const { router } = renderRouteAtWithRouter(`${REVIEWS}?detail=trace:review:${RUNNING_ID}`);
			await screen.findByRole("group", { name: "How this came about" }, ROUTE_RENDER_WAIT);
			lineages.set(RUNNING_ID, async () => {
				await release.promise;
				return HttpResponse.json(lineageOf(first));
			});
			const firstReads = requestsTo("/practices/reviews/lineage").length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			await vi.waitFor(() =>
				expect(requestsTo("/practices/reviews/lineage").length).toBeGreaterThan(firstReads),
			);
			router.history.push(`${REVIEWS}?detail=trace:review:${secondId}`);
			await screen.findByRole("dialog", { name: /Second selected work/u }, ROUTE_RENDER_WAIT);
			const secondReads = () =>
				requestsTo("/practices/reviews/lineage").filter(
					(url) => url.searchParams.get("reviewId") === secondId,
				).length;
			const settled = secondReads();
			release.resolve();
			await vi.advanceTimersByTimeAsync(2 * ACTIVE_REVIEW_POLL_MS);
			expect(secondReads()).toBe(settled);
			screen.getByRole("dialog", { name: /Second selected work/u });
			expect(router.state.location.search.detail).toBe(`trace:review:${secondId}`);
		} finally {
			release.resolve();
			vi.useRealTimers();
		}
	});

	it("follows a selected review's trace until it stops, then reads it once more", async () => {
		vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
		try {
			let review = reviewAs(RUNNING_ID, "RUNNING");
			serveTrace(
				() => HttpResponse.json(lineageOf(review)),
				() => HttpResponse.json(review),
			);
			const { router, queryClient } = renderRouteAtWithRouter(
				`${REVIEWS}?detail=trace:review:${RUNNING_ID}`,
			);
			await screen.findByRole("group", { name: "How this came about" }, ROUTE_RENDER_WAIT);
			await vi.waitFor(() =>
				expect(requestsTo(`/practices/reviews/${RUNNING_ID}`).length).toBeGreaterThan(0),
			);
			const reviewReads = requestsTo(`/practices/reviews/${RUNNING_ID}`).length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			// Waits must use Vitest's polling timer while the interval is faked.
			await vi.waitFor(() =>
				expect(requestsTo(`/practices/reviews/${RUNNING_ID}`).length).toBeGreaterThan(reviewReads),
			);

			review = reviewAs(RUNNING_ID, "COMPLETE");
			const before = requestsTo("/practices/reviews/lineage").length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			await vi.waitFor(() =>
				expect(requestsTo("/practices/reviews/lineage").length).toBeGreaterThan(before),
			);

			await screen.findByText("Review complete", undefined, ROUTE_RENDER_WAIT);
			const lineageKey = getReviewLineageQueryKey({
				path: { workspaceSlug: "acme" },
				query: { kind: "REVIEW", reviewId: RUNNING_ID },
			});
			await vi.waitFor(() =>
				expect(queryClient.getQueryState(lineageKey)?.fetchStatus).toBe("idle"),
			);
			const settledLineage = requestsTo("/practices/reviews/lineage").length;
			const settledReview = requestsTo(`/practices/reviews/${RUNNING_ID}`).length;
			await vi.advanceTimersByTimeAsync(2 * ACTIVE_REVIEW_POLL_MS);
			expect(requestsTo("/practices/reviews/lineage")).toHaveLength(settledLineage);
			expect(requestsTo(`/practices/reviews/${RUNNING_ID}`)).toHaveLength(settledReview);
			expect(
				requestsTo("/practices/reviews/lineage").every(
					(url) => url.searchParams.get("reviewId") === RUNNING_ID,
				),
			).toBe(true);
			expect(router.state.location.search.detail).toBe(`trace:review:${RUNNING_ID}`);
		} finally {
			vi.useRealTimers();
		}
	});

	it("shows a failed read of the review and retries its final state", async () => {
		vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
		try {
			let review = reviewAs(RUNNING_ID, "RUNNING");
			let reply = () => HttpResponse.json(review);
			serveTrace(
				() => HttpResponse.json(lineageOf(review)),
				() => reply(),
			);
			renderRouteAtWithRouter(`${REVIEWS}?detail=trace:review:${RUNNING_ID}`);
			await screen.findByRole("group", { name: "How this came about" }, ROUTE_RENDER_WAIT);
			await vi.waitFor(() =>
				expect(requestsTo(`/practices/reviews/${RUNNING_ID}`).length).toBeGreaterThan(0),
			);
			reply = () => unavailable("Review temporarily unavailable");
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			const retry = await screen.findByRole("button", { name: "Retry" }, ROUTE_RENDER_WAIT);
			expect(screen.queryByRole("group", { name: "How this came about" })).toBeNull();
			const failedReads = requestsTo(`/practices/reviews/${RUNNING_ID}`).length;
			await vi.advanceTimersByTimeAsync(2 * ACTIVE_REVIEW_POLL_MS);
			expect(requestsTo(`/practices/reviews/${RUNNING_ID}`)).toHaveLength(failedReads);

			review = reviewAs(RUNNING_ID, "FAILED");
			reply = () => HttpResponse.json(review);
			await userEvent.click(retry);
			await screen.findByRole("group", { name: "How this came about" }, ROUTE_RENDER_WAIT);
			const settledReviews = requestsTo(`/practices/reviews/${RUNNING_ID}`).length;
			const settledLineage = requestsTo("/practices/reviews/lineage").length;
			await vi.advanceTimersByTimeAsync(2 * ACTIVE_REVIEW_POLL_MS);
			expect(requestsTo(`/practices/reviews/${RUNNING_ID}`)).toHaveLength(settledReviews);
			expect(requestsTo("/practices/reviews/lineage")).toHaveLength(settledLineage);
		} finally {
			vi.useRealTimers();
		}
	});
});
