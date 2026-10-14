import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { listReviewedWorkReviewRunsInfiniteQueryKey } from "@/api/@tanstack/react-query.gen";
import { REVIEW_RUN_PAGE_SIZE } from "@/hooks/use-practice-group-detail";
import { server } from "@/mocks/server";
import { detailObservation, detailRun } from "@/stories/practice-detail-story-mock-data";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

// Mounting the real route pulls in the whole app shell and its lazy modules.
vi.setConfig({ testTimeout: 20_000 });

const RUNS_PATH =
	"*/workspaces/:workspaceSlug/practices/reviewed-work/:artifactKind/:artifactId/review-runs";
const RESPONSE_PATH = "*/workspaces/:workspaceSlug/practices/feedback/:feedbackId/response";

const PAGE = "/w/acme/feedback/scm.pull_request/1423";

/** The observation the first read shows, and the one only a later authorized read carries. */
const EARLIER = detailObservation.summary;
const LATER = "A later review found the tests missing";
const laterRun = {
	...detailRun,
	reviewId: "00000000-0000-0000-0000-000000000901",
	observations: [
		{ ...detailObservation, id: "00000000-0000-0000-0000-000000000902", summary: LATER },
	],
};

interface RunsAnswer {
	status: number;
	respond: () => Response;
}

const runs = (run: typeof detailRun): RunsAnswer => ({
	status: 200,
	respond: () => HttpResponse.json({ content: [run], page: 0, size: 10, hasNext: false }),
});
const failure = (status: number): RunsAnswer => ({
	status,
	respond: () => HttpResponse.json({ status, title: "Not now" }, { status }),
});
const disputeDraft = () =>
	screen.getByRole<HTMLTextAreaElement>("textbox", { name: "Why do you dispute this?" });

/** Each answer once, in order, and the last for every read after it; `served` records each status. */
function runsInTurn(served: number[], answers: readonly RunsAnswer[]) {
	return answers.map((answer, index) =>
		http.get(
			RUNS_PATH,
			() => {
				served.push(answer.status);
				return answer.respond();
			},
			{ once: index < answers.length - 1 },
		),
	);
}

/** Renders the page and returns a refresh of exactly the query it reads the work's reviews with. */
function renderPage() {
	const queryClient = renderRouteAt(PAGE);
	return async () => {
		await act(async () => {
			await queryClient.invalidateQueries({
				queryKey: listReviewedWorkReviewRunsInfiniteQueryKey({
					path: { workspaceSlug: "acme", artifactKind: "scm.pull_request", artifactId: 1423 },
					query: { size: REVIEW_RUN_PAGE_SIZE },
				}),
				exact: true,
			});
		});
	};
}

let reads: { artifactKind: string; artifactId: string }[] = [];

beforeEach(() => {
	reads = [];
	server.use(
		http.get(RUNS_PATH, ({ params }) => {
			reads.push({
				artifactKind: String(params.artifactKind),
				artifactId: String(params.artifactId),
			});
			return HttpResponse.json({ content: [detailRun], page: 0, size: 10, hasNext: false });
		}),
	);
});

/** The page is the address comments on the work link to; its states are stories, its wire is here. */
describe("the page a comment on the work links to", () => {
	it("reads the reader's reviews of exactly the linked work", async () => {
		renderRouteAt("/w/acme/feedback/scm.pull_request/1423");

		await screen.findByRole("heading", { name: "Your feedback on this work" }, ROUTE_RENDER_WAIT);
		await screen.findByRole("list", { name: "Reviews" }, ROUTE_RENDER_WAIT);
		expect(reads[0]).toStrictEqual({ artifactKind: "scm.pull_request", artifactId: "1423" });
	});

	it("writes a dispute and reads the work's reviews again", async () => {
		const written: unknown[] = [];
		server.use(
			http.put(RESPONSE_PATH, async ({ request, params }) => {
				written.push(await request.json());
				return HttpResponse.json({ feedbackId: String(params.feedbackId), resolution: "DISPUTED" });
			}),
		);
		renderRouteAt("/w/acme/feedback/scm.pull_request/1423");

		// One observation of the linked review carries feedback, so one can be disputed.
		await userEvent.click(
			await screen.findByRole("button", { name: "Disputed" }, ROUTE_RENDER_WAIT),
		);
		await userEvent.type(
			screen.getByRole("textbox", { name: "Why do you dispute this?" }),
			"The loader never ran on this path.",
		);
		await userEvent.click(screen.getByRole("button", { name: "Send" }));

		await waitFor(() => {
			expect(written).toHaveLength(1);
		});
		expect(written[0]).toMatchObject({
			resolution: "DISPUTED",
			comment: "The loader never ran on this path.",
		});
		await waitFor(() => {
			expect(reads.length).toBeGreaterThan(1);
		});
	});

	it("does not ask for anything when the address names no work", async () => {
		renderRouteAt("/w/acme/feedback/scm.pull_request/not-a-number");

		await screen.findByText(/not found/iu, undefined, ROUTE_RENDER_WAIT);
		expect(reads).toHaveLength(0);
	});

	it.each([
		[403, /Ask a workspace admin or instance admin for access/u],
		[404, /It may have been deleted or moved/u],
	] as const)(
		"keeps reviews refused with %i hidden through a later failure, until an authorized read",
		async (status, refusal) => {
			const served: number[] = [];
			server.use(
				...runsInTurn(served, [runs(detailRun), failure(status), failure(503), runs(laterRun)]),
			);
			const refresh = renderPage();
			await screen.findAllByText(EARLIER, undefined, ROUTE_RENDER_WAIT);
			expect(served).toStrictEqual([200]);

			await refresh();
			expect(served).toStrictEqual([200, status]);
			await screen.findByText(refusal);
			expect(screen.queryAllByText(EARLIER)).toStrictEqual([]);

			await refresh();
			expect(served).toStrictEqual([200, status, 503]);
			// The query settles before its observers hear of it; the screen has caught up once the
			// refusal it was showing is gone.
			await waitFor(() => expect(screen.queryByText(refusal)).toBeNull());
			expect(screen.queryAllByText(EARLIER)).toStrictEqual([]);

			await refresh();
			expect(served).toStrictEqual([200, status, 503, 200]);
			await screen.findAllByText(LATER);
			expect(screen.queryAllByText(EARLIER)).toStrictEqual([]);
		},
	);

	it("keeps a review and a dispute being written through a failed refresh, and retries it", async () => {
		const served: number[] = [];
		server.use(...runsInTurn(served, [runs(detailRun), failure(503), runs(detailRun)]));
		const refresh = renderPage();
		await userEvent.click(
			await screen.findByRole("button", { name: "Disputed" }, ROUTE_RENDER_WAIT),
		);
		await userEvent.type(disputeDraft(), "The loader never ran on this path.");

		await refresh();
		expect(served).toStrictEqual([200, 503]);
		expect(disputeDraft().value).toBe("The loader never ran on this path.");
		await screen.findByText("We could not refresh reviews");
		expect(disputeDraft().value).toBe("The loader never ran on this path.");
		await userEvent.click(screen.getByRole("button", { name: "Retry" }));

		await waitFor(() => expect(served).toStrictEqual([200, 503, 200]));
		await waitFor(() => expect(screen.queryByRole("button", { name: /^Retry/u })).toBeNull());
		expect(screen.queryAllByText(EARLIER)).not.toStrictEqual([]);
		expect(disputeDraft().value).toBe("The loader never ran on this path.");
	});
});
