import { fireEvent, render, screen, within } from "@testing-library/react";
import { userEvent } from "storybook/test";
import { describe, expect, it, vi } from "vitest";
import type { LlmConnection } from "@/api/types.gen";
import { expectUnavailable } from "@/test/controls";
import { AdminLlmConnectionsTable } from "./AdminLlmConnectionsTable";

const connection: LlmConnection = {
	id: 1,
	slug: "openai",
	displayName: "OpenAI production",
	baseUrl: "https://api.openai.com/v1",
	apiProtocol: "openai-responses",
	authMode: "BEARER",
	hasApiKey: true,
	enabled: true,
	createdAt: new Date("2026-07-01T00:00:00Z"),
};

describe("AdminLlmConnectionsTable", () => {
	it("opens a connection's models from a keyboard-focusable button", async () => {
		const onSelect = vi.fn();
		render(
			<AdminLlmConnectionsTable
				connections={[connection]}
				modelCounts={{ 1: 2 }}
				isLoading={false}
				isError={false}
				mutatingIds={new Set<number>()}
				selectedId={null}
				onSelect={onSelect}
				onEdit={vi.fn()}
				onToggleEnabled={vi.fn()}
				onDelete={vi.fn()}
			/>,
		);

		const manage = screen.getByRole("button", { name: "Manage models for OpenAI production" });
		manage.focus();
		// Focusability is the claim: a clickable row or a bare <div> takes no focus, so the keystroke
		// below would land on <body> and the assertion after it would pass for the wrong reason.
		expect(document.activeElement).toBe(manage);

		await userEvent.keyboard("{Enter}");
		expect(onSelect).toHaveBeenCalledWith(connection);
	});

	function renderTable(
		modelCounts: Record<number, number>,
		onToggleEnabled = vi.fn(),
	): { onToggleEnabled: ReturnType<typeof vi.fn> } {
		render(
			<AdminLlmConnectionsTable
				connections={[connection]}
				modelCounts={modelCounts}
				isLoading={false}
				isError={false}
				mutatingIds={new Set<number>()}
				selectedId={null}
				onSelect={vi.fn()}
				onEdit={vi.fn()}
				onToggleEnabled={onToggleEnabled}
				onDelete={vi.fn()}
			/>,
		);
		return { onToggleEnabled };
	}

	it("confirms before turning off every model on a connection", async () => {
		const { onToggleEnabled } = renderTable({ 1: 2 });

		fireEvent.click(screen.getByRole("switch", { name: "OpenAI production" }));

		// The confirmation is the point of the test, so it is the confirmation that gets read: it names
		// the connection and how many models stop with it, which is the number the admin is deciding on.
		const confirm = screen.getByRole("alertdialog");
		expect(within(confirm).getByRole("heading").textContent).toBe("Turn off “OpenAI production”?");
		expect(confirm.textContent).toContain(
			"This immediately stops requests through all 2 models on this connection.",
		);
		expect(onToggleEnabled).not.toHaveBeenCalled();

		fireEvent.click(screen.getByRole("button", { name: "Turn off connection" }));
		expect(onToggleEnabled).toHaveBeenCalledWith(connection, false);
	});

	it("counts one model as one", () => {
		// The sentence special-cases one. Two is the arity that reads correctly however the number is
		// interpolated, so a fixture of two alone would let "all 1 models on this connection" through.
		renderTable({ 1: 1 });

		fireEvent.click(screen.getByRole("switch", { name: "OpenAI production" }));

		expect(screen.getByRole("alertdialog").textContent).toContain(
			"This immediately stops requests through the model on this connection.",
		);
	});

	it("turns off a connection with no models without asking", () => {
		// The confirm exists to name what stops. With no models on the connection it names nothing,
		// and the sentence it would render is about nothing.
		const { onToggleEnabled } = renderTable({});

		fireEvent.click(screen.getByRole("switch", { name: "OpenAI production" }));

		expect(screen.queryByRole("alertdialog")).toBeNull();
		expect(onToggleEnabled).toHaveBeenCalledWith(connection, false);
	});

	it("blocks turning off a connection until its affected models are known", async () => {
		render(
			<AdminLlmConnectionsTable
				connections={[connection]}
				modelCounts={{}}
				modelCountsAvailable={false}
				isLoading={false}
				isError={false}
				mutatingIds={new Set<number>()}
				selectedId={null}
				onSelect={vi.fn()}
				onEdit={vi.fn()}
				onToggleEnabled={vi.fn()}
				onDelete={vi.fn()}
			/>,
		);

		// Announced as unavailable and out of the tab order, not merely greyed (WCAG 2.2 SC 4.1.2)…
		const toggle = screen.getByRole("switch", { name: "OpenAI production" });
		await expectUnavailable(toggle);
		expect(screen.getByRole("cell", { name: "—" })).toBeTruthy();

		// …and pressing it anyway neither opens the confirm nor turns the connection off behind it.
		fireEvent.click(toggle);
		expect(screen.queryByRole("alertdialog")).toBeNull();
		expect(toggle.getAttribute("aria-checked")).toBe("true");
	});
});
