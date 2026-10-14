import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ProfileReview } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { detailFeedback, detailReview } from "@/stories/practice-detail-story-mock-data";
import { daysBefore } from "@/stories/story-clock";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

// Mounting the real route pulls in the whole app shell and its lazy modules.
vi.setConfig({ testTimeout: 20_000 });

const REVIEWS_PATH = "*/workspaces/:workspaceSlug/practices/items/:itemId/reviews";
const RESPONSE_PATH = "*/workspaces/:workspaceSlug/practices/feedback/:feedbackId/response";

/** The linked review, whose feedback the reader already rated helpful on their practice page. */
const ratedReview: ProfileReview = {
	...detailReview,
	feedback: [
		{
			...detailFeedback,
			response: {
				usefulness: "HELPFUL",
				resolution: null,
				comment: null,
				createdAt: daysBefore(1),
			},
		},
	],
};

let reads: { workspaceSlug: string; itemId: string }[] = [];

beforeEach(() => {
	reads = [];
	server.use(
		http.get(REVIEWS_PATH, ({ params }) => {
			reads.push({ workspaceSlug: String(params.workspaceSlug), itemId: String(params.itemId) });
			return HttpResponse.json({
				content: [ratedReview],
				page: { number: 0, size: 10, totalElements: 1, totalPages: 1 },
			});
		}),
	);
});

/** The page is the address comments on the work link to; its states are stories, its wire is here. */
describe("the page a comment on the work links to", () => {
	it("reads the reader's reviews of exactly the linked item", async () => {
		renderRouteAt("/w/acme/feedback/1902");

		await screen.findByRole("heading", { name: "Your feedback on this work" }, ROUTE_RENDER_WAIT);
		await screen.findByRole("list", { name: "Reviews" }, ROUTE_RENDER_WAIT);
		expect(reads[0]).toStrictEqual({ workspaceSlug: "acme", itemId: "1902" });
	});

	it("writes a disagreement with its reason, keeps the rating, and reads the reviews again", async () => {
		const written: unknown[] = [];
		server.use(
			http.put(RESPONSE_PATH, async ({ request }) => {
				const body = await request.json();
				written.push(body);
				return HttpResponse.json(body);
			}),
		);
		renderRouteAt("/w/acme/feedback/1902");

		await userEvent.click(
			await screen.findByRole("button", { name: "Disagree" }, ROUTE_RENDER_WAIT),
		);
		await userEvent.type(
			screen.getByRole("textbox", { name: "Why do you disagree?" }),
			"The loader never ran on this path.",
		);
		await userEvent.click(screen.getByRole("button", { name: "Send" }));

		await waitFor(() => {
			expect(written).toHaveLength(1);
		});
		expect(written[0]).toStrictEqual({
			usefulness: "HELPFUL",
			resolution: "DISAGREE",
			comment: "The loader never ran on this path.",
		});
		await waitFor(() => {
			expect(reads.length).toBeGreaterThan(1);
		});
	});

	it("clears the response through the endpoint's delete", async () => {
		const cleared: string[] = [];
		server.use(
			http.delete(RESPONSE_PATH, ({ params }) => {
				cleared.push(String(params.feedbackId));
				return HttpResponse.json({});
			}),
		);
		renderRouteAt("/w/acme/feedback/1902");

		await userEvent.click(
			await screen.findByRole("button", { name: "Clear response" }, ROUTE_RENDER_WAIT),
		);

		await waitFor(() => {
			expect(cleared).toStrictEqual([detailFeedback.id]);
		});
	});

	it("does not ask for anything when the address names no item", async () => {
		renderRouteAt("/w/acme/feedback/not-a-number");

		await screen.findByText(/not found/iu, undefined, ROUTE_RENDER_WAIT);
		expect(reads).toHaveLength(0);
	});
});
