import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { AgentBinding, AvailableLlmModel } from "@/api/types.gen";
import { AgentBindingsPage, type AgentBindingsPageProps } from "./AgentBindingsPage";

const model: AvailableLlmModel = {
	id: 20,
	scope: "SHARED",
	displayName: "GPT Test",
	connectionDisplayName: "Shared OpenAI",
	supportsReasoning: false,
	pricingMode: "NO_CHARGE",
};

const detectionBinding: AgentBinding = {
	purpose: "PRACTICE_DETECTION",
	instanceModelId: 20,
	enabled: true,
	ready: false,
	timeoutSeconds: 600,
	maxConcurrentJobs: 1,
	allowInternet: false,
};

function renderPage(overrides: Partial<AgentBindingsPageProps> = {}) {
	const onSave = vi.fn();
	const onTurnOff = vi.fn();
	render(
		<AgentBindingsPage
			workspaceSlug="demo"
			bindings={[detectionBinding]}
			availableModels={[model]}
			practicesEnabled
			mentorEnabled
			isLoading={false}
			isError={false}
			loadError={null}
			pendingPurposes={new Set()}
			onRetry={vi.fn()}
			onSave={onSave}
			onTurnOff={onTurnOff}
			{...overrides}
		/>,
	);
	return { onSave, onTurnOff };
}

/** One card per purpose, so an assertion has to be scoped to a card, not to the page. */
function practiceDetectionCard(): HTMLElement {
	const field = screen.getByLabelText("Practice feedback runs on");
	const form = field.closest("form");
	if (form == null) throw new Error("practice-detection card has no form");
	return form;
}

describe("AgentBindingsPage", () => {
	it("renders an assignment card for each purpose", () => {
		renderPage();
		screen.getByText("Practice feedback");
		screen.getByText("Mentor");
	});

	it("shows a Not ready badge when the bound model cannot run", () => {
		renderPage();
		screen.getByText("Not ready");
	});

	it("shows the binding it saves, so the payload cannot disagree with the controls", () => {
		const { onSave } = renderPage();

		const card = within(practiceDetectionCard());
		card.getByText(/GPT Test/);
		expect(card.getByRole("switch", { name: "Active" }).getAttribute("aria-checked")).toBe("true");

		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(onSave).toHaveBeenCalledWith(
			"PRACTICE_DETECTION",
			expect.objectContaining({ instanceModelId: 20, enabled: true }),
		);
	});

	it("exposes the advanced settings as a disclosure", () => {
		renderPage();

		const trigger = screen.getAllByRole("button", { name: /Advanced/ })[0];
		expect(trigger.getAttribute("aria-expanded")).toBe("false");
		expect(screen.queryByLabelText("Timeout (seconds)")).toBeNull();

		fireEvent.click(trigger);

		expect(trigger.getAttribute("aria-expanded")).toBe("true");
		screen.getByLabelText("Timeout (seconds)");
	});

	it("refuses to save a cleared timeout instead of sending a zero", () => {
		const { onSave } = renderPage();

		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		fireEvent.change(screen.getByLabelText("Timeout (seconds)"), { target: { value: "" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		screen.getByText("Enter a number of seconds.");
		expect(screen.getByLabelText("Timeout (seconds)").getAttribute("aria-invalid")).toBe("true");
		expect(onSave).not.toHaveBeenCalled();
	});

	it("rejects a timeout below the floor and only saves once it is corrected", () => {
		const { onSave } = renderPage();

		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		const timeout = screen.getByLabelText("Timeout (seconds)");
		fireEvent.change(timeout, { target: { value: "5" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		screen.getByText("Enter a whole number of seconds, 30 or more.");
		expect(onSave).not.toHaveBeenCalled();

		fireEvent.change(timeout, { target: { value: "45" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(onSave).toHaveBeenCalledWith(
			"PRACTICE_DETECTION",
			expect.objectContaining({ timeoutSeconds: 45 }),
		);
	});

	it("rejects a timeout above the ceiling, says why, and accepts the ceiling itself", () => {
		const { onSave } = renderPage();

		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		const timeout = screen.getByLabelText("Timeout (seconds)");
		fireEvent.change(timeout, { target: { value: "7200" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		screen.getByText("Runs stop after an hour, so enter 3600 seconds or less.");
		expect(timeout.getAttribute("aria-invalid")).toBe("true");
		expect(onSave).not.toHaveBeenCalled();

		fireEvent.change(timeout, { target: { value: "3600" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(onSave).toHaveBeenCalledWith(
			"PRACTICE_DETECTION",
			expect.objectContaining({ timeoutSeconds: 3600 }),
		);
	});

	it("reopens the advanced disclosure when the field that blocked the save is inside it", () => {
		renderPage();

		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		fireEvent.change(screen.getByLabelText("Max concurrent runs"), { target: { value: "0" } });
		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		expect(screen.queryByLabelText("Max concurrent runs")).toBeNull();

		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		screen.getByText("Enter a whole number of runs, 1 or more.");
	});

	it("offers Turn off only for a purpose that is actually bound", () => {
		const { onTurnOff } = renderPage();

		const turnOff = within(practiceDetectionCard()).getByRole("button", { name: "Turn off" });
		expect(screen.getAllByRole("button", { name: "Turn off" })).toHaveLength(1);

		fireEvent.click(turnOff);

		expect(onTurnOff).toHaveBeenCalledWith("PRACTICE_DETECTION");
	});
});
