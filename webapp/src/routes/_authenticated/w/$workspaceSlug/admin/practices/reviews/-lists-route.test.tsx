import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, assert, describe, expect, it, vi } from "vitest";

import type { ReviewSummary } from "@/api/types.gen";
import {
	reviewAccounts,
	reviewFeedback,
	reviews,
} from "@/components/admin/practice-reviews/fixtures";
import { developerLabel } from "@/components/admin/practice-reviews/review-format";
import { ACTIVE_REVIEW_POLL_MS } from "@/components/admin/practice-reviews/review-search";
import { reviewHandlers } from "@/components/admin/practice-reviews/story-mock-server";

import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { ownMembership } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { precedes } from "@/test/dom";
import { ObserverStub } from "@/test/observers";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules; the timeout is a
// deadlock backstop, not a budget these renders were meant to fit inside.
vi.setConfig({ testTimeout: 15_000 });

/**
 * Repeated params and comma-joined params both, because which one the generated client emits is its
 * business and an assertion that guesses wrong passes or fails for the wrong reason.
 */
function values(url: URL | undefined, name: string): string[] {
	return (url?.searchParams.getAll(name) ?? [])
		.flatMap((value) => value.split(","))
		.filter(Boolean);
}

/**
 * The one-row reads behind the tab counts (`useReviewTabCounts`): they ask the same endpoints as the
 * lists, so a recorder of what a list asked for leaves them out.
 */
function isTabCount(url: URL): boolean {
	return url.searchParams.get("size") === "1";
}

/** A Spring page, as every list endpoint answers: page `number` of `totalPages`. */
function springPage<T>(content: T[], number = 0, totalPages = 1, totalElements = content.length) {
	return {
		content,
		number,
		size: 25,
		totalElements,
		totalPages,
		numberOfElements: content.length,
		first: number === 0,
		last: number >= totalPages - 1,
		empty: content.length === 0,
	};
}

/** The page a list endpoint answers with, every row on it. */
function pageOf<T>(rows: T[]) {
	return springPage(rows);
}

const emptyPage = pageOf([]);

/**
 * Every request the list routes make, with the review, result, problem and feedback URLs recorded.
 * The screens they feed take their rows as props, so a story mounts them without ever issuing the
 * request — this route test is the only place that can see what went on the wire.
 */
function recordRequests() {
	const reviewUrls: URL[] = [];
	const resultUrls: URL[] = [];
	const problemUrls: URL[] = [];
	const feedbackUrls: URL[] = [];
	const locationCountUrls: URL[] = [];
	const listed =
		(urls: URL[]) =>
		({ request }: { request: Request }) => {
			const url = new URL(request.url);
			if (!isTabCount(url)) {
				urls.push(url);
			}
			return HttpResponse.json(emptyPage);
		};
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json(ownMembership("ADMIN")),
		),
		http.get("*/workspaces/:workspaceSlug/members", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practice-groups", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/results", listed(resultUrls)),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/problems", listed(problemUrls)),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", listed(feedbackUrls)),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/locations", ({ request }) => {
			locationCountUrls.push(new URL(request.url));
			return HttpResponse.json([]);
		}),
		http.get("*/workspaces/:workspaceSlug/practices/reviews", listed(reviewUrls)),
	);
	return { reviewUrls, resultUrls, problemUrls, feedbackUrls, locationCountUrls };
}

/**
 * The reviews endpoint as the list and its poll of the active reviews read it. The poll is the
 * request with no page: it asks for the active statuses alone, so a review that stopped is not in it.
 */
function activeReviewsHandler(getReview: () => ReviewSummary, polls: URL[], reads: URL[]) {
	return http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
		const url = new URL(request.url);
		const poll = !url.searchParams.has("page");
		if (!isTabCount(url)) {
			(poll ? polls : reads).push(url);
		}
		const review = getReview();
		const rows = poll && !values(url, "status").includes(review.status) ? [] : [review];
		return HttpResponse.json(pageOf(rows));
	});
}

/** Two finished reviews of two different items, so the second page's row shows that it arrived. */
const [FIRST_PAGE, SECOND_PAGE] = twoCompleteReviews();

function twoCompleteReviews(): [ReviewSummary, ReviewSummary] {
	const complete = reviews.filter((review) => review.status === "COMPLETE");
	const first = complete[0];
	const second = complete.find((review) => review.item.label !== first?.item.label);
	if (!first || !second) {
		throw new Error("The fixture has fewer than two complete reviews of different items");
	}
	return [first, second];
}

