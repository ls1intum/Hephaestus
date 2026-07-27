import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { AvailableLlmModel } from "@/api/types.gen";
import { priceLabel } from "@/lib/llm-pricing";
import { ModelPicker } from "./ModelPicker";

const models: AvailableLlmModel[] = [
	{
		id: 1,
		scope: "SHARED",
		displayName: "GPT-5",
		connectionDisplayName: "Organization endpoint",
		pricingMode: "PRICED",
		per1mInputUsd: 1,
		per1mOutputUsd: 2,
		supportsReasoning: true,
	},
	{
		id: 2,
		scope: "WORKSPACE",
		displayName: "GPT-5",
		connectionDisplayName: "Workspace endpoint",
		pricingMode: "NO_CHARGE",
		supportsReasoning: false,
	},
];

describe("ModelPicker", () => {
	it("distinguishes duplicate model names by connection in the selection and options", () => {
		render(
			<ModelPicker
				availableModels={models}
				value={{ scope: "SHARED", id: 1 }}
				onChange={vi.fn()}
			/>,
		);
		expect(screen.getByRole("combobox").textContent).toContain("GPT-5 · Organization endpoint");
		fireEvent.click(screen.getByRole("combobox"));
		expect(screen.getByRole("option", { name: /GPT-5 · Organization endpoint/ })).toBeTruthy();
		expect(screen.getByRole("option", { name: /GPT-5 · Workspace endpoint/ })).toBeTruthy();
		expect(screen.getByText("Shared models")).toBeTruthy();
		expect(screen.getByText("Your models")).toBeTruthy();
	});

	it("keeps the price in each option's accessible name", () => {
		render(<ModelPicker availableModels={models} value={null} onChange={vi.fn()} />);
		fireEvent.click(screen.getByRole("combobox"));

		// The price wording is `priceLabel`'s and is stated in `llm-pricing.test.ts`; what the picker
		// is answerable for is that it ends up in the *name*, not only in sighted text beside it.
		for (const model of models) {
			expect(
				screen.getByRole("option", {
					name: `${model.displayName} · ${model.connectionDisplayName} · ${priceLabel(model, "workspace")}`,
				}),
			).toBeTruthy();
		}
	});

	it("marks the trigger invalid and links its description when asked to", () => {
		render(
			<ModelPicker
				availableModels={models}
				value={null}
				onChange={vi.fn()}
				invalid
				aria-describedby="picker-hint"
			/>,
		);

		const trigger = screen.getByRole("combobox");
		expect(trigger.getAttribute("aria-invalid")).toBe("true");
		expect(trigger.getAttribute("aria-describedby")).toContain("picker-hint");
	});
});
