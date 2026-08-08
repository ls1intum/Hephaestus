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
	runForAllUsers: true,
	deliverToMerged: false,
	cooldownMinutes: 15,
	reviewScope: { targetBranches: [], repositories: [] },
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
	it("reports model readiness and points at the page that owns the binding", async () => {
		await renderSettings();

		await screen.findByText("Ready to run");
		expect(screen.getByRole("link", { name: "Change" }).getAttribute("href")).toBe(
			"/w/acme/admin/models",
		);
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
