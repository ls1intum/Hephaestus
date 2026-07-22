import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { AvailableLlmModel } from "@/api/types.gen";
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
		expect(screen.getByRole("option", { name: "GPT-5 · Organization endpoint" })).toBeTruthy();
		expect(screen.getByRole("option", { name: "GPT-5 · Workspace endpoint" })).toBeTruthy();
		expect(screen.getByText("Organization models")).toBeTruthy();
		expect(screen.getByText("Your providers")).toBeTruthy();
	});
});
