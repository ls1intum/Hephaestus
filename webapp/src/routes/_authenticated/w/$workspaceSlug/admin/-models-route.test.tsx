import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { delay, HttpResponse, http } from "msw";
// `@testing-library/user-event` re-exported. Not installed under its own name (see `package.json`),
// and this is the only import path in the repo that reaches it. `fireEvent` is the house idiom and
// is used for everything else here; see the combobox call below for why one case cannot use it.
import { userEvent } from "storybook/test";
import { describe, expect, it, vi } from "vitest";
import { listAgentsQueryKey } from "@/api/@tanstack/react-query.gen";
import type { AgentBinding } from "@/api/types.gen";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { server } from "@/mocks/server";
import { routeTree } from "@/routeTree.gen";

// Mounting the real route pulls in the whole admin layout and its lazy modules.
vi.setConfig({ testTimeout: 20_000 });

/**
 * The first mount in a file pays the lazy transform of the whole admin layout and its route
 * modules — seconds under a loaded box, well past `findBy`'s own 1s default, which is separate
 * from the `testTimeout` above and is what actually decides these.
 */
const TRANSFORM_WAIT = { timeout: 10_000 };

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

/** The switcher matches the URL slug against this list; a mismatch navigates the page away. */
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

/**
 * Everything the models route reads, minus the writes each test installs itself. `bindings` is a
 * getter so a test can change what the next refetch answers — that is how "another admin repointed
 * this purpose" is staged.
 */
function mockModelsRoute(bindings: () => AgentBinding[]) {
	server.use(
		http.get("*/workspaces/:workspaceSlug/members/me", () =>
			HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
		),
		http.get("*/workspaces/:workspaceSlug/agents", () => HttpResponse.json(bindings())),
		http.get("*/workspaces/:workspaceSlug/llm/available-models", () => HttpResponse.json(MODELS)),
		http.get("*/workspaces/:workspaceSlug/llm/settings", () =>
			// The workspace's own-provider panel fetches for itself; keeping it off narrows these tests
			// to the binding cards.
			HttpResponse.json({ ownProviderAllowed: false }),
		),
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
		// The shell's workspace switcher reads this; left unmocked it answers empty and the app
		// navigates off the workspace entirely, taking the page under test with it.
		http.get("*/workspaces", () => HttpResponse.json([WORKSPACE_LIST_ITEM])),
	);
}

async function renderModelsRoute(bindings: () => AgentBinding[]) {
	mockModelsRoute(bindings);
	// One client for the guards and the provider, exactly as `main.tsx` wires it.
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	const router = createRouter({
		routeTree,
		history: createMemoryHistory({ initialEntries: ["/w/acme/admin/models"] }),
		context: { queryClient, auth: undefined },
	});
	render(
		<QueryClientProvider client={queryClient}>
			<AuthProvider>
				{/* biome-ignore lint/suspicious/noExplicitAny: the app's router context is wider than this test needs. */}
				<RouterProvider router={router as any} />
			</AuthProvider>
		</QueryClientProvider>,
	);
	await screen.findByRole("heading", { name: "AI models" }, TRANSFORM_WAIT);
	// The four page queries land after the first paint; until they do the page renders a spinner.
	await screen.findByLabelText("Practice detection runs on", undefined, TRANSFORM_WAIT);
	return queryClient;
}

