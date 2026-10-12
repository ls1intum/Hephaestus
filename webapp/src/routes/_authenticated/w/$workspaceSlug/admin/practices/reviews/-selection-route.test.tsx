import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import {
	observationDetail,
	reviewJob,
	reviewRuns,
} from "@/components/admin/practice-reviews/fixtures";
import { reviewHandlers } from "@/components/admin/practice-reviews/story-mock-server";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules; the timeout is a
// deadlock backstop, not a budget these renders were meant to fit inside.
vi.setConfig({ testTimeout: 15_000 });

const REVIEWS = "/w/acme/admin/practices/reviews";

/**
 * Real version-4 UUIDs: the lists' schema drops an id that is not one, as the fixtures' are. The
 * fixture review and observation are served under them.
 */
const REVIEW = "3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84";
const OBSERVATION = "9a1d3c5e-7b2f-4e6a-8c0d-1f3b5d7e9a2c";
const FIXTURE_REVIEW = "11111111-1111-1111-1111-111111111111";
const FIXTURE_OBSERVATION = "66666666-6666-6666-6666-666666666666";
/** The fixture review's work: pull request #1423, whose id is 42. */
const WORK = "artifactKind=scm.pull_request&artifactId=42";

const fixtureRun = reviewRuns.find((run) => run.id === FIXTURE_REVIEW);

/** Every list request, recorded, over the review fixture served under the ids above. */
function recordRequests() {
	const urls = { reviews: [] as URL[], work: [] as URL[], feedback: [] as URL[] };
	const emptyPage = { content: [], page: { number: 0, size: 25, totalElements: 0, totalPages: 0 } };
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews", ({ request }) => {
			urls.reviews.push(new URL(request.url));
			return HttpResponse.json({
				content: [{ ...fixtureRun, id: REVIEW }],
				page: { number: 0, size: 25, totalElements: 1, totalPages: 1 },
			});
		}),
		http.get("*/workspaces/:workspaceSlug/practices/trace", ({ request }) => {
			urls.work.push(new URL(request.url));
			return HttpResponse.json(emptyPage);
		}),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", ({ request }) => {
			urls.feedback.push(new URL(request.url));
			return HttpResponse.json(emptyPage);
		}),
		http.get("*/workspaces/:workspaceSlug/agents/jobs/:jobId", () =>
			HttpResponse.json({ ...reviewJob(FIXTURE_REVIEW), id: REVIEW }),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/observations/:observationId", () =>
			HttpResponse.json({
				...observationDetail(FIXTURE_OBSERVATION),
				id: OBSERVATION,
				agentJobId: REVIEW,
			}),
		),
		...reviewHandlers(),
	);
	return urls;
}

async function selectionBar() {
	return screen.findByRole("group", { name: "Selection", hidden: true }, ROUTE_RENDER_WAIT);
}

function pathOf(link: HTMLElement) {
	return new URL(String(link.getAttribute("href")), window.location.origin).pathname;
}

function carriedSearch(link: HTMLElement) {
	return Object.fromEntries(
		new URL(String(link.getAttribute("href")), window.location.origin).searchParams,
	);
}

