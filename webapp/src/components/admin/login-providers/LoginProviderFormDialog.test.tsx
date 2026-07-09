import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { LoginProviderFormDialog } from "./LoginProviderFormDialog";

describe("LoginProviderFormDialog", () => {
	it("edits Slack identity providers without requiring a base URL", () => {
		const onUpdate = vi.fn();
		render(
			<LoginProviderFormDialog
				open
				onOpenChange={vi.fn()}
				editing={{
					registrationId: "slack",
					type: "SLACK",
					displayName: "Slack",
					baseUrl: "https://slack.com",
					scopes: "openid profile email",
					enabled: true,
					seededFromEnv: true,
					redirectUri: "https://hephaestus-test.felixdietrich.com/api/login/oauth2/code/slack",
					createdAt: new Date("2026-05-02T00:00:00Z"),
					updatedAt: new Date("2026-05-02T00:00:00Z"),
				}}
				isSubmitting={false}
				onCreate={vi.fn()}
				onUpdate={onUpdate}
			/>,
		);

		expect(screen.queryByLabelText("Instance base URL")).toBeNull();
		expect(screen.getByText(/Use the same Slack app client ID and secret/)).toBeTruthy();
		fireEvent.change(screen.getByLabelText("Client ID"), { target: { value: "slack-client" } });
		fireEvent.change(screen.getByLabelText("Client secret"), {
			target: { value: "slack-secret" },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

		expect(onUpdate).toHaveBeenCalledWith("slack", {
			displayName: "Slack",
			baseUrl: undefined,
			clientId: "slack-client",
			clientSecret: "slack-secret",
			scopes: "openid profile email",
		});
	});
});
