import { screen, waitFor, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { reviewHandlers } from "@/components/admin/practice-reviews/story-mock-server";
import { server } from "@/mocks/server";
import { deferred } from "@/test/async";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules; the timeout is a
// deadlock backstop, not a budget these renders were meant to fit inside.
vi.setConfig({ testTimeout: 15_000 });

const FEEDBACK = "dddddddd-2222-2222-2222-222222222222";

const REVIEWS = "/w/acme/admin/practices/reviews";

/**
 * Every scope param the two record lists share, set at once, so a dropped one cannot hide. The
 * review id is a real version-4 UUID: the lists' schema drops one that is not, as the fixtures' are.
 */
const SCOPE = {
	agentJobId: "3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84",
	practiceResultId: "9a1d3c5e-7b2f-4e6a-8c0d-1f3b5d7e9a2c",
	artifactKind: "scm.pull_request",
	artifactId: "42",
	from: "2026-09-01",
	to: "2026-09-10",
};
const SCOPE_QUERY = new URLSearchParams(SCOPE).toString();

beforeEach(() => {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		...reviewHandlers(),
	);
});

/**
 * An open level is modal, so the page under it leaves the accessibility tree while it is open; the
 * tabs are still the tabs the reader returns to, which is what is asserted here.
 */
async function sectionNavigation() {
	return screen.findByRole(
		"navigation",
		{ name: "Practice review sections", hidden: true },
		ROUTE_RENDER_WAIT,
	);
}

function carriedSearch(link: HTMLAnchorElement) {
	return Object.fromEntries(new URL(link.href).searchParams);
}

/** A section's name, with or without the count a list tab carries once it is known: "Reviews, 3". */
function sectionName(name: string): RegExp {
	return new RegExp(`^${name}(, [\\d,]+)?$`, "u");
}

function sectionLink(navigation: HTMLElement, name: string) {
	return within(navigation).getByRole<HTMLAnchorElement>("link", {
		name: sectionName(name),
		hidden: true,
	});
}

/**
 * A list endpoint that answers its one-row count reads with `total` and records them; a list's own
 * read falls through to the fixture's handler.
 */
function countRead(path: string, total: number, counted: URL[]) {
	return http.get(`*/workspaces/:workspaceSlug${path}`, ({ request }) => {
		const url = new URL(request.url);
		if (url.searchParams.get("size") !== "1") {
			return;
		}
		counted.push(url);
		return HttpResponse.json({
			content: [],
			page: { number: 0, size: 1, totalElements: total, totalPages: 1 },
		});
	});
}

/** The results count read, failing with a 503 the first time it is asked and answering after. */
function failingOnceCount(total: number) {
	let reads = 0;
	const handler = http.get(
		"*/workspaces/:workspaceSlug/practices/reviews/results",
		({ request }) => {
			if (new URL(request.url).searchParams.get("size") !== "1") {
				return;
			}
			reads += 1;
			return reads === 1
				? HttpResponse.json({ status: 503 }, { status: 503 })
				: HttpResponse.json({
						content: [],
						page: { number: 0, size: 1, totalElements: total, totalPages: 1 },
					});
		},
	);
	return { handler, count: () => reads };
}

/** The results count for developer 7, held until `release`; other reads fall through. */
function heldScopedCount() {
	const held = deferred();
	let asked = false;
	const handler = http.get(
		"*/workspaces/:workspaceSlug/practices/reviews/results",
		async ({ request }) => {
			const url = new URL(request.url);
			if (url.searchParams.get("size") !== "1" || url.searchParams.get("subjectUserId") !== "7") {
				return;
			}
			asked = true;
			await held.promise;
			return HttpResponse.json({
				content: [],
				page: { number: 0, size: 1, totalElements: 42, totalPages: 1 },
			});
		},
	);
	return { handler, release: () => held.resolve(), asked: () => asked };
}

