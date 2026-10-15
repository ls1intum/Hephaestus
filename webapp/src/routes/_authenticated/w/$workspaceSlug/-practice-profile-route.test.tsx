import { cleanup, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type {
	GoingWellPractice,
	PracticeGuidanceDto,
	PracticePageFeedback,
	ProfileReview,
	ProfileReviewDetail,
	ResultDetail,
	ReviewedPractice,
	WorkspaceMembership,
} from "@/api/types.gen";
import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { reviewRangeStart } from "@/components/practice-profile/practice-profile-search";
import { formatDayTime } from "@/lib/dates";
import { workspaceListItem } from "@/mocks/fixtures/workspaces";
import { server } from "@/mocks/server";
import { clearUserView } from "@/runtime/user-view/session";
import { detailResult, detailReview } from "@/stories/practice-detail-story-mock-data";
import { bundledGuidance } from "@/stories/practice-guidance-story-mock-data";
import {
	GOING_WELL,
	groups,
	groupStandings,
	OVERVIEW_FIXTURE,
	packagingGroup,
	practiceStandings,
} from "@/stories/practice-profile-story-mock-data";
import {
	openProfileReview,
	openProfileReviewDetail,
	profileReviews,
} from "@/stories/profile-reviews-story-mock-data";
import { STORY_NOW } from "@/stories/story-clock";
import { deferred } from "@/test/async";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";
import { storeUserView } from "@/test/user-view";

// Mounting the real route pulls in the whole app shell and its lazy modules.
vi.setConfig({ testTimeout: ROUTE_RENDER_WAIT.timeout });

const PAGE = "/w/acme/practice-profile";

/** Long enough for a response to render once the route is up, short of the test's own timeout. */
const SETTLE_WAIT = { timeout: 5000 } as const;

const [first] = practiceStandings;
if (!first) {
	throw new Error("The fixtures carry at least one practice with a standing");
}
/** Narrowed once, so the helpers below can read it without a guard each. */
const practice = first;

const [ownResult] = detailReview.results;
if (!ownResult) {
	throw new Error("The detail review carries a result");
}

const ownSummary = ownResult.summary;
if (ownSummary === null) {
	throw new Error("The detail review's result has a summary");
}

/** The review before the open one, on the same merge request. */
const [, earlier] = profileReviews;
if (!earlier) {
	throw new Error("The fixtures carry a second review");
}

const [reused, ...unreused] = openProfileReviewDetail.results;
if (!reused) {
	throw new Error("The open review carries a result");
}
const reusingDetail: ProfileReviewDetail = {
	...openProfileReviewDetail,
	results: [{ ...reused, reviewId: earlier.review.id }, ...unreused],
};

const groupReview: ProfileReview = {
	...detailReview,
	results: [{ ...ownResult, practiceSlug: practice.slug, practiceName: practice.name }],
};

const membership: WorkspaceMembership = {
	account: {
		id: "1",
		displayName: "Ada",
		avatarUrl: null,
		status: "ACTIVE",
		identities: [{ provider: "GITHUB", login: "ada", displayName: "Ada", avatarUrl: null }],
	},
	role: "MEMBER",
	eligibleForPracticeReview: true,
	hidden: false,
	reviewSelected: true,
	joinedAt: new Date("2026-01-01T00:00:00Z"),
	leftAt: null,
};

const reviewedPractices: ReviewedPractice[] = practiceStandings.map((standing) => ({
	slug: standing.slug,
	name: standing.name,
	group: groups.find((candidate) => candidate.slug === standing.groupSlug) ?? null,
	whyItMatters: null,
	whatGoodLooksLike: null,
}));

const plainGuidance: PracticeGuidanceDto = {
	practiceSlug: "scope-the-change",
	visual: null,
	guide: null,
};

const pageOf = (reviews: ProfileReview[]) => ({
	content: reviews,
	number: 0,
	size: 10,
	totalElements: reviews.length,
	totalPages: 1,
	last: true,
});

function reviewDetailOf(reviewId: string): ProfileReviewDetail | undefined {
	if (reviewId === openProfileReview.review.id) {
		return openProfileReviewDetail;
	}
	const listed = profileReviews.find((entry) => entry.review.id === reviewId);
	return (
		listed && {
			...listed,
			practiceOutcomes: { met: 0, notMet: 0, doesNotApply: 0, couldNotDetermine: 0 },
			trace: {
				item: listed.review.item,
				review: listed.review,
				events: [],
				results: listed.results,
			},
		}
	);
}

const notFound = () => HttpResponse.json({ status: 404, title: "Not Found" }, { status: 404 });

function reviewReads(open: ProfileReviewDetail = openProfileReviewDetail) {
	return http.get(
		"*/workspaces/:workspaceSlug/practice-profile/reviews/:reviewId",
		({ params }) => {
			const found =
				params.reviewId === open.review.id ? open : reviewDetailOf(String(params.reviewId));
			return found ? HttpResponse.json(found) : notFound();
		},
	);
}

beforeEach(() => {
	server.use(
		// The surface exists only where practices review the work, and the shared fixture has them
		// off, so every case below has to say that this workspace reviews.
		http.get("*/workspaces", () =>
			HttpResponse.json([workspaceListItem("acme", { practicesEnabled: true })]),
		),
		http.get("*/workspaces/:workspaceSlug/members/me", () => HttpResponse.json(membership)),
		http.get("*/workspaces/:workspaceSlug/practices/reviewed", () =>
			HttpResponse.json(reviewedPractices),
		),
		http.get("*/workspaces/:workspaceSlug/practice-groups/standings", () =>
			HttpResponse.json(Object.values(groupStandings)),
		),
		http.get("*/workspaces/:workspaceSlug/practices/standings", () =>
			HttpResponse.json(practiceStandings),
		),
		http.get("*/workspaces/:workspaceSlug/practice-profile/overview", () =>
			HttpResponse.json(OVERVIEW_FIXTURE),
		),
		http.get("*/workspaces/:workspaceSlug/practices/feedback/practice-page", () =>
			HttpResponse.json([]),
		),
		http.get("*/workspaces/:workspaceSlug/practice-groups/:groupSlug/reviews", () =>
			HttpResponse.json(pageOf([groupReview])),
		),
		http.get("*/workspaces/:workspaceSlug/practices/:practiceSlug/guidance", () =>
			HttpResponse.json(plainGuidance),
		),
		http.get("*/workspaces/:workspaceSlug/practice-profile/reviews", () =>
			HttpResponse.json(pageOf(profileReviews)),
		),
		reviewReads(),
		http.get("*/workspaces/:workspaceSlug/practices/results/:resultId", () =>
			HttpResponse.json(detailResult),
		),
	);
});

/** The open review, by its address, over the list of reviews. */
const OPEN_REVIEW_PAGE = `${PAGE}?detail=${encodeURIComponent(
	JSON.stringify(["reviews:all", `review:${openProfileReview.review.id}`]),
)}`;

const openReviewRowName = `Open review of ${openProfileReview.review.item.label}, ${formatDayTime(
	openProfileReview.review.completedAt ?? openProfileReview.review.createdAt,
	new Date(STORY_NOW),
)}`;

async function renderProfile(path = PAGE) {
	const { router } = renderRouteAtWithRouter(path);
	await screen.findByRole("heading", { level: 1, name: "Practice profile" }, ROUTE_RENDER_WAIT);
	return router;
}

const openReviewsChip = async () =>
	screen.findByRole("link", { name: /^Latest review/u }, ROUTE_RENDER_WAIT);

const group = `practice-group:${packagingGroup.slug}`;
const practiceEntry = `practice:${practice.slug}`;

const emptyGroup = { ...packagingGroup, slug: "empty-group", name: "Nothing in here" };

function useEmptyGroup() {
	server.use(
		http.get("*/workspaces/:workspaceSlug/practices/reviewed", () =>
			HttpResponse.json([
				...reviewedPractices,
				{
					slug: "release-notes",
					name: "Write release notes",
					group: emptyGroup,
					whyItMatters: null,
					whatGoodLooksLike: null,
				} satisfies ReviewedPractice,
			]),
		),
	);
}

/** Through the page to the first practice's level, one level per history entry. */
async function openPractice(router: Awaited<ReturnType<typeof renderProfile>>) {
	fireEvent.click(
		await screen.findByRole("link", { name: "See all practice groups" }, ROUTE_RENDER_WAIT),
	);
	fireEvent.click(
		await screen.findByRole(
			"button",
			{ name: `Open group ${packagingGroup.name}` },
			ROUTE_RENDER_WAIT,
		),
	);
	fireEvent.click(
		await screen.findByRole(
			"button",
			{ name: `Open practice ${practice.name}` },
			ROUTE_RENDER_WAIT,
		),
	);
	await waitFor(() =>
		expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
			"practice-groups:all",
			group,
			practiceEntry,
		]),
	);
}

