import { setTimeout as sleep } from "node:timers/promises";

import { focusManager } from "@tanstack/react-query";
import { screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { ReviewQueue as ReviewQueueAnswer } from "@/api/types.gen";
import {
	busyReviewQueue,
	idleReviewQueue,
	practiceReviewOverview,
} from "@/components/admin/practice-reviews/fixtures";
import type * as ReviewQueueModule from "@/components/admin/practice-reviews/review-queue";
import { reviewHandlers } from "@/components/admin/practice-reviews/story-mock-server";
import { server } from "@/mocks/server";
import { ObserverStub } from "@/test/observers";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

// A short poll, so the test watches several re-reads in real time instead of faking the clock
// under the whole router.
vi.mock("@/components/admin/practice-reviews/review-queue", async (importOriginal) => ({
	...(await importOriginal<typeof ReviewQueueModule>()),
	QUEUE_POLL_MS: 100,
}));

vi.setConfig({ testTimeout: 30_000 });

// jsdom needs an IntersectionObserver stub for paged lists.
beforeEach(() => {
	vi.stubGlobal("IntersectionObserver", ObserverStub);
});

const OVERVIEW = "/w/acme/admin/practices/reviews";

/** The overview with nothing queued or running, so only the queue itself can keep the poll going. */
const quietOverview = {
	...practiceReviewOverview,
	reviews: { ...practiceReviewOverview.reviews, queued: 0, running: 0 },
};

/** Answers each read of the queue with the next answer, the last one for good, and counts the reads. */
function serveQueue(...answers: ReviewQueueAnswer[]) {
	let reads = 0;
	server.use(
		http.get("*/workspaces/:workspaceSlug/practices/reviews/overview", () =>
			HttpResponse.json(quietOverview),
		),
		http.get("*/workspaces/:workspaceSlug/practices/reviews/queue", () => {
			const answer = answers[Math.min(reads, answers.length - 1)];
			reads += 1;
			return HttpResponse.json(answer);
		}),
		...reviewHandlers(),
	);
	return { reads: () => reads };
}

/** Long enough for several polls at the test's interval. */
const settle = async () => sleep(600);

afterEach(() => {
	focusManager.setFocused(undefined);
});

describe("practice review queue on the overview", () => {
	it("re-reads the queue while reviews wait and stops once it is empty", async () => {
		const queue = serveQueue(busyReviewQueue, busyReviewQueue, idleReviewQueue);
		renderRouteAtWithRouter(OVERVIEW);

		await screen.findByText("Nothing waits or runs.", undefined, ROUTE_RENDER_WAIT);
		const readsWhenEmpty = queue.reads();
		expect(readsWhenEmpty).toBe(3);

		await settle();
		expect(queue.reads()).toBe(readsWhenEmpty);
	});

	it("does not poll an empty queue", async () => {
		const queue = serveQueue(idleReviewQueue);
		renderRouteAtWithRouter(OVERVIEW);

		await screen.findByText("Nothing waits or runs.", undefined, ROUTE_RENDER_WAIT);
		await settle();
		expect(queue.reads()).toBe(1);
	});

	it("pauses while the browser tab is hidden and resumes when it is back", async () => {
		const queue = serveQueue(busyReviewQueue);
		renderRouteAtWithRouter(OVERVIEW);

		await screen.findByRole("link", { name: /^See all/u }, ROUTE_RENDER_WAIT);
		await waitFor(() => expect(queue.reads()).toBeGreaterThan(1), ROUTE_RENDER_WAIT);

		focusManager.setFocused(false);
		// A read already in flight may still land.
		await settle();
		const readsWhileHidden = queue.reads();
		await settle();
		expect(queue.reads()).toBe(readsWhileHidden);

		focusManager.setFocused(true);
		await waitFor(() => expect(queue.reads()).toBeGreaterThan(readsWhileHidden), ROUTE_RENDER_WAIT);
	});
});
