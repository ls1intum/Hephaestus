import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { AgentConfig } from "@/api/types.gen";
import { AgentConfigForm } from "./AgentConfigForm";

const configWithRevokedModel: AgentConfig = {
	id: 1,
	name: "Reviewer",
	instanceModelId: 99,
	allowInternet: false,
	enabled: true,
	maxConcurrentJobs: 1,
	timeoutSeconds: 600,
	createdAt: new Date("2026-07-01T00:00:00Z"),
};

describe("AgentConfigForm", () => {
	it("omits an unchanged unavailable binding so the config can still be disabled", () => {
		const onUpdate = vi.fn();
		render(
			<AgentConfigForm
				config={configWithRevokedModel}
				availableModels={[]}
				isPending={false}
				onCreate={vi.fn()}
				onUpdate={onUpdate}
			/>,
		);

		fireEvent.click(screen.getByRole("switch", { name: "Enabled" }));
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));
		expect(onUpdate).toHaveBeenCalledWith(
			expect.objectContaining({
				enabled: false,
			}),
		);
		expect(onUpdate.mock.calls[0]?.[0]).not.toHaveProperty("instanceModelId");
		expect(onUpdate.mock.calls[0]?.[0]).not.toHaveProperty("workspaceModelId");
	});
});