async function showResults() {
	fireEvent.click(
		await screen.findByRole("tab", { name: /^Results/u, selected: false }, ROUTE_RENDER_WAIT),
	);
	await screen.findByRole("tab", { name: /^Results/u, selected: true });
}

/**
 * What the route owns and no story can see: how the page's controls are spelled in the URL. The
 * drawer's levels are addressed by the `detail` stack, and the page's tab and the table's sort are
 * silent at their defaults so the address a reader shares is the shortest one that means the same.
 */
describe("practice profile route", () => {
	it("sends a reader away when this workspace does not review practices, without delivering feedback", async () => {
		const practicePage = vi.fn(() => HttpResponse.json([]));
		server.use(
			http.get("*/workspaces", () =>
				HttpResponse.json([workspaceListItem("acme", { practicesEnabled: false })]),
			),
			http.get("*/workspaces/:workspaceSlug/practices/feedback/practice-page", practicePage),
		);
		const { router } = renderRouteAtWithRouter(PAGE);

		// With practices off the page does not exist here, so the reader lands on the workspace home,
		// which is Activity for such a workspace, rather than on a profile with nothing to be about.
		await waitFor(
			() => expect(router.state.location.pathname).toBe("/w/acme/activity"),
			ROUTE_RENDER_WAIT,
		);
		// Reading the cards is what delivers them: a reader who is sent away must never have their
		// unread feedback marked delivered on the way out.
		expect(practicePage).not.toHaveBeenCalled();
	});

	it("stacks the list, a group and a practice in the detail param, one history entry each", async () => {
		const router = await renderProfile();
		const entries = router.history.length;

		const detail = () => stackInSearch(router.state.location.search.detail);

		fireEvent.click(
			await screen.findByRole("link", { name: "See all practice groups" }, ROUTE_RENDER_WAIT),
		);
		await waitFor(() => expect(detail()).toStrictEqual(["practice-groups:all"]));
		fireEvent.click(
			await screen.findByRole(
				"button",
				{ name: `Open group ${packagingGroup.name}` },
				ROUTE_RENDER_WAIT,
			),
		);
		await waitFor(() => expect(detail()).toStrictEqual(["practice-groups:all", group]));
		fireEvent.click(
			await screen.findByRole(
				"button",
				{ name: `Open practice ${practice.name}` },
				ROUTE_RENDER_WAIT,
			),
		);

		await waitFor(() =>
			expect(detail()).toStrictEqual(["practice-groups:all", group, practiceEntry]),
		);
		// The list, the group and the practice: three levels, and Back pops exactly one.
		expect(router.history).toHaveLength(entries + 3);
		router.history.back();
		await waitFor(() => expect(detail()).toStrictEqual(["practice-groups:all", group]));
	});

	it("lists no group without a practice", async () => {
		useEmptyGroup();
		// The drawer over the page hides the page's own heading, so the level is waited for itself.
		renderRouteAtWithRouter(
			`${PAGE}?detail=${encodeURIComponent(JSON.stringify(["practice-groups:all"]))}`,
		);

		const table = await screen.findByRole(
			"table",
			{ name: "All practice groups" },
			ROUTE_RENDER_WAIT,
		);
		await within(table).findByRole(
			"button",
			{ name: `Open group ${packagingGroup.name}` },
			ROUTE_RENDER_WAIT,
		);
		expect(
			within(table).queryByRole("button", { name: `Open group ${emptyGroup.name}` }),
		).toBeNull();
	});

	it("opens a link to a group without a practice on a level that says so", async () => {
		useEmptyGroup();
		renderRouteAtWithRouter(
			`${PAGE}?detail=${encodeURIComponent(JSON.stringify([`practice-group:${emptyGroup.slug}`]))}`,
		);

		await screen.findByText("This group has no practices", undefined, ROUTE_RENDER_WAIT);
		screen.getByRole("heading", { name: emptyGroup.name });
		expect(screen.queryByText("We could not find this practice group")).toBeNull();
	});

	it("never asks the admin-only list of practice groups", async () => {
		const adminGroups = vi.fn(() => HttpResponse.json([]));
		server.use(http.get("*/workspaces/:workspaceSlug/practice-groups", adminGroups));
		await renderProfile();
		await screen.findByRole("link", { name: "See all practice groups" }, ROUTE_RENDER_WAIT);

		expect(adminGroups).not.toHaveBeenCalled();
	});

	it("opens every review of the reader's work from the chip, then one review over it", async () => {
		const router = await renderProfile();
		const detail = () => stackInSearch(router.state.location.search.detail);

		fireEvent.click(await openReviewsChip());
		await waitFor(() => expect(detail()).toStrictEqual(["reviews:all"]));
		await screen.findByRole("heading", { name: "Reviews of your work" }, ROUTE_RENDER_WAIT);

		fireEvent.click(
			await screen.findByRole("button", { name: openReviewRowName }, ROUTE_RENDER_WAIT),
		);
		await waitFor(() =>
			expect(detail()).toStrictEqual(["reviews:all", `review:${openProfileReview.review.id}`]),
		);
		const table = await waitFor(() => {
			const loaded = screen.getByRole("table", { name: "Every practice in this review" });
			expect(loaded.getAttribute("aria-busy")).not.toBe("true");
			return loaded;
		}, ROUTE_RENDER_WAIT);
		await within(table).findByRole("button", { name: "Thin controllers" }, SETTLE_WAIT);
		// The row under the level and the head over it.
		await waitFor(() => expect(screen.getAllByText("Review 2")).toHaveLength(2));

		// One level per history entry, so Back closes exactly one and then the drawer.
		router.history.back();
		await waitFor(() => expect(detail()).toStrictEqual(["reviews:all"]));
		router.history.back();
		await waitFor(() => expect(detail()).toStrictEqual([]));
	});

	it("turns the chosen timeframe into the since the reviews list asks for", async () => {
		// Fixed at noon, so the day the bound is counted from cannot turn during the test.
		const noon = new Date(2026, 8, 22, 12, 0);
		vi.useFakeTimers({ toFake: ["Date"] });
		vi.setSystemTime(noon);
		try {
			const asked: (string | null)[] = [];
			server.use(
				http.get("*/workspaces/:workspaceSlug/practice-profile/reviews", ({ request }) => {
					asked.push(new URL(request.url).searchParams.get("since"));
					return HttpResponse.json(pageOf(profileReviews));
				}),
			);
			const router = await renderProfile();

			fireEvent.click(await openReviewsChip());
			await screen.findByRole("heading", { name: "Reviews of your work" }, ROUTE_RENDER_WAIT);
			await waitFor(() => expect(asked[0]).toBeNull());

			const user = userEvent.setup();
			await user.click(screen.getByRole("combobox", { name: "Timeframe" }));
			await user.click(await screen.findByRole("option", { name: "Last 30 days" }));

			await waitFor(() => expect(router.state.location.search.reviewSince).toBe("30d"));
			await waitFor(() =>
				expect(asked.at(-1)).toBe(reviewRangeStart(noon.getTime(), "30d").toISOString()),
			);
		} finally {
			vi.useRealTimers();
		}
	});

	it("says why a review asked for from a row was refused", async () => {
		const asked: unknown[] = [];
		server.use(
			http.post("*/workspaces/:workspaceSlug/practices/review-requests", async ({ request }) => {
				asked.push(await request.json());
				return HttpResponse.json({
					status: "REFUSED",
					reason: "REQUEST_COOLDOWN_ACTIVE",
					reviewId: null,
				});
			}),
		);
		await renderProfile();
		fireEvent.click(await openReviewsChip());

		fireEvent.click(
			await screen.findByRole("button", { name: "Request review: #890" }, ROUTE_RENDER_WAIT),
		);

		await screen.findByText(
			"A review was requested recently, so another request waits.",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		const failed = profileReviews.find((entry) => entry.review.item.label === "#890");
		expect(asked).toStrictEqual([{ itemId: failed?.review.item.id }]);
	});

	it("says a refusal on the review it was asked from, and on no other review of the item", async () => {
		server.use(
			http.post("*/workspaces/:workspaceSlug/practices/review-requests", () =>
				HttpResponse.json({
					status: "REFUSED",
					reason: "REQUEST_COOLDOWN_ACTIVE",
					reviewId: null,
				}),
			),
		);
		// The page is inert under the drawer, so the head's own button is what says it has rendered.
		const { router } = renderRouteAtWithRouter(OPEN_REVIEW_PAGE);

		fireEvent.click(
			await screen.findByRole("button", { name: "Request review" }, ROUTE_RENDER_WAIT),
		);
		await screen.findByText("No review was started", undefined, SETTLE_WAIT);

		router.history.push(
			`${PAGE}?detail=${encodeURIComponent(
				JSON.stringify(["reviews:all", `review:${earlier.review.id}`]),
			)}`,
		);
		await screen.findByRole("tab", { name: /^Every practice/u }, ROUTE_RENDER_WAIT);
		await waitFor(() => expect(screen.queryByText("No review was started")).toBeNull());
	});

	it("opens the earlier review that recorded a reused result in place of this one, without this one's filter", async () => {
		server.use(reviewReads(reusingDetail));
		const { router } = renderRouteAtWithRouter(
			`${OPEN_REVIEW_PAGE}&reviewPractice=thin&feedback=resolved`,
		);

		fireEvent.click(
			await screen.findByRole("link", { name: "Open the earlier review" }, ROUTE_RENDER_WAIT),
		);

		await waitFor(() =>
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				"reviews:all",
				`review:${earlier.review.id}`,
			]),
		);
		// The filter belonged to the review it replaced; the page's own tab is the reader's still.
		expect(router.state.location.search.reviewPractice).toBeUndefined();
		expect(router.state.location.search.feedback).toBe("resolved");
		const table = await waitFor(() => {
			const loaded = screen.getByRole("table", { name: "Every practice in this review" });
			expect(loaded.getAttribute("aria-busy")).not.toBe("true");
			return loaded;
		}, ROUTE_RENDER_WAIT);
		await within(table).findByRole("button", { name: "Explain each change" }, SETTLE_WAIT);
	});

	it("shows a review that is not the reader's as not found, without asking again", async () => {
		let reads = 0;
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-profile/reviews/:reviewId", () => {
				reads += 1;
				return notFound();
			}),
		);
		renderRouteAtWithRouter(
			`${PAGE}?detail=${encodeURIComponent(
				JSON.stringify(["review:00000000-0000-0000-0000-00000000dead"]),
			)}`,
		);

		await screen.findByText("We could not find this review", undefined, ROUTE_RENDER_WAIT);
		expect(reads).toBe(1);
	});

	it("shows the practice's own results from its group's reviews, and no other practice's", async () => {
		const router = await renderProfile();
		await openPractice(router);
		await showResults();
		await screen.findAllByText(ownSummary, undefined, SETTLE_WAIT);
		await screen.findByRole("tab", { name: "Results 1" }, SETTLE_WAIT);
	});

	it("clears the selection with the level when the level is closed forward", async () => {
		// Arrived by address: nothing behind the level to go back to, so closing writes a new one.
		// The page under the open drawer is hidden from the accessibility tree, so the level's own
		// heading is what says the route has rendered.
		const { router } = renderRouteAtWithRouter(
			`${PAGE}?detail=${encodeURIComponent(JSON.stringify([group, practiceEntry]))}&practiceTab=results`,
		);
		await screen.findByRole("heading", { name: practice.name }, ROUTE_RENDER_WAIT);
		const search = () => router.state.location.search;
		expect(search().practiceTab).toBe("results");

		fireEvent.click(screen.getByRole("button", { name: "Back" }));

		await waitFor(() => expect(stackInSearch(search().detail)).toStrictEqual([group]));
		expect(router.state.location.searchStr).not.toContain("practiceTab");
	});

	it("opens a practice tab it does not know on the feedback, and drops it from the URL", async () => {
		const { router } = renderRouteAtWithRouter(
			`${PAGE}?detail=${encodeURIComponent(JSON.stringify([group, practiceEntry]))}&practiceTab=about`,
		);
		await screen.findByRole("heading", { name: practice.name }, ROUTE_RENDER_WAIT);

		const feedback = await screen.findByRole("tab", { name: /^Feedback/u }, SETTLE_WAIT);
		expect(feedback.getAttribute("aria-selected")).toBe("true");
		await waitFor(() => expect(router.state.location.searchStr).not.toContain("practiceTab"));
	});

	it("opens a link to the guide on the feedback when the practice has no guide", async () => {
		const { visual } = bundledGuidance;
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/:practiceSlug/guidance", () =>
				HttpResponse.json({ ...plainGuidance, visual }),
			),
		);
		renderRouteAtWithRouter(
			`${PAGE}?detail=${encodeURIComponent(JSON.stringify([group, practiceEntry]))}&practiceTab=guide`,
		);
		// The picture is in, so the guidance has settled without a guide.
		await screen.findByRole("img", { name: visual.alt }, ROUTE_RENDER_WAIT);

		const feedback = await screen.findByRole("tab", { name: /^Feedback/u }, SETTLE_WAIT);
		expect(feedback.getAttribute("aria-selected")).toBe("true");
		expect(screen.queryByRole("tab", { name: "Guide" })).toBeNull();
	});

	it("keeps the URL silent on the default feedback tab and spells every other one", async () => {
		const router = await renderProfile();

		fireEvent.click(await screen.findByRole("tab", { name: /^Resolved/u }));
		await waitFor(() => expect(router.state.location.search.feedback).toBe("resolved"));
		// The tab is selected once the page has read it back from the URL; a press on a tab that is
		// still selected in the DOM is not a change.
		fireEvent.click(await screen.findByRole("tab", { name: /^Open/u, selected: false }));

		await waitFor(() => expect(router.state.location.searchStr).toBe(""));
	});

	it("links to itself without the default view, as the sidebar does", async () => {
		const router = await renderProfile();
		await screen.findByRole("link", { name: "See all practice groups" }, ROUTE_RENDER_WAIT);

		const location = router.buildLocation({
			to: "/w/$workspaceSlug/practice-profile",
			params: { workspaceSlug: "acme" },
		});
		expect(location.searchStr).toBe("");
	});

	it("keeps the URL silent on the default sort direction, and the level a press from dismissed", async () => {
		const router = await renderProfile(`${PAGE}?dir=asc`);

		fireEvent.click(
			await screen.findByRole("link", { name: "See all practice groups" }, ROUTE_RENDER_WAIT),
		);
		const entries = router.history.length;
		const table = await screen.findByRole("table", { name: "All practice groups" });
		const standing = () => within(table).getByRole("button", { name: /Standing/u });
		// One press flips the sort away from the default, the next lands back on it — once the
		// header has read the first press back from the URL.
		fireEvent.click(standing());
		await waitFor(() => expect(router.state.location.search.dir).toBe("desc"));
		await waitFor(() =>
			expect(standing().closest("th")?.getAttribute("aria-sort")).toBe("descending"),
		);
		fireEvent.click(standing());

		await waitFor(() =>
			expect(standing().closest("th")?.getAttribute("aria-sort")).toBe("ascending"),
		);
		expect(router.state.location.searchStr).not.toContain("dir=");
		// Sorting is a view of the open level, not a place: neither press left a history entry, and
		// the level was pushed on this one, so Back still dismisses it in a single step.
		expect(router.history).toHaveLength(entries);
		router.history.back();
		await waitFor(() =>
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([]),
		);
	});
});

