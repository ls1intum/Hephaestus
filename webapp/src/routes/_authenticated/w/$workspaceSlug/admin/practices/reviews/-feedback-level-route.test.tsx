import type { QueryClient } from "@tanstack/react-query";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { assert, describe, expect, it, vi } from "vitest";

import {
	getReviewOverviewQueryKey,
	listReviewResultsQueryKey,
	listReviewsQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { FeedbackDecision, ReviewFeedback } from "@/api/types.gen";
import {
	awaitingApprovalFeedback,
	awaitingApprovalFeedbackDetail,
	feedbackDetail,
	reviewAccounts,
} from "@/components/admin/practice-reviews/fixtures";
import {
	reviewHandlers,
	feedbackCountHandlers,
} from "@/components/admin/practice-reviews/story-mock-server";
import { stackInSearch } from "@/components/layout/detail-drawer/detail-stack";
import { hasText } from "@/lib/text";
import { server } from "@/mocks/server";
import { deferred, sleep } from "@/test/async";
import { levelsOpenedBy } from "@/test/detail-stack";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter, testQueryClient } from "@/test/router-harness";

vi.setConfig({ testTimeout: 20_000 });

const FEEDBACK_ID = awaitingApprovalFeedbackDetail.id;
const CONTENT_DIGEST = awaitingApprovalFeedbackDetail.contentDigest;

/** The level opened by its URL over the list it belongs to, as a shared link opens it. */
const FEEDBACK_LEVEL = `/w/acme/admin/practices/reviews/feedback?detail=feedback:${FEEDBACK_ID}`;

type ProposalState = Extract<ReviewFeedback["state"], "AWAITING_APPROVAL" | "READY">;

/** A proposal with its planned copies, or the same feedback once approved and ready to send. */
function proposalWire(state: ProposalState, id = FEEDBACK_ID) {
	return {
		...awaitingApprovalFeedbackDetail,
		id,
		state,
		stopReason: state === "AWAITING_APPROVAL" ? "PRACTICE_REQUIRES_APPROVAL" : null,
		body: "Please keep the cache scoped to one workspace so membership changes cannot leak.",
	};
}

/** The decision the server records, as `createFeedbackDecision` answers. */
function decisionWire(feedbackId: string, action: FeedbackDecision["action"]) {
	return {
		id: `${feedbackId.slice(0, -3)}d99`,
		feedbackId,
		action,
		actor: reviewAccounts.grace,
		reasonCode: null,
		note: null,
		contentDigest: CONTENT_DIGEST,
		decidedAt: new Date().toISOString(),
	};
}

/**
 * Cached reads that count feedback by delivery state, under filters the level cannot know and the
 * page under it does not mount, so each stays in the cache for the assertion to read.
 */
function seedCountingReads(queryClient: QueryClient, workspaceSlug: string) {
	const path = { workspaceSlug };
	const keys = [
		getReviewOverviewQueryKey({
			path,
			query: { from: new Date("2026-01-01T00:00:00Z"), zone: "UTC" },
		}),
		listReviewsQueryKey({ path, query: { status: ["COMPLETE"] } }),
		listReviewResultsQueryKey({ path, query: { reviewId: "cached-review" } }),
	];
	for (const key of keys) {
		queryClient.setQueryData(key, {});
	}
	return keys;
}

/**
 * The approval queue's steps once they read `position`. Read within them: the overview under the
 * level has figures that read "1 of 3" too.
 */
async function queueSteps(position: string) {
	return waitFor(() => {
		const steps = within(screen.getByRole("navigation", { name: "Feedback awaiting approval" }));
		steps.getByText(position);
		return steps;
	}, ROUTE_RENDER_WAIT);
}

