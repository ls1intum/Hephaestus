import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
	listPracticeReviewsInfiniteOptions,
	listPracticeReviewObservationsInfiniteOptions,
	listPracticeReviewFeedbackInfiniteOptions,
} from "@/api/@tanstack/react-query.gen";
import type { ReviewRunSummary } from "@/api/types.gen";
import {
	reviewRuns,
	reviewObservations,
	reviewFeedback,
} from "@/components/admin/practice-reviews/fixtures";
import {
	ACTIVE_REVIEW_POLL_MS,
	runsQuery,
	observationsQuery,
	feedbackQuery,
} from "@/components/admin/practice-reviews/review-search";
import { reviewHandlers } from "@/components/admin/practice-reviews/story-mock-server";

import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { server } from "@/mocks/server";
import { QUERY_RETRIES } from "@/runtime/tanstack-query/query-defaults";
import { deferred } from "@/test/async";
import { precedes } from "@/test/dom";
import { ObserverStub } from "@/test/observers";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter, testQueryClient } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules; the timeout is a
// deadlock backstop, not a budget these renders were meant to fit inside.
vi.setConfig({ testTimeout: 15_000 });

const emptyPage = { content: [], page: { number: 0, size: 25, totalElements: 0, totalPages: 0 } };

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

/**
 * Every request the list routes make, with the work, review, observation and feedback URLs recorded.
 * The screens they feed take their rows as props, so a story mounts them without ever issuing the
 * request — this route test is the only place that can see what went on the wire.
 */
function recordRequests() {
	const reviewUrls: URL[] = [];
	const observationUrls: URL[] = [];
	const feedbackUrls: URL[] = [];
	const workUrls: URL[] = [];
	const locationCountUrls: URL[] = [];
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/members", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practices", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practice-groups", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/observations", ({ request }) => {
			const url = new URL(request.url);
			if (!isTabCount(url)) {
				observationUrls.push(url);
			}
			return HttpResponse.json(emptyPage);
		}),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", ({ request }) => {
			const url = new URL(request.url);
			if (!isTabCount(url)) {
				feedbackUrls.push(url);
			}
			return HttpResponse.json(emptyPage);
		}),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/locations", ({ request }) => {
			locationCountUrls.push(new URL(request.url));
			return HttpResponse.json([]);
		}),
		http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
			const url = new URL(request.url);
			if (!isTabCount(url)) {
				reviewUrls.push(url);
			}
			return HttpResponse.json(emptyPage);
		}),
		http.get("*/workspaces/:workspaceSlug/practices/trace", ({ request }) => {
			workUrls.push(new URL(request.url));
			return HttpResponse.json(emptyPage);
		}),
	);
	return { reviewUrls, observationUrls, feedbackUrls, workUrls, locationCountUrls };
}

function processingReviewsHandler(getReview: () => ReviewRunSummary, polls: URL[], reads: URL[]) {
	return http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
		const url = new URL(request.url);
		const processingPoll = values(url, "resultProcessing").includes("PENDING");
		if (!isTabCount(url)) {
			(processingPoll ? polls : reads).push(url);
		}
		const review = getReview();
		const rows = processingPoll && review.resultProcessing !== "PENDING" ? [] : [review];
		return HttpResponse.json({
			content: rows,
			page: { number: 0, size: 25, totalElements: rows.length, totalPages: 1 },
		});
	});
}

/** Two finished reviews, so the second page's row shows that it arrived. */
const [FIRST_PAGE, SECOND_PAGE] = twoCompletedReviews();

function twoCompletedReviews(): [ReviewRunSummary, ReviewRunSummary] {
	const [first, second] = reviewRuns.filter((run) => run.status === "COMPLETED");
	if (!first || !second) {
		throw new Error("The fixture has fewer than two completed reviews");
	}
	return [first, second];
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
			return HttpResponse.json({
				content: number === 0 ? [FIRST_PAGE] : [FIRST_PAGE, SECOND_PAGE],
				page: { number, size: 25, totalElements: 3, totalPages: 2 },
			});
		}),
	);
}

function responseFor(status: number, rows: readonly object[]): Response {
	return status === 200
		? HttpResponse.json({
				content: rows,
				page: { number: 0, size: 25, totalElements: rows.length, totalPages: 1 },
			})
		: HttpResponse.json({ status }, { status });
}
function listResponse(endpoint: string, response: () => Response, read?: () => void) {
	return http.get(`*/workspaces/:workspaceSlug/practices/${endpoint}`, ({ request }) => {
		if (isTabCount(new URL(request.url))) {
			return HttpResponse.json(emptyPage);
		}
		read?.();
		return response();
	});
}
function activePollRequest(url: URL): boolean {
	return values(url, "status").includes("RUNNING");
}
function processingPollRequest(url: URL): boolean {
	return values(url, "resultProcessing").includes("PENDING");
}
function pendingRows(review: ReviewRunSummary): ReviewRunSummary[] {
	return review.status === "COMPLETED" && review.resultProcessing === "DELIVERED" ? [] : [review];
}
function pollResponse(
	isPoll: (url: URL) => boolean,
	poll: () => Response,
	page: () => Response,
	pollRead: () => void,
	pageRead: () => void,
) {
	return http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
		const url = new URL(request.url);
		if (isTabCount(url)) {
			return HttpResponse.json(emptyPage);
		}
		if (isPoll(url)) {
			pollRead();
			return poll();
		}
		pageRead();
		return page();
	});
}
function heldFilterResponse(held: Promise<Response>) {
	return http.get("*/workspaces/:workspaceSlug/practices/reviews", async ({ request }) => {
		const url = new URL(request.url);
		if (isTabCount(url)) {
			return HttpResponse.json(emptyPage);
		}
		if (activePollRequest(url)) {
			return held;
		}
		const rows =
			url.searchParams.get("artifactId") === "99"
				? [SECOND_PAGE]
				: [{ ...FIRST_PAGE, status: "RUNNING" }];
		return responseFor(200, rows);
	});
}