const [linked, unworded] = GOING_WELL;
if (!linked || !unworded) {
	throw new Error("The fixtures carry two practices going well");
}
const [proof] = linked.evidence;
if (!proof) {
	throw new Error("The first practice going well carries a result that shows it");
}
const linkedSentence = linked.goingWell;
if (linkedSentence === null) {
	throw new Error("The first practice going well carries its workspace's sentence");
}

describe("going well", () => {
	const provingReview: ProfileReviewDetail = {
		...openProfileReviewDetail,
		review: { ...openProfileReviewDetail.review, id: proof.reviewId, item: proof.item },
		results: [
			...openProfileReviewDetail.results,
			{
				...ownResult,
				id: proof.resultId,
				reviewId: proof.reviewId,
				item: proof.item,
				practiceSlug: linked.practiceSlug,
				practiceName: linked.practiceName,
				outcome: "MET",
				severity: null,
				summary: "The pull request is marked ready and closes its issue",
				problems: [],
			},
		],
	};

	it("names each practice under a heading that links to its level", async () => {
		const router = await renderProfile();
		const section = await screen.findByRole("region", { name: "Going well" }, ROUTE_RENDER_WAIT);
		const name = within(section).getByRole("link", { name: linked.practiceName });
		expect(name.closest("h3")).not.toBeNull();
		within(section).getByText(linkedSentence);
		within(section).getByRole("link", { name: unworded.practiceName });

		fireEvent.click(name);
		await waitFor(() =>
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				`practice:${linked.practiceSlug}`,
			]),
		);
		await screen.findByRole("heading", { name: linked.practiceName }, ROUTE_RENDER_WAIT);
	});

	it("opens a supporting result in its own review, at that result", async () => {
		const reviewsRead: string[] = [];
		const resultsRead: string[] = [];
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-profile/reviews/:reviewId", ({ params }) => {
				reviewsRead.push(String(params.reviewId));
				return HttpResponse.json(provingReview);
			}),
			http.get("*/workspaces/:workspaceSlug/practices/results/:resultId", ({ params }) => {
				resultsRead.push(String(params.resultId));
				return HttpResponse.json({
					...detailResult,
					id: proof.resultId,
					reviewId: proof.reviewId,
					item: proof.item,
					practiceSlug: linked.practiceSlug,
					practiceName: linked.practiceName,
					outcome: "MET",
					severity: null,
					summary: "The pull request is marked ready and closes its issue",
					problems: [],
					explanation:
						"The description opens with Closes #41 and the pull request left draft first.",
				} satisfies ResultDetail);
			}),
		);
		const router = await renderProfile();
		const section = await screen.findByRole("region", { name: "Going well" }, ROUTE_RENDER_WAIT);

		fireEvent.click(within(section).getByRole("link", { name: "Pull request #22" }));

		await waitFor(() =>
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				`review:${proof.reviewId}`,
			]),
		);
		expect(router.state.location.search.reviewResult).toBe(proof.resultId);
		await screen.findByText(
			"The description opens with Closes #41 and the pull request left draft first.",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		expect(reviewsRead).toContain(proof.reviewId);
		expect(resultsRead).toStrictEqual([proof.resultId]);
	});

	it("opens a practice's results list from More", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-profile/overview", () =>
				HttpResponse.json({
					...OVERVIEW_FIXTURE,
					goingWell: [{ ...linked, hasMore: true }] satisfies GoingWellPractice[],
				}),
			),
		);
		const router = await renderProfile();
		const section = await screen.findByRole("region", { name: "Going well" }, ROUTE_RENDER_WAIT);

		fireEvent.click(
			within(section).getByRole("link", { name: `More results for ${linked.practiceName}` }),
		);

		await waitFor(() =>
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				`practice:${linked.practiceSlug}`,
			]),
		);
		await screen.findByRole("tab", { name: /^Results/u, selected: true }, ROUTE_RENDER_WAIT);
	});

	it("shows a custom practice's own sentence as the workspace wrote it", async () => {
		const sentence = "Every migration ships with its rollback, written down before it runs.";
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-profile/overview", () =>
				HttpResponse.json({
					...OVERVIEW_FIXTURE,
					goingWell: [
						{
							...linked,
							practiceSlug: "migrations-roll-back",
							practiceName: "Migrations roll back",
							goingWell: sentence,
						},
					] satisfies GoingWellPractice[],
				}),
			),
		);
		await renderProfile();
		const section = await screen.findByRole("region", { name: "Going well" }, ROUTE_RENDER_WAIT);

		within(section).getByText(sentence);
		within(section).getByRole("link", { name: "Migrations roll back" });
	});

	it("says what will appear when nothing goes well yet, and praises nothing", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-profile/overview", () =>
				HttpResponse.json({ ...OVERVIEW_FIXTURE, goingWell: [] }),
			),
		);
		await renderProfile();
		const section = await screen.findByRole("region", { name: "Going well" }, ROUTE_RENDER_WAIT);

		within(section).getByText(
			"Practices that your work keeps meeting appear here, with the work that shows it.",
		);
		expect(within(section).queryAllByRole("link")).toHaveLength(0);
	});

	it("draws skeletons while the overview loads, and claims nothing", async () => {
		const overview = deferred();
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-profile/overview", async () => {
				await overview.promise;
				return HttpResponse.json(OVERVIEW_FIXTURE);
			}),
		);
		await renderProfile();
		const section = await screen.findByRole("region", { name: "Going well" }, ROUTE_RENDER_WAIT);

		within(section).getByText("Loading what is going well…");
		expect(within(section).queryAllByRole("link")).toHaveLength(0);
		expect(within(section).queryByText(/appear here/u)).toBeNull();
		overview.resolve();
		await within(section).findByRole("link", { name: linked.practiceName }, SETTLE_WAIT);
	});

	it("claims nothing when the overview fails to load", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-profile/overview", () =>
				HttpResponse.json({ status: 500, title: "Internal Server Error" }, { status: 500 }),
			),
		);
		renderRouteAtWithRouter(PAGE);

		await screen.findByText("We could not load your practices", undefined, ROUTE_RENDER_WAIT);
		expect(screen.queryByRole("region", { name: "Going well" })).toBeNull();
	});
});

