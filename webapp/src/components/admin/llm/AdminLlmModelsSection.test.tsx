import { fireEvent, render, screen } from "@testing-library/react";
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
				mutatingId={null}
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
});
