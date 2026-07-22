import { render, screen } from "@testing-library/react";
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
});
