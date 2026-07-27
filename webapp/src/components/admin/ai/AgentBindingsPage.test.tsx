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

/**
 * The binding cards take everything they render as a prop, so a test states the situation directly
 * instead of standing up a QueryClient and five request mocks to arrive at the same screen.
 *
 * `ownProviderAllowed: false` is not incidental: the provider panel this page also renders still
 * fetches and mutates on its own, so it is the one part of this screen a test cannot reach without a
 * QueryClient. Everything asserted here is above that line.
 */
function renderPage(overrides: Partial<AgentBindingsPageProps> = {}) {
	const onSave = vi.fn();
	const onTurnOff = vi.fn();
	render(
		<AgentBindingsPage
			workspaceSlug="demo"
			bindings={[detectionBinding]}
			availableModels={[model]}
			// Whether a purpose may run at all is a property of the workspace, not of the AI config.
			practicesEnabled
			mentorEnabled
			// Registering providers of your own is an instance-level permission, asked for separately.
			ownProviderAllowed={false}
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

/**
 * One card per purpose, each with its own "Active" switch and model picker, so an assertion about
 * practice detection has to be scoped to its card rather than to the first match on the page.
 */
function practiceDetectionCard(): HTMLElement {
	const field = screen.getByLabelText("Practice detection runs on");
	const form = field.closest("form");
	if (form == null) throw new Error("practice-detection card has no form");
	return form;
}

describe("AgentBindingsPage", () => {
	it("renders an assignment card for each purpose", () => {
		renderPage();
		expect(screen.getByText("Practice detection")).toBeTruthy();
		expect(screen.getByText("Mentor")).toBeTruthy();
	});

	it("shows a Not ready badge when the bound model cannot run", () => {
		renderPage();
		expect(screen.getByText("Not ready")).toBeTruthy();
	});

	it("saves the bound model id when the admin clicks Save", () => {
		const { onSave } = renderPage();

		// The card shows the binding it is about to send back: the bound model by name, and Active on.
		// Without this the payload could be right while the controls the admin reads showed otherwise.
		const card = within(practiceDetectionCard());
		expect(card.getByLabelText("Practice detection runs on").textContent).toContain("GPT Test");
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
		// The panel the trigger claims to control is the one that actually holds the fields.
		const panelId = trigger.getAttribute("aria-controls");
		expect(panelId).toBeTruthy();
		const timeout = screen.getByLabelText("Timeout (seconds)");
		expect(panelId && document.getElementById(panelId)?.contains(timeout)).toBe(true);
	});

	it("refuses to save a cleared timeout instead of sending a zero", () => {
		const { onSave } = renderPage();

		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		fireEvent.change(screen.getByLabelText("Timeout (seconds)"), { target: { value: "" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(screen.getByText("Enter a number of seconds.")).toBeTruthy();
		expect(screen.getByLabelText("Timeout (seconds)").getAttribute("aria-invalid")).toBe("true");
		// Nothing is sent: an empty field is a field left blank, never a `timeoutSeconds: 0` that would
		// time every run out instantly.
		expect(onSave).not.toHaveBeenCalled();
	});

	it("rejects a timeout below the floor and only saves once it is corrected", () => {
		const { onSave } = renderPage();

		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		const timeout = screen.getByLabelText("Timeout (seconds)");
		fireEvent.change(timeout, { target: { value: "5" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(screen.getByText("Enter a whole number of seconds, 30 or more.")).toBeTruthy();
		expect(onSave).not.toHaveBeenCalled();

		fireEvent.change(timeout, { target: { value: "45" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(onSave).toHaveBeenCalledWith(
			"PRACTICE_DETECTION",
			expect.objectContaining({ timeoutSeconds: 45 }),
		);
	});

	it("rejects a timeout above the ceiling and says why an hour is the limit", () => {
		const { onSave } = renderPage();

		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		const timeout = screen.getByLabelText("Timeout (seconds)");
		fireEvent.change(timeout, { target: { value: "7200" } });
		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		// The reason, not just the range: a run that outlives an hour is stopped, so the number cannot
		// be honoured however it is typed.
		expect(
			screen.getByText("Runs stop after an hour, so enter 3600 seconds or less."),
		).toBeTruthy();
		expect(timeout.getAttribute("aria-invalid")).toBe("true");
		// Nothing is sent, so the admin never meets the server's bare 400 for this.
		expect(onSave).not.toHaveBeenCalled();

		// The ceiling itself is allowed — it is a maximum, not a value just short of one.
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
		// Collapse it again, so the invalid field is out of sight when Save is pressed.
		fireEvent.click(screen.getAllByRole("button", { name: /Advanced/ })[0]);
		expect(screen.queryByLabelText("Max concurrent runs")).toBeNull();

		fireEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

		expect(screen.getByText("Enter a whole number of runs, 1 or more.")).toBeTruthy();
	});

	it("offers Turn off only for a purpose that is actually bound", () => {
		const { onTurnOff } = renderPage();

		// Two cards are on screen and only practice detection has a binding, so only it has something
		// to turn off — Mentor's card offers Save alone. `getByRole` (singular) is the assertion: a
		// second Turn off anywhere on the page fails it.
		const turnOff = screen.getByRole("button", { name: "Turn off" });
		expect(screen.getAllByRole("button", { name: "Save" })).toHaveLength(2);
		expect(turnOff.closest("form")?.textContent).toContain("Practice detection");

		fireEvent.click(turnOff);

		expect(onTurnOff).toHaveBeenCalledWith("PRACTICE_DETECTION");
	});
});