describe("the selection across practice review tabs", () => {
	/**
	 * Track on a row writes the selection to the address without opening the row; the bar names it,
	 * and every tab link carries it.
	 */
	it("tracks a review from its row and carries it to every tab", async () => {
		recordRequests();
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/runs`);

		const toggle = await screen.findByRole(
			"button",
			// The row names the review by its work and when it ran, so two reviews of one pull request differ.
			{ name: /^Track review of #1423, .+\d:\d{2}/u },
			ROUTE_RENDER_WAIT,
		);
		await userEvent.click(toggle);

		await waitFor(() =>
			expect(router.state.location.search).toMatchObject({ agentJobId: REVIEW, selected: true }),
		);
		expect(router.state.location.search).not.toHaveProperty("detail");
		const bar = await selectionBar();
		await within(bar).findByText(/^Review of #1423 · /u);
		const navigation = screen.getByRole("navigation", { name: "Practice review sections" });
		for (const tab of ["Overview", "Observations", "Feedback"]) {
			expect(
				carriedSearch(within(navigation).getByRole("link", { name: new RegExp(`^${tab}`, "u") })),
			).toStrictEqual({
				agentJobId: REVIEW,
				selected: "true",
			});
		}
	});

	/**
	 * The same review without `selected` — what a "See all" link writes — is a filter: the list
	 * narrows the same way, the toolbar names it as a pill, and there is no selection bar.
	 */
	it("shows a review the address filters by as a pill, not as the selection", async () => {
		const urls = recordRequests();
		const { router } = renderRouteAtWithRouter(`${REVIEWS}/feedback?agentJobId=${REVIEW}`);

		await screen.findByText(/^Review: #1423 · /u, {}, ROUTE_RENDER_WAIT);
		await waitFor(() => expect(urls.feedback.at(-1)?.searchParams.get("agentJobId")).toBe(REVIEW));
		expect(screen.queryByRole("group", { name: "Selection" })).toBeNull();
		await userEvent.click(screen.getByRole("button", { name: /^Clear review filter \(#1423/u }));
		await waitFor(() => expect(router.state.location.search).not.toHaveProperty("agentJobId"));
	});

	it("narrows the reviews to a tracked piece of work, and Show trace opens its trace", async () => {
		const urls = recordRequests();
		renderRouteAtWithRouter(`${REVIEWS}/runs?${WORK}&selected=true`);

		const bar = await selectionBar();
		await waitFor(() => expect(urls.reviews.at(-1)?.searchParams.get("artifactId")).toBe("42"));
		expect(urls.reviews.at(-1)?.searchParams.get("artifactKind")).toBe("scm.pull_request");
		const trace = await within(bar).findByRole("link", { name: "Show trace" });
		// The work's trace over the tab, keeping the selection.
		expect(pathOf(trace)).toBe(`${REVIEWS}/runs`);
		expect(carriedSearch(trace)).toMatchObject({
			artifactKind: "scm.pull_request",
			artifactId: "42",
			selected: "true",
			detail: "trace:work:pull-request:42",
		});
	});

	/** An observation names its review: the reviews narrow to it, the feedback to what it led to. */
	it("narrows the reviews and the feedback to a selected observation", async () => {
		const urls = recordRequests();
		const selected = `agentJobId=${REVIEW}&observationId=${OBSERVATION}&selected=true`;
		renderRouteAtWithRouter(`${REVIEWS}/feedback?${selected}`);

		const bar = await selectionBar();
		await within(bar).findByText("A cache miss and a permission failure come back as the same 404");
		await waitFor(() =>
			expect(urls.feedback.at(-1)?.searchParams.get("observationId")).toBe(OBSERVATION),
		);
		expect(urls.feedback.at(-1)?.searchParams.get("agentJobId")).toBe(REVIEW);
		// Show trace opens the observation's own trace over the tab.
		const trace = within(bar).getByRole("link", { name: "Show trace" });
		expect(pathOf(trace)).toBe(`${REVIEWS}/feedback`);
		expect(carriedSearch(trace)).toMatchObject({
			agentJobId: REVIEW,
			observationId: OBSERVATION,
			selected: "true",
			detail: `trace:observation:${OBSERVATION}`,
		});
		const navigation = screen.getByRole("navigation", { name: "Practice review sections" });
		await userEvent.click(within(navigation).getByRole("link", { name: /^Reviews/u }));
		await waitFor(() => expect(urls.reviews.at(-1)?.searchParams.get("agentJobId")).toBe(REVIEW));
	});

	it("clears the selection from the bar and from the address", async () => {
		recordRequests();
		const { router } = renderRouteAtWithRouter(
			`${REVIEWS}/observations?agentJobId=${REVIEW}&selected=true`,
		);

		const bar = await selectionBar();
		await userEvent.click(
			await within(bar).findByRole("button", { name: /^Stop tracking \(Review of #1423/u }),
		);

		await waitFor(() => expect(router.state.location.search).not.toHaveProperty("agentJobId"));
		expect(router.state.location.search).not.toHaveProperty("selected");
		await waitFor(() => expect(screen.queryByRole("group", { name: "Selection" })).toBeNull());
	});
});