describe("feedback approval level", () => {
	it("shows the exact proposal, approves it, and refreshes this workspace's counts of it", async () => {
		let state: ProposalState = "AWAITING_APPROVAL";
		let requestBody: unknown;
		server.use(
			...feedbackCountHandlers(),
			http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", () =>
				HttpResponse.json(proposalWire(state)),
			),
			http.post(
				"*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId/decisions",
				async ({ request }) => {
					requestBody = await request.json();
					state = "READY";
					return HttpResponse.json(decisionWire(FEEDBACK_ID, "APPROVE"), { status: 201 });
				},
			),
			...reviewHandlers(),
		);

		const queryClient = testQueryClient();
		const ours = seedCountingReads(queryClient, "acme");
		const theirs = seedCountingReads(queryClient, "other");
		renderRouteAtWithRouter(FEEDBACK_LEVEL, queryClient);

		// Titled by its first line, as its row is; the text itself is in the package below.
		await screen.findByRole(
			"heading",
			{ name: /^Please keep the cache scoped/u },
			ROUTE_RENDER_WAIT,
		);
		expect(screen.getAllByText(/Please keep the cache scoped/u).length).toBeGreaterThan(1);
		// The sheet is the feedback alone: what it was based on is its trace, not a link here.
		expect(screen.queryByRole("group", { name: "How this came about" })).toBeNull();

		await userEvent.click(screen.getByRole("button", { name: "Approve for delivery" }));
		await screen.findByText("Feedback approved. Delivery is being checked.");
		// Approval names the copies the admin read, so a package composed again since is refused.
		expect(requestBody).toStrictEqual({ action: "APPROVE", contentDigest: CONTENT_DIGEST });
		// The level reads the decision back rather than assuming it: once ready, there is nothing
		// left to approve.
		await waitFor(() =>
			expect(screen.queryByRole("button", { name: "Approve for delivery" })).toBeNull(),
		);
		// Every count of it moved: the overview's "awaiting approval", a review's tally, an
		// result's feedback — in this workspace only.
		const invalidated = (keys: typeof ours) =>
			keys.map((key) => queryClient.getQueryState(key)?.isInvalidated);
		expect(invalidated(ours)).toStrictEqual([true, true, true]);
		expect(invalidated(theirs)).toStrictEqual([false, false, false]);
	});

	it("sends the selected rejection category and reviewer context", async () => {
		let requestBody: unknown;
		server.use(
			...feedbackCountHandlers(),
			http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", () =>
				HttpResponse.json(proposalWire("AWAITING_APPROVAL")),
			),
			http.post(
				"*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId/decisions",
				async ({ request }) => {
					requestBody = await request.json();
					return HttpResponse.json(decisionWire(FEEDBACK_ID, "REJECT"), { status: 201 });
				},
			),
			...reviewHandlers(),
		);

		renderRouteAtWithRouter(FEEDBACK_LEVEL);
		await screen.findByRole(
			"heading",
			{ name: /^Please keep the cache scoped/u },
			ROUTE_RENDER_WAIT,
		);
		await userEvent.click(screen.getByRole("button", { name: "Reject feedback" }));
		const dialog = await screen.findByRole("dialog", { name: "Reject this feedback" });
		await userEvent.click(within(dialog).getByText("Missing important context"));
		await userEvent.type(
			within(dialog).getByLabelText("Note"),
			"The fallback path was not considered.",
		);
		await userEvent.click(within(dialog).getByRole("button", { name: "Reject feedback" }));

		await screen.findByText("Feedback rejected");
		expect(requestBody).toStrictEqual({
			action: "REJECT",
			reasonCode: "MISSING_CONTEXT",
			note: "The fallback path was not considered.",
			contentDigest: CONTENT_DIGEST,
		});
	});

	it("reloads the winning decision after a concurrent approval conflict", async () => {
		let state: ProposalState = "AWAITING_APPROVAL";
		let detailReads = 0;
		server.use(
			...feedbackCountHandlers(),
			http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", () => {
				detailReads += 1;
				return HttpResponse.json(proposalWire(state));
			}),
			http.post(
				"*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId/decisions",
				() => {
					state = "READY";
					return HttpResponse.json(
						{ status: 409, title: "This review was already decided" },
						{ status: 409 },
					);
				},
			),
			...reviewHandlers(),
		);

		renderRouteAtWithRouter(FEEDBACK_LEVEL);
		await screen.findByRole("button", { name: "Approve for delivery" });
		fireEvent.click(screen.getByRole("button", { name: "Approve for delivery" }));

		await waitFor(() =>
			expect(screen.queryByRole("button", { name: "Approve for delivery" })).toBeNull(),
		);
		expect(detailReads).toBeGreaterThan(1);
	});

	/**
	 * The approval queue is worked oldest first, one level deep: a decision swaps the level for the
	 * next proposal — the one it showed as next when the reader decided, or else the oldest still
	 * waiting — over the same history entry, and closes it once nothing else waits. Only a level
	 * opened as the queue walks it.
	 */
	describe("the approval queue", () => {
		const queue = [...awaitingApprovalFeedback].reverse();
		const [oldest, second, newest] = queue;
		assert(oldest && second && newest, "The fixtures hold three pieces of feedback to approve");

		const DECISIONS =
			"*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId/decisions";

		/**
		 * Serves the queue as the server would: a decided proposal leaves it. A `refusal` answers every
		 * decision with a conflict — one that says someone else decided first, and so has taken the
		 * proposal out of the queue, or one that leaves it waiting.
		 */
		function serveQueue(
			approved: string[],
			refusal?: { title: string; decidedElsewhere: boolean },
		) {
			const rows = awaitingApprovalFeedback.map((item) =>
				approved.includes(item.id) ? { ...item, state: "READY" as const, stopReason: null } : item,
			);
			server.use(
				...feedbackCountHandlers(),
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId",
					({ params }) => {
						const id = String(params.feedbackId);
						return HttpResponse.json(
							proposalWire(approved.includes(id) ? "READY" : "AWAITING_APPROVAL", id),
						);
					},
				),
				http.post(DECISIONS, ({ params }) => {
					const conflict = () =>
						HttpResponse.json({ status: 409, title: refusal?.title }, { status: 409 });
					if (refusal?.decidedElsewhere === false) {
						return conflict();
					}
					const id = String(params.feedbackId);
					approved.push(id);
					const index = rows.findIndex((item) => item.id === id);
					const row = rows[index];
					if (row) {
						rows[index] = { ...row, state: "READY", stopReason: null };
					}
					return refusal
						? conflict()
						: HttpResponse.json(decisionWire(id, "APPROVE"), { status: 201 });
				}),
				...reviewHandlers({ feedback: rows }),
			);
		}

		it("approves and swaps the level for the next proposal", async () => {
			const approved: string[] = [];
			serveQueue(approved);
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews?detail=feedback:${oldest.id}&queue=approvals`,
			);

			await queueSteps("1 of 3");
			const { length: entries } = router.history;
			await userEvent.click(screen.getByRole("button", { name: "Approve and open next" }));

			await waitFor(
				() =>
					expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
						`feedback:${second.id}`,
					]),
				ROUTE_RENDER_WAIT,
			);
			expect(approved).toStrictEqual([oldest.id]);
			// In place of the level just decided, not over it, and not as another step back.
			expect(router.history).toHaveLength(entries);
			await waitFor(() => expect(screen.getAllByRole("dialog")).toHaveLength(1));
		});

		/**
		 * Stepping past proposals leaves them waiting: deciding the last one goes back to the oldest
		 * skipped, not out of the level.
		 */
		it("returns to the proposals skipped past after deciding the last", async () => {
			const approved: string[] = [];
			serveQueue(approved);
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews?detail=feedback:${oldest.id}&queue=approvals`,
			);

			const atOldest = await queueSteps("1 of 3");
			await userEvent.click(atOldest.getByRole("link", { name: "Next" }));
			const atSecond = await queueSteps("2 of 3");
			await userEvent.click(atSecond.getByRole("link", { name: "Next" }));
			await queueSteps("3 of 3");
			await userEvent.click(screen.getByRole("button", { name: "Approve and open next" }));

			await waitFor(
				() =>
					expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
						`feedback:${oldest.id}`,
					]),
				ROUTE_RENDER_WAIT,
			);
			expect(approved).toStrictEqual([newest.id]);
			expect(router.state.location.search).toMatchObject({ queue: "approvals" });
			await queueSteps("1 of 2");
		});

		it("approves the only proposal left and closes the level", async () => {
			const approved = [oldest.id, second.id];
			serveQueue(approved);
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews?detail=feedback:${newest.id}&queue=approvals`,
			);

			await userEvent.click(
				await screen.findByRole("button", { name: "Approve and close" }, ROUTE_RENDER_WAIT),
			);

			await waitFor(
				() => expect(router.state.location.search).not.toHaveProperty("detail"),
				ROUTE_RENDER_WAIT,
			);
			expect(approved).toStrictEqual([oldest.id, second.id, newest.id]);
			// The queue closes with its level, so the next feedback opened from a list is not one.
			expect(router.state.location.search).not.toHaveProperty("queue");
		});

		/**
		 * Someone else deciding first is a refusal that still moved the queue on: the level follows it
		 * rather than leaving the admin on a proposal with nothing left to decide.
		 */
		it("moves on after a conflict from a decision taken elsewhere", async () => {
			const approved: string[] = [];
			serveQueue(approved, {
				title: "This proposal has already been decided",
				decidedElsewhere: true,
			});
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews?detail=feedback:${oldest.id}&queue=approvals`,
			);

			await queueSteps("1 of 3");
			await userEvent.click(screen.getByRole("button", { name: "Approve and open next" }));

			await screen.findByText("This proposal has already been decided");
			await waitFor(
				() =>
					expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
						`feedback:${second.id}`,
					]),
				ROUTE_RENDER_WAIT,
			);
		});

		/** A conflict that leaves the proposal waiting — sending paused — keeps the admin on it. */
		it("stays on a proposal a conflict left waiting", async () => {
			serveQueue([], {
				title: "Sending is paused for this workspace, so approving would not send anything",
				decidedElsewhere: false,
			});
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews?detail=feedback:${oldest.id}&queue=approvals`,
			);

			await queueSteps("1 of 3");
			await userEvent.click(screen.getByRole("button", { name: "Approve and open next" }));

			await screen.findByText(/Sending is paused/u);
			// Enabled again once the decision settles, which is when a move on would have run.
			await waitFor(() =>
				expect(
					screen.getByRole("button", { name: "Approve and open next" }).hasAttribute("disabled"),
				).toBe(false),
			);
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				`feedback:${oldest.id}`,
			]);
			await queueSteps("1 of 3");
		});

		/**
		 * The admin is not held to a decision in flight: dismissing the level while it is on its way
		 * leaves the level dismissed once it lands, and the page under it still opens the next.
		 */
		it("stays dismissed when dismissed while a decision is in flight", async () => {
			const approved: string[] = [];
			serveQueue(approved);
			const decision = deferred();
			server.use(
				// Held, then answered by the queue's own handler.
				http.post(DECISIONS, async () => {
					await decision.promise;
				}),
			);
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews/feedback?detail=feedback:${oldest.id}&queue=approvals`,
			);

			await queueSteps("1 of 3");
			await userEvent.click(screen.getByRole("button", { name: "Approve and open next" }));
			fireEvent.keyDown(document.body, { key: "Escape" });
			await waitFor(
				() => expect(router.state.location.search).not.toHaveProperty("detail"),
				ROUTE_RENDER_WAIT,
			);
			decision.resolve();

			await waitFor(() => expect(approved).toStrictEqual([oldest.id]));
			await sleep(100);
			expect(router.state.location.search).not.toHaveProperty("detail");
			await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

			const row = await waitFor(() => {
				const found = screen
					.getAllByRole("link")
					.find((link) => String(levelsOpenedBy(link)) === `feedback:${second.id}`);
				assert(found, "The list shows the feedback as a row");
				return found;
			}, ROUTE_RENDER_WAIT);
			await userEvent.click(row);
			await screen.findByRole(
				"dialog",
				{ name: /^Please keep the cache scoped/u },
				ROUTE_RENDER_WAIT,
			);
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
				`feedback:${second.id}`,
			]);
		});

		/** A record opened from the queue while a decision is in flight is still there once it lands. */
		it("keeps a record opened from the queue while a decision is in flight", async () => {
			// Another record, opened while the decision on the oldest in the queue is in flight.
			const [source] = feedbackDetail(oldest.id).basis;
			assert(source, "The feedback is based on a result");
			const { summary } = source.result;
			assert(hasText(summary), "The result has a summary to name its level");
			const approved: string[] = [];
			serveQueue(approved);
			const decision = deferred();
			server.use(
				http.post(DECISIONS, async () => {
					await decision.promise;
				}),
			);
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews?detail=feedback:${oldest.id}&queue=approvals`,
			);

			await queueSteps("1 of 3");
			await userEvent.click(screen.getByRole("button", { name: "Approve and open next" }));
			// A sheet links to no other record: the reader opens the result from its address.
			const opened = [`result:${source.resultId}`];
			await router.navigate({
				to: ".",
				search: (previous: Record<string, unknown>) => ({ ...previous, detail: opened }),
			});
			await waitFor(
				() => expect(stackInSearch(router.state.location.search.detail)).toStrictEqual(opened),
				ROUTE_RENDER_WAIT,
			);
			decision.resolve();

			await screen.findByText("Feedback approved. Delivery is being checked.");
			// Once decided, the level reads it back as ready and drops its footer; the move on would
			// run right after.
			await waitFor(() =>
				expect(
					screen.queryByRole("button", { name: "Approve and open next", hidden: true }),
				).toBeNull(),
			);
			await sleep(100);
			expect(stackInSearch(router.state.location.search.detail)).toStrictEqual(opened);
			screen.getByRole("dialog", { name: summary });
		});

		/** A decision taken before the queue has been read still moves on once it has. */
		it("moves on from a decision taken before the queue was read", async () => {
			const approved: string[] = [];
			serveQueue(approved);
			const queueRead = deferred();
			server.use(
				// Held, then answered by the queue's own handler: a resolver that returns nothing falls
				// through to the next one that matches.
				http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", async () => {
					await queueRead.promise;
				}),
			);
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews?detail=feedback:${oldest.id}&queue=approvals`,
			);

			await userEvent.click(
				await screen.findByRole("button", { name: "Approve for delivery" }, ROUTE_RENDER_WAIT),
			);
			await waitFor(() => expect(approved).toStrictEqual([oldest.id]));
			queueRead.resolve();

			await waitFor(
				() =>
					expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
						`feedback:${second.id}`,
					]),
				ROUTE_RENDER_WAIT,
			);
			expect(router.state.location.search).toMatchObject({ queue: "approvals" });
		});

		/**
		 * The last of the page the queue was read in is not the last in the queue: a decision there
		 * moves on to the oldest still waiting once the queue is read again, not out of the level.
		 */
		it("moves past the page the queue was read in", async () => {
			const approved: string[] = [];
			serveQueue(approved);
			server.use(
				http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback", () => {
					const waiting = queue.filter((item) => !approved.includes(item.id));
					return HttpResponse.json({
						content: waiting,
						// One more waits beyond this page.
						page: { number: 0, size: 100, totalElements: waiting.length + 1, totalPages: 2 },
					});
				}),
			);
			const { router } = renderRouteAtWithRouter(
				`/w/acme/admin/practices/reviews?detail=feedback:${newest.id}&queue=approvals`,
			);

			await queueSteps("3 of 4");
			await userEvent.click(screen.getByRole("button", { name: "Approve and open next" }));

			await waitFor(
				() =>
					expect(stackInSearch(router.state.location.search.detail)).toStrictEqual([
						`feedback:${oldest.id}`,
					]),
				ROUTE_RENDER_WAIT,
			);
			expect(approved).toStrictEqual([newest.id]);
			expect(router.state.location.search).toMatchObject({ queue: "approvals" });
		});

		it("walks the queue when it is opened from Needs you", async () => {
			serveQueue([]);
			const { router } = renderRouteAtWithRouter("/w/acme/admin/practices/reviews");

			await userEvent.click(
				await screen.findByRole("link", { name: "Review 3 pieces of feedback" }, ROUTE_RENDER_WAIT),
			);

			// Within the level: the overview under it has figures of its own that read "1 of 3".
			const level = await screen.findByRole("dialog", {}, ROUTE_RENDER_WAIT);
			await within(level).findByText("1 of 3", undefined, ROUTE_RENDER_WAIT);
			within(level).getByRole("button", { name: "Approve and open next" });
			expect(router.state.location.search).toMatchObject({
				detail: `feedback:${oldest.id}`,
				queue: "approvals",
			});
		});

		/** The same feedback opened from a row of the Feedback list is that one piece, alone. */
		it("does not walk the queue when the feedback is opened from the Feedback list", async () => {
			serveQueue([]);
			const { router } = renderRouteAtWithRouter("/w/acme/admin/practices/reviews/feedback");

			const row = await waitFor(() => {
				const found = screen
					.getAllByRole("link")
					.find((link) => String(levelsOpenedBy(link)) === `feedback:${second.id}`);
				assert(found, "The list shows the feedback as a row");
				return found;
			}, ROUTE_RENDER_WAIT);
			await userEvent.click(row);

			const level = await screen.findByRole("dialog", {}, ROUTE_RENDER_WAIT);
			await within(level).findByRole("button", { name: "Approve for delivery" }, ROUTE_RENDER_WAIT);
			expect(router.state.location.search).not.toHaveProperty("queue");
			expect(within(level).queryByText(/ of 3$/u)).toBeNull();
			expect(within(level).queryByRole("button", { name: "Approve and open next" })).toBeNull();
		});
	});
});