/** Each card is the region around its own model picker; every card's Save reads the same. */
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
		// Both cards submit into one `useMutation` pair, and a single observer's `variables` names only
		// its most recent call. Save practice detection, then save the mentor without waiting: the
		// observer flips to MENTOR, practice detection re-enables with its own PUT still in flight, and
		// when the mentor settles both cards look idle while a request is still out.
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

		fireEvent.click(saveButton("Practice detection runs on"));
		await waitFor(() => expect(detectionSaves).toBe(1));
		fireEvent.click(saveButton("Mentor runs on"));

		// The mentor's PUT settles; its card becomes usable again.
		await waitFor(() => expect(saveButton("Mentor runs on").disabled).toBe(false));

		// …and the card whose request is still out stays frozen, so it cannot be submitted twice.
		expect(saveButton("Practice detection runs on").disabled).toBe(true);

		releaseSlowSave?.();
		await waitFor(() => expect(saveButton("Practice detection runs on").disabled).toBe(false));
		expect(detectionSaves).toBe(1);
	});

	it("keeps unsaved run limits when another admin repoints the same purpose", async () => {
		// The card is remounted by key when its own save lands, so the key must not contain anything a
		// *different* admin can change: keying on the bound model means someone else repointing the
		// purpose remounts this admin's open form and discards the timeout they had typed — fields
		// that have nothing to do with the model, lost because the model was in the key.
		let bindings = [binding("PRACTICE_DETECTION", 20)];
		const queryClient = await renderModelsRoute(() => bindings);

		const detection = card("Practice detection runs on");
		fireEvent.click(within(detection).getByRole("button", { name: "Advanced" }));
		const timeout = within(detection).getByLabelText("Timeout (seconds)") as HTMLInputElement;
		fireEvent.change(timeout, { target: { value: "900" } });
		expect(timeout.value).toBe("900");

		// Another admin repoints the purpose; this tab refetches in the background. The readiness badge
		// renders straight off the refetched binding, so it is the signal that the new one landed —
		// the picker deliberately does not move, because it is holding this admin's edit.
		bindings = [{ ...binding("PRACTICE_DETECTION", 21), ready: false }];
		await queryClient.invalidateQueries({
			queryKey: listAgentsQueryKey({ path: { workspaceSlug: "acme" } }),
		});
		await waitFor(() =>
			expect(within(card("Practice detection runs on")).getByText("Not ready")).toBeTruthy(),
		);

		expect(
			(
				within(card("Practice detection runs on")).getByLabelText(
					"Timeout (seconds)",
				) as HTMLInputElement
			).value,
		).toBe("900");
	});

	it("reads back what was just saved, not what the card was showing before", async () => {
		// The card reseeds from the cached binding when this admin's own save completes, so the reseed
		// has to be driven by the response and not by `invalidateQueries`: invalidation schedules a
		// refetch, it does not touch `data`. Driven off the invalidation, the picker reseeds from the
		// *pre-save* array — snapping back to the model that was just replaced, under a "saved" toast,
		// and staying there once the refetch lands because the key never changes again. Saving from
		// that screen writes the old model back.
		let bindings = [binding("PRACTICE_DETECTION", 20)];
		await renderModelsRoute(() => bindings);
		server.use(
			// A refetch is a network round trip, so it cannot land in the same tick as the write that
			// triggered it. Without this the mocked GET answers inside a microtask and hides the defect:
			// the cache happens to be fresh by the time React flushes the remount.
			http.get("*/workspaces/:workspaceSlug/agents", async () => {
				await delay(50);
				return HttpResponse.json(bindings);
			}),
			http.put("*/workspaces/:workspaceSlug/agents/PRACTICE_DETECTION", async ({ request }) => {
				const body = (await request.json()) as { instanceModelId: number; timeoutSeconds: number };
				// `ready: false` is the one field the card cannot be holding locally, so it is how the
				// test knows the saved binding has reached the card — in either implementation.
				const saved: AgentBinding = {
					...binding("PRACTICE_DETECTION", body.instanceModelId),
					timeoutSeconds: body.timeoutSeconds,
					ready: false,
				};
				bindings = [saved];
				return HttpResponse.json(saved);
			}),
		);

		const detection = card("Practice detection runs on");
		fireEvent.click(within(detection).getByRole("button", { name: "Advanced" }));
		fireEvent.change(within(detection).getByLabelText("Timeout (seconds)"), {
			target: { value: "900" },
		});
		// `userEvent`, not `fireEvent`: the listbox commits on the pointer sequence, not on a bare click.
		await userEvent.click(within(detection).getByRole("combobox"));
		// The listbox is portalled, so it is not inside the card.
		await userEvent.click(await screen.findByRole("option", { name: /GPT Other/ }));

		fireEvent.click(saveButton("Practice detection runs on"));
		await waitFor(() =>
			expect(within(card("Practice detection runs on")).getByText("Not ready")).toBeTruthy(),
		);

		const saved = card("Practice detection runs on");
		expect(within(saved).getByRole("combobox").textContent).toContain("GPT Other");
		// Reseeding remounts the card, which closes the disclosure with it — so it has to be reopened
		// to read the run limits back.
		fireEvent.click(within(saved).getByRole("button", { name: "Advanced" }));
		expect((within(saved).getByLabelText("Timeout (seconds)") as HTMLInputElement).value).toBe(
			"900",
		);
	});

	it("reseeds the card to its defaults when the purpose is turned off", async () => {
		let bindings = [{ ...binding("PRACTICE_DETECTION", 20), timeoutSeconds: 900 }];
		await renderModelsRoute(() => bindings);
		server.use(
			// See the save test: the refetch has to cost a round trip, or it hides what the reseed reads.
			http.get("*/workspaces/:workspaceSlug/agents", async () => {
				await delay(50);
				return HttpResponse.json(bindings);
			}),
			http.delete("*/workspaces/:workspaceSlug/agents/PRACTICE_DETECTION", () => {
				bindings = [];
				return new HttpResponse(null, { status: 204 });
			}),
		);

		const detection = card("Practice detection runs on");
		fireEvent.click(within(detection).getByRole("button", { name: "Advanced" }));
		expect((within(detection).getByLabelText("Timeout (seconds)") as HTMLInputElement).value).toBe(
			"900",
		);

		fireEvent.click(within(detection).getByRole("button", { name: "Turn off" }));

		// No binding left, so the readiness badge and the Turn off button go with it…
		await waitFor(() =>
			expect(
				within(card("Practice detection runs on")).queryByRole("button", { name: "Turn off" }),
			).toBeNull(),
		);
		// …and the run limits are the defaults again, not the turned-off binding's values.
		const reset = card("Practice detection runs on");
		fireEvent.click(within(reset).getByRole("button", { name: "Advanced" }));
		expect((within(reset).getByLabelText("Timeout (seconds)") as HTMLInputElement).value).toBe(
			"600",
		);
	});
});