describe("practice review routes", () => {
	it.each([
		["Overview", REVIEWS],
		// The Work tab is gone: its address lands on the Reviews tab, showing the work without a review.
		["Reviews", `${REVIEWS}/work`],
		["Reviews", `${REVIEWS}/runs`],
		["Results", `${REVIEWS}/results`],
		["Feedback", `${REVIEWS}/feedback`],
		// A level is opened over the tab the reader is on, and never moves them off it.
		["Results", `${REVIEWS}/results?detail=feedback:${FEEDBACK}`],
	])("marks only %s as current on %s", async (expectedCurrent, url) => {
		renderRouteAtWithRouter(url);

		const navigation = await sectionNavigation();
		const currentLinks = within(navigation).getAllByRole("link", { current: "page", hidden: true });
		expect(currentLinks).toHaveLength(1);
		within(navigation).getByRole("link", {
			name: sectionName(expectedCurrent),
			current: "page",
			hidden: true,
		});
	});

	/**
	 * The shared scope follows the reader to every tab: the person, practices and groups, the range,
	 * and the selection. The person is the developer a result is
	 * about, and on the feedback list the person the feedback is addressed to. A tab's own facets stay
	 * on that tab.
	 */
	it.each([
		["/results", `subjectUserId=7&severity=${encodeURIComponent('["MAJOR"]')}`],
		["/feedback", `recipientUserId=7&location=${encodeURIComponent('["GITHUB"]')}`],
		["/runs", `subjectUserId=7&status=${encodeURIComponent('["FAILED"]')}`],
		["/work", "subjectUserId=7&kind=scm.issue"],
		["", "subjectUserId=7"],
	])('carries the scope from "%s" to every tab', async (fromPath, own) => {
		const scope = `${SCOPE_QUERY}&practiceSlug=${encodeURIComponent('["thin-controllers"]')}&groupSlug=${encodeURIComponent('["design"]')}`;
		renderRouteAtWithRouter(`${REVIEWS}${fromPath}?${scope}&${own}`);

		const navigation = await sectionNavigation();
		// A list of one is written as its one value.
		const expected = { ...SCOPE, practiceSlug: "thin-controllers", groupSlug: "design" };
		for (const [tab, person] of [
			["Overview", "subjectUserId"],
			["Reviews", "subjectUserId"],
			["Results", "subjectUserId"],
			["Feedback", "recipientUserId"],
		] as const) {
			expect(carriedSearch(sectionLink(navigation, tab))).toStrictEqual({
				...expected,
				[person]: "7",
			});
		}
	});

	it("carries nothing when nothing is scoped", async () => {
		renderRouteAtWithRouter(`${REVIEWS}/results?severity=${encodeURIComponent('["MAJOR"]')}`);
		const navigation = await sectionNavigation();
		for (const tab of ["Overview", "Reviews", "Results", "Feedback"]) {
			expect(carriedSearch(sectionLink(navigation, tab))).toStrictEqual({});
		}
	});

	/**
	 * The range is the overview's: a list has its own dates, so a range chosen on the overview does
	 * not follow the reader to a list tab — and neither back to the overview from one.
	 */
	it("keeps a chosen range on the overview and off every tab", async () => {
		renderRouteAtWithRouter(`${REVIEWS}?range=90d`);
		const navigation = await sectionNavigation();
		for (const tab of ["Overview", "Reviews", "Results", "Feedback"]) {
			expect(carriedSearch(sectionLink(navigation, tab))).toStrictEqual({});
		}
	});

	/**
	 * A practice opens the Results list filtered to it, over the days the overview counted, so
	 * the counts above that list are the row's own.
	 */
	it("opens a practice's sheet from the overview, over the overview's range", async () => {
		renderRouteAtWithRouter(`${REVIEWS}?range=90d`);
		const practice = await screen.findByRole<HTMLAnchorElement>(
			"link",
			{ name: "Open practice Thin controllers" },
			ROUTE_RENDER_WAIT,
		);
		expect(new URL(practice.href).pathname).toBe(REVIEWS);
		expect(carriedSearch(practice)).toMatchObject({
			detail: "practice:thin-controllers",
			range: "90d",
		});
	});
});

describe("the old Work tab address", () => {
	// The browser extension's "review details" link opens one piece of work.
	it("lands on that work's own reviews, with its sheet open", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/work?detail=work:pull-request:42`);
		await sectionNavigation();
		await waitFor(() => expect(router.state.location.pathname).toBe(`${REVIEWS}/runs`));
		expect(router.state.location.search).toMatchObject({
			artifactKind: "scm.pull_request",
			artifactId: 42,
			detail: "work:pull-request:42",
		});
		expect(router.state.location.search).not.toHaveProperty("status");
	});

	it("lists the work no review started on when it opens no work", async () => {
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/work`);
		await sectionNavigation();
		await waitFor(() => expect(router.state.location.pathname).toBe(`${REVIEWS}/runs`));
		expect(router.state.location.search).toMatchObject({ status: "NOT_STARTED" });
	});
});