/** The accessible name of a review row's link, by the item it reviewed. */
function reviewLinkName(review: ReviewSummary): RegExp {
	return new RegExp(`^Open review of ${review.item.label},`, "u");
}

/**
 * The reviews endpoint with two pages, every request recorded. A review started after the first page
 * loaded pushed that page's row down, so the second page starts with it again.
 */
function serveReviewPages(reviewUrls: URL[]) {
	server.use(
		http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
			const url = new URL(request.url);
			if (!isTabCount(url)) {
				reviewUrls.push(url);
			}
			const number = Number(url.searchParams.get("page"));
			return HttpResponse.json(
				springPage(number === 0 ? [FIRST_PAGE] : [FIRST_PAGE, SECOND_PAGE], number, 2, 3),
			);
		}),
	);
}

describe("practice review list routes", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	/**
	 * The one wire detail these screens can get wrong silently. The URL spells the ordering `order`,
	 * because another route already owns the word `sort` in the same search namespace with entirely
	 * different values; the endpoint spells it `sort`. A list ordered by the server looks exactly like
	 * a list ordered by the server's default, so nothing but the request itself can catch a rename —
	 * and this is the surface that exists to put the results most worth acting on at the top.
	 */
	it("asks the endpoint for the ordering the URL chose, under the name the endpoint uses", async () => {
		const { resultUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/results?order=ACTIONABILITY&severity=["MAJOR"]',
		);
		await screen.findByText("No results match these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = resultUrls.at(-1);
		expect(requested?.searchParams.get("sort")).toBe("ACTIONABILITY");
		expect(requested?.searchParams.get("order")).toBeNull();
		// The rest of the query travelled too, so a passing `sort` cannot be the only surviving param.
		expect(values(requested, "severity")).toContain("MAJOR");
		expect(requested?.searchParams.get("size")).toBe("25");
	});

	/**
	 * The feedback list reads its ordering from the same URL word, and the approvals queue on the
	 * overview links here oldest first, so a lost ordering would put the newest proposal at the top of
	 * a queue that is worked through from the other end.
	 */
	it("asks the feedback endpoint for the ordering the URL chose, under the same names", async () => {
		const { feedbackUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/feedback?order=OLDEST&state=["AWAITING_APPROVAL"]',
		);
		await screen.findByText("No feedback matches these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = feedbackUrls.at(-1);
		expect(requested?.searchParams.get("sort")).toBe("OLDEST");
		expect(requested?.searchParams.get("order")).toBeNull();
		expect(values(requested, "state")).toStrictEqual(["AWAITING_APPROVAL"]);
	});

	/** The default ordering is the server's, so nothing is sent rather than a guess at its name. */
	it("sends no ordering when the reader has not chosen one", async () => {
		const { resultUrls } = recordRequests();

		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/results");
		await screen.findByText("No results yet", undefined, ROUTE_RENDER_WAIT);

		expect(resultUrls.at(-1)?.searchParams.get("sort")).toBeNull();
	});

	it("sends no feedback ordering when the reader has not chosen one", async () => {
		const { feedbackUrls } = recordRequests();

		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/feedback");
		await screen.findByText("No feedback yet", undefined, ROUTE_RENDER_WAIT);

		expect(feedbackUrls.at(-1)?.searchParams.get("sort")).toBeNull();
	});

	/**
	 * The overview's counts link here as outcome and marked-incorrect filters, so a count that opens a
	 * list which forgot either shows more rows than the number the reader clicked.
	 */
	it("sends the outcome and marked-incorrect filters to the endpoint", async () => {
		const { resultUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/results?outcome=["NOT_MET"]&markedIncorrect=true',
		);
		await screen.findByText("No results match these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = resultUrls.at(-1);
		expect(values(requested, "outcome")).toStrictEqual(["NOT_MET"]);
		expect(requested?.searchParams.get("markedIncorrect")).toBe("true");
	});

	/**
	 * The URL carries withholding *families*, which is the question an operator asks; the endpoint
	 * filters on individual stop reasons. The expansion happens on the way to the request, so a family
	 * that stopped expanding would return everything and read as a filter that simply matched a lot.
	 */
	it("expands a withholding family to the stop reasons the endpoint filters on", async () => {
		const { feedbackUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/feedback?withheldFamily=["HOUSEKEEPING"]',
		);
		await screen.findByText("No feedback matches these filters", undefined, ROUTE_RENDER_WAIT);

		const reasons = values(feedbackUrls.at(-1), "stopReason");
		expect(reasons).toContain("NOTHING_NEW");
		expect(reasons).not.toContain("HOUSEKEEPING");
		expect(feedbackUrls.at(-1)?.searchParams.get("withheldFamily")).toBeNull();
	});

	it("sends the severity and scope filters to the problems endpoint", async () => {
		const { problemUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/observations?severity=["MAJOR"]&developerId=7&practiceSlug=["thin-controllers"]&groupSlug=["design"]&from=2026-09-01&to=2026-09-10&resultId=3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84',
		);
		await screen.findByText("No problems match these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = problemUrls.at(-1);
		expect(values(requested, "severity")).toStrictEqual(["MAJOR"]);
		expect(requested?.searchParams.get("developerId")).toBe("7");
		expect(values(requested, "practiceSlug")).toStrictEqual(["thin-controllers"]);
		expect(values(requested, "groupSlug")).toStrictEqual(["design"]);
		expect(requested?.searchParams.get("from")).not.toBeNull();
		expect(requested?.searchParams.get("to")).not.toBeNull();
		// A filter to one result lists only its problems.
		expect(requested?.searchParams.get("resultId")).toBe("3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84");
	});

	it("asks for the problems of the severity chosen, and for all of them once it is cleared", async () => {
		const { problemUrls } = recordRequests();

		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices/reviews/observations");
		const severity = await screen.findByRole("combobox", { name: "Severity" }, ROUTE_RENDER_WAIT);
		await userEvent.click(severity);
		await userEvent.click(await screen.findByRole("option", { name: /Major/u }));
		await waitFor(() => expect(router.state.location.searchStr).toContain("severity=MAJOR"));
		await waitFor(() => expect(values(problemUrls.at(-1), "severity")).toStrictEqual(["MAJOR"]));

		await userEvent.click(await screen.findByRole("option", { name: /Major/u }));
		await waitFor(() => expect(router.state.location.searchStr).not.toContain("severity"));
		await waitFor(() => expect(values(problemUrls.at(-1), "severity")).toStrictEqual([]));
	});

	/**
	 * A practice's feedback count on the overview links here filtered to that practice, so a list that
	 * dropped the practice would show every piece of feedback under a number that counted a few.
	 */
	it("sends the practice filter to the feedback endpoint", async () => {
		const { feedbackUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/feedback?practiceSlug=["thin-controllers"]',
		);
		await screen.findByText("No feedback matches these filters", undefined, ROUTE_RENDER_WAIT);

		expect(values(feedbackUrls.at(-1), "practiceSlug")).toStrictEqual(["thin-controllers"]);
	});

	/** A location is a destination split by the provider that shows it; the endpoint filters by it. */
	it("sends the location filter to the feedback endpoint", async () => {
		const { feedbackUrls, locationCountUrls } = recordRequests();

		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/feedback?location=GITLAB");
		await screen.findByText("No feedback matches these filters", undefined, ROUTE_RENDER_WAIT);

		expect(feedbackUrls.at(-1)?.searchParams.get("location")).toBe("GITLAB");
		// No developer is filtered to, so there is no summary to count.
		expect(locationCountUrls).toHaveLength(0);
	});

	/**
	 * Filtered to a developer, the list leads with their summary. It counts everything for them,
	 * whatever else the list is narrowed to, so its counts can open the list for them alone.
	 */
	it("counts everything for the developer filtered to, and nothing narrower", async () => {
		const { feedbackUrls, locationCountUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/feedback?developerId=9&location=SLACK&state=["DELIVERED"]',
		);
		await screen.findByText("No feedback for this developer yet", undefined, ROUTE_RENDER_WAIT);

		expect(feedbackUrls.at(-1)?.searchParams.get("developerId")).toBe("9");
		const counts = locationCountUrls.at(-1);
		expect(counts?.searchParams.get("developerId")).toBe("9");
		expect(counts?.searchParams.get("location")).toBeNull();
		expect(values(counts, "state")).toStrictEqual([]);
	});

	/** A row's developer opens the list filtered to them alone, which is where their summary is. */
	it("names a row's developer as text, so the row opens one thing", async () => {
		recordRequests();
		const [feedback] = reviewFeedback;
		assert(feedback);
		const name = developerLabel(feedback.developer);
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
				HttpResponse.json(pageOf([feedback])),
			),
		);

		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/feedback");
		await screen.findByText(name, undefined, ROUTE_RENDER_WAIT);

		// The feedback's level links the developer; the row's one link is the feedback itself.
		expect(screen.queryByRole("link", { name: `All feedback for ${name}` })).toBeNull();
	});

	/**
	 * A running review changes after the list loaded, also under a status filter: the list follows
	 * the active reviews it shows until they stop, reads itself again, and then stops asking.
	 */
	it("follows a running review until it stops, then stops asking", async () => {
		vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
		try {
			const { reviewUrls } = recordRequests();
			const polls: URL[] = [];
			let review: ReviewSummary = { ...FIRST_PAGE, status: "RUNNING", completedAt: null };
			server.use(activeReviewsHandler(() => review, polls, reviewUrls));

			renderRouteAtWithRouter('/w/acme/admin/practices/reviews/runs?status=["RUNNING","FAILED"]');
			const table = await screen.findByRole(
				"table",
				{ name: "Practice reviews, newest first" },
				ROUTE_RENDER_WAIT,
			);
			await within(table).findByRole("link", { name: reviewLinkName(FIRST_PAGE) });
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			// The interval is faked, so the waits poll on vitest's own timer rather than on setInterval.
			await vi.waitFor(() => expect(polls.length).toBeGreaterThan(0));
			// The poll keeps the list's own filter: a status it leaves out is not asked for.
			expect(values(polls.at(-1), "status")).toStrictEqual(["RUNNING"]);
			expect(within(table).queryAllByText("Failed")).toHaveLength(0);

			review = { ...review, status: "FAILED" };
			const listReads = reviewUrls.length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			await vi.waitFor(() => expect(reviewUrls.length).toBeGreaterThan(listReads));
			await within(table).findAllByText("Failed", undefined, ROUTE_RENDER_WAIT);

			const settled = polls.length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS * 2);
			expect(polls).toHaveLength(settled);
		} finally {
			vi.useRealTimers();
		}
	});

	/**
	 * The shared scope reaches the reviews endpoint under the names the result list uses: the
	 * developer, the practice and the group that a review recorded a result of.
	 */
	it("sends the shared developer, practice, group, range and review to the reviews endpoint", async () => {
		const { reviewUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/runs?developerId=7&practiceSlug=["thin-controllers"]&groupSlug=["design"]&from=2026-09-01&to=2026-09-10&reviewId=3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84',
		);
		await screen.findByText("No reviews match these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = reviewUrls.at(-1);
		expect(requested?.searchParams.get("developerId")).toBe("7");
		expect(values(requested, "practiceSlug")).toStrictEqual(["thin-controllers"]);
		expect(values(requested, "groupSlug")).toStrictEqual(["design"]);
		expect(requested?.searchParams.get("from")).not.toBeNull();
		expect(requested?.searchParams.get("to")).not.toBeNull();
		// A review the address filters by narrows the list to itself.
		expect(requested?.searchParams.get("reviewId")).toBe("3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84");
	});

	/** A group finds the feedback written from results of any practice in it. */
	it("sends the group filter to the feedback endpoint", async () => {
		const { feedbackUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/feedback?groupSlug=["design"]&from=2026-09-06&to=2026-10-05&state=["WITHHELD","REJECTED","REPLACED"]',
		);
		await screen.findByText("No feedback matches these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = feedbackUrls.at(-1);
		expect(values(requested, "groupSlug")).toStrictEqual(["design"]);
		expect(values(requested, "state")).toStrictEqual(["WITHHELD", "REJECTED", "REPLACED"]);
	});

	/**
	 * The scope applies to every tab, so it is above the tabs, with the tracked record; each tab's own
	 * filters, its Reset and its count are below them.
	 */
	it.each([
		["runs", "Created", "Status"],
		["results", "Recorded", "Outcome"],
		["observations", "Recorded", "Severity"],
		["feedback", "Composed", "Delivery"],
	])(
		"puts the scope above the tabs and the %s tab's own filters below them",
		async (tab, date, own) => {
			recordRequests();
			renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews/${tab}?from=2026-09-01&to=2026-09-10`,
			);

			const scope = await screen.findByRole(
				"group",
				{ name: "Applies to every tab" },
				ROUTE_RENDER_WAIT,
			);
			const sections = screen.getByRole("navigation", { name: "Practice review sections" });
			const filters = screen.getByRole("group", { name: "Filters" });
			expect(precedes(scope, sections)).toBe(true);
			expect(precedes(sections, filters)).toBe(true);
			within(scope).getByRole("combobox", { name: "Developer" });
			within(scope).getByRole("button", { name: new RegExp(`^${date}`, "u") });
			within(scope).getByRole("button", { name: "Clear scope" });
			within(filters).getByRole("combobox", { name: own });
			expect(within(filters).queryByRole("combobox", { name: "Developer" })).toBeNull();
			expect(within(filters).queryByRole("button", { name: "Clear scope" })).toBeNull();
		},
	);

	/** The overview keeps the scope it does not apply in the same row above the tabs. */
	it("keeps the scope above the tabs on the overview, marked as not applied", async () => {
		recordRequests();
		renderRouteAtWithRouter("/w/acme/admin/practices/reviews?developerId=7");

		const scope = await screen.findByRole(
			"group",
			{ name: "Applies to every tab" },
			ROUTE_RENDER_WAIT,
		);
		expect(
			precedes(scope, screen.getByRole("navigation", { name: "Practice review sections" })),
		).toBe(true);
		within(scope).getByText("Not applied on the overview");
	});

	/** Reset clears the list's own facets; the shared scope stays for this tab and the next. */
	it("keeps the shared scope when the reader resets the list's own facets", async () => {
		const { resultUrls } = recordRequests();

		const { router } = renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/results?developerId=7&outcome=["NOT_MET"]',
		);
		await screen.findByText("No results match these filters", undefined, ROUTE_RENDER_WAIT);
		await userEvent.click(screen.getByRole("button", { name: "Reset" }));

		await waitFor(() => expect(values(resultUrls.at(-1), "outcome")).toStrictEqual([]));
		expect(resultUrls.at(-1)?.searchParams.get("developerId")).toBe("7");
		expect(router.state.location.search).toMatchObject({ developerId: "7" });

		await userEvent.click(screen.getByRole("button", { name: "Clear scope" }));
		await waitFor(() => expect(resultUrls.at(-1)?.searchParams.get("developerId")).toBeNull());
	});

	/**
	 * The list loads the next page at its end, under the same filters: a next page that lost a filter
	 * would append rows that do not match it. The observer never fires here, so the press is what asks.
	 */
	it("asks for the next page of reviews under the same filters, and shows each review once", async () => {
		vi.stubGlobal("IntersectionObserver", ObserverStub);
		const { reviewUrls } = recordRequests();
		serveReviewPages(reviewUrls);

		renderRouteAtWithRouter('/w/acme/admin/practices/reviews/runs?status=["COMPLETE"]');
		await screen.findByRole("link", { name: reviewLinkName(FIRST_PAGE) }, ROUTE_RENDER_WAIT);
		await userEvent.click(screen.getByRole("button", { name: "Show more reviews" }));
		await screen.findByRole("link", { name: reviewLinkName(SECOND_PAGE) }, ROUTE_RENDER_WAIT);

		const next = reviewUrls.at(-1);
		expect(next?.searchParams.get("page")).toBe("1");
		expect(next?.searchParams.get("size")).toBe("25");
		expect(values(next, "status")).toStrictEqual(["COMPLETE"]);
		expect(screen.getAllByRole("link", { name: reviewLinkName(FIRST_PAGE) })).toHaveLength(1);
		// The last page is in, so the list ends without a button.
		expect(screen.queryByRole("button", { name: "Show more reviews" })).toBeNull();
	});

	/**
	 * What the wire says of a review reaches its row: who asked for it and a stop worth a second look.
	 * The row opens the review's sheet over the list.
	 */
	it("names who asked for a review and its failure on its row, and opens its sheet", async () => {
		server.use(...reviewHandlers());
		const requestedReview: ReviewSummary = {
			...FIRST_PAGE,
			origin: "REQUEST",
			requestedBy: reviewAccounts.grace,
			status: "FAILED",
			stopReason: "TIMED_OUT",
		};
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/reviews", () =>
				HttpResponse.json(pageOf([requestedReview])),
			),
		);

		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs");
		const table = await screen.findByRole(
			"table",
			{ name: "Practice reviews, newest first" },
			ROUTE_RENDER_WAIT,
		);
		const requester = reviewAccounts.grace.displayName;
		assert(requester !== null);
		await within(table).findByText(requester, undefined, ROUTE_RENDER_WAIT);
		within(table).getByRole("columnheader", { name: "Started by" });
		expect(within(table).getAllByText("Failed").length).toBeGreaterThan(0);
		await userEvent.click(
			within(table).getByRole("link", { name: reviewLinkName(requestedReview) }),
		);

		await waitFor(() => {
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				`review:${requestedReview.id}`,
			]);
		});
		expect(router.state.location.pathname).toBe("/w/acme/admin/practices/reviews/runs");
	});
});