// The self endpoint owns evidence visibility; this route may show only the fields it returns.
describe("own result", () => {
	it("shows a result whose evidence was withheld without inventing proof", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/results/:resultId", () =>
				HttpResponse.json({
					...detailResult,
					explanation: null,
					evidence: null,
					example: null,
					coverage: null,
					problems: detailResult.problems.map((problem) => ({
						...problem,
						explanation: null,
						citations: [],
					})),
				} satisfies ResultDetail),
			),
		);
		const { router } = renderRouteAtWithRouter(`${OPEN_REVIEW_PAGE}&reviewResult=${reused.id}`);

		await screen.findByText(
			"The caching change hides inside the package move",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		expect(router.state.location.search.reviewResult).toBe(reused.id);
		expect(screen.queryByText(/PracticeCatalogLoader/u)).toBeNull();
		expect(screen.queryByText(/so a reviewer cannot tell which hunk/u)).toBeNull();
	});
});

/** One piece of feedback on the page, about the practice the level opens. */
const feedback: PracticePageFeedback = {
	unverifiedHistory: false,
	id: "00000000-0000-0000-0000-000000000201",
	headline: "Pull requests bundle a fix with a refactor",
	body: "Reviewers had to follow two intentions in one diff.",
	nextStep: null,
	practiceSlug: practice.slug,
	practiceName: practice.name,
	groupSlug: packagingGroup.slug,
	groupName: packagingGroup.name,
	preparedAt: new Date("2026-09-09T14:10:00Z"),
	readAt: null,
	closedAt: null,
	closedBy: null,
	response: null,
	whyItMatters: null,
	whatGoodLooksLike: null,
	withdrawn: false,
	cleanNeeded: 3,
	cleanWork: [],
	evidence: [],
};

