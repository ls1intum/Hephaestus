import {
	createMemoryHistory,
	createRootRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { AgentBinding, AvailableLlmModel, PracticeReviewSettings } from "@/api/types.gen";
import { expectUnavailable } from "@/test/controls";
import { PracticeDetectionPolicyCard } from "./PracticeDetectionPolicyCard";

const availableModel: AvailableLlmModel = {
	id: 20,
	scope: "SHARED",
	displayName: "GPT Test",
	connectionDisplayName: "Shared OpenAI",
	supportsReasoning: false,
	pricingMode: "NO_CHARGE",
};

const readyBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
	enabled: true,
	ready: true,
	instanceModelId: availableModel.id,
};

const settings: PracticeReviewSettings = {
	runForAllUsers: true,
	skipDrafts: true,
	deliverToMerged: false,
	cooldownMinutes: 15,
};

/** The card renders TanStack `Link`s to the AI models page, so it needs a router in scope. */
function renderCard(props: Partial<React.ComponentProps<typeof PracticeDetectionPolicyCard>> = {}) {
	const rootRoute = createRootRoute({
		component: () => (
			<PracticeDetectionPolicyCard
				settings={settings}
				detectionBinding={readyBinding}
				availableModels={[availableModel]}
				workspaceSlug="acme"
				autoTriggerEnabled
				manualTriggerEnabled
				isLoading={false}
				isSaving={false}
				onUpdateReviewSettings={vi.fn()}
				onUpdateFeatures={vi.fn()}
				onResetReviewField={vi.fn()}
				{...props}
			/>
		),
	});
	const router = createRouter({
		routeTree: rootRoute,
		history: createMemoryHistory({ initialEntries: ["/"] }),
	});
	// biome-ignore lint/suspicious/noExplicitAny: standalone test router has no generated route types
	render(<RouterProvider router={router as any} />);
}

describe("PracticeDetectionPolicyCard model binding", () => {
	it("reports the model detection runs on, and points at the page that owns the binding", async () => {
		renderCard();

		expect(await screen.findByText("GPT Test")).toBeTruthy();
		expect(screen.getByRole("link", { name: "AI models page" })).toBeTruthy();
	});

	it("explains that reviews are paused when the bound model can no longer run", async () => {
		renderCard({ detectionBinding: { ...readyBinding, ready: false } });

		expect(await screen.findByText("Practice detection's model is unavailable")).toBeTruthy();
		expect(screen.getByRole("link", { name: "Open AI models" })).toBeTruthy();
	});

	it("explains that reviews cannot run at all when no model is bound", async () => {
		renderCard({ detectionBinding: undefined });

		expect(await screen.findByText("Practice detection has no model")).toBeTruthy();
		expect(screen.getByRole("link", { name: "Open AI models" })).toBeTruthy();
	});

	it("reports a load failure with the server's reason and no Retry when one cannot help", async () => {
		renderCard({
			isError: true,
			settings: undefined,
			error: { status: 403, detail: "You are not an admin of this workspace." },
			onRetry: vi.fn(),
		});

		expect(await screen.findByText("Couldn't load the review policy")).toBeTruthy();
		expect(screen.getByText(/You are not an admin of this workspace/)).toBeTruthy();
		expect(screen.queryByRole("button", { name: "Retry" })).toBeNull();
	});

	it("offers a Retry when the failure is transient", async () => {
		renderCard({ isError: true, settings: undefined, error: { status: 503 }, onRetry: vi.fn() });

		expect(await screen.findByRole("button", { name: "Retry" })).toBeTruthy();
	});

	it("puts the triggers out of reach, not merely grey, while nothing can run", async () => {
		const onUpdateFeatures = vi.fn();
		renderCard({
			detectionBinding: undefined,
			autoTriggerEnabled: false,
			manualTriggerEnabled: false,
			onUpdateFeatures,
		});

		const auto = await screen.findByRole("switch", { name: "Automatic reviews" });
		const manual = screen.getByRole("switch", { name: "Manual reviews" });

		await expectUnavailable(auto);
		await expectUnavailable(manual);

		fireEvent.click(auto);
		expect(auto.getAttribute("aria-checked")).toBe("false");
		expect(onUpdateFeatures).not.toHaveBeenCalled();
	});
});
