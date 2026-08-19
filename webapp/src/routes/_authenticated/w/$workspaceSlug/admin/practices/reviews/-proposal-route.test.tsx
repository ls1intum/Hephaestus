import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { reviewFeedbackDetail } from "@/components/admin/practice-reviews/story-mock-data";
import { reviewHandlers } from "@/components/admin/practice-reviews/story-mock-server";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

vi.setConfig({ testTimeout: 20_000 });

const FEEDBACK_ID = reviewFeedbackDetail.id;

function proposalWire(deliveryState: "AWAITING_APPROVAL" | "PREPARED") {
	return {
		...reviewFeedbackDetail,
		deliveryState,
		body: "Please keep the cache scoped to one workspace so membership changes cannot leak.",
		createdAt: reviewFeedbackDetail.createdAt.toISOString(),
		observations: reviewFeedbackDetail.observations.map((observation) => ({
			...observation,
			observedAt: observation.observedAt.toISOString(),
		})),
	};
}

describe("feedback proposal route", () => {
	it("shows the exact proposal and sends an approval through the generated operation", async () => {
		let decided = false;
		let requestBody: unknown;
		server.use(
			http.get("*/workspaces/:workspaceSlug/members/me", () =>
				HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
			),
			http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", () =>
				HttpResponse.json(proposalWire(decided ? "PREPARED" : "AWAITING_APPROVAL")),
			),
			http.put(
				"*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId/approval",
				async ({ request }) => {
					requestBody = await request.json();
					decided = true;
					return HttpResponse.json({ feedbackId: FEEDBACK_ID, decision: "APPROVED" });
				},
			),
			...reviewHandlers(),
		);

		renderRouteAtWithRouter(`/w/acme/admin/practices/reviews/delivery/${FEEDBACK_ID}`);

		await screen.findByRole("heading", { name: /Review feedback for/ }, ROUTE_RENDER_WAIT);
		expect(screen.getByText(/Please keep the cache scoped/)).not.toBeNull();
		expect(
			screen.getByRole("link", { name: "Inspect observation and source evidence" }),
		).not.toBeNull();

		await userEvent.click(screen.getByRole("button", { name: "Approve and send" }));
		await screen.findByRole("heading", { name: /Feedback for/ }, ROUTE_RENDER_WAIT);
		expect(requestBody).toEqual({ decision: "APPROVED" });
	});

	it("sends the selected structured rejection reason", async () => {
		let requestBody: unknown;
		server.use(
			http.get("*/workspaces/:workspaceSlug/members/me", () =>
				HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
			),
			http.get("*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId", () =>
				HttpResponse.json(proposalWire("AWAITING_APPROVAL")),
			),
			http.put(
				"*/workspaces/:workspaceSlug/practices/reviews/feedback/:feedbackId/approval",
				async ({ request }) => {
					requestBody = await request.json();
					return HttpResponse.json({ feedbackId: FEEDBACK_ID, decision: "REJECTED" });
				},
			),
			...reviewHandlers(),
		);

		renderRouteAtWithRouter(`/w/acme/admin/practices/reviews/delivery/${FEEDBACK_ID}`);
		await screen.findByRole("heading", { name: /Review feedback for/ }, ROUTE_RENDER_WAIT);
		await userEvent.click(screen.getByRole("button", { name: "Reject feedback" }));
		const dialog = await screen.findByRole("dialog");
		await userEvent.click(within(dialog).getByText("Missing important context"));
		await userEvent.click(within(dialog).getByRole("button", { name: "Reject feedback" }));

		expect(requestBody).toEqual({ decision: "REJECTED", rejectionReason: "MISSING_CONTEXT" });
	});
});