describe("practice review page titles", () => {
	it.each([
		["the tab, with nothing open", REVIEWS, "Practice reviews · Admin · Hephaestus"],
		[
			"the record open in front",
			`${REVIEWS}?detail=feedback:${FEEDBACK}`,
			"Feedback · Practice reviews · Admin · Hephaestus",
		],
	])("names %s", async (_, url, title) => {
		const { router } = renderRouteAtWithRouter(url);
		await sectionNavigation();
		await waitFor(() =>
			expect(
				router.state.matches
					.flatMap((match) => match.meta)
					.reverse()
					.find((meta) => meta !== undefined),
			).toStrictEqual({ title }),
		);
	});

	/**
	 * Each list tab says how many it holds under the shared scope, read as the total of a one-row page
	 * of its own endpoint: the developer, practices, groups, dates and tracked record apply, a tab's own
	 * facets do not, and the overview has no count.
	 */
	it("counts each list tab under the shared scope, not under a tab's own facets", async () => {
		const counted: URL[] = [];
		server.use(
			countRead("/practices/reviews", 1688, counted),
			countRead("/practices/reviews/results", 13_308, counted),
			countRead("/practices/reviews/feedback", 1951, counted),
		);
		renderRouteAtWithRouter(
			`${REVIEWS}/runs?subjectUserId=7&groupSlug=${encodeURIComponent('["design"]')}&status=${encodeURIComponent('["FAILED"]')}`,
		);
		const navigation = await sectionNavigation();
		await within(navigation).findByRole(
			"link",
			{ name: "Reviews, 1,688", hidden: true },
			ROUTE_RENDER_WAIT,
		);
		await within(navigation).findByRole("link", { name: "Results, 13,308", hidden: true });
		await within(navigation).findByRole("link", { name: "Feedback, 1,951", hidden: true });
		within(navigation).getByRole("link", { name: "Overview", hidden: true });

		const reviews = counted.find((url) => url.pathname.endsWith("/practices/reviews"));
		expect(reviews?.searchParams.get("subjectUserId")).toBe("7");
		expect(reviews?.searchParams.getAll("groupSlug").join(",")).toContain("design");
		// The Reviews tab's own Status facet narrows its table, not its count.
		expect(reviews?.searchParams.has("status")).toBe(false);
		// The feedback list names the person as the one the feedback is addressed to.
		const feedback = counted.find((url) => url.pathname.endsWith("/feedback"));
		expect(feedback?.searchParams.get("recipientUserId")).toBe("7");
	});

	/**
	 * A count read that fails is asked again rather than leaving the tab without its count until the
	 * page is reloaded; the results table is the large one, whose read is the likeliest to fail.
	 */
	it("asks again for a count that failed, and shows it once it is known", async () => {
		const reads = failingOnceCount(13_308);
		server.use(reads.handler);
		renderRouteAtWithRouter(`${REVIEWS}/runs`);
		const navigation = await sectionNavigation();
		await within(navigation).findByRole(
			"link",
			{ name: "Results, 13,308", hidden: true },
			{ timeout: 10_000 },
		);
		expect(reads.count()).toBe(2);
	});

	/** A scope change must not attribute the previous total to a new person while its read is held. */
	it("hides the previous scope's count until the new one is known", async () => {
		const scoped = heldScopedCount();
		server.use(scoped.handler, countRead("/practices/reviews/results", 13_308, []));
		try {
			const { router } = renderRouteAtWithRouter(`${REVIEWS}/runs`);
			const navigation = await sectionNavigation();
			await within(navigation).findByRole(
				"link",
				{ name: "Results, 13,308", hidden: true },
				ROUTE_RENDER_WAIT,
			);
			await router.navigate({
				to: ".",
				search: (previous: Record<string, unknown>) => ({ ...previous, subjectUserId: 7 }),
			});
			await waitFor(() => expect(scoped.asked()).toBe(true));
			expect(
				within(navigation).queryByRole("link", { name: "Results, 13,308", hidden: true }),
			).toBeNull();
			within(navigation).getByRole("link", { name: "Results", hidden: true });
			scoped.release();
			await within(navigation).findByRole("link", { name: "Results, 42", hidden: true });
		} finally {
			scoped.release();
		}
	});
});