function firstReviewWithTitle(title: string): ReviewRunSummary {
	const work = FIRST_PAGE.target.reviewedWork;
	return {
		...FIRST_PAGE,
		target: {
			...FIRST_PAGE.target,
			title,
			...(work === undefined ? {} : { reviewedWork: { ...work, title } }),
		},
	};
}

/** Native production retries, with only the delay removed from this deterministic HTTP fixture. */
function retryingClient() {
	const client = testQueryClient();
	client.setDefaultOptions({
		queries: { retry: QUERY_RETRIES, retryDelay: 0 },
		mutations: { retry: false },
	});
	return client;
}
function recoveringPollHandler(
	ready: Promise<Response>,
	requested: () => void,
	counts: { pages: number; polls: number },
) {
	return http.get("*/workspaces/:workspaceSlug/practices/reviews", async ({ request }) => {
		const url = new URL(request.url);
		if (isTabCount(url)) {
			return HttpResponse.json(emptyPage);
		}
		if (activePollRequest(url)) {
			counts.polls += 1;
			if (counts.polls === 1) {
				return responseFor(403, []);
			}
			return responseFor(200, [
				{
					...firstReviewWithTitle("Poll-only metadata"),
					status: "RUNNING",
				},
			]);
		}
		counts.pages += 1;
		if (counts.pages === 1) {
			return responseFor(200, [
				{ ...FIRST_PAGE, status: "RUNNING", resultProcessing: "DELIVERED" },
			]);
		}
		requested();
		return ready;
	});
}
function mixedPollRefusalHandler(counts: { active: number; processing: number }) {
	return http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
		const url = new URL(request.url);
		if (isTabCount(url)) {
			return HttpResponse.json(emptyPage);
		}
		if (activePollRequest(url)) {
			counts.active += 1;
			return responseFor(503, []);
		}
		if (processingPollRequest(url)) {
			counts.processing += 1;
			return responseFor(403, []);
		}
		return responseFor(200, [
			{ ...FIRST_PAGE, status: "RUNNING", resultProcessing: "DELIVERED" },
			{ ...SECOND_PAGE, status: "COMPLETED", resultProcessing: "PENDING" },
		]);
	});
}

