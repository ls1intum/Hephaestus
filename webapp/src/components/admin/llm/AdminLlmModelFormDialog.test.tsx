import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { LlmModel } from "@/api/types.gen";
import { validateLlmModelForm } from "@/lib/llm-form-validation";
import { expectUnavailable } from "@/test/controls";
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
	it("creates a model inactive and shared with no workspace by default", async () => {
		const onSave = renderDialog();
		const active = screen.getByRole("switch", { name: "Active" }) as HTMLButtonElement;
		expect(active.getAttribute("aria-checked")).toBe("false");
		await expectUnavailable(active);
		fireEvent.click(active);
		expect(active.getAttribute("aria-checked")).toBe("false");
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

	it("refuses an all-zero price, which the free option is for", () => {
		const onSave = renderDialog();
		fireEvent.change(screen.getByLabelText("Display name"), { target: { value: "GPT-5" } });
		fireEvent.change(screen.getByLabelText("Upstream model id"), { target: { value: "gpt-5" } });
		fireEvent.click(screen.getByRole("radio", { name: "Price per 1M tokens" }));
		fireEvent.change(screen.getByLabelText(/^Input \(USD\)/), { target: { value: "0" } });
		fireEvent.change(screen.getByLabelText(/^Output \(USD\)/), { target: { value: "0" } });

		fireEvent.click(screen.getByRole("button", { name: "Add model" }));

		const rejection = validateLlmModelForm({
			displayName: "GPT-5",
			upstreamModelId: "gpt-5",
			contextWindow: "",
			maxOutputTokens: "",
			pricingMode: "PRICED",
			per1mInputUsd: 0,
			per1mOutputUsd: 0,
		}).per1mInputUsd;
		expect(rejection).toBeTruthy();
		expect(screen.getByText(rejection as string)).toBeTruthy();
		expect(onSave).not.toHaveBeenCalled();
	});

	it("says why an out-of-range token count was rejected, instead of a Save that does nothing", () => {
		const onSave = renderDialog();
		fireEvent.change(screen.getByLabelText("Display name"), { target: { value: "GPT-5" } });
		fireEvent.change(screen.getByLabelText("Upstream model id"), { target: { value: "gpt-5" } });
		const contextWindow = screen.getByLabelText(/^Context window/);
		const maxOutput = screen.getByLabelText(/^Max output tokens/);
		fireEvent.change(contextWindow, { target: { value: "3000000000" } });
		fireEvent.change(maxOutput, { target: { value: "3000000000" } });

		fireEvent.click(screen.getByRole("button", { name: "Add model" }));

		const rejected = validateLlmModelForm({
			displayName: "GPT-5",
			upstreamModelId: "gpt-5",
			contextWindow: "3000000000",
			maxOutputTokens: "3000000000",
			pricingMode: "UNPRICED",
		});
		expect(rejected.contextWindow).toBeTruthy();
		expect(onSave).not.toHaveBeenCalled();

		const alerts = screen.getAllByRole("alert");
		expect(alerts.map((alert) => alert.textContent)).toEqual([
			rejected.contextWindow,
			rejected.maxOutputTokens,
		]);
		for (const [field, alert] of [
			[contextWindow, alerts[0]],
			[maxOutput, alerts[1]],
		] as const) {
			expect(field.getAttribute("aria-invalid")).toBe("true");
			expect(field.getAttribute("aria-describedby")).toBe(alert?.id);
		}

		fireEvent.change(contextWindow, { target: { value: "200000" } });
		fireEvent.change(maxOutput, { target: { value: "8000" } });
		fireEvent.click(screen.getByRole("button", { name: "Add model" }));
		expect(onSave).toHaveBeenCalledTimes(1);
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
		expect(
			screen.getByText("Work on this model stops immediately, in every workspace"),
		).toBeTruthy();
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		expect(onSave.mock.calls[0]?.[0].metadata).toEqual(expect.objectContaining({ enabled: false }));
	});
});
