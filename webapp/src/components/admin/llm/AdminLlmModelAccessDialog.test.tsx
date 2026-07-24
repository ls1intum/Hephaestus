import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { LlmModel } from "@/api/types.gen";
import { AdminLlmModelAccessDialog } from "./AdminLlmModelAccessDialog";

const model: LlmModel = {
	id: 7,
	slug: "gpt-5",
	displayName: "GPT-5",
	upstreamModelId: "gpt-5",
	connectionId: 1,
	connectionDisplayName: "OpenAI",
	enabled: true,
	supportsReasoning: true,
	visibility: "PUBLIC",
	grantedWorkspaceIds: [],
	createdAt: new Date("2026-07-01T00:00:00Z"),
};

const workspaces = [
	{ id: 10, displayName: "Alpha", workspaceSlug: "alpha" },
	{ id: 11, displayName: "Beta", workspaceSlug: "beta" },
];

describe("AdminLlmModelAccessDialog", () => {
	it("makes an access reduction and its immediate effect explicit", () => {
		render(
			<AdminLlmModelAccessDialog
				open
				onOpenChange={vi.fn()}
				model={model}
				workspaceOptions={workspaces}
				isSubmitting={false}
				onSave={vi.fn()}
			/>,
		);

		fireEvent.click(screen.getByRole("radio", { name: /^Selected workspaces/i }));
		expect(
			screen.getByText(/existing configurations in removed workspaces will stop running/i),
		).toBeTruthy();
		expect(screen.getByText(/no workspace will be able to use this model/i)).toBeTruthy();
	});

	it("saves the selected workspace allowlist", () => {
		const onSave = vi.fn();
		render(
			<AdminLlmModelAccessDialog
				open
				onOpenChange={vi.fn()}
				model={{ ...model, visibility: "GRANTED", grantedWorkspaceIds: [10] }}
				workspaceOptions={workspaces}
				isSubmitting={false}
				onSave={onSave}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Alpha" }));
		fireEvent.click(screen.getByRole("checkbox", { name: /beta/i }));
		fireEvent.click(screen.getByRole("button", { name: "Save access" }));

		expect(onSave).toHaveBeenCalledWith({ visibility: "GRANTED", workspaceIds: [10, 11] });
	});

	it("distinguishes future restrictions from removing current workspace access", () => {
		render(
			<AdminLlmModelAccessDialog
				open
				onOpenChange={vi.fn()}
				model={model}
				workspaceOptions={workspaces}
				isSubmitting={false}
				onSave={vi.fn()}
			/>,
		);

		fireEvent.click(screen.getByRole("radio", { name: /^Selected workspaces/i }));
		fireEvent.click(screen.getByRole("button", { name: "Select workspaces…" }));
		fireEvent.click(screen.getByRole("checkbox", { name: /alpha/i }));
		fireEvent.click(screen.getByRole("checkbox", { name: /beta/i }));

		expect(screen.getByText("Future workspaces will need an explicit grant")).toBeTruthy();
		expect(screen.queryByText(/existing configurations in removed workspaces/i)).toBeNull();
	});

	it("can grant public access even when the workspace directory is unavailable", () => {
		const onSave = vi.fn();
		render(
			<AdminLlmModelAccessDialog
				open
				onOpenChange={vi.fn()}
				model={{ ...model, visibility: "GRANTED", grantedWorkspaceIds: [10] }}
				workspaceOptions={[]}
				isWorkspaceError
				isSubmitting={false}
				onSave={onSave}
			/>,
		);

		fireEvent.click(screen.getByRole("radio", { name: /^All workspaces/i }));
		fireEvent.click(screen.getByRole("button", { name: "Save access" }));
		expect(onSave).toHaveBeenCalledWith({ visibility: "PUBLIC" });
	});
});
