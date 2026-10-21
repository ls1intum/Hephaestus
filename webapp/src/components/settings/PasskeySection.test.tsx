import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PasskeySection } from "./PasskeySection";

const props = {
	supported: true,
	onRetry: vi.fn(),
	onRegister: vi.fn(),
	onVerify: vi.fn(),
	onProtection: vi.fn(),
	onRemove: vi.fn(),
	onCreateRecoveryCodes: vi.fn(),
	onRecover: vi.fn(),
};
const status = {
	protectionEnabled: false,
	recoveryRequired: false,
	instanceAdminRequired: true,
	workspaceAdminRequired: false,
	verified: false,
	credentials: [{ id: "one", label: "Personal device" }],
};
const twoPasskeys = [
	{ id: "one", label: "Personal device" },
	{ id: "two", label: "Phone" },
];

/** A gated control stays focusable, so it reports `aria-disabled` rather than `disabled`. */
function unavailable(name: string): boolean {
	return screen.getByRole("button", { name }).getAttribute("aria-disabled") === "true";
}

/** The words a control's `aria-describedby` points at. */
function reasonOf(name: string): string {
	const ids = screen.getByRole("button", { name }).getAttribute("aria-describedby") ?? "";
	return ids
		.split(" ")
		.map((id) => document.getElementById(id)?.textContent ?? "")
		.join(" ");
}

describe("PasskeySection", () => {
	it("keeps the last credential and requires verification for protection changes", () => {
		render(<PasskeySection {...props} status={status} />);
		expect(unavailable("Remove Personal device")).toBe(true);
		expect(reasonOf("Remove Personal device")).toContain("You cannot remove your last passkey.");
		expect(
			screen
				.getByRole("switch", { name: "Personal admin protection" })
				.getAttribute("aria-disabled"),
		).toBe("true");
		expect(unavailable("Create new recovery codes")).toBe(true);
		expect(unavailable("Verify passkey")).toBe(false);
	});
	it("asks for a confirmation before a second passkey is added, and says why", async () => {
		render(<PasskeySection {...props} status={status} />);
		await userEvent.setup().click(screen.getByRole("button", { name: "Register passkey" }));
		expect(unavailable("Register passkey")).toBe(true);
		expect(props.onRegister).not.toHaveBeenCalled();
		expect(reasonOf("Register passkey")).toContain("Confirm it’s you with one of your passkeys");
		expect(reasonOf("Register passkey")).toContain(
			"turns on personal admin protection and signs you out of your other sessions",
		);
	});
	it("adds a first passkey without a confirmation, also during recovery", async () => {
		render(
			<PasskeySection {...props} status={{ ...status, recoveryRequired: true, credentials: [] }} />,
		);
		expect(screen.queryByRole("button", { name: "Verify passkey" })).toBeNull();
		await userEvent.setup().click(screen.getByRole("button", { name: "Register passkey" }));
		expect(props.onRegister).toHaveBeenCalledWith("");
	});
	it("names each confirmed change by what it does", async () => {
		const user = userEvent.setup();
		render(
			<PasskeySection
				{...props}
				status={{ ...status, verified: true, credentials: twoPasskeys }}
			/>,
		);
		expect(screen.queryByRole("button", { name: "Verify passkey" })).toBeNull();
		expect(unavailable("Register passkey")).toBe(false);
		await user.click(screen.getByRole("button", { name: "Remove Phone" }));
		await user.click(await screen.findByRole("button", { name: "Remove passkey" }));
		expect(props.onRemove).toHaveBeenCalledWith("two");
		await user.click(screen.getByRole("button", { name: "Create new recovery codes" }));
		await user.click(await screen.findByRole("button", { name: "Create codes" }));
		expect(props.onCreateRecoveryCodes).toHaveBeenCalledOnce();
	});
	it("states that recovery does not restore admin access", () => {
		render(
			<PasskeySection {...props} status={{ ...status, recoveryRequired: true, credentials: [] }} />,
		);
		expect(screen.getByText(/Recovery is active/u).getAttribute("role")).toBe("alert");
	});
	it("shows a retry control when settings cannot load", () => {
		render(<PasskeySection {...props} error="Could not load passkeys." />);
		expect(screen.getByRole("alert").textContent).toContain("Could not load passkeys.");
		expect(screen.getByRole<HTMLButtonElement>("button", { name: "Retry" }).disabled).toBe(false);
	});
	it("does not permit enrollment in an unsupported browser", async () => {
		render(<PasskeySection {...props} supported={false} status={{ ...status, credentials: [] }} />);
		await userEvent.setup().type(screen.getByLabelText("Passkey name"), "Device");
		expect(unavailable("Register passkey")).toBe(true);
		expect(screen.getByText(/This browser cannot use passkeys/u).getAttribute("role")).toBe(
			"alert",
		);
	});
});