describe("practice review list routes", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it.each([
		{
			path: '/w/acme/admin/practices/reviews/runs?status=["COMPLETED"]',
			endpoint: "reviews",
			rows: [FIRST_PAGE],
			table: "Practice reviews, newest first",
			region: "Practice reviews",
			warning: "These reviews may be out of date",
			key: listPracticeReviewsInfiniteOptions({
				path: { workspaceSlug: "acme" },
				query: runsQuery({ status: ["COMPLETED"] }, 25),
			}).queryKey,
		},
		{
			path: "/w/acme/admin/practices/reviews/observations",
			endpoint: "reviews/observations",
			rows: reviewObservations.slice(0, 1),
			table: "Observations",
			region: "Practice review observations",
			warning: "These observations may be out of date",
			key: listPracticeReviewObservationsInfiniteOptions({
				path: { workspaceSlug: "acme" },
				query: observationsQuery({}, 25),
			}).queryKey,
		},
		{
			path: "/w/acme/admin/practices/reviews/feedback",
			endpoint: "reviews/feedback",
			rows: reviewFeedback.slice(0, 1),
			table: "Feedback",
			region: "Feedback",
			warning: "These pieces of feedback may be out of date",
			key: listPracticeReviewFeedbackInfiniteOptions({
				path: { workspaceSlug: "acme" },
				query: feedbackQuery({}, 25),
			}).queryKey,
		},
	])(
		"retains $endpoint rows with failed refresh and retries that read",
		async ({ path, endpoint, rows, table, region: regionName, warning, key }) => {
			recordRequests();
			let status = 200;
			let reads = 0;
			server.use(
				listResponse(
					endpoint,
					() => responseFor(status, rows),
					() => {
						reads += 1;
					},
				),
			);
			const { queryClient } = renderRouteAtWithRouter(path);
			await screen.findByRole("table", { name: table }, ROUTE_RENDER_WAIT);
			const region = screen.getByRole("region", { name: regionName });
			const before = within(region).getByRole("table").textContent;
			status = 503;
			await act(async () => {
				await queryClient.refetchQueries({ queryKey: key, exact: true });
			});
			await screen.findByText(warning);
			expect(within(region).getByRole("table").textContent).toBe(before);
			status = 200;
			const failedReads = reads;
			await userEvent.click(within(region).getByRole("button", { name: "Retry" }));
			await waitFor(() => expect(screen.queryByText(warning)).toBeNull());
			expect(reads).toBe(failedReads + 1);
		},
	);

	it.each([403, 404])(
		"does not restore denied list words after a later 503 (%s)",
		async (denied) => {
			recordRequests();
			let status = 200;
			server.use(listResponse("reviews", () => responseFor(status, [FIRST_PAGE])));
			const { queryClient } = renderRouteAtWithRouter(
				'/w/acme/admin/practices/reviews/runs?status=["COMPLETED"]',
			);
			await screen.findByText(FIRST_PAGE.target.title, undefined, ROUTE_RENDER_WAIT);
			const key = listPracticeReviewsInfiniteOptions({
				path: { workspaceSlug: "acme" },
				query: runsQuery({ status: ["COMPLETED"] }, 25),
			}).queryKey;
			status = denied;
			await act(async () => {
				await queryClient.refetchQueries({ queryKey: key, exact: true });
			});
			await waitFor(() => expect(queryClient.getQueryData(key)).toBeUndefined());
			expect(screen.queryByText(FIRST_PAGE.target.title)).toBeNull();
			status = 503;
			await act(async () => {
				await queryClient.refetchQueries({ queryKey: key, exact: true });
			});
			await screen.findByText("We could not load reviews");
			expect(screen.queryByText(FIRST_PAGE.target.title)).toBeNull();
			expect(screen.queryByText("These reviews may be out of date")).toBeNull();
		},
	);

	it.each([
		{
			kind: "active",
			initialStatus: "RUNNING" as const,
			processing: "DELIVERED" as const,
			isPoll: activePollRequest,
		},
		{
			kind: "processing",
			initialStatus: "COMPLETED" as const,
			processing: "PENDING" as const,
			isPoll: processingPollRequest,
		},
	])(
		"retries the failed $kind poll without refreshing every loaded page",
		async ({ initialStatus, processing, isPoll }) => {
			vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
			try {
				recordRequests();
				let status = 503;
				let pollReads = 0;
				let pageReads = 0;
				let review: ReviewRunSummary = {
					...FIRST_PAGE,
					status: initialStatus,
					resultProcessing: processing,
				};
				server.use(
					pollResponse(
						isPoll,
						() => responseFor(status, pendingRows(review)),
						() => responseFor(200, [review]),
						() => {
							pollReads += 1;
						},
						() => {
							pageReads += 1;
						},
					),
				);
				renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs");
				await screen.findByText(FIRST_PAGE.target.title, undefined, ROUTE_RENDER_WAIT);
				await screen.findByText("These reviews may be out of date");
				const pagesBefore = pageReads;
				status = 200;
				await userEvent.click(screen.getByRole("button", { name: "Retry" }));
				await waitFor(() =>
					expect(screen.queryByText("These reviews may be out of date")).toBeNull(),
				);
				expect(pageReads).toBe(pagesBefore);
				review = {
					...firstReviewWithTitle("Finished review"),
					status: "COMPLETED",
					resultProcessing: "DELIVERED",
				};
				await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
				await screen.findByText("Finished review");
				expect(pageReads).toBeGreaterThan(pagesBefore);
				const settled = pollReads;
				await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS * 2);
				expect(pollReads).toBe(settled);
			} finally {
				vi.useRealTimers();
			}
		},
	);

	it("discloses a failed refresh even when the loaded list is empty", async () => {
		recordRequests();
		let status = 200;
		server.use(listResponse("reviews", () => responseFor(status, [])));
		const { queryClient } = renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/runs?status=["COMPLETED"]',
		);
		await screen.findByText("No reviews match these filters", undefined, ROUTE_RENDER_WAIT);
		status = 503;
		await act(async () => {
			await queryClient.refetchQueries({
				queryKey: listPracticeReviewsInfiniteOptions({
					path: { workspaceSlug: "acme" },
					query: runsQuery({ status: ["COMPLETED"] }, 25),
				}).queryKey,
				exact: true,
			});
		});
		await screen.findByText("These reviews may be out of date");
		screen.getByText("No reviews match these filters");
	});

	it("a refused active poll clears loaded words until a newly authorized page returns", async () => {
		recordRequests();
		let pollStatus = 503;
		let pageStatus = 200;
		let review: ReviewRunSummary = {
			...FIRST_PAGE,
			status: "RUNNING",
			resultProcessing: "DELIVERED",
		};
		server.use(
			pollResponse(
				activePollRequest,
				() => responseFor(pollStatus, [review]),
				() => responseFor(pageStatus, [review]),
				() => undefined,
				() => undefined,
			),
		);
		const { queryClient } = renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs");
		await screen.findByText("These reviews may be out of date", undefined, ROUTE_RENDER_WAIT);
		screen.getByText(FIRST_PAGE.target.title);
		pollStatus = 403;
		await userEvent.click(screen.getByRole("button", { name: "Retry" }));
		await screen.findByText("You do not have access to reviews");
		const key = listPracticeReviewsInfiniteOptions({
			path: { workspaceSlug: "acme" },
			query: runsQuery({}, 25),
		}).queryKey;
		expect(queryClient.getQueryData(key)).toBeUndefined();
		expect(screen.queryByText(FIRST_PAGE.target.title)).toBeNull();
		pageStatus = 503;
		await act(async () => {
			await queryClient.refetchQueries({ queryKey: key, exact: true });
		});
		expect(screen.queryByText(FIRST_PAGE.target.title)).toBeNull();
		pageStatus = 200;
		review = { ...review, status: "COMPLETED" };
		await act(async () => {
			await queryClient.refetchQueries({ queryKey: key, exact: true });
		});
		await screen.findByText(FIRST_PAGE.target.title);
		expect(screen.queryByText("You do not have access to reviews")).toBeNull();
	});

	it("recovers a refused poll through a fresh authorized page with native retries", async () => {
		recordRequests();
		const ready = deferred<Response>();
		const requested = deferred();
		const counts = { pages: 0, polls: 0 };
		server.use(recoveringPollHandler(ready.promise, () => requested.resolve(), counts));
		const client = retryingClient();
		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs", client);
		try {
			await waitFor(() => expect(counts.polls).toBeGreaterThanOrEqual(2), ROUTE_RENDER_WAIT);
			await waitFor(() => expect(counts.pages).toBe(2));
			await requested.promise;
			expect(screen.queryByText(FIRST_PAGE.target.title)).toBeNull();
			expect(screen.queryByText("Poll-only metadata")).toBeNull();
			await act(async () => {
				ready.resolve(
					responseFor(200, [
						{
							...firstReviewWithTitle("Fresh authorized review"),
							status: "COMPLETED",
							resultProcessing: "DELIVERED",
						},
					]),
				);
			});
			await screen.findByText("Fresh authorized review");
			expect(screen.queryByText("Poll-only metadata")).toBeNull();
		} finally {
			ready.resolve(responseFor(200, []));
		}
	});

	it("shows a processing-poll refusal before an active-poll transport error with native retries", async () => {
		recordRequests();
		const counts = { active: 0, processing: 0 };
		server.use(mixedPollRefusalHandler(counts));
		const client = retryingClient();
		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs", client);
		await waitFor(() => expect(counts.active).toBe(4), ROUTE_RENDER_WAIT);
		await waitFor(() => expect(counts.processing).toBe(4));
		await screen.findByText("You do not have access to reviews");
		expect(screen.queryByText(FIRST_PAGE.target.title)).toBeNull();
		expect(screen.queryByText(SECOND_PAGE.target.title)).toBeNull();
		expect(screen.queryByText("These reviews may be out of date")).toBeNull();
		const key = listPracticeReviewsInfiniteOptions({
			path: { workspaceSlug: "acme" },
			query: runsQuery({}, 25),
		}).queryKey;
		expect(client.getQueryData(key)).toBeUndefined();
	});

	it("keeps next-page failure separate from refresh and retries the next page", async () => {
		vi.stubGlobal("IntersectionObserver", ObserverStub);
		const { reviewUrls } = recordRequests();
		serveReviewPages(reviewUrls);
		renderRouteAtWithRouter('/w/acme/admin/practices/reviews/runs?status=["COMPLETED"]');
		await screen.findByText(FIRST_PAGE.target.title, undefined, ROUTE_RENDER_WAIT);
		server.use(
			http.get(
				"*/workspaces/:workspaceSlug/practices/reviews",
				() => HttpResponse.json({ status: 503 }, { status: 503 }),
				{ once: true },
			),
		);
		await userEvent.click(screen.getByRole("button", { name: "Show more reviews" }));
		await screen.findByText("We could not load more reviews.");
		expect(screen.queryByText("These reviews may be out of date")).toBeNull();
		screen.getByText(FIRST_PAGE.target.title);
		await userEvent.click(screen.getByRole("button", { name: "Retry" }));
		await screen.findByText(SECOND_PAGE.target.title);
		expect(reviewUrls.at(-1)?.searchParams.get("page")).toBe("1");
	});

	it("keeps the new filter identity when an old poll finishes", async () => {
		recordRequests();
		const held = deferred<Response>();
		server.use(heldFilterResponse(held.promise));
		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs");
		await screen.findByText(FIRST_PAGE.target.title, undefined, ROUTE_RENDER_WAIT);
		await act(async () => {
			await router.navigate({
				to: "/w/$workspaceSlug/admin/practices/reviews/runs",
				params: { workspaceSlug: "acme" },
				search: { artifactKind: "scm.pull_request", artifactId: 99 },
			});
		});
		await screen.findByText(SECOND_PAGE.target.title);
		await act(async () => {
			held.resolve(responseFor(200, [FIRST_PAGE]));
		});
		expect(screen.queryByText(FIRST_PAGE.target.title)).toBeNull();
		screen.getByText(SECOND_PAGE.target.title);
	});

	/**
	 * The one wire detail these screens can get wrong silently. The URL spells the ordering `order`,
	 * because another route already owns the word `sort` in the same search namespace with entirely
	 * different values; the endpoint spells it `sort`. A list ordered by the server looks exactly like
	 * a list ordered by the server's default, so nothing but the request itself can catch a rename —
	 * and this is the surface that exists to put the observations most worth acting on at the top.
	 */
	it("asks the endpoint for the ordering the URL chose, under the name the endpoint uses", async () => {
		const { observationUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/observations?order=ACTIONABILITY&severity=["MAJOR"]',
		);
		await screen.findByText("No observations match these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = observationUrls.at(-1);
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
			'/w/acme/admin/practices/reviews/feedback?order=OLDEST&deliveryState=["AWAITING_APPROVAL"]',
		);
		await screen.findByText("No feedback matches these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = feedbackUrls.at(-1);
		expect(requested?.searchParams.get("sort")).toBe("OLDEST");
		expect(requested?.searchParams.get("order")).toBeNull();
		expect(values(requested, "deliveryState")).toStrictEqual(["AWAITING_APPROVAL"]);
	});

	/** The default ordering is the server's, so nothing is sent rather than a guess at its name. */
	it("sends no ordering when the reader has not chosen one", async () => {
		const { observationUrls } = recordRequests();

		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/observations");
		await screen.findByText("No observations yet", undefined, ROUTE_RENDER_WAIT);

		expect(observationUrls.at(-1)?.searchParams.get("sort")).toBeNull();
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
		const { observationUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/observations?outcome=["NOT_MET"]&invalidated=true',
		);
		await screen.findByText("No observations match these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = observationUrls.at(-1);
		expect(values(requested, "outcome")).toStrictEqual(["NOT_MET"]);
		expect(requested?.searchParams.get("invalidated")).toBe("true");
	});

	/**
	 * The URL carries withholding *families*, which is the question an operator asks; the endpoint
	 * filters on individual reasons. The expansion happens on the way to the request, so a family that
	 * stopped expanding would return everything and read as a filter that simply matched a lot.
	 */
	it("expands a withholding family to the reasons the endpoint filters on", async () => {
		const { feedbackUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/feedback?withheldFamily=["HOUSEKEEPING"]',
		);
		await screen.findByText("No feedback matches these filters", undefined, ROUTE_RENDER_WAIT);

		const reasons = values(feedbackUrls.at(-1), "suppressionReason");
		expect(reasons).toContain("COMPOSER_DEDUPED");
		expect(reasons).not.toContain("HOUSEKEEPING");
		expect(feedbackUrls.at(-1)?.searchParams.get("withheldFamily")).toBeNull();
	});

	/** Live, requested and backfilled reviews are separate populations; the origin travels as asked. */
	it("sends the origin filter to the endpoint", async () => {
		const { observationUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/observations?origin=["MANUAL","BACKFILL"]',
		);
		await screen.findByText("No observations match these filters", undefined, ROUTE_RENDER_WAIT);

		expect(values(observationUrls.at(-1), "origin")).toStrictEqual(["MANUAL", "BACKFILL"]);
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

	/**
	 * The channel facet offers locations — a channel split by the provider that shows it — and the
	 * endpoint filters on them by that name. The channel itself is no longer sent, so the two cannot
	 * intersect into a filter the toolbar does not show.
	 */
	it("sends the location filter to the feedback endpoint", async () => {
		const { feedbackUrls, locationCountUrls } = recordRequests();

		renderRouteAtWithRouter('/w/acme/admin/practices/reviews/feedback?location=["GITLAB","SLACK"]');
		await screen.findByText("No feedback matches these filters", undefined, ROUTE_RENDER_WAIT);

		expect(values(feedbackUrls.at(-1), "location")).toStrictEqual(["GITLAB", "SLACK"]);
		expect(feedbackUrls.at(-1)?.searchParams.get("channel")).toBeNull();
		// No person is filtered to, so there is no summary to count.
		expect(locationCountUrls).toHaveLength(0);
	});

	/**
	 * Filtered to a recipient, the list leads with their summary. It counts everything addressed to
	 * them, whatever else the list is narrowed to, so its counts can open the list for them alone.
	 */
	it("counts everything addressed to the recipient filtered to, and nothing narrower", async () => {
		const { feedbackUrls, locationCountUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/feedback?recipientUserId=9&location=["SLACK"]&deliveryState=["DELIVERED"]',
		);
		await screen.findByText("No feedback for this developer yet", undefined, ROUTE_RENDER_WAIT);

		expect(feedbackUrls.at(-1)?.searchParams.get("recipientUserId")).toBe("9");
		const counts = locationCountUrls.at(-1);
		expect(counts?.searchParams.get("recipientUserId")).toBe("9");
		expect(values(counts, "location")).toStrictEqual([]);
		expect(values(counts, "deliveryState")).toStrictEqual([]);
	});

	/** A row's recipient opens the list filtered to them alone, which is where their summary is. */
	it("names a row's recipient as text, so the row opens one thing", async () => {
		recordRequests();
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () =>
				HttpResponse.json({
					content: [
						{
							id: "11111111-1111-4111-8111-111111111111",
							agentJobId: "22222222-2222-4222-8222-222222222222",
							bodyTruncated: false,
							channel: "IN_CHAT",
							location: "SLACK",
							createdAt: "2026-07-26T16:06:00Z",
							deliveryState: "DELIVERED",
							observationCount: 1,
							recipient: { id: 9, login: "grace", name: "Grace Hopper" },
						},
					],
					page: { number: 0, size: 25, totalElements: 1, totalPages: 1 },
				}),
			),
		);

		renderRouteAtWithRouter('/w/acme/admin/practices/reviews/feedback?deliveryState=["DELIVERED"]');
		await screen.findByText("Grace Hopper", undefined, ROUTE_RENDER_WAIT);
		screen.getByText("With Heph in Slack");

		// The feedback's level links the person; the row's one link is the feedback itself.
		expect(screen.queryByRole("link", { name: "All feedback for Grace Hopper" })).toBeNull();
	});

	/** A completed review whose results failed to process is only found by this filter. */
	it("sends the result-processing filter to the reviews endpoint", async () => {
		const { reviewUrls } = recordRequests();

		renderRouteAtWithRouter('/w/acme/admin/practices/reviews/runs?resultProcessing=["FAILED"]');
		await screen.findByText("No reviews match these filters", undefined, ROUTE_RENDER_WAIT);

		expect(values(reviewUrls.at(-1), "resultProcessing")).toStrictEqual(["FAILED"]);
	});

	/**
	 * A completed review whose results are still processed changes after it stopped, also under a
	 * status filter that names no active status: the list follows it until processing settles, reads
	 * itself again, and then stops asking.
	 */
	it("follows a completed review until its results are processed, then stops", async () => {
		vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
		try {
			const { reviewUrls } = recordRequests();
			const processingPolls: URL[] = [];
			let review: ReviewRunSummary = { ...FIRST_PAGE, resultProcessing: "PENDING" };
			server.use(processingReviewsHandler(() => review, processingPolls, reviewUrls));

			renderRouteAtWithRouter('/w/acme/admin/practices/reviews/runs?status=["COMPLETED"]');
			await screen.findByText(FIRST_PAGE.target.title, undefined, ROUTE_RENDER_WAIT);
			const table = await screen.findByRole(
				"table",
				{ name: "Practice reviews, newest first" },
				ROUTE_RENDER_WAIT,
			);
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			// The interval is faked, so the waits poll on vitest's own timer rather than on setInterval.
			await vi.waitFor(() => expect(processingPolls.length).toBeGreaterThan(0));
			expect(values(processingPolls.at(-1), "status")).toStrictEqual(["COMPLETED"]);
			expect(within(table).queryByText("Result processing failed")).toBeNull();

			review = { ...review, resultProcessing: "FAILED" };
			const listReads = reviewUrls.length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS);
			await vi.waitFor(() => expect(reviewUrls.length).toBeGreaterThan(listReads));
			await screen.findByText("Result processing failed", undefined, ROUTE_RENDER_WAIT);

			const settled = processingPolls.length;
			await vi.advanceTimersByTimeAsync(ACTIVE_REVIEW_POLL_MS * 2);
			expect(processingPolls).toHaveLength(settled);
		} finally {
			vi.useRealTimers();
		}
	});

	/** The overview's count of reviews that finished after the merge opens this list with the filter. */
	it("sends the finished-after-merge filter to the reviews endpoint, and clears it", async () => {
		const { reviewUrls } = recordRequests();

		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs?finishedAfterMerge=true");
		await screen.findByText("No reviews match these filters", undefined, ROUTE_RENDER_WAIT);
		expect(reviewUrls.at(-1)?.searchParams.get("finishedAfterMerge")).toBe("true");

		await userEvent.click(
			screen.getByRole("button", { name: "Clear finished after merge filter (Only)" }),
		);
		await waitFor(() =>
			expect(reviewUrls.at(-1)?.searchParams.has("finishedAfterMerge")).toBe(false),
		);
	});

	it("asks for the work without a review a page at a time, of every kind", async () => {
		const { workUrls, reviewUrls } = recordRequests();

		renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs?withoutReview=true");
		await screen.findByText("No work without a review", undefined, ROUTE_RENDER_WAIT);

		const requested = workUrls.at(-1);
		expect(requested?.searchParams.get("withoutReview")).toBe("true");
		expect(requested?.searchParams.get("page")).toBe("0");
		expect(requested?.searchParams.get("size")).toBe("25");
		expect(requested?.searchParams.get("artifactKind")).toBeNull();
		expect(reviewUrls).toHaveLength(0);
	});

	/**
	 * "No review started" is a value of the Status filter: it lists the work instead of the reviews,
	 * and a status a review has, chosen next, lists the reviews again.
	 */
	it("lists the work with no review started from the Status filter, and the reviews again", async () => {
		const { workUrls, reviewUrls } = recordRequests();

		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs");
		await userEvent.click(
			await screen.findByRole("combobox", { name: "Status" }, ROUTE_RENDER_WAIT),
		);
		await userEvent.click(await screen.findByRole("option", { name: "No review started" }));
		await waitFor(() =>
			expect(router.state.location.search).toMatchObject({ status: "NOT_STARTED" }),
		);
		await waitFor(() => expect(workUrls.at(-1)?.searchParams.get("withoutReview")).toBe("true"));
		await screen.findByText("No work without a review", undefined, ROUTE_RENDER_WAIT);

		const reviewsBefore = reviewUrls.length;
		await userEvent.click(await screen.findByRole("option", { name: "Completed" }));
		await waitFor(() =>
			expect(router.state.location.search).toMatchObject({ status: "COMPLETED" }),
		);
		await waitFor(() => expect(reviewUrls.length).toBeGreaterThan(reviewsBefore));
		const requested = reviewUrls.at(-1);
		expect(requested?.searchParams.getAll("status").join(",")).toContain("COMPLETED");
		expect(requested?.searchParams.toString()).not.toContain("NOT_STARTED");
	});

	/** An address from before the Status value, with the toggle's parameter, opens the same list. */
	it("reads the old toggle's address as the status No review started", async () => {
		const { workUrls } = recordRequests();

		const { router } = renderRouteAtWithRouter(
			"/w/acme/admin/practices/reviews/runs?withoutReview=true",
		);
		await screen.findByText("No work without a review", undefined, ROUTE_RENDER_WAIT);
		expect(workUrls.at(-1)?.searchParams.get("withoutReview")).toBe("true");
		screen.getByRole("button", { name: "Clear status filter (No review started)" });

		// The next change of the status drops the old parameter, so it cannot bring the list back.
		await userEvent.click(screen.getByRole("combobox", { name: /^Status/u }));
		await userEvent.click(await screen.findByRole("option", { name: "Completed" }));
		await waitFor(() =>
			expect(router.state.location.search).toMatchObject({ status: "COMPLETED" }),
		);
		expect(router.state.location.search).not.toHaveProperty("withoutReview");
	});

	/**
	 * The shared scope reaches the reviews endpoint under the names the observation list uses: the
	 * developer, the practices and the groups that a review recorded an observation of.
	 */
	it("sends the shared developer, practice, group, range and review to the reviews endpoint", async () => {
		const { reviewUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/runs?subjectUserId=7&practiceSlug=["thin-controllers"]&groupSlug=["design"]&from=2026-09-01&to=2026-09-10&agentJobId=3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84',
		);
		await screen.findByText("No reviews match these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = reviewUrls.at(-1);
		expect(requested?.searchParams.get("subjectUserId")).toBe("7");
		expect(values(requested, "practiceSlug")).toStrictEqual(["thin-controllers"]);
		expect(values(requested, "groupSlug")).toStrictEqual(["design"]);
		expect(requested?.searchParams.get("from")).not.toBeNull();
		expect(requested?.searchParams.get("to")).not.toBeNull();
		// A review the address filters by narrows the list to itself.
		expect(requested?.searchParams.get("agentJobId")).toBe("3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84");
	});

	/** The work without a review applies the range and one piece of work, and keeps the rest of the scope. */
	it("sends the shared range to the work endpoint and nothing it cannot apply", async () => {
		const { workUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/runs?withoutReview=true&from=2026-09-01&to=2026-09-10&subjectUserId=7&practiceSlug=["thin-controllers"]&artifactKind=scm.issue&artifactId=5',
		);
		await screen.findByText("No work matches these filters", undefined, ROUTE_RENDER_WAIT);

		// The work's pill reads its name from the same endpoint; the list is the read without a review.
		const requested = workUrls.find((url) => url.searchParams.has("withoutReview"));
		expect(requested?.searchParams.get("from")).not.toBeNull();
		expect(requested?.searchParams.get("to")).not.toBeNull();
		expect(requested?.searchParams.get("artifactKind")).toBe("scm.issue");
		expect(requested?.searchParams.get("artifactId")).toBe("5");
		expect(requested?.searchParams.get("subjectUserId")).toBeNull();
		expect(requested?.searchParams.get("practiceSlug")).toBeNull();
		expect(screen.getAllByText("Not applied on this list")).toHaveLength(2);
	});

	/** A group finds the feedback written from observations of any practice in it. */
	it("sends the group filter to the feedback endpoint", async () => {
		const { feedbackUrls } = recordRequests();

		renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/feedback?groupSlug=["design"]&from=2026-09-06&to=2026-10-05&deliveryState=["SUPPRESSED","DISCARDED","SUPERSEDED"]',
		);
		await screen.findByText("No feedback matches these filters", undefined, ROUTE_RENDER_WAIT);

		const requested = feedbackUrls.at(-1);
		expect(values(requested, "groupSlug")).toStrictEqual(["design"]);
		expect(values(requested, "deliveryState")).toStrictEqual([
			"SUPPRESSED",
			"DISCARDED",
			"SUPERSEDED",
		]);
	});

	/**
	 * The scope applies to every tab, so it is above the tabs, with the tracked record; each tab's own
	 * filters, its Reset and its count are below them.
	 */
	it.each([
		["runs", "Created", "Status"],
		["observations", "Observed", "Outcome"],
		["feedback", "Composed", "Channel"],
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
		renderRouteAtWithRouter("/w/acme/admin/practices/reviews?subjectUserId=7");

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
		const { observationUrls } = recordRequests();

		const { router } = renderRouteAtWithRouter(
			'/w/acme/admin/practices/reviews/observations?subjectUserId=7&outcome=["NOT_MET"]',
		);
		await screen.findByText("No observations match these filters", undefined, ROUTE_RENDER_WAIT);
		await userEvent.click(screen.getByRole("button", { name: "Reset" }));

		await waitFor(() => expect(values(observationUrls.at(-1), "outcome")).toStrictEqual([]));
		expect(observationUrls.at(-1)?.searchParams.get("subjectUserId")).toBe("7");
		expect(router.state.location.search).toMatchObject({ subjectUserId: 7 });

		await userEvent.click(screen.getByRole("button", { name: "Clear scope" }));
		await waitFor(() =>
			expect(observationUrls.at(-1)?.searchParams.get("subjectUserId")).toBeNull(),
		);
	});

	/**
	 * The list loads the next page at its end, under the same filters: a next page that lost a filter
	 * would append rows that do not match it. The observer never fires here, so the press is what asks.
	 */
	it("asks for the next page of reviews under the same filters, and shows each review once", async () => {
		vi.stubGlobal("IntersectionObserver", ObserverStub);
		const { reviewUrls } = recordRequests();
		serveReviewPages(reviewUrls);

		renderRouteAtWithRouter('/w/acme/admin/practices/reviews/runs?status=["COMPLETED"]');
		await screen.findByText(FIRST_PAGE.target.title, undefined, ROUTE_RENDER_WAIT);
		await userEvent.click(screen.getByRole("button", { name: "Show more reviews" }));
		await screen.findByText(SECOND_PAGE.target.title, undefined, ROUTE_RENDER_WAIT);

		const next = reviewUrls.at(-1);
		expect(next?.searchParams.get("page")).toBe("1");
		expect(next?.searchParams.get("size")).toBe("25");
		expect(values(next, "status")).toStrictEqual(["COMPLETED"]);
		expect(screen.getAllByText(FIRST_PAGE.target.title)).toHaveLength(1);
		// The last page is in, so the list ends without a button.
		expect(screen.queryByRole("button", { name: "Show more reviews" })).toBeNull();
	});

	/**
	 * What the wire says of a review reaches its row: who it was about, its place on the work and its
	 * problems. The row opens the review's own page.
	 */
	it("names the developer, a later review and its problems on each row, and opens its sheet", async () => {
		server.use(...reviewHandlers());
		const requestedReview: ReviewRunSummary = {
			...FIRST_PAGE,
			origin: "MANUAL",
			positionOnWork: 2,
			reviewsOnWork: 3,
			holdReason: "BUDGET",
			finishedAfterMerge: true,
			finishedAfterMergeStartedByMerge: false,
			developers: [{ id: 42, login: "grace", name: "Grace Hopper" }],
		};
		server.use(
			http.get("*/workspaces/:workspaceSlug/members/me", () =>
				HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
			),
			http.get("*/workspaces/:workspaceSlug/practices/reviews", () =>
				HttpResponse.json({
					content: [requestedReview],
					page: { number: 0, size: 25, totalElements: 1, totalPages: 1 },
				}),
			),
		);

		const { router } = renderRouteAtWithRouter("/w/acme/admin/practices/reviews/runs");
		await screen.findByText("Grace Hopper", undefined, ROUTE_RENDER_WAIT);
		const table = screen.getByRole("table", { name: "Practice reviews, newest first" });
		// The time's head; how the review was occasioned is on its sheet, not in the row.
		within(table).getByRole("columnheader", { name: "Created" });
		within(table).getByText("Review 2 of 3");
		// No result column: its notable states are chips beside the work, on hold for the budget and
		// finished after the merge.
		expect(within(table).queryByRole("columnheader", { name: "Result" })).toBeNull();
		within(table).getByText("On hold");
		within(table).getByText("Finished after the merge");
		await userEvent.click(within(table).getByRole("link", { name: /^Open review of #/u }));

		await waitFor(() => {
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				`review:${requestedReview.id}`,
			]);
		});
		expect(router.state.location.pathname).toBe("/w/acme/admin/practices/reviews/runs");
		await screen.findByRole("dialog", { name: requestedReview.target.title }, ROUTE_RENDER_WAIT);
	});
});
