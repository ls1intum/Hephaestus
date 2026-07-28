import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { delay, HttpResponse, http } from "msw";
import { userEvent } from "storybook/test";
import { describe, expect, it, vi } from "vitest";
import { listAgentsQueryKey } from "@/api/@tanstack/react-query.gen";
import type { AgentBinding } from "@/api/types.gen";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt } from "@/test/router-harness";

// Mounting the real route pulls in the whole admin layout and its lazy modules.
vi.setConfig({ testTimeout: 20_000 });

const MODELS = [
	{
		id: 20,
		scope: "SHARED",
		displayName: "GPT Test",
		connectionDisplayName: "Shared OpenAI",
		supportsReasoning: false,
		pricingMode: "NO_CHARGE",
	},
	{
		id: 21,
		scope: "SHARED",
		displayName: "GPT Other",
		connectionDisplayName: "Shared OpenAI",
		supportsReasoning: false,
		pricingMode: "NO_CHARGE",
	},
];

const WORKSPACE = {
	id: 1,
	slug: "acme",
	displayName: "Acme",
	practicesEnabled: true,
	mentorEnabled: true,
};

const WORKSPACE_LIST_ITEM = { ...WORKSPACE, workspaceSlug: "acme", accountLogin: "acme" };

function binding(purpose: AgentBinding["purpose"], instanceModelId: number): AgentBinding {
	return {
		purpose,
		instanceModelId,
		enabled: true,
		ready: true,
		timeoutSeconds: 600,
		maxConcurrentJobs: 3,
		allowInternet: false,
	};
}

function mockModelsRoute(bindings: () => AgentBinding[]) {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/agents", () => HttpResponse.json(bindings())),
		http.get("*/workspaces/:workspaceSlug/llm/available-models", () => HttpResponse.json(MODELS)),
		http.get("*/workspaces/:workspaceSlug/llm/settings", () =>
			HttpResponse.json({ ownProviderAllowed: false }),
		),
		http.get("*/workspaces/:workspaceSlug/llm/connections", () => HttpResponse.json([])),
		http.get("*/workspaces/:workspaceSlug/llm/usage", () =>
			HttpResponse.json({
				month: "2026-07",
				instanceTotalCostUsd: 0,
				ownProviderTotalCostUsd: 0,
				instanceBudgetVerdict: "WITHIN",
				ownProviderBudgetVerdict: "WITHIN",
				instancePaused: false,
				ownProviderPaused: false,
				unpricedEventCount: 0,
				byJobType: [],
				byDay: [],
			}),
		),
		http.get("*/workspaces/:workspaceSlug", () => HttpResponse.json(WORKSPACE)),
		// The shell's switcher navigates off the workspace when this list does not contain the slug.
		http.get("*/workspaces", () => HttpResponse.json([WORKSPACE_LIST_ITEM])),
	);
}

/** The refetch has to cost a round trip: answered in a microtask it lands before the remount. */
function slowBindingsRefetch(bindings: () => AgentBinding[]) {
	return http.get("*/workspaces/:workspaceSlug/agents", async () => {
		await delay(50);
		return HttpResponse.json(bindings());
	});
}

async function renderModelsRoute(bindings: () => AgentBinding[]) {
	mockModelsRoute(bindings);
	const queryClient = renderRouteAt("/w/acme/admin/models");
	await screen.findByRole("heading", { name: "AI models" }, ROUTE_RENDER_WAIT);
	await screen.findByLabelText("Practice feedback runs on", undefined, ROUTE_RENDER_WAIT);
	return queryClient;
}

function card(purposeLabel: string): HTMLElement {
	const field = screen.getByLabelText(purposeLabel);
	const cardElement = field.closest("[data-slot='card']");
	if (!(cardElement instanceof HTMLElement)) throw new Error(`No card for ${purposeLabel}`);
	return cardElement;
}

const saveButton = (purposeLabel: string) =>
	within(card(purposeLabel)).getByRole("button", { name: "Save" }) as HTMLButtonElement;

