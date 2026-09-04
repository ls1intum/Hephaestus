import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import type { FirstLoginConsent } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAtWithRouter } from "@/test/router-harness";

const notice = {
	completed: false,
	noticeText: "Hephaestus analyzes connected activity.\n\nResearch participation is optional.",
	noticeVersion: "2026-08-30",
	participateInResearch: false,
};

function showNotice(onComplete: (body: FirstLoginConsent) => void) {
	server.use(
		http.get("*/user/consent", () => HttpResponse.json(notice)),
		http.put<never, FirstLoginConsent>("*/user/consent", async ({ request }) => {
			onComplete(await request.json());
			return HttpResponse.json({ ...notice, completed: true });
		}),
	);
}

describe("first-login consent route", () => {
	it("records refusal when the optional choice remains unchecked", async () => {
		let submitted: FirstLoginConsent | undefined;
		showNotice((body) => {
			submitted = body;
		});
		renderRouteAtWithRouter("/consent");

		await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		const continueButton = screen.getByRole("button", { name: "Continue" });
		expect(continueButton.hasAttribute("disabled")).toBe(true);

		await userEvent.click(screen.getByRole("checkbox", { name: /terms of use/i }));
		fireEvent.click(continueButton);

		await waitFor(() =>
			expect(submitted).toStrictEqual({
				noticeVersion: notice.noticeVersion,
				participateInResearch: false,
				termsAccepted: true,
			}),
		);
	});

	it("records an affirmative research choice", async () => {
		let submitted: FirstLoginConsent | undefined;
		showNotice((body) => {
			submitted = body;
		});
		renderRouteAtWithRouter("/consent");

		await screen.findByRole("dialog", undefined, ROUTE_RENDER_WAIT);
		await userEvent.click(screen.getByRole("checkbox", { name: /terms of use/i }));
		await userEvent.click(screen.getByRole("checkbox", { name: /research/i }));
		fireEvent.click(screen.getByRole("button", { name: "Continue" }));

		await waitFor(() =>
			expect(submitted).toStrictEqual({
				noticeVersion: notice.noticeVersion,
				participateInResearch: true,
				termsAccepted: true,
			}),
		);
	});
});
