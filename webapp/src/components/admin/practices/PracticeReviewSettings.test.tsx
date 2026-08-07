import {
	createMemoryHistory,
	createRootRoute,
	createRouter,
	RouterProvider,
} from "@tanstack/react-router";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type {
	AgentBinding,
	PracticeReviewSettings as PracticeReviewSettingsData,
} from "@/api/types.gen";
import { PracticeReviewSettings } from "./PracticeReviewSettings";

const readyBinding: AgentBinding = {
	purpose: "PRACTICE_REVIEW",
	enabled: true,
	ready: true,
	instanceModelId: 20,
};

const settings: PracticeReviewSettingsData = {
	runForAllUsers: true,
	deliverToMerged: false,
	cooldownMinutes: 15,
	reviewScope: { targetBranches: [], repositories: [] },
};

function renderSettings(props: Partial<React.ComponentProps<typeof PracticeReviewSettings>> = {}) {
	const rootRoute = createRootRoute({
		component: () => (
			<PracticeReviewSettings
				workspaceSlug="acme"
				model={{
					binding: readyBinding,
					isLoading: false,
					isError: false,
					onRetry: vi.fn(),
				}}
				workspace={{
					enabled: true,
					autoTriggerEnabled: true,
					manualTriggerEnabled: true,
					isSaving: false,
					onUpdate: vi.fn(),
				}}
				policy={{
					settings,
					isSaving: false,
					onUpdate: vi.fn(),
					onReset: vi.fn(),
				}}
				{...props}
			/>
		),
	});
	const router = createRouter({
		routeTree: rootRoute,
		history: createMemoryHistory({ initialEntries: ["/"] }),
	});
	render(<RouterProvider router={router} />);
}

describe("PracticeReviewSettings", () => {
	it("reports model readiness and points at the page that owns the binding", async () => {
		renderSettings();

		await screen.findByText("Ready to run");
		expect(screen.getByRole("link", { name: "Change" }).getAttribute("href")).toBe(
			"/w/acme/admin/models",
		);
	});

	it("explains when the selected model can no longer run", async () => {
		renderSettings({
			model: {
				binding: { ...readyBinding, ready: false },
				isLoading: false,
				isError: false,
				onRetry: vi.fn(),
			},
		});

		await screen.findByText("The review model is unavailable");
		expect(screen.getByRole("link", { name: "Choose a review model" }).getAttribute("href")).toBe(
			"/w/acme/admin/models",
		);
	});

	it("lets admins prepare triggers before choosing a runnable model", async () => {
		const onUpdate = vi.fn();
		renderSettings({
			model: { binding: undefined, isLoading: false, isError: false, onRetry: vi.fn() },
			workspace: {
				enabled: false,
				autoTriggerEnabled: false,
				manualTriggerEnabled: false,
				isSaving: false,
				onUpdate,
			},
		});

		const automatic = await screen.findByRole("switch", { name: "Automatic reviews" });
		expect(automatic.getAttribute("aria-disabled")).not.toBe("true");
		expect(
			screen.getByRole("switch", { name: "Start practice reviews" }).getAttribute("aria-disabled"),
		).toBe("true");

		fireEvent.click(automatic);
		expect(onUpdate).toHaveBeenCalledWith({ practiceReviewAutoTriggerEnabled: true });
	});

	it("allows conversation reviews without a project trigger", async () => {
		const onUpdate = vi.fn();
		renderSettings({
			workspace: {
				enabled: false,
				autoTriggerEnabled: false,
				manualTriggerEnabled: false,
				isSaving: false,
				onUpdate,
			},
		});

		const start = await screen.findByRole("switch", { name: "Start practice reviews" });
		expect(start.getAttribute("aria-disabled")).not.toBe("true");

		fireEvent.click(start);
		expect(onUpdate).toHaveBeenCalledWith({ practicesEnabled: true });
	});

	it("keeps an invalid cooldown local and explains how to fix it", async () => {
		const onUpdate = vi.fn();
		renderSettings({
			policy: { settings, isSaving: false, onUpdate, onReset: vi.fn() },
		});

		const cooldown = await screen.findByRole("spinbutton", {
			name: "Time between reviews (minutes)",
		});
		fireEvent.change(cooldown, { target: { value: "1500" } });
		fireEvent.blur(cooldown);

		expect(cooldown.getAttribute("aria-invalid")).toBe("true");
		expect(screen.getByText("Enter a whole number from 0 to 1,440.")).toBeTruthy();
		expect(onUpdate).not.toHaveBeenCalled();
	});
});