describe("workspace AI models route", () => {
	it("keeps each purpose's card pending independently when two saves run at once", async () => {
		let releaseSlowSave: (() => void) | undefined;
		const slowSave = new Promise<void>((resolve) => {
			releaseSlowSave = resolve;
		});
		let detectionSaves = 0;
		server.use(
			http.put("*/workspaces/:workspaceSlug/agents/PRACTICE_DETECTION", async () => {
				detectionSaves += 1;
				await slowSave;
				return HttpResponse.json(binding("PRACTICE_DETECTION", 20));
			}),
			http.put("*/workspaces/:workspaceSlug/agents/MENTOR", () =>
				HttpResponse.json(binding("MENTOR", 20)),
			),
		);

		await renderModelsRoute(() => [binding("PRACTICE_DETECTION", 20), binding("MENTOR", 20)]);

		fireEvent.click(saveButton("Practice feedback runs on"));
		await waitFor(() => expect(detectionSaves).toBe(1));
		fireEvent.click(saveButton("Mentor runs on"));

		await waitFor(() => expect(saveButton("Mentor runs on").disabled).toBe(false));
		expect(saveButton("Practice feedback runs on").disabled).toBe(true);

		releaseSlowSave?.();
		await waitFor(() => expect(saveButton("Practice feedback runs on").disabled).toBe(false));
		expect(detectionSaves).toBe(1);
	});

	it("keeps unsaved run limits when another admin repoints the same purpose", async () => {
		let bindings = [binding("PRACTICE_DETECTION", 20)];
		const queryClient = await renderModelsRoute(() => bindings);

		const detection = card("Practice feedback runs on");
		fireEvent.click(within(detection).getByRole("button", { name: "Advanced" }));
		const timeout = within(detection).getByLabelText("Timeout (seconds)") as HTMLInputElement;
		fireEvent.change(timeout, { target: { value: "900" } });
		expect(timeout.value).toBe("900");

		// The badge is the signal the other admin's write landed; the picker deliberately does not move.
		bindings = [{ ...binding("PRACTICE_DETECTION", 21), ready: false }];
		await queryClient.invalidateQueries({
			queryKey: listAgentsQueryKey({ path: { workspaceSlug: "acme" } }),
		});
		await waitFor(() => within(card("Practice feedback runs on")).getByText("Not ready"));

		expect(
			(
				within(card("Practice feedback runs on")).getByLabelText(
					"Timeout (seconds)",
				) as HTMLInputElement
			).value,
		).toBe("900");
	});

	it("reads back what was just saved, not what the card was showing before", async () => {
		let bindings = [binding("PRACTICE_DETECTION", 20)];
		await renderModelsRoute(() => bindings);
		server.use(
			slowBindingsRefetch(() => bindings),
			http.put("*/workspaces/:workspaceSlug/agents/PRACTICE_DETECTION", async ({ request }) => {
				const body = (await request.json()) as { instanceModelId: number; timeoutSeconds: number };
				const saved: AgentBinding = {
					...binding("PRACTICE_DETECTION", body.instanceModelId),
					timeoutSeconds: body.timeoutSeconds,
					ready: false,
				};
				bindings = [saved];
				return HttpResponse.json(saved);
			}),
		);

		const detection = card("Practice feedback runs on");
		fireEvent.click(within(detection).getByRole("button", { name: "Advanced" }));
		fireEvent.change(within(detection).getByLabelText("Timeout (seconds)"), {
			target: { value: "900" },
		});
		// `userEvent`, not `fireEvent`: the listbox commits on the pointer sequence, not on a bare click.
		await userEvent.click(within(detection).getByRole("combobox"));
		await userEvent.click(await screen.findByRole("option", { name: /GPT Other/ }));

		fireEvent.click(saveButton("Practice feedback runs on"));
		await waitFor(() => within(card("Practice feedback runs on")).getByText("Not ready"));

		const saved = card("Practice feedback runs on");
		expect(within(saved).getByRole("combobox").textContent).toContain("GPT Other");
		// Reseeding remounts the card, closing the disclosure with it.
		fireEvent.click(within(saved).getByRole("button", { name: "Advanced" }));
		expect((within(saved).getByLabelText("Timeout (seconds)") as HTMLInputElement).value).toBe(
			"900",
		);
	});

	it("reseeds the card to its defaults when the purpose is turned off", async () => {
		let bindings = [{ ...binding("PRACTICE_DETECTION", 20), timeoutSeconds: 900 }];
		await renderModelsRoute(() => bindings);
		server.use(
			slowBindingsRefetch(() => bindings),
			http.delete("*/workspaces/:workspaceSlug/agents/PRACTICE_DETECTION", () => {
				bindings = [];
				return new HttpResponse(null, { status: 204 });
			}),
		);

		const detection = card("Practice feedback runs on");
		fireEvent.click(within(detection).getByRole("button", { name: "Advanced" }));
		expect((within(detection).getByLabelText("Timeout (seconds)") as HTMLInputElement).value).toBe(
			"900",
		);

		fireEvent.click(within(detection).getByRole("button", { name: "Turn off" }));

		await waitFor(() =>
			expect(
				within(card("Practice feedback runs on")).queryByRole("button", { name: "Turn off" }),
			).toBeNull(),
		);
		const reset = card("Practice feedback runs on");
		fireEvent.click(within(reset).getByRole("button", { name: "Advanced" }));
		expect((within(reset).getByLabelText("Timeout (seconds)") as HTMLInputElement).value).toBe(
			"600",
		);
	});
});
