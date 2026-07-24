import { fireEvent, render, screen } from "@testing-library/react";
import { userEvent } from "storybook/test";
import { describe, expect, it, vi } from "vitest";
import type { LlmConnection } from "@/api/types.gen";
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
				mutatingId={null}
				selectedId={null}
				onSelect={onSelect}
				onEdit={vi.fn()}
				onToggleEnabled={vi.fn()}
				onDelete={vi.fn()}
			/>,
		);

		const manage = screen.getByRole("button", { name: "Manage models for OpenAI production" });
		manage.focus();
		await userEvent.keyboard("{Enter}");
		expect(onSelect).toHaveBeenCalledWith(connection);
	});

	it("confirms before turning off every model on a connection", async () => {
		const onToggleEnabled = vi.fn();
		render(
			<AdminLlmConnectionsTable
				connections={[connection]}
				modelCounts={{ 1: 2 }}
				isLoading={false}
				isError={false}
				mutatingId={null}
				selectedId={null}
				onSelect={vi.fn()}
				onEdit={vi.fn()}
				onToggleEnabled={onToggleEnabled}
				onDelete={vi.fn()}
			/>,
		);

		fireEvent.click(screen.getByRole("switch", { name: "Turn off OpenAI production" }));
		expect(onToggleEnabled).not.toHaveBeenCalled();
		fireEvent.click(screen.getByRole("button", { name: "Turn off connection" }));
		expect(onToggleEnabled).toHaveBeenCalledWith(connection, false);
	});

	it("blocks turning off a connection until its affected models are known", () => {
		render(
			<AdminLlmConnectionsTable
				connections={[connection]}
				modelCounts={{}}
				modelCountsAvailable={false}
				isLoading={false}
				isError={false}
				mutatingId={null}
				selectedId={null}
				onSelect={vi.fn()}
				onEdit={vi.fn()}
				onToggleEnabled={vi.fn()}
				onDelete={vi.fn()}
			/>,
		);

		expect(
			screen
				.getByRole("switch", { name: "Turn off OpenAI production" })
				.hasAttribute("data-disabled"),
		).toBe(true);
		expect(screen.getByRole("cell", { name: "—" })).toBeTruthy();
	});
});
