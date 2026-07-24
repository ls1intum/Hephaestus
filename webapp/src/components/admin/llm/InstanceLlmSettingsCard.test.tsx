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

	it("saves the workspace-provider toggle alongside the host allowlist", () => {
		const onSave = vi.fn();
		render(
			<InstanceLlmSettingsCard
				settings={{
					allowedEgressHosts: "api.openai.com",
					allowWorkspaceConnections: true,
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

		expect(onSave).toHaveBeenCalledWith(
			expect.objectContaining({
				allowedEgressHosts: "llm.example.com",
				allowWorkspaceConnections: true,
			}),
		);
	});
});
