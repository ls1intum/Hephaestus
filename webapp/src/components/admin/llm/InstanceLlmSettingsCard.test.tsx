import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { InstanceLlmSettingsCard } from "./InstanceLlmSettingsCard";

describe("InstanceLlmSettingsCard", () => {
	it("sends an explicit empty host list so an admin can clear the allowlist", () => {
		const onSave = vi.fn();
		render(
			<InstanceLlmSettingsCard
				settings={{
					allowedEgressHosts: "api.openai.com",
					allowWorkspaceConnections: true,
					defaultUnpricedPolicy: "WARN",
				}}
				isLoading={false}
				isSubmitting={false}
				onSave={onSave}
			/>,
		);
		fireEvent.change(screen.getByLabelText("Allowed provider hosts"), { target: { value: "" } });
		fireEvent.click(screen.getByRole("button", { name: "Save settings" }));
		expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ allowedEgressHosts: "" }));
	});

	it("preserves the current unpriced-usage policy when another setting changes", () => {
		const onSave = vi.fn();
		render(
			<InstanceLlmSettingsCard
				settings={{
					allowedEgressHosts: "api.openai.com",
					allowWorkspaceConnections: true,
					defaultUnpricedPolicy: "WARN",
				}}
				isLoading={false}
				isSubmitting={false}
				onSave={onSave}
			/>,
		);

		fireEvent.change(screen.getByLabelText("Allowed provider hosts"), {
			target: { value: "llm.example.com" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save settings" }));

		expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ defaultUnpricedPolicy: "WARN" }));
	});

	it("lets an instance admin block new AI work after unknown-price usage", () => {
		const onSave = vi.fn();
		render(
			<InstanceLlmSettingsCard
				settings={{
					allowWorkspaceConnections: true,
					defaultUnpricedPolicy: "WARN",
				}}
				isLoading={false}
				isSubmitting={false}
				onSave={onSave}
			/>,
		);

		fireEvent.click(screen.getByRole("radio", { name: /^Block new AI work/ }));
		fireEvent.click(screen.getByRole("button", { name: "Save settings" }));

		expect(screen.getByText(/pauses new AI work across the instance/i)).toBeTruthy();
		expect(onSave).toHaveBeenCalledWith(
			expect.objectContaining({ defaultUnpricedPolicy: "BLOCK" }),
		);
	});
});