/** The practice's own picture and guide, as the server returns them for the open practice. */
const guidance = bundledGuidance satisfies PracticeGuidanceDto;

const PRACTICE_PAGE = `${PAGE}?detail=${encodeURIComponent(JSON.stringify([group, practiceEntry]))}`;

describe("practice introduction", () => {
	let guidanceAsked: unknown[] = [];

	beforeEach(() => {
		guidanceAsked = [];
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/:practiceSlug/guidance", ({ params }) => {
				guidanceAsked.push(params.practiceSlug);
				return HttpResponse.json(guidance);
			}),
		);
	});
	it("asks for the guidance only for the practice that is open", async () => {
		const router = await renderProfile();
		expect(guidanceAsked).toStrictEqual([]);

		await openPractice(router);

		const intro = await screen.findByRole("region", { name: "Introduction" }, SETTLE_WAIT);
		await within(intro).findByRole("img", { name: guidance.visual.alt }, SETTLE_WAIT);
		expect(guidanceAsked).toStrictEqual([practice.slug]);
	});

	it("opens the guide a link asks for, in the practice's Guide tab", async () => {
		renderRouteAtWithRouter(`${PRACTICE_PAGE}&practiceTab=guide`);
		await screen.findByRole("heading", { name: practice.name }, ROUTE_RENDER_WAIT);

		const tab = await screen.findByRole("tab", { name: "Guide" }, SETTLE_WAIT);
		expect(tab.getAttribute("aria-selected")).toBe("true");
		screen.getByRole("heading", { level: 4, name: "How to do it" });
	});

	it("shows a practice with no standing from its catalog words and guide, with no result made up", async () => {
		const sentence = "We pair on every schema change before it reaches review.";
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/reviewed", () =>
				HttpResponse.json([
					...reviewedPractices,
					{
						slug: "pair-on-schema-changes",
						name: "Pair on schema changes",
						group: null,
						whyItMatters: sentence,
						whatGoodLooksLike: null,
					} satisfies ReviewedPractice,
				]),
			),
			http.get("*/workspaces/:workspaceSlug/practices/:practiceSlug/guidance", () =>
				HttpResponse.json({
					...plainGuidance,
					practiceSlug: "pair-on-schema-changes",
					guide: guidance.guide,
				} satisfies PracticeGuidanceDto),
			),
		);
		renderRouteAtWithRouter(
			`${PAGE}?detail=${encodeURIComponent(JSON.stringify(["practice:pair-on-schema-changes"]))}`,
		);

		await screen.findByText(
			"This practice offers guidance without automated review.",
			undefined,
			ROUTE_RENDER_WAIT,
		);
		screen.getByText(sentence);
		screen.getByRole("heading", { name: "Pair on schema changes" });
		screen.getByRole("heading", { level: 4, name: "How to do it" });
		expect(screen.queryByRole("tab", { name: /^Results/u })).toBeNull();
		// The page behind counts its own practices by standing; the level makes up none for this one.
		const level = screen.getByRole("dialog", { name: "Pair on schema changes" });
		expect(within(level).queryByText("Not reviewed")).toBeNull();
	});
});

