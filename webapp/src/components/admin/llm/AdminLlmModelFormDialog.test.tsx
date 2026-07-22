import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { LlmModel } from "@/api/types.gen";
import { AdminLlmModelFormDialog } from "./AdminLlmModelFormDialog";

function renderDialog(onSave = vi.fn()) {
	render(
		<AdminLlmModelFormDialog
			open
			onOpenChange={vi.fn()}
			editing={null}
			workspaceOptions={[]}
			probedModelIds={[]}
			isSubmitting={false}
			onSave={onSave}
		/>,
	);
	return onSave;
}

describe("AdminLlmModelFormDialog", () => {
	it("creates a model inactive and shared with no workspace by default", () => {
		const onSave = renderDialog();
		const active = screen.getByRole("switch", { name: "Active" }) as HTMLButtonElement;
		expect(active.getAttribute("aria-checked")).toBe("false");
		expect(active.hasAttribute("data-disabled")).toBe(true);
		expect(screen.queryByLabelText("Slug")).toBeNull();
		fireEvent.change(screen.getByLabelText("Display name"), { target: { value: "GPT-5" } });
		fireEvent.change(screen.getByLabelText("Upstream model id"), { target: { value: "gpt-5" } });
		fireEvent.click(screen.getByRole("button", { name: "Add model" }));
		expect(onSave).toHaveBeenCalledWith(
			expect.objectContaining({
				metadata: expect.objectContaining({ enabled: false }),
				sharing: { visibility: "GRANTED", workspaceIds: [] },
			}),
		);
	});

	it("keeps the upstream model identity immutable", () => {
		const onSave = vi.fn();
		const editing: LlmModel = {
			id: 1,
			slug: "gpt-5",
			displayName: "GPT-5",
			upstreamModelId: "gpt-5",
			connectionId: 1,
			connectionDisplayName: "OpenAI",
			enabled: false,
			supportsReasoning: true,
			visibility: "PUBLIC",
			grantedWorkspaceIds: [],
			createdAt: new Date("2026-07-01T00:00:00Z"),
		};
		render(
			<AdminLlmModelFormDialog
				open
				onOpenChange={vi.fn()}
				editing={editing}
				workspaceOptions={[]}
				probedModelIds={[]}
				isSubmitting={false}
				onSave={onSave}
			/>,
		);
		expect((screen.getByLabelText("Upstream model id") as HTMLInputElement).disabled).toBe(true);
		expect(screen.queryByLabelText("Initial workspace access")).toBeNull();
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		expect(onSave.mock.calls[0]?.[0].metadata).not.toHaveProperty("upstreamModelId");
		expect(onSave.mock.calls[0]?.[0]).not.toHaveProperty("sharing");
	});

	it("turns an active model off when its price becomes unknown", () => {
		const onSave = vi.fn();
		const editing: LlmModel = {
			id: 2,
			slug: "gpt-5-active",
			displayName: "GPT-5 active",
			upstreamModelId: "gpt-5",
			connectionId: 1,
			connectionDisplayName: "OpenAI",
			enabled: true,
			supportsReasoning: true,
			visibility: "GRANTED",
			grantedWorkspaceIds: [],
			currentPrice: {
				id: 1,
				pricingMode: "PRICED",
				per1mInputUsd: 1,
				per1mOutputUsd: 2,
				currency: "USD",
				effectiveFrom: new Date("2026-07-01T00:00:00Z"),
			},
			createdAt: new Date("2026-07-01T00:00:00Z"),
		};
		render(
			<AdminLlmModelFormDialog
				open
				onOpenChange={vi.fn()}
				editing={editing}
				workspaceOptions={[]}
				probedModelIds={[]}
				isSubmitting={false}
				onSave={onSave}
			/>,
		);

		fireEvent.click(screen.getByRole("radio", { name: "No price set" }));
		expect(screen.getByRole("switch", { name: "Active" }).getAttribute("aria-checked")).toBe(
			"false",
		);
		expect(screen.getByText("Existing configurations will stop immediately")).toBeTruthy();
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		expect(onSave.mock.calls[0]?.[0].metadata).toEqual(expect.objectContaining({ enabled: false }));
	});
});
