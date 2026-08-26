import { act, fireEvent, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type {
	AgentBinding,
	PracticeReviewSettings as PracticeReviewSettingsData,
} from "@/api/types.gen";
import { renderWithRouter } from "@/test/router-harness";
import { PracticeReviewSettings } from "./PracticeReviewSettings";
import { mockReviewSettings } from "./story-mock-data";

const readyBinding: AgentBinding = {
	purpose: "PRACTICE_REVIEW",
	enabled: true,
	ready: true,
	instanceModelId: 20,
};

const settings: PracticeReviewSettingsData = mockReviewSettings({
	deliverToMerged: false,
	cooldownMinutes: 15,
});

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
			coverage={{
				preview: vi.fn(async () => ({
					current: settings.coverageSummary,
					proposed: settings.coverageSummary,
					widens: true,
				})),
				repositories: {
					options: [{ value: "acme/widgets", label: "acme/widgets" }],
					isLoading: false,
					isError: false,
					error: undefined,
					onRetry: vi.fn(),
				},
				people: {
					options: [{ value: 7, label: "Ada" }],
					isLoading: false,
					isError: false,
					error: undefined,
					onRetry: vi.fn(),
				},
			}}
			{...props}
		/>,
		"/w/acme/admin/practices",
	);
}

describe("PracticeReviewSettings", () => {
	it("names both coverage mode groups", async () => {
		await renderSettings();

		await screen.findByRole("radiogroup", { name: "Repositories" });
		screen.getByRole("radiogroup", { name: "People" });
	});

	it("keeps persisted targets visible when they are no longer available", async () => {
		await renderSettings({
			policy: {
				settings: mockReviewSettings({
					reviewScope: {
						repositoryMode: "SELECTED",
						personMode: "SELECTED",
						repositories: [{ nameWithOwner: "acme/archived", baseBranches: ["main"] }],
						personUserIds: [99],
					},
				}),
				isSaving: false,
				onUpdate: vi.fn(),
				onReset: vi.fn(),
			},
		});

		await screen.findByText("acme/archived");
		screen.getByText("Not monitored");
		screen.getByTitle("Member 99 (unavailable)");
	});

	it("points at the page that owns the binding without restating the page banner", async () => {
		await renderSettings();

		const change = await screen.findByRole("link", { name: "Change the review model" });
		expect(change.getAttribute("href")).toBe("/w/acme/admin/models");
		expect(screen.queryByText("Ready to run")).toBeNull();
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

	it("does not apply a coverage preview after the page closes", async () => {
		let resolvePreview:
			| ((value: {
					current: typeof settings.coverageSummary;
					proposed: typeof settings.coverageSummary;
					widens: boolean;
			  }) => void)
			| undefined;
		const preview = vi.fn(
			() =>
				new Promise<{
					current: typeof settings.coverageSummary;
					proposed: typeof settings.coverageSummary;
					widens: boolean;
				}>((resolve) => {
					resolvePreview = resolve;
				}),
		);
		const onUpdate = vi.fn();
		const view = await renderSettings({
			policy: { settings, isSaving: false, onUpdate, onReset: vi.fn() },
			coverage: {
				preview,
				repositories: {
					options: [{ value: "acme/widgets", label: "acme/widgets" }],
					isLoading: false,
					isError: false,
					error: undefined,
					onRetry: vi.fn(),
				},
				people: {
					options: [{ value: 7, label: "Ada" }],
					isLoading: false,
					isError: false,
					error: undefined,
					onRetry: vi.fn(),
				},
			},
		});

		fireEvent.click(screen.getByRole("radio", { name: "All monitored repositories" }));
		view.unmount();
		act(() => {
			resolvePreview?.({
				current: settings.coverageSummary,
				proposed: settings.coverageSummary,
				widens: false,
			});
		});

		expect(onUpdate).not.toHaveBeenCalled();
	});
});