// User view is read-only; every response control must stay absent.
describe("practice profile in a user view", () => {
	beforeEach(() => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/feedback/practice-page", () =>
				HttpResponse.json([feedback]),
			),
		);
	});
	afterEach(clearUserView);

	const responseControls = async () => {
		const router = await renderProfile();
		await screen.findByText(feedback.headline, undefined, ROUTE_RENDER_WAIT);
		const ratings = screen.queryAllByRole("button", { name: "Helpful" }).length;
		const answers = screen.queryAllByRole("button", { name: "I fixed this" }).length;
		await openPractice(router);
		await showResults();
		await screen.findAllByText(ownSummary, undefined, ROUTE_RENDER_WAIT);
		return {
			ratings,
			answers,
			responses: screen.queryAllByRole("group", { name: /^Your response to the feedback/u }).length,
		};
	};

	it("offers the developer a rating, an answer and a response", async () => {
		await expect(responseControls()).resolves.toStrictEqual({
			ratings: 1,
			answers: 1,
			responses: groupReview.feedback.length,
		});
	});

	it("offers an administrator viewing as the developer no review to start", async () => {
		storeUserView({ workspaceSlug: "acme" });
		await renderProfile();
		fireEvent.click(await openReviewsChip());

		await screen.findByRole("button", { name: openReviewRowName }, ROUTE_RENDER_WAIT);
		expect(screen.queryAllByRole("button", { name: /^Request review/u })).toHaveLength(0);
	});

	it("offers an administrator viewing as the developer none of them", async () => {
		storeUserView({ workspaceSlug: "acme" });
		await expect(responseControls()).resolves.toStrictEqual({
			ratings: 0,
			answers: 0,
			responses: 0,
		});
	});
});

