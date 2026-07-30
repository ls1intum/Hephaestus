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
				savingReviewSettings={false}
				savingTriggers={false}
				onUpdateReviewSettings={vi.fn()}
				onUpdateTriggers={vi.fn()}
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

		await screen.findByText("GPT Test");
		expect(screen.getByRole("link", { name: "AI models page" }).getAttribute("href")).toBe(
			"/w/acme/admin/models",
		);
	});

	it("explains that reviews are paused when the bound model can no longer run", async () => {
		renderCard({ detectionBinding: { ...readyBinding, ready: false } });

		await screen.findByText("The practice feedback model is unavailable");
		expect(screen.getByRole("link", { name: "Open AI models" }).getAttribute("href")).toBe(
			"/w/acme/admin/models",
		);
	});

	it("explains that reviews cannot run at all when no model is bound", async () => {
		renderCard({ detectionBinding: undefined });

		await screen.findByText("Practice feedback has no model");
		expect(screen.getByRole("link", { name: "Open AI models" }).getAttribute("href")).toBe(
			"/w/acme/admin/models",
		);
	});

	it("reports a load failure with the server's reason and no Retry when one cannot help", async () => {
		renderCard({
			isError: true,
			settings: undefined,
			error: { status: 403, detail: "You are not an admin of this workspace." },
			onRetry: vi.fn(),
		});

		await screen.findByText("Couldn't load the review policy");
		screen.getByText(/You are not an admin of this workspace/);
		expect(screen.queryByRole("button", { name: "Retry" })).toBeNull();
	});

	it("offers a Retry when the failure is transient", async () => {
		renderCard({ isError: true, settings: undefined, error: { status: 503 }, onRetry: vi.fn() });

		await screen.findByRole("button", { name: "Retry" });
	});

	it("puts the triggers out of reach, not merely grey, while nothing can run", async () => {
		const onUpdateTriggers = vi.fn();
		renderCard({
			detectionBinding: undefined,
			autoTriggerEnabled: false,
			manualTriggerEnabled: false,
			onUpdateTriggers,
		});

		const auto = await screen.findByRole("switch", { name: "Automatic reviews" });
		const manual = screen.getByRole("switch", { name: "Manual reviews" });

		await expectUnavailable(auto);
		await expectUnavailable(manual);

		fireEvent.click(auto);
		expect(auto.getAttribute("aria-checked")).toBe("false");
		expect(onUpdateTriggers).not.toHaveBeenCalled();
	});
});
