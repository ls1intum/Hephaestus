import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Dialog } from "@/components/ui/dialog";
import { AddLoginProviderDialog } from "./AddLoginProviderDialog";

// The dialog is a presentation component (no router/query), so it renders cheaply inside the
// shadcn <Dialog> wrapper it expects. We assert the client-side validation gate: submitting
// with empty/invalid required fields must NOT fire onSubmit, and a valid HTTPS issuer must.
function renderDialog(onSubmit = vi.fn()) {
	const result = render(
		<Dialog open>
			<AddLoginProviderDialog isSubmitting={false} onSubmit={onSubmit} />
		</Dialog>,
	);
	return { ...result, onSubmit };
}

function setField(label: string, value: string) {
	fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

function submit() {
	fireEvent.click(screen.getByRole("button", { name: /Validate.*add/ }));
}

describe("AddLoginProviderDialog", () => {
	it("blocks submission and shows field errors when required fields are empty", () => {
		const { baseElement, onSubmit } = renderDialog();

		submit();

		expect(onSubmit).not.toHaveBeenCalled();
		expect(baseElement.querySelector('[aria-invalid="true"]')).not.toBeNull();
	});

	it("rejects a non-HTTPS issuer URL", () => {
		const { onSubmit } = renderDialog();

		setField("Display name", "Acme");
		setField("Issuer / base URL", "http://git.example.com");
		setField("Client ID", "cid");
		setField("Client secret", "secret");
		submit();

		expect(onSubmit).not.toHaveBeenCalled();
	});

	it("submits a normalized payload when all fields are valid", () => {
		const { onSubmit } = renderDialog();

		setField("Display name", "  Acme GHE  ");
		setField("Issuer / base URL", " https://git.example.com ");
		setField("Client ID", " cid ");
		setField("Client secret", "shh");
		submit();

		expect(onSubmit).toHaveBeenCalledTimes(1);
		expect(onSubmit).toHaveBeenCalledWith({
			kind: "OIDC_LOGIN_GITHUB",
			userInput: {
				issuer_url: "https://git.example.com",
				client_id: "cid",
				client_secret: "shh",
				display_name: "Acme GHE",
			},
		});
	});
});