const pullRequestEvidence = (number: number) => ({
	resultId: `0b9c6f0e-0000-4000-8000-0000000090${number}`,
	reviewId: `5d1e2a3b-0000-4000-8000-0000000090${number}`,
	recordedAt: "2026-09-08T10:00:00Z",
	summary: null,
	item: {
		kind: "scm.pull_request",
		id: String(9000 + number),
		parentId: null,
		label: `#${number}`,
		title: `Pull request ${number}`,
		provider: "GITHUB" as const,
		url: null,
		container: null,
		path: null,
		removedAt: null,
	},
});

/** A practice of the open group that every review so far found not to apply. */
const neverApplied = practiceStandings.find(
	(each) => each.groupSlug === packagingGroup.slug && each.slug !== practice.slug,
);
if (!neverApplied) {
	throw new Error("The fixtures carry a second practice in the packaging group");
}
const withNeverApplied = practiceStandings.map((each) =>
	each.slug === neverApplied.slug
		? { ...each, standing: "NO_OPPORTUNITY" as const, doesNotApplyOnly: true }
		: each,
);

/** Every practice of the packaging group never applied. */
const packagingNeverApplied = practiceStandings.map((each) =>
	each.groupSlug === packagingGroup.slug
		? { ...each, standing: "NO_OPPORTUNITY" as const, doesNotApplyOnly: true }
		: each,
);
/** The server reads such a group as NO_OPPORTUNITY. */
const packagingGroupNothingToReport = Object.values(groupStandings).map((each) =>
	each.groupSlug === packagingGroup.slug ? { ...each, standing: "NO_OPPORTUNITY" as const } : each,
);

