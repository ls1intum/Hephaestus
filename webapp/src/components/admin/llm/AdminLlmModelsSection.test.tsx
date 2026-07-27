import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { LlmModel } from "@/api/types.gen";
import { AdminLlmModelsSection } from "./AdminLlmModelsSection";

const model: LlmModel = {
	id: 7,
	slug: "gpt-5",
	displayName: "GPT-5",
	upstreamModelId: "gpt-5",
	connectionId: 1,
	connectionDisplayName: "OpenAI",
	enabled: true,
	supportsReasoning: true,
	visibility: "GRANTED",
	grantedWorkspaceIds: [10],
	createdAt: new Date("2026-07-01T00:00:00Z"),
};

describe("AdminLlmModelsSection", () => {
	it("offers a discoverable access-management action", () => {
		const onManageAccess = vi.fn();
		render(
			<AdminLlmModelsSection
				connectionDisplayName="OpenAI"
				connectionEnabled
				workspaceOptions={[{ id: 10, displayName: "Alpha", workspaceSlug: "alpha" }]}
				models={[model]}
				mutatingIds={new Set<number>()}
				onAdd={vi.fn()}
				onEdit={vi.fn()}
				onManageAccess={onManageAccess}
				onDelete={vi.fn()}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Manage access for GPT-5" }));
		expect(onManageAccess).toHaveBeenCalledWith(model);
		expect(screen.getByRole("columnheader", { name: "Workspace access" })).toBeTruthy();
		expect(screen.getByText("Alpha")).toBeTruthy();
	});

	it("closes the delete confirm on confirming, while the DELETE is still in flight", async () => {
		// The row disappears the moment the list refetches. A confirm that stayed up would still be
		// naming it, with Delete offered again — a second DELETE for a model that no longer exists,
		// answered 404 and toasted "Could not delete the model" for a delete that had worked.
		const onDelete = vi.fn();
		const props = {
			connectionDisplayName: "OpenAI",
			connectionEnabled: true,
			workspaceOptions: [{ id: 10, displayName: "Alpha", workspaceSlug: "alpha" }],
			onAdd: vi.fn(),
			onEdit: vi.fn(),
			onManageAccess: vi.fn(),
			onDelete,
		};
		const { rerender } = render(
			<AdminLlmModelsSection {...props} models={[model]} mutatingIds={new Set<number>()} />,
		);

		fireEvent.click(screen.getByRole("button", { name: "Delete GPT-5" }));
		fireEvent.click(screen.getByRole("button", { name: "Delete" }));
		expect(onDelete).toHaveBeenCalledExactlyOnceWith(model);

		// What the container does next: the row is now mid-write, and the refetched list has dropped it.
		rerender(<AdminLlmModelsSection {...props} models={[]} mutatingIds={new Set([model.id])} />);

		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
		expect(screen.getByText("No models yet")).toBeTruthy();
	});
});
