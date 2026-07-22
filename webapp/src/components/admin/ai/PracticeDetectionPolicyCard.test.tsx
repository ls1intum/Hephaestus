import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { AgentConfig, AiSettingsView, AvailableLlmModel } from "@/api/types.gen";
import { PracticeDetectionPolicyCard } from "./PracticeDetectionPolicyCard";

const availableModel: AvailableLlmModel = {
	id: 20,
	scope: "SHARED",
	displayName: "GPT Test",
	connectionDisplayName: "Shared OpenAI",
	supportsReasoning: false,
	pricingMode: "NO_CHARGE",
};

const validConfig: AgentConfig = {
	id: 1,
	name: "Available reviewer",
	enabled: true,
	instanceModelId: availableModel.id,
	allowInternet: false,
	maxConcurrentJobs: 1,
	timeoutSeconds: 600,
	createdAt: new Date("2026-07-01T00:00:00Z"),
};

const disabledConfig: AgentConfig = {
	...validConfig,
	id: 2,
	name: "Disabled reviewer",
	enabled: false,
};

const revokedConfig: AgentConfig = {
	...validConfig,
	id: 3,
	name: "Revoked reviewer",
	instanceModelId: 999,
};

const settings: AiSettingsView = {
	practicesEnabled: true,
	mentorEnabled: true,
	workspaceConnectionsAllowed: true,
	runForAllUsers: true,
	skipDrafts: true,
	deliverToMerged: false,
	cooldownMinutes: 15,
};

function renderCard(practiceConfigId?: number) {
	render(
		<PracticeDetectionPolicyCard
			settings={{ ...settings, practiceConfigId }}
			configs={[validConfig, disabledConfig, revokedConfig]}
			availableModels={[availableModel]}
			autoTriggerEnabled
			manualTriggerEnabled
			isLoading={false}
			isSaving={false}
			onBindConfig={vi.fn()}
			onUpdateReviewSettings={vi.fn()}
			onUpdateFeatures={vi.fn()}
			onResetReviewField={vi.fn()}
		/>,
	);
}

describe("PracticeDetectionPolicyCard model binding", () => {
	it("keeps an unavailable existing binding visible and explains why reviews are paused", () => {
		renderCard(revokedConfig.id);

		expect(screen.getByRole("combobox", { name: "Configuration" }).textContent).toContain(
			"Revoked reviewer (unavailable)",
		);
		expect(
			screen.getByText(/the selected configuration cannot run.*clear the binding/i),
		).toBeTruthy();
		fireEvent.click(screen.getByRole("combobox", { name: "Configuration" }));
		expect(
			screen
				.getByRole("option", { name: "Revoked reviewer (unavailable)" })
				.hasAttribute("data-disabled"),
		).toBe(true);
	});

	it("offers only configurations whose current model is executable", () => {
		renderCard();

		fireEvent.click(screen.getByRole("combobox", { name: "Configuration" }));

		expect(screen.getByText("Available reviewer")).toBeTruthy();
		expect(screen.queryByText("Disabled reviewer")).toBeNull();
		expect(screen.queryByText("Revoked reviewer")).toBeNull();
	});

	it("does not offer fan-out or new triggers when nothing can run", () => {
		render(
			<PracticeDetectionPolicyCard
				settings={settings}
				configs={[disabledConfig, revokedConfig]}
				availableModels={[availableModel]}
				autoTriggerEnabled={false}
				manualTriggerEnabled={false}
				isLoading={false}
				isSaving={false}
				onBindConfig={vi.fn()}
				onUpdateReviewSettings={vi.fn()}
				onUpdateFeatures={vi.fn()}
				onResetReviewField={vi.fn()}
			/>,
		);

		expect(screen.getByText("No runnable configuration")).toBeTruthy();
		expect(
			screen.getByRole("combobox", { name: "Configuration" }).hasAttribute("data-disabled"),
		).toBe(true);
		expect(
			screen.getByRole("switch", { name: "Automatic reviews" }).hasAttribute("data-disabled"),
		).toBe(true);
		expect(
			screen.getByRole("switch", { name: "Manual reviews" }).hasAttribute("data-disabled"),
		).toBe(true);
	});

	it("lets an admin clear an unavailable explicit binding", () => {
		const onBindConfig = vi.fn();
		render(
			<PracticeDetectionPolicyCard
				settings={{ ...settings, practiceConfigId: revokedConfig.id }}
				configs={[revokedConfig]}
				availableModels={[availableModel]}
				autoTriggerEnabled={false}
				manualTriggerEnabled={false}
				isLoading={false}
				isSaving={false}
				onBindConfig={onBindConfig}
				onUpdateReviewSettings={vi.fn()}
				onUpdateFeatures={vi.fn()}
				onResetReviewField={vi.fn()}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Clear binding" }));
		expect(onBindConfig).toHaveBeenCalledWith(null);
	});
});