describe("practice profile pattern label and folds", () => {
	const patterned = {
		...feedback,
		evidence: [pullRequestEvidence(21), pullRequestEvidence(22)],
	};
	const groupPage = `${PAGE}?detail=${encodeURIComponent(JSON.stringify([group]))}`;

	it("folds a practice that never applied under its group's table", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/standings", () =>
				HttpResponse.json(withNeverApplied),
			),
		);
		const openNeverApplied = { name: `Open practice ${neverApplied.name}` };
		const fold = { name: "Does not apply to your work yet" };

		renderRouteAtWithRouter(groupPage);
		const folded = await screen.findByRole("button", fold, ROUTE_RENDER_WAIT);
		expect(folded.getAttribute("aria-expanded")).toBe("false");
		expect(screen.queryByRole("button", openNeverApplied)).toBeNull();
		cleanup();

		// Its own level says it does not apply, in place of "Nothing to report yet".
		renderRouteAtWithRouter(
			`${PAGE}?detail=${encodeURIComponent(JSON.stringify([group, `practice:${neverApplied.slug}`]))}`,
		);
		await expect(
			screen.findAllByText("Does not apply to your work yet", {}, ROUTE_RENDER_WAIT),
		).resolves.not.toHaveLength(0);
	});

	it("names a group whose every practice never applied as its folded row does", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/standings", () =>
				HttpResponse.json(packagingNeverApplied),
			),
			http.get("*/workspaces/:workspaceSlug/practice-groups/standings", () =>
				HttpResponse.json(packagingGroupNothingToReport),
			),
		);
		renderRouteAtWithRouter(groupPage);
		await expect(
			screen.findAllByRole(
				"button",
				{ name: "Does not apply to your work yet" },
				ROUTE_RENDER_WAIT,
			),
		).resolves.not.toHaveLength(0);
		expect(screen.queryByText("Nothing to report yet")).toBeNull();
	});

	it("shows the pattern as a plain label in the group's table", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/feedback/practice-page", () =>
				HttpResponse.json([patterned]),
			),
		);
		renderRouteAtWithRouter(groupPage);
		const label = await screen.findByText(/^Has open feedback/u, {}, ROUTE_RENDER_WAIT);
		expect(label.closest("button")).toBeNull();
	});
});
