import { fireEvent, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type {
	AgentBinding,
	PracticeReviewSettings as PracticeReviewSettingsData,
} from "@/api/types.gen";
import { renderWithRouter } from "@/test/router-harness";
import { PracticeReviewSettings } from "./PracticeReviewSettings";

const readyBinding: AgentBinding = {
	purpose: "PRACTICE_REVIEW",
	enabled: true,
	ready: true,
	instanceModelId: 20,
};

const settings: PracticeReviewSettingsData = {
	deliverToMerged: false,
	cooldownMinutes: 15,
	reviewScope: { targetBranches: [], repositories: [] },
	defaultAutonomy: "AUTOMATIC",
};

function renderSettings(props: Partial<React.ComponentProps<typeof PracticeReviewSettings>> = {}) {
	return renderWithRouter(
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
		/>,
		"/w/acme/admin/practices",
	);
}

describe("PracticeReviewSettings", () => {
	it("points at the page that owns the binding without restating the page banner", async () => {
		await renderSettings();

		const change = await screen.findByRole("link", { name: "Change the review model" });
		expect(change.getAttribute("href")).toBe("/w/acme/admin/models");
		// Readiness is the banner's sentence; saying it again here was the same fact three times.
		expect(screen.queryByText("Ready to run")).toBeNull();
	});

	it("explains when the selected model can no longer run", async () => {
		await renderSettings({
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
		await renderSettings({
			model: { binding: undefined, isLoading: false, isError: false, onRetry: vi.fn() },
			workspace: {
				enabled: false,
				autoTriggerEnabled: false,
				manualTriggerEnabled: false,
				isSaving: false,
				onUpdate,
			},
		});

		const automatic = await screen.findByRole("switch", { name: "Reviews the work starts" });
		expect(automatic.getAttribute("aria-disabled")).not.toBe("true");
		expect(
			screen.getByRole("switch", { name: "Start practice reviews" }).getAttribute("aria-disabled"),
		).toBe("true");

		fireEvent.click(automatic);
		expect(onUpdate).toHaveBeenCalledWith({ practiceReviewAutoTriggerEnabled: true });
	});

	it("allows conversation reviews without a project trigger", async () => {
		const onUpdate = vi.fn();
		await renderSettings({
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
});
